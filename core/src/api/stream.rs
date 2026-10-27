use crate::api::sub::fetch_aniskip_intervals;
use crate::db::{CachedEpisode, Database, SkipInterval, SubtitleTrack};
use anyhow::{anyhow, Result};
use base64::prelude::*;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::LazyLock;
use std::time::Duration;

const HI_BASE: &str = "https://hianime.at";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(Duration::from_secs(12))
        .build()
        .expect("Failed to initialize reqwest client")
});

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ResolvedStream {
    pub slug: String,
    pub episode: i32,
    pub mode: String,
    pub master: String,
    pub referer: String,
    pub server: String,
    pub subtitles: Vec<SubtitleTrack>,
    pub intro: SkipInterval,
    pub outro: SkipInterval,
    pub proxied_master_url: String,
}

pub fn clean_slug_to_title(slug: &str) -> String {
    let parts: Vec<&str> = slug.split('-').collect();
    if parts.len() > 1 && parts.last().map(|p| p.chars().all(|c| c.is_ascii_digit())).unwrap_or(false) {
        parts[..parts.len() - 1].join(" ")
    } else {
        slug.replace('-', " ")
    }
}

pub fn try_extract_anilist_id_from_slug(slug: &str) -> Option<i64> {
    let parts: Vec<&str> = slug.split('-').collect();
    if parts.len() > 1 {
        if let Some(last) = parts.last() {
            if let Ok(id) = last.parse::<i64>() {
                if id > 0 {
                    return Some(id);
                }
            }
        }
    }
    None
}

fn deobfuscate_zoko(html: &str) -> Result<(String, Vec<SubtitleTrack>)> {
    let re_p = regex::Regex::new(r#"window\.__P="([^"]+)""#)?;
    let blob = re_p
        .captures(html)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str())
        .ok_or_else(|| anyhow!("window.__P not found in embed page"))?;

    let decoded = BASE64_STANDARD.decode(blob)?;
    const KEY: &[u8] = b"otaku-embed-v1";
    let mut xored = Vec::with_capacity(decoded.len());
    for (i, b) in decoded.iter().enumerate() {
        xored.push(b ^ KEY[i % KEY.len()]);
    }

    let decoded_str = String::from_utf8_lossy(&xored);
    let unquoted = urlencoding::decode(&decoded_str)?;
    let json_val: Value = serde_json::from_str(&unquoted)?;

    let master_src = json_val
        .get("src")
        .and_then(|s| s.as_str())
        .ok_or_else(|| anyhow!("src not found in deobfuscated payload"))?
        .to_string();

    let mut subtitles = Vec::new();
    if let Some(subs_arr) = json_val.get("subtitles").and_then(|s| s.as_array()) {
        for s in subs_arr {
            let label = s.get("label").and_then(|l| l.as_str()).unwrap_or("English").to_string();
            let lang = s.get("lang").and_then(|l| l.as_str()).unwrap_or("en").to_string();
            let src = s.get("src").and_then(|f| f.as_str()).unwrap_or("").to_string();
            let is_default = s.get("default").and_then(|d| d.as_bool()).unwrap_or(false);

            if !src.is_empty() {
                subtitles.push(SubtitleTrack {
                    label,
                    lang,
                    url: src,
                    default: is_default,
                });
            }
        }
    }

    Ok((master_src, subtitles))
}

async fn fetch_zoko_by_mal(mal_id: i64, episode: i32, mode: &str) -> Result<(String, Vec<SubtitleTrack>)> {
    let url = format!("https://zokoanime.video/stream/mal/{}/{}/{}", mal_id, episode, mode);
    let html = HTTP_CLIENT
        .get(&url)
        .header("Referer", "https://hianime.at/")
        .send()
        .await?
        .text()
        .await?;

    let (master_src, subs) = deobfuscate_zoko(&html)?;

    // Fast verify upstream master M3U8 is live
    let check_resp = HTTP_CLIENT
        .get(&master_src)
        .header("Referer", "https://zokoanime.video/")
        .send()
        .await?;
    if !check_resp.status().is_success() {
        return Err(anyhow!("Upstream stream returned HTTP {}", check_resp.status()));
    }

    Ok((master_src, subs))
}

