use super::{BoxFuture, ProviderSearchResult, ResolvedStreamSource, StreamProvider, StreamServer};
use crate::db::SubtitleTrack;
use anyhow::{anyhow, Result};
use reqwest::Client;
use scraper::{Html, Selector};
use std::sync::LazyLock;
use std::time::Duration;

const ANICHAN_BASE: &str = "https://anichan.net";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(Duration::from_secs(8))
        .build()
        .expect("Failed to initialize reqwest client for anichan")
});

pub struct AniChanProvider;

impl AniChanProvider {
    pub fn new() -> Self {
        Self
    }
}

pub fn parse_search_results(html_str: &str) -> Vec<ProviderSearchResult> {
    let doc = Html::parse_document(html_str);
    let card_sel = Selector::parse("article.bs, div.animpost").ok();
    let link_sel = Selector::parse("a").ok();
    let title_sel = Selector::parse(".title, h2, h3, .tt").ok();
    let img_sel = Selector::parse("img").ok();

    let mut results = Vec::new();
    if let (Some(cs), Some(ls), Some(ts)) = (card_sel, link_sel, title_sel) {
        for card in doc.select(&cs) {
            let url = card.select(&ls).next().and_then(|a| a.value().attr("href")).unwrap_or("");
            let title = card.select(&ts).next().map(|t| t.text().collect::<String>()).unwrap_or_default();
            let poster = img_sel.as_ref().and_then(|is| {
                card.select(is).next().and_then(|img| {
                    img.value().attr("src").or_else(|| img.value().attr("data-src"))
                }).map(|s| s.to_string())
            });

            if !url.is_empty() && !title.trim().is_empty() && !results.iter().any(|r: &ProviderSearchResult| r.url == url) {
                let id = url.trim_end_matches('/').rsplit('/').next().unwrap_or(url).to_string();
                results.push(ProviderSearchResult {
                    id,
                    title: title.trim().to_string(),
                    url: url.to_string(),
                    poster,
                });
            }
        }
    }
    results
}

pub fn parse_episode_embeds(html_str: &str) -> Vec<(String, String)> {
    let doc = Html::parse_document(html_str);
    let mut embeds = Vec::new();

    // 1. Selector options: select.mirror option or select[name="server"] option
    if let Ok(opt_sel) = Selector::parse("select.mirror option, select.server option, .mobius select option") {
        for opt in doc.select(&opt_sel) {
            let name = opt.text().collect::<String>().trim().to_string();
            if let Some(val) = opt.value().attr("value") {
                if !val.is_empty() && !val.starts_with('#') {
                    let server_name = if name.is_empty() { "mirror".to_string() } else { name };
                    embeds.push((server_name, val.to_string()));
                }
            }
        }
    }

    // 2. Server items: div.server-item a or .servers li a
    if let Ok(item_sel) = Selector::parse("div.server-item a, ul.servers li a, .server-list a") {
        for item in doc.select(&item_sel) {
            let name = item.text().collect::<String>().trim().to_string();
            let src = item.value().attr("data-src")
                .or_else(|| item.value().attr("href"))
                .unwrap_or("");
            if !src.is_empty() && !src.starts_with('#') && !src.starts_with("javascript") {
                embeds.push((name, src.to_string()));
            }
        }
    }

    // 3. Iframe fallback
    if embeds.is_empty() {
        if let Ok(iframe_sel) = Selector::parse("div.player-embed iframe, div.responsive-embed iframe, iframe[src]") {
            for iframe in doc.select(&iframe_sel) {
                if let Some(src) = iframe.value().attr("src") {
                    if !src.is_empty() {
                        embeds.push(("default".to_string(), src.to_string()));
                    }
                }
            }
        }
    }
    embeds
}

pub fn resolve_direct_embed_stream(embed_url: &str) -> String {
    // Pixeldrain direct file streaming transformation: /u/{id} -> /api/file/{id}
    if embed_url.contains("pixeldrain.com/u/") {
        embed_url.replace("pixeldrain.com/u/", "pixeldrain.com/api/file/")
    } else {
        embed_url.to_string()
    }
}

impl StreamProvider for AniChanProvider {
    fn id(&self) -> &'static str { "anichan" }
    fn name(&self) -> &'static str { "AniChan (Indonesia)" }
    fn has_sub(&self) -> bool { true }
    fn has_dub(&self) -> bool { false }

