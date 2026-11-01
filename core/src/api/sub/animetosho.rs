use anyhow::Result;
use axum::{
    extract::Query,
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::LazyLock;
use std::time::Duration;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .expect("Failed to initialize AnimeTosho HTTP client")
});

#[derive(Debug, Deserialize)]
pub struct AnimeToshoQuery {
    pub mal_id: Option<i64>,
    pub title: String,
    pub episode: i32,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ExternalSubtitleSource {
    pub source: String,
    pub filename: String,
    pub format: String,
    pub lang: String,
    pub label: String,
    pub download_url: String,
}

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
struct AnimeToshoEntry {
    pub id: Option<i64>,
    pub title: Option<String>,
    pub attachments: Option<Vec<AnimeToshoAttachment>>,
}

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
struct AnimeToshoAttachment {
    pub name: Option<String>,
    pub url: Option<String>,
    pub size: Option<u64>,
}

fn detect_sub_lang_and_label(filename: &str, format: &str) -> (String, String) {
    let lower = filename.to_lowercase();
    let fmt_upper = format.to_uppercase();

    if lower.contains("[id]") || lower.contains(".id.") || lower.contains("_id.") || lower.contains("[ind]") || lower.contains("_ind.") || lower.contains(".ind.") || lower.contains("indonesian") || lower.contains("bahasa") {
        ("id".to_string(), format!("AnimeTosho (Indonesian {})", fmt_upper))
    } else if lower.contains("[en]") || lower.contains(".en.") || lower.contains("_en.") || lower.contains("[eng]") || lower.contains("_eng.") || lower.contains(".eng.") || lower.contains("english") {
        ("en".to_string(), format!("AnimeTosho (English {})", fmt_upper))
    } else if lower.contains("[ja]") || lower.contains(".ja.") || lower.contains("_ja.") || lower.contains("[jp]") || lower.contains("_jp.") || lower.contains(".jp.") || lower.contains("japanese") {
        ("ja".to_string(), format!("AnimeTosho (Japanese {})", fmt_upper))
    } else {
        ("und".to_string(), format!("AnimeTosho ({})", fmt_upper))
    }
}

pub async fn query_animetosho(
    mal_id: Option<i64>,
    title: &str,
    episode: i32,
) -> Result<Vec<ExternalSubtitleSource>> {
    let mut search_query = format!("{} {:02}", title.trim(), episode);
    if let Some(mid) = mal_id {
        if mid > 0 {
            search_query = format!("{} {:02}", title.trim(), episode);
        }
    }

    let url = format!(
        "https://feed.animetosho.org/json?qx=1&q={}",
        urlencoding::encode(&search_query)
    );

    let res = HTTP_CLIENT
        .get(&url)
        .header("User-Agent", "Miru/0.2 (Android; SubtitleEngine)")
        .send()
        .await?;

    if !res.status().is_success() {
        return Ok(Vec::new());
    }

    let entries: Vec<AnimeToshoEntry> = res.json().await.unwrap_or_default();
    let mut results = Vec::new();

    let ep_patterns = [
        format!(" {:02} ", episode),
        format!(" - {:02}", episode),
        format!(" - {}", episode),
        format!("E{:02}", episode),
        format!("EP{:02}", episode),
    ];

    for entry in entries {
        let entry_title = entry.title.as_deref().unwrap_or("");
        let matches_ep = ep_patterns.iter().any(|pat| entry_title.contains(pat)) || episode == 1;

        if let Some(attachments) = entry.attachments {
            for att in attachments {
                let name = att.name.unwrap_or_default();
                let url = att.url.unwrap_or_default();
                if url.is_empty() {
                    continue;
                }

                let lower_name = name.to_lowercase();
                let (is_valid, format) = if lower_name.ends_with(".ass") {
                    (true, "ass")
                } else if lower_name.ends_with(".ssa") {
                    (true, "ssa")
                } else if lower_name.ends_with(".srt") {
                    (true, "srt")
                } else {
                    (false, "")
                };

                if is_valid && (matches_ep || lower_name.contains(&format!("{:02}", episode))) {
                    let (lang, label) = detect_sub_lang_and_label(&name, format);
                    let mode = if format == "ass" || format == "ssa" { "clean_ass" } else { "raw" };
                    let download_url = format!(
                        "http://127.0.0.1:8767/api/player/sub?url={}&mode={}",
                        urlencoding::encode(&url),
                        mode
                    );

                    results.push(ExternalSubtitleSource {
                        source: "animetosho".to_string(),
                        filename: name,
                        format: format.to_string(),
                        lang,
                        label,
                        download_url,
                    });
                }
            }
        }
    }

    Ok(results)
}

pub async fn animetosho_sources_handler(Query(params): Query<AnimeToshoQuery>) -> Response {
    if params.title.trim().is_empty() || params.episode <= 0 {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid title or episode" })),
        ).into_response();
    }

    match query_animetosho(params.mal_id, &params.title, params.episode).await {
        Ok(sources) => Json(json!({ "success": true, "data": sources })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_lang_label() {
        let (lang, label) = detect_sub_lang_and_label("[SubsPlease] Frieren - 01 [ID].ass", "ass");
        assert_eq!(lang, "id");
        assert_eq!(label, "AnimeTosho (Indonesian ASS)");

        let (lang_en, label_en) = detect_sub_lang_and_label("Frieren_01_eng.srt", "srt");
        assert_eq!(lang_en, "en");
        assert_eq!(label_en, "AnimeTosho (English SRT)");
    }
}
