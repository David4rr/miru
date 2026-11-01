use super::{BoxFuture, ProviderSearchResult, ResolvedStreamSource, StreamProvider, StreamServer};
use crate::db::SubtitleTrack;
use anyhow::{anyhow, Result};
use base64::prelude::*;
use reqwest::Client;
use serde_json::Value;
use std::sync::LazyLock;
use std::time::Duration;

const HI_BASE: &str = "https://hianime.at";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(Duration::from_secs(8))
        .build()
        .expect("Failed to initialize reqwest client")
});

pub struct HiAnimeProvider;

impl HiAnimeProvider {
    pub fn new() -> Self {
        Self
    }
}

pub fn deobfuscate_zoko(html: &str) -> Result<(String, Vec<SubtitleTrack>)> {
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
            let raw_lang = s.get("lang").and_then(|l| l.as_str()).unwrap_or("en").to_string();
            let src = s.get("src").and_then(|f| f.as_str()).unwrap_or("").to_string();
            let is_default = s.get("default").and_then(|d| d.as_bool()).unwrap_or(false);

            if !src.is_empty() {
                subtitles.push(SubtitleTrack { label, lang: raw_lang, url: src, default: is_default });
            }
        }
    }
    Ok((master_src, subtitles))
}

async fn try_fetch_embed(url: &str) -> Option<(String, Vec<SubtitleTrack>)> {
    let resp = HTTP_CLIENT.get(url).header("Referer", "https://hianime.at/").send().await.ok()?;
    let html = resp.text().await.ok()?;
    deobfuscate_zoko(&html).ok()
}

async fn fetch_zoko_by_mal(mal_id: i64, episode: i32, mode: &str) -> Result<(String, Vec<SubtitleTrack>)> {
    let url = format!("https://zokoanime.video/stream/mal/{}/{}/{}", mal_id, episode, mode);
    let html = HTTP_CLIENT.get(&url).header("Referer", "https://hianime.at/").send().await?.text().await?;
    deobfuscate_zoko(&html)
}

