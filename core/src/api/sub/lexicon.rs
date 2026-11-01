use crate::db::LexiconRule;
use crate::AppState;
use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Debug, Serialize, Deserialize)]
pub struct LexiconRulePayload {
    pub target_term: String,
    pub replacement: String,
    pub category: Option<String>,
    pub is_regex: Option<bool>,
}

#[derive(Debug, Deserialize)]
pub struct LexiconDeleteParams {
    pub id: Option<i64>,
}

/// Shields lexicon terms in the source text before passing to MT/LLM.
/// Returns (shielded_text, token_mappings).
pub fn shield_terms(text: &str, rules: &[LexiconRule]) -> (String, Vec<(String, String)>) {
    let mut shielded = text.to_string();
    let mut mappings = Vec::new();

    for (idx, rule) in rules.iter().enumerate() {
        if rule.is_regex {
            continue; // regex rules are handled post-translation
        }
        let token = format!("__LX_{}__", idx);
        // Word boundary or case-insensitive replacement
        let pattern = format!(r"(?i)\b{}\b", regex::escape(&rule.target_term));
        if let Ok(re) = Regex::new(&pattern) {
            if re.is_match(&shielded) {
                shielded = re.replace_all(&shielded, &token).to_string();
                mappings.push((token, rule.replacement.clone()));
            }
        }
    }

    (shielded, mappings)
}

/// Restores shielded tokens and applies regex post-translation rules.
pub fn unshield_terms(text: &str, mappings: &[(String, String)], rules: &[LexiconRule]) -> String {
    let mut result = text.to_string();

    // 1. Unshield tokens
    for (token, replacement) in mappings {
        result = result.replace(token, replacement);
    }

    // 2. Apply regex rules
    for rule in rules {
        if rule.is_regex {
            if let Ok(re) = Regex::new(&rule.target_term) {
                result = re.replace_all(&result, &rule.replacement).to_string();
            }
        }
    }

    result
}

// -------------------------------------------------------------
// REST Handlers
// -------------------------------------------------------------

pub async fn lexicon_get_handler(State(state): State<AppState>) -> Response {
    match state.db.get_lexicon_rules() {
        Ok(rules) => Json(json!({ "success": true, "data": rules })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

pub async fn lexicon_post_handler(
    State(state): State<AppState>,
    Json(payload): Json<LexiconRulePayload>,
) -> Response {
    let category = payload.category.unwrap_or_else(|| "anime".to_string());
    let is_regex = payload.is_regex.unwrap_or(false);

    match state.db.add_lexicon_rule(&payload.target_term, &payload.replacement, &category, is_regex) {
        Ok(_) => Json(json!({ "success": true, "message": "Lexicon rule saved" })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

pub async fn lexicon_delete_handler(
    State(state): State<AppState>,
    Query(params): Query<LexiconDeleteParams>,
) -> Response {
    let rule_id = match params.id {
        Some(id) => id,
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "id parameter is required" })),
            ).into_response()
        }
    };

    match state.db.delete_lexicon_rule(rule_id) {
        Ok(true) => Json(json!({ "success": true, "deleted": true })).into_response(),
        Ok(false) => (
            StatusCode::NOT_FOUND,
            Json(json!({ "success": false, "error": "Rule not found" })),
        ).into_response(),
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
    fn test_shield_and_unshield() {
        let rules = vec![LexiconRule {
            id: 1,
            target_term: "Bankai".into(),
            replacement: "Bankai".into(),
            category: "technique".into(),
            is_regex: false,
            created_at: 0,
        }];

        let input = "Ichigo shouted: Bankai, Tensa Zangetsu!";
        let (shielded, map) = shield_terms(input, &rules);
        assert!(shielded.contains("__LX_0__"));
        assert!(!shielded.contains("Bankai"));

        let simulated_mt = shielded.replace("shouted", "berteriak");
        let restored = unshield_terms(&simulated_mt, &map, &rules);
        assert_eq!(restored, "Ichigo berteriak: Bankai, Tensa Zangetsu!");
    }
}
