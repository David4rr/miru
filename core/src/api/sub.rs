use crate::db::Database;
use anyhow::Result;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::LazyLock;
use std::time::Duration;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .expect("Failed to initialize sub HTTP client")
});

#[derive(Debug, Serialize, Deserialize)]
pub struct SkipTimesResponse {
    pub found: bool,
    pub op: Option<Interval>,
    pub ed: Option<Interval>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Interval {
    pub start: f64,
    pub end: f64,
}

/// Fetch intro (OP) and outro (ED) skip intervals from AniSkip API
pub async fn fetch_aniskip_intervals(
    mal_id: i64,
    episode_num: i32,
    episode_length: f64,
) -> Result<SkipTimesResponse> {
    if mal_id <= 0 || episode_num <= 0 {
        return Ok(SkipTimesResponse {
            found: false,
            op: None,
            ed: None,
        });
    }

    let url = format!(
        "https://api.aniskip.com/v2/skip-times/{}/{}?types=op&types=ed&episodeLength={}",
        mal_id, episode_num, episode_length as i64
    );

    let resp = HTTP_CLIENT.get(&url).send().await?;
    if !resp.status().is_success() {
        return Ok(SkipTimesResponse {
            found: false,
            op: None,
            ed: None,
        });
    }

    let json_val: Value = resp.json().await?;
    let found = json_val.get("found").and_then(|f| f.as_bool()).unwrap_or(false);

    let mut op = None;
    let mut ed = None;

    if let Some(results) = json_val.get("results").and_then(|r| r.as_array()) {
        for res in results {
            let skip_type = res.get("skipType").and_then(|s| s.as_str()).unwrap_or("");
            if let Some(interval) = res.get("interval") {
                let start = interval.get("startTime").and_then(|s| s.as_f64()).unwrap_or(0.0);
                let end = interval.get("endTime").and_then(|e| e.as_f64()).unwrap_or(0.0);
                let iv = Interval { start, end };
                if skip_type == "op" {
                    op = Some(iv);
                } else if skip_type == "ed" {
                    ed = Some(iv);
                }
            }
        }
    }

    Ok(SkipTimesResponse { found, op, ed })
}

/// Fetch WebVTT subtitle and translate to Indonesian via Google GTX+ pool with SQLite caching
pub async fn get_or_translate_vtt(
    url: &str,
    mode: &str,
    db: &Database,
) -> Result<String> {
    let cache_key = format!("sub2|{}|{}", mode, url);

    // 1. Check local SQLite cache first (TTL 30 days)
    if let Ok(Some(cached_vtt)) = db.get_subtitle_cache(&cache_key, 86400 * 30) {
        return Ok(cached_vtt);
    }

    // 2. Fetch original WebVTT from upstream
    let raw_vtt = HTTP_CLIENT.get(url).send().await?.text().await?;

    if mode == "raw" || !mode.contains("gtx") {
        let _ = db.set_subtitle_cache(&cache_key, &raw_vtt);
        return Ok(raw_vtt);
    }

    // 3. Parse WebVTT cues and translate dialogue lines
    let mut translated_lines = Vec::new();
    let mut is_in_cue = false;

    for line in raw_vtt.lines() {
        let trimmed = line.trim();

        if trimmed.starts_with("WEBVTT") || trimmed.starts_with("NOTE") || trimmed.is_empty() {
            translated_lines.push(line.to_string());
            is_in_cue = false;
            continue;
        }

        // Timestamp line: 00:01:23.456 --> 00:01:25.789
        if trimmed.contains("-->") {
            translated_lines.push(line.to_string());
            is_in_cue = true;
            continue;
        }

        if is_in_cue && !trimmed.is_empty() {
            // Translate cue text
            let translated = translate_gtx_text(trimmed, "en", "id").await.unwrap_or_else(|_| trimmed.to_string());
            translated_lines.push(translated);
        } else {
            translated_lines.push(line.to_string());
        }
    }

    let result_vtt = translated_lines.join("\n");
    let _ = db.set_subtitle_cache(&cache_key, &result_vtt);

    Ok(result_vtt)
}

/// Translate text chunk using Google GTX+ pool
async fn translate_gtx_text(text: &str, sl: &str, tl: &str) -> Result<String> {
    let api_url = format!(
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl={}&tl={}&q={}",
        sl, tl, urlencoding::encode(text)
    );

    let res = HTTP_CLIENT.get(&api_url).send().await?;
    if res.status().is_success() {
        let val: Value = res.json().await?;
        if let Some(arr) = val.as_array() {
            if let Some(first) = arr.first().and_then(|s| s.as_str()) {
                return Ok(first.to_string());
            }
        }
    }

    Ok(text.to_string())
}