async fn resolve_via_hianime_scraping(title: &str, episode: i32, server_hint: Option<&str>) -> Result<(String, Vec<SubtitleTrack>, String)> {
    let search_url = format!("{}/search?keyword={}", HI_BASE, urlencoding::encode(title));
    let search_resp = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
    let re_anime_id = regex::Regex::new(r#"href="(?:https://hianime\.at)?(?:/watch)?/[^"?/]+-(\d+)""#)?;
    let anime_id = re_anime_id
        .captures(&search_resp)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .ok_or_else(|| anyhow!("Anime not found on HiAnime for query: {}", title))?;

    let ep_list_url = format!("{}/api/theme/episode/list/{}", HI_BASE, anime_id);
    let ep_resp = HTTP_CLIENT.get(&ep_list_url).send().await?.json::<Value>().await?;
    let ep_html = ep_resp.get("html").and_then(|h| h.as_str()).ok_or_else(|| anyhow!("Missing ep list HTML"))?;

    let re_ep_1 = regex::Regex::new(&format!(r#"data-number="{}"[^>]*data-id="(\d+)""#, episode))?;
    let re_ep_2 = regex::Regex::new(&format!(r#"data-id="(\d+)"[^>]*data-number="{}""#, episode))?;
    let target_ep_id = re_ep_1.captures(ep_html).and_then(|c| c.get(1)).map(|m| m.as_str().to_string())
        .or_else(|| re_ep_2.captures(ep_html).and_then(|c| c.get(1)).map(|m| m.as_str().to_string()))
        .ok_or_else(|| anyhow!("Episode {} not found in HiAnime episode list", episode))?;

    let servers_url = format!("{}/api/theme/episode/servers?episodeId={}", HI_BASE, target_ep_id);
    let srv_resp = HTTP_CLIENT.get(&servers_url).send().await?.json::<Value>().await?;
    let srv_html = srv_resp.get("html").and_then(|h| h.as_str()).ok_or_else(|| anyhow!("Missing servers HTML"))?;

    let re_hash = regex::Regex::new(r#"data-hash="([^"]+)""#)?;
    let mut embed_urls = Vec::new();
    for cap in re_hash.captures_iter(srv_html) {
        if let Some(h) = cap.get(1) {
            if let Ok(bytes) = BASE64_STANDARD.decode(h.as_str()) {
                if let Ok(url) = String::from_utf8(bytes) {
                    embed_urls.push(url);
                }
            }
        }
    }
    if embed_urls.is_empty() {
        return Err(anyhow!("No playable streaming servers found for episode {}", episode));
    }
    if let Some(hint) = server_hint {
        for url in &embed_urls {
            if url.to_lowercase().contains(&hint.to_lowercase()) {
                if let Some((m, s)) = try_fetch_embed(url).await {
                    return Ok((m, s, "https://zokoanime.video/".to_string()));
                }
            }
        }
    }
    for url in &embed_urls {
        if let Some((m, s)) = try_fetch_embed(url).await {
            return Ok((m, s, "https://zokoanime.video/".to_string()));
        }
    }
    Err(anyhow!("Failed to decode video sources from server hashes"))
}

impl StreamProvider for HiAnimeProvider {
    fn id(&self) -> &'static str { "hianime" }
    fn name(&self) -> &'static str { "HiAnime (Global)" }
    fn has_sub(&self) -> bool { true }
    fn has_dub(&self) -> bool { true }

    fn search<'a>(&'a self, query: &'a str) -> BoxFuture<'a, Result<Vec<ProviderSearchResult>>> {
        Box::pin(async move {
            let search_url = format!("{}/search?keyword={}", HI_BASE, urlencoding::encode(query));
            let html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
            let re_item = regex::Regex::new(r#"href="([^"]+/watch/[^"]+-(\d+))"[^>]*title="([^"]+)""#)?;
            let mut results = Vec::new();
            for cap in re_item.captures_iter(&html) {
                if let (Some(url), Some(id), Some(title)) = (cap.get(1), cap.get(2), cap.get(3)) {
                    results.push(ProviderSearchResult {
                        id: id.as_str().to_string(),
                        title: title.as_str().to_string(),
                        url: url.as_str().to_string(),
                        poster: None,
                    });
                }
            }
            Ok(results)
        })
    }

    fn get_servers<'a>(&'a self, _slug: &'a str, _ep: i32, mode: &'a str) -> BoxFuture<'a, Result<Vec<StreamServer>>> {
        Box::pin(async move {
            let tag = if mode.eq_ignore_ascii_case("dub") { "DUB" } else { "SUB" };
            Ok(vec![
                StreamServer { server_id: "megacloud".to_string(), server_name: format!("MegaCloud (HD-1 {})", tag), latency_ms: 120 },
                StreamServer { server_id: "vidcloud".to_string(), server_name: format!("VidCloud (HD-2 {})", tag), latency_ms: 145 },
                StreamServer { server_id: "zokoanime".to_string(), server_name: format!("ZokoAnime (Direct {})", tag), latency_ms: 95 },
            ])
        })
    }

    fn resolve_episode<'a>(
        &'a self,
        slug: &'a str,
        ep: i32,
        mode: &'a str,
        server: Option<&'a str>,
        mal_id: Option<i64>,
        title_hint: Option<&'a str>,
    ) -> BoxFuture<'a, Result<ResolvedStreamSource>> {
        Box::pin(async move {
            let active_mode = if mode.eq_ignore_ascii_case("dub") { "dub" } else { "sub" };
            let title = title_hint.unwrap_or(slug);

            if let Some(m_id) = mal_id {
                if let Ok((master, subs)) = fetch_zoko_by_mal(m_id, ep, active_mode).await {
                    return Ok(ResolvedStreamSource {
                        master,
                        referer: "https://zokoanime.video/".to_string(),
                        server: server.unwrap_or("zokoanime").to_string(),
                        subtitles: subs,
                    });
                }
            }

            let (master, subs, referer) = resolve_via_hianime_scraping(title, ep, server).await?;
            Ok(ResolvedStreamSource {
                master,
                referer,
                server: server.unwrap_or("megacloud").to_string(),
                subtitles: subs,
            })
        })
    }
}
