use super::otakudesu_extractor::{extract_direct_stream, resolve_ajax_mirror};
use super::{BoxFuture, ProviderSearchResult, ResolvedStreamSource, StreamProvider, StreamServer};
use crate::db::SubtitleTrack;
use anyhow::{anyhow, Result};
use reqwest::Client;
use scraper::{Html, Selector};
use std::sync::LazyLock;
use std::time::Duration;

const OTAKU_BASE: &str = "https://otakudesu.blog";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(Duration::from_secs(12))
        .build()
        .expect("Failed to initialize reqwest client for otakudesu")
});

pub struct OtakudesuProvider;

impl OtakudesuProvider {
    pub fn new() -> Self {
        Self
    }
}

pub fn parse_search_results(html_str: &str) -> Vec<ProviderSearchResult> {
    let doc = Html::parse_document(html_str);
    let item_sel = Selector::parse("ul.chivsrc li, ul.channellist li, .venser .venz ul li").ok();
    let link_sel = Selector::parse("h2 a, a").ok();
    let img_sel = Selector::parse("img").ok();

    let mut results = Vec::new();
    if let (Some(is), Some(ls)) = (item_sel, link_sel) {
        for item in doc.select(&is) {
            if let Some(link) = item.select(&ls).next() {
                let url = link.value().attr("href").unwrap_or("");
                let title = link.text().collect::<String>().trim().to_string();
                let poster = img_sel.as_ref().and_then(|sel| {
                    item.select(sel).next().and_then(|img| img.value().attr("src")).map(|s| s.to_string())
                });

                if !url.is_empty() && !title.is_empty() {
                    let id = url.trim_end_matches('/').rsplit('/').next().unwrap_or(url).to_string();
                    results.push(ProviderSearchResult { id, title, url: url.to_string(), poster });
                }
            }
        }
    }
    results
}

pub fn parse_mirror_embeds(html_str: &str) -> Vec<(String, String)> {
    let doc = Html::parse_document(html_str);
    let mut mirrors = Vec::new();

    // 1. Primary responsive embed stream iframe
    if let Ok(iframe_sel) = Selector::parse("div.responsive-embed-stream iframe, iframe[src]") {
        for iframe in doc.select(&iframe_sel) {
            if let Some(src) = iframe.value().attr("src") {
                if !src.is_empty() && (src.starts_with("http") || src.starts_with("//")) {
                    let full = if src.starts_with("//") { format!("https:{}", src) } else { src.to_string() };
                    mirrors.push(("desustream".to_string(), full));
                }
            }
        }
    }

    // 2. Secondary mirror items with data-content payloads
    if let Ok(a_sel) = Selector::parse(".mirrorstream ul li a, ul.mirrorstream li a") {
        for a in doc.select(&a_sel) {
            let name = a.text().collect::<String>().trim().to_lowercase();
            let src = a.value().attr("data-content")
                .or_else(|| a.value().attr("href"))
                .unwrap_or("");
            if !src.is_empty() && !src.starts_with('#') {
                mirrors.push((name, src.to_string()));
            }
        }
    }
    mirrors
}

pub fn find_episode_link(anime_html: &str, ep: i32) -> Option<String> {
    let doc = Html::parse_document(anime_html);
    let a_sel = Selector::parse("a[href*='/episode/']").ok()?;
    let ep_pat = format!("episode {}", ep);
    let ep_slug_1 = format!("-episode-{}-", ep);
    let ep_slug_2 = format!("-episode-{}/", ep);
    let ep_slug_3 = format!("-episode-0{}-", ep);

    for a in doc.select(&a_sel) {
        let href = a.value().attr("href")?;
        let text = a.text().collect::<String>().to_lowercase();
        if href.contains("pembatas") || text.contains("pembatas") {
            continue;
        }
        if text.contains(&ep_pat) || href.contains(&ep_slug_1) || href.contains(&ep_slug_2) || href.contains(&ep_slug_3) {
            return Some(href.to_string());
        }
    }
    None
}

impl StreamProvider for OtakudesuProvider {
    fn id(&self) -> &'static str { "otakudesu" }
    fn name(&self) -> &'static str { "Otakudesu (Indonesia)" }
    fn has_sub(&self) -> bool { true }
    fn has_dub(&self) -> bool { false }

    fn search<'a>(&'a self, query: &'a str) -> BoxFuture<'a, Result<Vec<ProviderSearchResult>>> {
        Box::pin(async move {
            let clean = query.replace([':', '-', '–', '—', '(', ')'], " ");
            let search_url = format!("{}/?s={}&post_type=anime", OTAKU_BASE, urlencoding::encode(clean.trim()));
            let html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
            let items = parse_search_results(&html);
            if items.is_empty() {
                return Err(anyhow!("No results found on Otakudesu for '{}'", query));
            }
            Ok(items)
        })
    }

