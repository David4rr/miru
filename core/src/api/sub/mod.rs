pub mod ai_byok;
pub mod ai_client;
pub mod align;
pub mod animetosho;
pub mod ass_cleaner;
pub mod bilingual;
pub mod cue_merger;
pub mod gtx;
pub mod lexicon;
pub mod opensubtitles;

pub use gtx::translate_text;

use crate::AppState;
use crate::db::Database;
use anyhow::Result;
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
        .timeout(Duration::from_secs(12))
        .tcp_nodelay(true)
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

#[derive(Debug, Deserialize)]
pub struct SubtitleOffsetQuery {
    pub slug: String,
    pub episode: i32,
}

#[derive(Debug, Deserialize)]
pub struct SubtitleOffsetPayload {
    pub slug: String,
    pub episode: i32,
    pub offset_ms: i32,
}

pub async fn offset_get_handler(
    State(state): State<AppState>,
    Query(params): Query<SubtitleOffsetQuery>,
) -> Response {
    match state.db.get_subtitle_offset(&params.slug, params.episode) {
        Ok(offset) => Json(json!({ "success": true, "offset_ms": offset.unwrap_or(0) })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

pub async fn offset_post_handler(
    State(state): State<AppState>,
    Json(payload): Json<SubtitleOffsetPayload>,
) -> Response {
    match state.db.set_subtitle_offset(&payload.slug, payload.episode, payload.offset_ms) {
        Ok(_) => Json(json!({ "success": true, "message": "Offset saved" })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

pub async fn fetch_aniskip_intervals(
    mal_id: i64,
    episode_num: i32,
    episode_length: f64,
) -> Result<SkipTimesResponse> {
    if mal_id <= 0 || episode_num <= 0 {
        return Ok(SkipTimesResponse { found: false, op: None, ed: None });
    }

    let len_param = if episode_length <= 0.0 || (episode_length - 1440.0).abs() < 0.1 { 0 } else { episode_length as i64 };
    let url = format!("https://api.aniskip.com/v2/skip-times/{}/{}?types=op&types=ed&episodeLength={}", mal_id, episode_num, len_param);

    let resp = HTTP_CLIENT.get(&url).send().await?;
    if !resp.status().is_success() {
        return Ok(SkipTimesResponse { found: false, op: None, ed: None });
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
                if skip_type == "op" { op = Some(iv); } else if skip_type == "ed" { ed = Some(iv); }
            }
        }
    }

    Ok(SkipTimesResponse { found, op, ed })
}

pub async fn get_or_translate_vtt(
    url: &str,
    mode: &str,
    offset_ms: i64,
    db: &Database,
) -> Result<String> {
    crate::net::validator::is_safe_remote_url(url)
        .map_err(|e| anyhow::anyhow!("Disallowed subtitle target: {}", e))?;
    let cache_key = format!("sub4|{}|{}|{}", mode, offset_ms, url);
    if let Ok(Some(cached_vtt)) = db.get_subtitle_cache(&cache_key, 86400 * 30) {
        return Ok(cached_vtt);
    }

    let referer = if url.contains("hianime") { "https://hianime.at/" } else { "https://zokoanime.video/" };
    let req = HTTP_CLIENT
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .header("Referer", referer);

    let raw_content = match req.send().await {
        Ok(res) if res.status().is_success() => res.text().await.unwrap_or_else(|_| "WEBVTT\n\n".to_string()),
        _ => return Ok("WEBVTT\n\n".to_string()),
    };

    let cleaned_content = if mode == "clean_ass" || url.ends_with(".ass") || url.ends_with(".ssa") {
        ass_cleaner::clean_ass_to_vtt(&raw_content)
    } else {
        opensubtitles::strip_ad_watermarks(&raw_content)
    };

    let processed_vtt = if mode == "raw" || mode == "clean_ass" {
        cleaned_content
    } else if mode == "bilingual" {
        process_bilingual_vtt(&cleaned_content, db).await?
    } else {
        process_gtx_vtt(&cleaned_content, db).await?
    };

    let merged_vtt = cue_merger::merge_vtt_overlaps(&processed_vtt, 200);
    let final_vtt = if offset_ms != 0 {
        let cues = cue_merger::parse_vtt_cues(&merged_vtt);
        let shifted = align::apply_offset(&cues, offset_ms);
        cue_merger::cues_to_vtt(&shifted)
    } else {
        merged_vtt
    };

    let _ = db.set_subtitle_cache(&cache_key, &final_vtt);
    Ok(final_vtt)
}

async fn process_bilingual_vtt(en_vtt: &str, db: &Database) -> Result<String> {
    let en_cues = cue_merger::parse_vtt_cues(en_vtt);
    if en_cues.is_empty() {
        return Ok(en_vtt.to_string());
    }

    let lexicon_rules = db.get_lexicon_rules().unwrap_or_default();
    let id_cues = gtx::translate_cues_gtx(&en_cues, &lexicon_rules).await?;
    Ok(bilingual::assemble_bilingual_vtt(&id_cues, &en_cues))
}

async fn process_gtx_vtt(vtt: &str, db: &Database) -> Result<String> {
    let cues = cue_merger::parse_vtt_cues(vtt);
    if cues.is_empty() {
        return Ok(vtt.to_string());
    }

    let lexicon_rules = db.get_lexicon_rules().unwrap_or_default();
    let id_cues = gtx::translate_cues_gtx(&cues, &lexicon_rules).await?;
    Ok(cue_merger::cues_to_vtt(&id_cues))
}
