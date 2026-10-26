use crate::db::{CachedEpisode, Database, SkipInterval, SubtitleTrack};
use anyhow::{anyhow, Result};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::LazyLock;

const HI_BASE: &str = "https://hianime.at";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(std::time::Duration::from_secs(12))
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

pub async fn resolve_stream(
    slug: &str,
    episode: i32,
    mode: &str,
    db: &Database,
) -> Result<ResolvedStream> {
    // 1. Check local SQLite cache first (TTL 2 hours)
    if let Ok(Some(cached)) = db.get_episode_cache(slug, episode, mode, 7200) {
        let proxied = format!(
            "/api/player/video?url={}&referer={}",
            urlencoding::encode(&cached.master),
            urlencoding::encode(&cached.referer)
        );

        return Ok(ResolvedStream {
            slug: cached.slug,
            episode: cached.ep,
            mode: cached.mode,
            master: cached.master,
            referer: cached.referer,
            server: cached.server,
            subtitles: cached.subtitles,
            intro: cached.intro,
            outro: cached.outro,
            proxied_master_url: proxied,
        });
    }

    // 2. Fetch episode server list from HiAnime
    // Step A: Search anime on HiAnime to get anime ID
    let search_url = format!("{}/search?keyword={}", HI_BASE, urlencoding::encode(slug));
    let search_resp = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
    
    // Parse anime id from HTML (e.g. data-id="12345" or href="/watch/slug-12345")
    let re_anime_id = regex::Regex::new(r#"/watch/[^"?]+-(\d+)"#)?;
    let anime_id = re_anime_id
        .captures(&search_resp)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .ok_or_else(|| anyhow!("Anime not found on stream provider for slug: {}", slug))?;

    // Step B: Get episodes list for anime ID
    let ep_list_url = format!("{}/api/theme/episode/list/{}", HI_BASE, anime_id);
    let ep_resp = HTTP_CLIENT.get(&ep_list_url).send().await?.json::<Value>().await?;
    let ep_html = ep_resp
        .get("html")
        .and_then(|h| h.as_str())
        .ok_or_else(|| anyhow!("Failed to get episode list HTML"))?;

    // Find the episode ID matching episode number
    let re_ep_item = regex::Regex::new(r#"data-number="(\d+)"[^>]*data-id="(\d+)""#)?;
    let mut target_ep_id = None;
    for cap in re_ep_item.captures_iter(ep_html) {
        let num: i32 = cap[1].parse().unwrap_or(0);
        if num == episode {
            target_ep_id = Some(cap[2].to_string());
            break;
        }
    }
    let episode_id = target_ep_id.ok_or_else(|| anyhow!("Episode {} not found", episode))?;

    // Step C: Get server list for episode ID
    let servers_url = format!("{}/api/theme/episode/servers?episodeId={}", HI_BASE, episode_id);
    let srv_resp = HTTP_CLIENT.get(&servers_url).send().await?.json::<Value>().await?;
    let srv_html = srv_resp
        .get("html")
        .and_then(|h| h.as_str())
        .ok_or_else(|| anyhow!("Failed to get server list HTML"))?;

    // Extract server link (HD-1 / MegaCloud / RapidCloud)
    let re_server_id = regex::Regex::new(r#"data-id="(\d+)"[^>]*data-server-id="(\d+)""#)?;
    let mut server_id = None;
    for cap in re_server_id.captures_iter(srv_html) {
        server_id = Some(cap[1].to_string());
        break;
    }
    let s_id = server_id.ok_or_else(|| anyhow!("No streaming server found"))?;

    // Step D: Get embed link from server ID
    let embed_api = format!("{}/api/theme/episode/sources?id={}", HI_BASE, s_id);
    let embed_resp = HTTP_CLIENT.get(&embed_api).send().await?.json::<Value>().await?;
    let embed_link = embed_resp
        .get("link")
        .and_then(|l| l.as_str())
        .ok_or_else(|| anyhow!("Missing embed link in server source"))?;

    // Step E: Extract master stream and tracks from MegaCloud/RapidCloud embed
    let referer = embed_link.to_string();
    let re_embed_id = regex::Regex::new(r#"/embed-2/e-1/([^?]+)"#)?;
    let embed_source_id = re_embed_id
        .captures(embed_link)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str())
        .unwrap_or("");

    let sources_url = format!("https://megacloud.tv/embed-2/ajax/e-1/getSources?id={}", embed_source_id);
    let sources_resp = HTTP_CLIENT
        .get(&sources_url)
        .header("Referer", &referer)
        .header("X-Requested-With", "XMLHttpRequest")
        .send()
        .await?
        .json::<Value>()
        .await?;

    let master_stream = sources_resp
        .get("sources")
        .and_then(|s| s.as_array())
        .and_then(|arr| arr.first())
        .and_then(|obj| obj.get("file"))
        .and_then(|f| f.as_str())
        .ok_or_else(|| anyhow!("Failed to extract master HLS stream URL"))?
        .to_string();

    let mut subtitles = Vec::new();
    if let Some(tracks) = sources_resp.get("tracks").and_then(|t| t.as_array()) {
        for t in tracks {
            if t.get("kind").and_then(|k| k.as_str()) == Some("captions") {
                if let (Some(file), Some(label)) = (
                    t.get("file").and_then(|f| f.as_str()),
                    t.get("label").and_then(|l| l.as_str()),
                ) {
                    subtitles.push(SubtitleTrack {
                        label: label.to_string(),
                        lang: label.to_lowercase(),
                        url: file.to_string(),
                        default: t.get("default").and_then(|d| d.as_bool()).unwrap_or(false),
                    });
                }
            }
        }
    }

    let intro = SkipInterval {
        start: sources_resp.get("intro").and_then(|i| i.get("start")).and_then(|s| s.as_f64()).unwrap_or(0.0),
        end: sources_resp.get("intro").and_then(|i| i.get("end")).and_then(|s| s.as_f64()).unwrap_or(0.0),
    };

    let outro = SkipInterval {
        start: sources_resp.get("outro").and_then(|o| o.get("start")).and_then(|s| s.as_f64()).unwrap_or(0.0),
        end: sources_resp.get("outro").and_then(|o| o.get("end")).and_then(|s| s.as_f64()).unwrap_or(0.0),
    };

    // 3. Cache into SQLite
    let cached = CachedEpisode {
        slug: slug.to_string(),
        ep: episode,
        mode: mode.to_string(),
        master: master_stream.clone(),
        variants: Value::Array(vec![]),
        sub: subtitles.first().map(|s| s.url.clone()).unwrap_or_default(),
        sub_lang: subtitles.first().map(|s| s.label.clone()).unwrap_or_default(),
        referer: referer.clone(),
        server: "megacloud".to_string(),
        subtitles: subtitles.clone(),
        intro: SkipInterval { start: intro.start, end: intro.end },
        outro: SkipInterval { start: outro.start, end: outro.end },
        fetched_at: 0,
    };
    let _ = db.set_episode_cache(&cached);

    let proxied = format!(
        "/api/player/video?url={}&referer={}",
        urlencoding::encode(&master_stream),
        urlencoding::encode(&referer)
    );

    Ok(ResolvedStream {
        slug: slug.to_string(),
        episode,
        mode: mode.to_string(),
        master: master_stream,
        referer,
        server: "megacloud".to_string(),
        subtitles,
        intro,
        outro,
        proxied_master_url: proxied,
    })
}