    fn search<'a>(&'a self, query: &'a str) -> BoxFuture<'a, Result<Vec<ProviderSearchResult>>> {
        Box::pin(async move {
            let search_url = format!("{}/?s={}", ANICHAN_BASE, urlencoding::encode(query));
            let html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
            let items = parse_search_results(&html);
            if items.is_empty() {
                return Err(anyhow!("No results found on AniChan for '{}'", query));
            }
            Ok(items)
        })
    }

    fn get_servers<'a>(&'a self, _slug: &'a str, _ep: i32, mode: &'a str) -> BoxFuture<'a, Result<Vec<StreamServer>>> {
        Box::pin(async move {
            if mode.eq_ignore_ascii_case("dub") {
                return Err(anyhow!("AniChan does not support DUB mode"));
            }
            Ok(vec![
                StreamServer { server_id: "blogspot".to_string(), server_name: "Blogspot / Blogger (HD)".to_string(), latency_ms: 90 },
                StreamServer { server_id: "pixeldrain".to_string(), server_name: "Pixeldrain Mirror".to_string(), latency_ms: 115 },
                StreamServer { server_id: "vidhide".to_string(), server_name: "Vidhide Stream".to_string(), latency_ms: 135 },
                StreamServer { server_id: "gofile".to_string(), server_name: "Gofile Fast".to_string(), latency_ms: 160 },
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
                return Err(anyhow!("AniChan only supports Indonesian SUB audio"));
            }

            let ep_slug = format!("{}-episode-{}", slug.trim_end_matches('/'), ep);
            let ep_url = format!("{}/{}/", ANICHAN_BASE, ep_slug);
            let html = match HTTP_CLIENT.get(&ep_url).send().await {
                Ok(resp) if resp.status().is_success() => resp.text().await?,
                _ => {
                    let search_term = title_hint.unwrap_or(slug);
                    let search_url = format!("{}/?s={}+episode+{}", ANICHAN_BASE, urlencoding::encode(search_term), ep);
                    let s_html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
                    let items = parse_search_results(&s_html);
                    let first = items.into_iter().next().ok_or_else(|| anyhow!("Episode {} not found on AniChan", ep))?;
                    HTTP_CLIENT.get(&first.url).send().await?.text().await?
                }
            };

            let embeds = parse_episode_embeds(&html);
            if embeds.is_empty() {
                return Err(anyhow!("No stream mirrors found for episode {} on AniChan", ep));
            }

            let chosen = if let Some(target_srv) = server {
                embeds.iter().find(|(name, _)| name.to_lowercase().contains(&target_srv.to_lowercase()))
                    .or_else(|| embeds.first())
            } else {
                embeds.first()
            }.ok_or_else(|| anyhow!("Selected server mirror not available"))?;

            let master = resolve_direct_embed_stream(&chosen.1);
            let subs = vec![SubtitleTrack {
                label: "Indonesia (Hardsub)".to_string(),
                lang: "id".to_string(),
                url: "".to_string(),
                default: true,
            }];

            Ok(ResolvedStreamSource {
                master,
                referer: format!("{}/", ANICHAN_BASE),
                server: server.unwrap_or("blogspot").to_string(),
                subtitles: subs,
            })
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_anichan_search() {
        let sample = r#"
            <div class="result-item">
                <article class="bs">
                    <div class="animposx"><a href="https://anichan.net/anime/naruto/"><img src="https://anichan.net/naruto.jpg" /></a></div>
                    <div class="title">Naruto Shippuden</div>
                </article>
            </div>
        "#;
        let results = parse_search_results(sample);
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].title, "Naruto Shippuden");
        assert_eq!(results[0].id, "naruto");
        assert_eq!(results[0].poster, Some("https://anichan.net/naruto.jpg".to_string()));
    }

    #[test]
    fn test_parse_anichan_episode_embeds() {
        let sample = r#"
            <div class="player-embed">
                <select class="mirror">
                    <option value="https://pixeldrain.com/u/abc12345">Pixeldrain HD</option>
                    <option value="https://vidhidepro.com/v/xyz98765">Vidhide Stream</option>
                </select>
            </div>
        "#;
        let embeds = parse_episode_embeds(sample);
        assert_eq!(embeds.len(), 2);
        assert_eq!(embeds[0].0, "Pixeldrain HD");
        assert_eq!(resolve_direct_embed_stream(&embeds[0].1), "https://pixeldrain.com/api/file/abc12345");
    }
}
