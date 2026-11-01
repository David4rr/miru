use super::ai_client::call_llm;
use crate::api::sub::cue_merger::{cues_to_vtt, parse_vtt_cues, SubtitleCue};
use crate::api::sub::lexicon::{shield_terms, unshield_terms};
use crate::AppState;
use anyhow::Result;
use axum::{extract::State, http::StatusCode, response::{IntoResponse, Response}, Json};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::LazyLock;
use std::time::Instant;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| Client::new());

pub const DEFAULT_SYSTEM_PROMPT: &str = "Terjemahkan takarir anime berikut ke bahasa Indonesia yang alami dan ekspresif. Pertahankan honorifik Jepang (-san, -kun, -chan, -senpai, -sama) dan jangan ubah tag format. Pisahkan setiap dialog dengan separator ' ~~~ '.";

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AiProviderConfig {
    pub provider_type: String,
    pub base_url: Option<String>,
    pub api_key: Option<String>,
    pub model_id: String,
    pub prompt_template: Option<String>,
    pub is_active: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AiFansubRequest {
    pub vtt_url: String,
    pub provider_id: Option<String>,
    pub source_lang: Option<String>,
    pub target_lang: Option<String>,
    pub batch_size: Option<usize>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AiFansubResponseData {
    pub cached: bool,
    pub cues_translated: usize,
    pub duration_ms: u64,
    pub vtt_content: String,
}

pub fn get_default_providers() -> Vec<AiProviderConfig> {
    vec![
        AiProviderConfig {
            provider_type: "gemini".into(),
            base_url: Some("https://generativelanguage.googleapis.com".into()),
            api_key: None,
            model_id: "gemini-1.5-flash".into(),
            prompt_template: Some(DEFAULT_SYSTEM_PROMPT.into()),
            is_active: false,
        },
        AiProviderConfig {
            provider_type: "groq".into(),
            base_url: Some("https://api.groq.com/openai/v1".into()),
            api_key: None,
            model_id: "llama-3.3-70b-versatile".into(),
            prompt_template: Some(DEFAULT_SYSTEM_PROMPT.into()),
            is_active: false,
        },
        AiProviderConfig {
            provider_type: "openai".into(),
            base_url: Some("https://api.openai.com/v1".into()),
            api_key: None,
            model_id: "gpt-4o-mini".into(),
            prompt_template: Some(DEFAULT_SYSTEM_PROMPT.into()),
            is_active: false,
        },
        AiProviderConfig {
            provider_type: "ollama".into(),
            base_url: Some("http://127.0.0.1:11434".into()),
            api_key: None,
            model_id: "qwen2.5:7b".into(),
            prompt_template: Some(DEFAULT_SYSTEM_PROMPT.into()),
            is_active: false,
        },
    ]
}

pub async fn execute_fansub_translation(
    vtt_content: &str,
    provider: &AiProviderConfig,
    state: &AppState,
    batch_size: usize,
) -> Result<String> {
    let cues = parse_vtt_cues(vtt_content);
    if cues.is_empty() {
        return Ok(vtt_content.to_string());
    }

    let lexicon_rules = state.db.get_lexicon_rules().unwrap_or_default();
    let bsize = if batch_size == 0 { 40 } else { batch_size };
    let mut join_set = tokio::task::JoinSet::new();

    for (chunk_idx, chunk) in cues.chunks(bsize).enumerate() {
        let texts: Vec<String> = chunk.iter().map(|c| c.text.clone()).collect();
        let joined = texts.join(" ~~~ ");
        let (shielded, map) = shield_terms(&joined, &lexicon_rules);
        let prov = provider.clone();
        let rules_clone = lexicon_rules.clone();

        join_set.spawn(async move {
            let res = call_llm(&prov, &shielded).await;
            (chunk_idx, res, texts, map, rules_clone)
        });
    }

    let mut translated_chunks: Vec<(usize, Vec<String>)> = Vec::new();
    while let Some(res) = join_set.join_next().await {
        if let Ok((idx, task_res, original_texts, map, rules)) = res {
            match task_res {
                Ok(translated_text) => {
                    let unshielded = unshield_terms(&translated_text, &map, &rules);
                    let parts: Vec<String> = unshielded.split("~~~").map(|s| s.trim().to_string()).collect();
                    let final_texts: Vec<String> = (0..original_texts.len())
                        .map(|i| parts.get(i).cloned().unwrap_or_else(|| original_texts[i].clone()))
                        .collect();
                    translated_chunks.push((idx, final_texts));
                }
                Err(_) => {
                    translated_chunks.push((idx, original_texts));
                }
            }
        }
    }

    translated_chunks.sort_by_key(|(idx, _)| *idx);
    let mut flat_texts = Vec::with_capacity(cues.len());
    for (_, chunk) in translated_chunks {
        flat_texts.extend(chunk);
    }

    let result_cues: Vec<SubtitleCue> = cues
        .into_iter()
        .enumerate()
        .map(|(i, cue)| SubtitleCue {
            start_ms: cue.start_ms,
            end_ms: cue.end_ms,
            text: flat_texts.get(i).cloned().unwrap_or(cue.text),
        })
        .collect();

    Ok(cues_to_vtt(&result_cues))
}

pub async fn ai_fansub_handler(
    State(state): State<AppState>,
    Json(payload): Json<AiFansubRequest>,
) -> Response {
    if let Err(err) = crate::net::validator::is_safe_remote_url(&payload.vtt_url) {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": format!("Disallowed VTT URL: {}", err) })),
        )
            .into_response();
    }
    let start_time = Instant::now();
    let cache_key = format!("fansub|{}|{}", payload.provider_id.as_deref().unwrap_or("active"), payload.vtt_url);

    if let Ok(Some(cached_vtt)) = state.db.get_subtitle_cache(&cache_key, 86400 * 7) {
        let cues = parse_vtt_cues(&cached_vtt);
        return Json(json!({
            "success": true,
            "data": AiFansubResponseData {
                cached: true,
                cues_translated: cues.len(),
                duration_ms: start_time.elapsed().as_millis() as u64,
                vtt_content: cached_vtt,
            }
        })).into_response();
    }

    let raw_vtt = match HTTP_CLIENT.get(&payload.vtt_url).send().await {
        Ok(res) if res.status().is_success() => res.text().await.unwrap_or_default(),
        _ => return (StatusCode::BAD_GATEWAY, Json(json!({ "success": false, "error": "Failed to fetch source VTT" }))).into_response(),
    };

    let providers = get_configured_providers(&state.db);
    let provider = match payload.provider_id.as_deref() {
        Some(id) => providers.iter().find(|p| p.provider_type.eq_ignore_ascii_case(id)).cloned(),
        None => providers.iter().find(|p| p.is_active).cloned(),
    }.or_else(|| providers.first().cloned()).unwrap_or_else(|| get_default_providers().remove(0));

    match execute_fansub_translation(&raw_vtt, &provider, &state, payload.batch_size.unwrap_or(40)).await {
        Ok(translated_vtt) => {
            let _ = state.db.set_subtitle_cache(&cache_key, &translated_vtt);
            let cues = parse_vtt_cues(&translated_vtt);
            Json(json!({
                "success": true,
                "data": AiFansubResponseData {
                    cached: false,
                    cues_translated: cues.len(),
                    duration_ms: start_time.elapsed().as_millis() as u64,
                    vtt_content: translated_vtt,
                }
            })).into_response()
        }
        Err(err) => (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": err.to_string() }))).into_response(),
    }
}

pub fn get_configured_providers(db: &crate::db::Database) -> Vec<AiProviderConfig> {
    if let Ok(Some(json_str)) = db.get_setting("ai_providers") {
        if let Ok(configs) = serde_json::from_str::<Vec<AiProviderConfig>>(&json_str) {
            return configs;
        }
    }
    get_default_providers()
}

pub async fn ai_providers_get_handler(State(state): State<AppState>) -> Response {
    let providers = get_configured_providers(&state.db);
    Json(json!({ "success": true, "data": providers })).into_response()
}

pub async fn ai_providers_post_handler(
    State(state): State<AppState>,
    Json(payload): Json<AiProviderConfig>,
) -> Response {
    let mut providers = get_configured_providers(&state.db);
    let mut found = false;
    for p in providers.iter_mut() {
        if p.provider_type.eq_ignore_ascii_case(&payload.provider_type) {
            *p = payload.clone();
            found = true;
            break;
        }
    }
    if !found {
        providers.push(payload);
    }
    let serialized = serde_json::to_string(&providers).unwrap_or_default();
    match state.db.set_setting("ai_providers", &serialized) {
        Ok(_) => Json(json!({ "success": true, "message": "Provider config saved" })).into_response(),
        Err(err) => (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": err.to_string() }))).into_response(),
    }
}
