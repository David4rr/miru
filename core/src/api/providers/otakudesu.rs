use super::{BoxFuture, ProviderSearchResult, ResolvedStreamSource, StreamProvider, StreamServer};
use crate::db::SubtitleTrack;
use anyhow::{anyhow, Result};
use reqwest::Client;
use scraper::{Html, Selector};
use std::sync::LazyLock;
use std::time::Duration;

const OTAKU_BASE: &str = "https://otakudesu.cloud";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(Duration::from_secs(8))
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
    let item_sel = Selector::parse("ul.channellist li, .venser .venz ul li").ok();
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

    // 1. Mirror stream items: .mirrorstream ul li a
    if let Ok(a_sel) = Selector::parse(".mirrorstream ul li a, ul.mirrorstream li a") {
        for a in doc.select(&a_sel) {
            let name = a.text().collect::<String>().trim().to_string();
            let src = a.value().attr("data-content")
                .or_else(|| a.value().attr("href"))
                .unwrap_or("");
            if !src.is_empty() && !src.starts_with('#') {
                mirrors.push((name, src.to_string()));
            }
        }
    }

    // 2. Responsive embed stream iframe
    if mirrors.is_empty() {
        if let Ok(iframe_sel) = Selector::parse("div.responsive-embed-stream iframe, iframe[src]") {
            for iframe in doc.select(&iframe_sel) {
                if let Some(src) = iframe.value().attr("src") {
                    if !src.is_empty() {
                        mirrors.push(("DesuStream".to_string(), src.to_string()));
                    }
                }
            }
        }
    }
    mirrors
}

impl StreamProvider for OtakudesuProvider {
    fn id(&self) -> &'static str { "otakudesu" }
    fn name(&self) -> &'static str { "Otakudesu (Indonesia)" }
    fn has_sub(&self) -> bool { true }
    fn has_dub(&self) -> bool { false }

    fn search<'a>(&'a self, query: &'a str) -> BoxFuture<'a, Result<Vec<ProviderSearchResult>>> {
        Box::pin(async move {
            let search_url = format!("{}/?s={}&post_type=anime", OTAKU_BASE, urlencoding::encode(query));
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
                StreamServer { server_id: "odstream".to_string(), server_name: "Odstream Mirror".to_string(), latency_ms: 110 },
                StreamServer { server_id: "mega".to_string(), server_name: "Mega Cloud Mirror".to_string(), latency_ms: 145 },
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
            let html = match HTTP_CLIENT.get(&candidate_url).send().await {
                Ok(resp) if resp.status().is_success() => resp.text().await?,
                _ => {
                    let search_term = title_hint.unwrap_or(slug);
                    let search_url = format!("{}/?s={}&post_type=anime", OTAKU_BASE, urlencoding::encode(search_term));
                    let s_html = HTTP_CLIENT.get(&search_url).send().await?.text().await?;
                    let items = parse_search_results(&s_html);
                    let first = items.into_iter().next().ok_or_else(|| anyhow!("Anime not found on Otakudesu"))?;
                    let anime_html = HTTP_CLIENT.get(&first.url).send().await?.text().await?;
                    let re_ep = regex::Regex::new(&format!(r#"href="([^"]+episode[^-]*-{}[^"]*)""#, ep))?;
                    let ep_link = re_ep.captures(&anime_html).and_then(|c| c.get(1)).map(|m| m.as_str().to_string())
                        .ok_or_else(|| anyhow!("Episode {} not found on Otakudesu anime page", ep))?;
                    HTTP_CLIENT.get(&ep_link).send().await?.text().await?
                }
            };

            let mirrors = parse_mirror_embeds(&html);
            if mirrors.is_empty() {
                return Err(anyhow!("No video mirrors found for episode {} on Otakudesu", ep));
            }

            let chosen = if let Some(srv) = server {
                mirrors.iter().find(|(name, _)| name.to_lowercase().contains(&srv.to_lowercase()))
                    .or_else(|| mirrors.first())
            } else {
                mirrors.first()
            }.ok_or_else(|| anyhow!("Selected Otakudesu server not available"))?;

            let subs = vec![SubtitleTrack {
                label: "Indonesia (Hardsub)".to_string(),
                lang: "id".to_string(),
                url: "".to_string(),
                default: true,
            }];

            Ok(ResolvedStreamSource {
                master: chosen.1.clone(),
                referer: format!("{}/", OTAKU_BASE),
                server: server.unwrap_or("desustream").to_string(),
                subtitles: subs,
            })
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_otakudesu_search() {
        let sample = r#"
            <ul class="channellist">
                <li>
                    <img src="https://otakudesu.cloud/one-piece.jpg" />
                    <h2><a href="https://otakudesu.cloud/anime/one-piece-sub-indo/">One Piece</a></h2>
                </li>
            </ul>
        "#;
        let results = parse_search_results(sample);
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].title, "One Piece");
        assert_eq!(results[0].id, "one-piece-sub-indo");
        assert_eq!(results[0].poster, Some("https://otakudesu.cloud/one-piece.jpg".to_string()));
    }

    #[test]
    fn test_parse_otakudesu_mirrors() {
        let sample = r#"
            <div class="mirrorstream">
                <ul class="m720p">
                    <li><a data-content="https://desustream.me/embed/op1100">DesuStream HD</a></li>
                    <li><a data-content="https://odstream.com/v/op1100">Odstream 720p</a></li>
                </ul>
            </div>
        "#;
        let mirrors = parse_mirror_embeds(sample);
        assert_eq!(mirrors.len(), 2);
        assert_eq!(mirrors[0].0, "DesuStream HD");
        assert_eq!(mirrors[0].1, "https://desustream.me/embed/op1100");
    }
}
