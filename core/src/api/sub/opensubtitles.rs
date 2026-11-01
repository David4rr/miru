use crate::AppState;
use anyhow::{anyhow, Result};
use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::LazyLock;
use std::time::Duration;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .expect("Failed to initialize OpenSubtitles HTTP client")
});

#[derive(Debug, Deserialize)]
pub struct OpenSubtitlesQuery {
    pub query: String,
    pub episode: i32,
    pub languages: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct OpenSubtitlesTrack {
    pub source: String,
    pub file_id: i64,
    pub lang: String,
    pub label: String,
    pub download_url: String,
}

pub fn strip_ad_watermarks(vtt_or_srt: &str) -> String {
    let ad_patterns = [
        "opensubtitles",
        "subtitles downloaded from",
        "advertise your product",
        "diterjemahkan oleh",
        "sync & corrections by",
        "synced by",
        "resync by",
        "translated by",
        "vip member",
    ];

    let mut filtered_lines = Vec::new();
    for line in vtt_or_srt.lines() {
        let lower = line.to_lowercase();
        let is_ad = ad_patterns.iter().any(|&p| lower.contains(p));
        if !is_ad {
            filtered_lines.push(line);
        }
    }
    filtered_lines.join("\n")
}

pub async fn query_opensubtitles(
    state: &AppState,
    query: &str,
    episode: i32,
    languages: &str,
) -> Result<Vec<OpenSubtitlesTrack>> {
    let api_key = match state.db.get_setting("opensubtitles_api_key").ok().flatten() {
        Some(k) if !k.trim().is_empty() => k,
        _ => {
            return Err(anyhow!("OpenSubtitles API key is not configured. Please set 'opensubtitles_api_key' in settings."));
        }
    };

    let encoded_q = urlencoding::encode(query.trim());
    let url = format!(
        "https://api.opensubtitles.com/api/v1/subtitles?query={}&episode_number={}&languages={}",
        encoded_q, episode, languages
    );

    let res = HTTP_CLIENT
        .get(&url)
        .header("User-Agent", "Miru v0.2.19")
        .header("Api-Key", api_key)
        .send()
        .await?;

    if !res.status().is_success() {
        return Ok(Vec::new());
    }

    let val: Value = res.json().await.unwrap_or_default();
    let mut tracks = Vec::new();

    if let Some(data) = val.get("data").and_then(|d| d.as_array()) {
        for item in data {
            let attrs = &item["attributes"];
            let lang = attrs["language"].as_str().unwrap_or("und");
            let files = attrs["files"].as_array();
            let file_id = files
                .and_then(|f| f.first())
                .and_then(|f| f["file_id"].as_i64())
                .unwrap_or(0);

            if file_id <= 0 {
                continue;
            }

            let dl_link = format!(
                "https://api.opensubtitles.com/api/v1/download?file_id={}",
                file_id
            );
            let label = match lang {
                "id" => "OpenSubtitles (Indonesian)".to_string(),
                "en" => "OpenSubtitles (English)".to_string(),
                other => format!("OpenSubtitles ({})", other.to_uppercase()),
            };

            let proxy_download_url = format!(
                "http://127.0.0.1:8767/api/player/sub?url={}&mode=vtt",
                urlencoding::encode(&dl_link)
            );

            tracks.push(OpenSubtitlesTrack {
                source: "opensubtitles".to_string(),
                file_id,
                lang: lang.to_string(),
                label,
                download_url: proxy_download_url,
            });
        }
    }

    Ok(tracks)
}

pub async fn opensubtitles_sources_handler(
    State(state): State<AppState>,
    Query(params): Query<OpenSubtitlesQuery>,
) -> Response {
    if params.query.trim().is_empty() || params.episode <= 0 {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid query or episode" })),
        ).into_response();
    }

    let langs = params.languages.as_deref().unwrap_or("id,en");
    match query_opensubtitles(&state, &params.query, params.episode, langs).await {
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
    fn test_strip_ads() {
        let input = "1\n00:00:01.000 --> 00:00:04.000\nSubtitles downloaded from OpenSubtitles.org\n\n2\n00:00:05.000 --> 00:00:08.000\nHello world!\n";
        let stripped = strip_ad_watermarks(input);
        assert!(!stripped.contains("OpenSubtitles.org"));
        assert!(stripped.contains("Hello world!"));
    }
}
