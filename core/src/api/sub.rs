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

    // 2. Fetch original WebVTT from upstream with spoofed referer
    let referer = if url.contains("hianime") {
        "https://hianime.at/"
    } else {
        "https://zokoanime.video/"
    };
    let req = HTTP_CLIENT
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .header("Referer", referer);
    let raw_vtt = match req.send().await {
        Ok(res) if res.status().is_success() => res.text().await.unwrap_or_else(|_| "WEBVTT\n\n".to_string()),
        _ => return Ok("WEBVTT\n\n".to_string()),
    };
    if mode == "raw" || !mode.contains("gtx") {
        let _ = db.set_subtitle_cache(&cache_key, &raw_vtt);
        return Ok(raw_vtt);
    }

    // 3. Fast batched translation: collect dialogue lines
    let lines: Vec<&str> = raw_vtt.lines().collect();
    let mut is_in_cue = false;
    let mut cue_indices = Vec::new();
    let mut cue_texts = Vec::new();

    for (idx, line) in lines.iter().enumerate() {
        let trimmed = line.trim();
        if trimmed.starts_with("WEBVTT") || trimmed.starts_with("NOTE") || trimmed.is_empty() {
            is_in_cue = false;
            continue;
        }
        if trimmed.contains("-->") {
            is_in_cue = true;
            continue;
        }
        if is_in_cue && !trimmed.is_empty() {
            cue_indices.push(idx);
            cue_texts.push(trimmed.to_string());
        }
    }

    if cue_texts.is_empty() {
        let _ = db.set_subtitle_cache(&cache_key, &raw_vtt);
        return Ok(raw_vtt);
    }

    // Batch translate in blocks of 30 cues
    let mut translated_map: std::collections::HashMap<usize, String> = std::collections::HashMap::new();
    for chunk in cue_indices.chunks(30) {
        let combined_text: Vec<String> = chunk.iter().map(|&i| lines[i].trim().to_string()).collect();
        let joined = combined_text.join(" ~~~ ");
        if let Ok(translated_joined) = translate_text(&joined, "en", "id").await {
            let parts: Vec<&str> = translated_joined.split("~~~").collect();
            for (i, &line_idx) in chunk.iter().enumerate() {
                let translated_part = parts.get(i).map(|s| s.trim().to_string()).unwrap_or_else(|| lines[line_idx].to_string());
                translated_map.insert(line_idx, translated_part);
            }
        }
    }

    let mut result_lines = Vec::with_capacity(lines.len());
    for (idx, line) in lines.iter().enumerate() {
        if let Some(tr) = translated_map.get(&idx) {
            result_lines.push(tr.clone());
        } else {
            result_lines.push(line.to_string());
        }
    }

    let result_vtt = result_lines.join("\n");
    let _ = db.set_subtitle_cache(&cache_key, &result_vtt);
    Ok(result_vtt)
}

/// Translate text chunk using Google GTX+ pool
pub async fn translate_text(text: &str, sl: &str, tl: &str) -> Result<String> {
    if text.trim().is_empty() {
        return Ok(String::new());
    }

    let api_url = format!(
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl={}&tl={}&q={}",
        sl, tl, urlencoding::encode(text)
    );

    let res = HTTP_CLIENT.get(&api_url).send().await?;
    if res.status().is_success() {
        let val: Value = res.json().await?;
        if let Some(arr) = val.as_array() {
            let mut pieces = Vec::new();
            for item in arr {
                if let Some(inner_arr) = item.as_array() {
                    if let Some(t) = inner_arr.first().and_then(|v| v.as_str()) {
                        pieces.push(t.to_string());
                    }
                } else if let Some(t) = item.as_str() {
                    pieces.push(t.to_string());
                }
            }
            if !pieces.is_empty() {
                return Ok(pieces.join(" "));
            }
        }
    }

    Ok(text.to_string())
}