async fn resolve_via_hianime_scraping(title: &str, episode: i32) -> Result<(String, Vec<SubtitleTrack>, String)> {
    // Step 1: Search anime on HiAnime
    let search_url = format!("{}/search?keyword={}", HI_BASE, urlencoding::encode(title));
    let search_resp = HTTP_CLIENT.get(&search_url).send().await?.text().await?;

    // HiAnime search results: match both /slug-12345 and /watch/slug-12345
    let re_anime_id = regex::Regex::new(r#"href="(?:https://hianime\.at)?(?:/watch)?/[^"?/]+-(\d+)""#)?;
    let anime_id = re_anime_id
        .captures(&search_resp)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .ok_or_else(|| anyhow!("Anime not found on HiAnime for query: {}", title))?;

    // Step 2: Get episode list
    let ep_list_url = format!("{}/api/theme/episode/list/{}", HI_BASE, anime_id);
    let ep_resp = HTTP_CLIENT.get(&ep_list_url).send().await?.json::<Value>().await?;
    let ep_html = ep_resp
        .get("html")
        .and_then(|h| h.as_str())
        .ok_or_else(|| anyhow!("Failed to get episode list HTML"))?;

    // Find the episode ID matching episode number
    let re_ep_1 = regex::Regex::new(&format!(r#"data-number="{}"[^>]*data-id="(\d+)""#, episode))?;
    let re_ep_2 = regex::Regex::new(&format!(r#"data-id="(\d+)"[^>]*data-number="{}""#, episode))?;
    let target_ep_id = re_ep_1
        .captures(ep_html)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .or_else(|| {
            re_ep_2
                .captures(ep_html)
                .and_then(|c| c.get(1))
                .map(|m| m.as_str().to_string())
        })
        .ok_or_else(|| anyhow!("Episode {} not found in HiAnime episode list", episode))?;

    // Step 3: Get server list
    let servers_url = format!("{}/api/theme/episode/servers?episodeId={}", HI_BASE, target_ep_id);
    let srv_resp = HTTP_CLIENT.get(&servers_url).send().await?.json::<Value>().await?;
    let srv_html = srv_resp
        .get("html")
        .and_then(|h| h.as_str())
        .ok_or_else(|| anyhow!("Failed to get server list HTML"))?;

    // Extract data-hash attributes (base64 embed URLs)
    let re_hash = regex::Regex::new(r#"data-hash="([^"]+)""#)?;
    let mut embed_urls = Vec::new();
    for cap in re_hash.captures_iter(srv_html) {
        if let Some(h) = cap.get(1) {
            if let Ok(decoded_bytes) = BASE64_STANDARD.decode(h.as_str()) {
                if let Ok(decoded_url) = String::from_utf8(decoded_bytes) {
                    embed_urls.push(decoded_url);
                }
            }
        }
    }

    if embed_urls.is_empty() {
        return Err(anyhow!("No playable streaming servers found for episode {}", episode));
    }

    // Try ZokoAnime first if present, otherwise try other servers
    for embed_url in &embed_urls {
        if embed_url.contains("zokoanime") {
            let html = HTTP_CLIENT
                .get(embed_url)
                .header("Referer", "https://hianime.at/")
                .send()
                .await?
                .text()
                .await?;
            if let Ok((master, subs)) = deobfuscate_zoko(&html) {
                return Ok((master, subs, "https://zokoanime.video/".to_string()));
            }
        }
    }

    // Fallback: try any embed URL with zoko deobfuscation
    for embed_url in &embed_urls {
        if let Ok(resp) = HTTP_CLIENT.get(embed_url).header("Referer", "https://hianime.at/").send().await {
            if let Ok(html) = resp.text().await {
                if let Ok((master, subs)) = deobfuscate_zoko(&html) {
                    return Ok((master, subs, "https://zokoanime.video/".to_string()));
                }
            }
        }
    }

    Err(anyhow!("Failed to decode video sources from server hashes"))
}

pub async fn resolve_stream(
    slug: &str,
    episode: i32,
    mode: &str,
    mal_id: Option<i64>,
    db: &Database,
) -> Result<ResolvedStream> {
    // 1. Check local SQLite cache first (TTL 2 hours)
    if let Ok(Some(cached)) = db.get_episode_cache(slug, episode, mode, 7200) {
        let proxied = format!(
            "http://127.0.0.1:8767/api/player/video?url={}&referer={}",
            urlencoding::encode(&cached.master),
            urlencoding::encode(&cached.referer)
        );

        let proxied_subtitles = cached.subtitles.into_iter().map(|mut s| {
            if !s.url.starts_with("http://127.0.0.1:8767") {
                let is_id = s.lang == "id" || s.label.contains("Indonesia");
                let sub_mode = if is_id { "gtx+" } else { "raw" };
                s.url = format!(
                    "http://127.0.0.1:8767/api/player/sub?url={}&mode={}",
                    urlencoding::encode(&s.url),
                    sub_mode
                );
            }
            s
        }).collect();

        return Ok(ResolvedStream {
            slug: cached.slug,
            episode: cached.ep,
            mode: cached.mode,
            master: cached.master,
            referer: cached.referer,
            server: cached.server,
            subtitles: proxied_subtitles,
            intro: cached.intro,
            outro: cached.outro,
            proxied_master_url: proxied,
        });
    }

    // 2. Resolve target MAL ID (from param or query AniList GraphQL using ID from slug)
    let target_mal_id = match mal_id {
        Some(m) if m > 0 => Some(m),
        _ => {
            if let Some(al_id) = try_extract_anilist_id_from_slug(slug) {
                crate::api::anilist::get_mal_id_by_anilist_id(al_id).await.unwrap_or(None)
            } else {
                let title = clean_slug_to_title(slug);
                crate::api::anilist::search_anime(&title, 1, 1, db).await.ok()
                    .and_then(|cards| cards.into_iter().next())
                    .and_then(|c| c.id_mal)
            }
        }
    };
    // 3. Resolve master stream and subtitles
    let (master_stream, mut subtitles, referer) = match target_mal_id {
        Some(m_id) => {
            // Direct MAL extraction
            match fetch_zoko_by_mal(m_id, episode, mode).await {
                Ok((master, subs)) => (master, subs, "https://zokoanime.video/".to_string()),
                Err(_) => {
                    // Fallback to title search on HiAnime
                    let title = clean_slug_to_title(slug);
                    resolve_via_hianime_scraping(&title, episode).await?
                }
            }
        }
        None => {
            let title = clean_slug_to_title(slug);
            resolve_via_hianime_scraping(&title, episode).await?
        }
    };

    // 4. Fetch AniSkip OP/ED intervals if MAL ID is available
    let (intro, outro) = if let Some(m_id) = target_mal_id {
        if let Ok(aniskip) = fetch_aniskip_intervals(m_id, episode, 0.0).await {
            let op_interval = aniskip.op.map(|i| SkipInterval { start: i.start, end: i.end }).unwrap_or(SkipInterval { start: 0.0, end: 0.0 });
            let ed_interval = aniskip.ed.map(|i| SkipInterval { start: i.start, end: i.end }).unwrap_or(SkipInterval { start: 0.0, end: 0.0 });
            (op_interval, ed_interval)
        } else {
            (SkipInterval { start: 0.0, end: 0.0 }, SkipInterval { start: 0.0, end: 0.0 })
        }
    } else {
        (SkipInterval { start: 0.0, end: 0.0 }, SkipInterval { start: 0.0, end: 0.0 })
    };

    // 5. Indonesian Subtitle Handling & Local Proxy Rewriting
    let has_indo = subtitles.iter().any(|s| {
        let l = s.lang.to_lowercase();
        let lb = s.label.to_lowercase();
        l == "id" || l == "ind" || lb.contains("indonesia")
    });

    if !has_indo {
        // If English subtitle exists, add auto-translated Indonesian subtitle track
        if let Some(eng_sub) = subtitles.iter().find(|s| {
            let l = s.lang.to_lowercase();
            let lb = s.label.to_lowercase();
            l == "en" || lb.contains("english")
        }) {
            let raw_eng_url = eng_sub.url.clone();
            let translated_url = format!(
                "http://127.0.0.1:8767/api/player/sub?url={}&mode=gtx+",
                urlencoding::encode(&raw_eng_url)
            );
            subtitles.insert(0, SubtitleTrack {
                label: "Indonesia (Otomatis)".to_string(),
                lang: "id".to_string(),
                url: translated_url,
                default: true,
            });
        }
    }

    // Ensure EVERY subtitle URL points to the local daemon proxy on 127.0.0.1:8767
    for s in &mut subtitles {
        let l = s.lang.to_lowercase();
        let lb = s.label.to_lowercase();
        let is_id = l == "id" || l == "ind" || lb.contains("indonesia");
        s.default = is_id;

        if !s.url.starts_with("http://127.0.0.1:8767") {
            let sub_mode = if is_id { "gtx+" } else { "raw" };
            s.url = format!(
                "http://127.0.0.1:8767/api/player/sub?url={}&mode={}",
                urlencoding::encode(&s.url),
                sub_mode
            );
        }
    }

    // 6. Cache into SQLite
    let cached = CachedEpisode {
        slug: slug.to_string(),
        ep: episode,
        mode: mode.to_string(),
        master: master_stream.clone(),
        variants: Value::Array(vec![]),
        sub: subtitles.first().map(|s| s.url.clone()).unwrap_or_default(),
        sub_lang: subtitles.first().map(|s| s.label.clone()).unwrap_or_default(),
        referer: referer.clone(),
        server: "zokoanime".to_string(),
        subtitles: subtitles.clone(),
        intro: SkipInterval { start: intro.start, end: intro.end },
        outro: SkipInterval { start: outro.start, end: outro.end },
        fetched_at: 0,
    };
    let _ = db.set_episode_cache(&cached);

    let proxied = format!(
        "http://127.0.0.1:8767/api/player/video?url={}&referer={}",
        urlencoding::encode(&master_stream),
        urlencoding::encode(&referer)
    );

    Ok(ResolvedStream {
        slug: slug.to_string(),
        episode,
        mode: mode.to_string(),
        master: master_stream,
        referer,
        server: "zokoanime".to_string(),
        subtitles,
        intro,
        outro,
        proxied_master_url: proxied,
    })
}