    fn get_servers<'a>(&'a self, _slug: &'a str, _ep: i32, mode: &'a str) -> BoxFuture<'a, Result<Vec<StreamServer>>> {
        Box::pin(async move {
            if mode.eq_ignore_ascii_case("dub") {
                return Err(anyhow!("Otakudesu does not support DUB mode"));
            }
            Ok(vec![
                StreamServer { server_id: "desustream".to_string(), server_name: "DesuStream (HD)".to_string(), latency_ms: 85 },
                StreamServer { server_id: "mp4load".to_string(), server_name: "MP4Upload Mirror".to_string(), latency_ms: 105 },
                StreamServer { server_id: "odstream".to_string(), server_name: "Odstream Mirror".to_string(), latency_ms: 120 },
                StreamServer { server_id: "vidhide".to_string(), server_name: "Vidhide Fast".to_string(), latency_ms: 135 },
            ])
        })
    }

    fn resolve_episode<'a>(
        &'a self,
        slug: &'a str,
        ep: i32,
        mode: &'a str,
        server: Option<&'a str>,
        _mal_id: Option<i64>,
        title_hint: Option<&'a str>,
    ) -> BoxFuture<'a, Result<ResolvedStreamSource>> {
        Box::pin(async move {
            if mode.eq_ignore_ascii_case("dub") {
                return Err(anyhow!("Otakudesu only supports Indonesian SUB audio"));
            }

            let candidate_url = format!("{}/episode/{}-episode-{}-sub-indo/", OTAKU_BASE, slug.trim_end_matches('/'), ep);
            let (ep_url, ep_html) = match HTTP_CLIENT.get(&candidate_url).send().await {
                Ok(resp) if resp.status().is_success() => (candidate_url, resp.text().await?),
                _ => {
                    let search_term = title_hint.unwrap_or(slug);
                    let clean = search_term.replace([':', '-', '–', '—', '(', ')'], " ");
                    let search_url = format!("{}/?s={}&post_type=anime", OTAKU_BASE, urlencoding::encode(clean.trim()));
                    let s_html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
                    let items = parse_search_results(&s_html);
                    let first = items.into_iter().next().ok_or_else(|| anyhow!("Anime not found on Otakudesu for '{}'", search_term))?;
                    let anime_html = HTTP_CLIENT.get(&first.url).header("Referer", OTAKU_BASE).send().await?.text().await?;
                    let ep_link = find_episode_link(&anime_html, ep)
                        .ok_or_else(|| anyhow!("Episode {} not found on Otakudesu anime page", ep))?;
                    let h = HTTP_CLIENT.get(&ep_link).header("Referer", OTAKU_BASE).send().await?.text().await?;
                    (ep_link, h)
                }
            };

            let mirrors = parse_mirror_embeds(&ep_html);
            if mirrors.is_empty() {
                return Err(anyhow!("No video mirrors found for episode {} on Otakudesu", ep));
            }

            let target_server = server.unwrap_or("desustream").to_lowercase();
            let mut candidates: Vec<&(String, String)> = mirrors.iter().collect();
            candidates.sort_by_key(|(name, _)| {
                let n = name.to_lowercase();
                if n.contains(&target_server) { 0 }
                else if n == "desustream" { 1 }
                else if n.contains("mp4") || n.contains("odstream") { 2 }
                else { 3 }
            });

            let mut resolved_stream: Option<(String, String)> = None;
            for (name, val) in candidates {
                let stream_opt = if name == "desustream" || val.starts_with("http") {
                    extract_direct_stream(&HTTP_CLIENT, val, OTAKU_BASE).await
                } else if let Ok(u) = resolve_ajax_mirror(&HTTP_CLIENT, OTAKU_BASE, val, &ep_url).await {
                    extract_direct_stream(&HTTP_CLIENT, &u, &ep_url).await
                } else {
                    None
                };
                if let Some(s) = stream_opt {
                    resolved_stream = Some(s);
                    break;
                }
            }
            let (master, referer) = resolved_stream
                .ok_or_else(|| anyhow!("Unable to extract playable stream for episode {} from Otakudesu", ep))?;

            let subs = vec![SubtitleTrack {
                label: "Indonesia (Hardsub)".to_string(),
                lang: "id".to_string(),
                url: "".to_string(),
                default: true,
            }];

            Ok(ResolvedStreamSource {
                master,
                referer,
                server: target_server.to_string(),
                subtitles: subs,
            })
        })
    }
}
