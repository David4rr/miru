use crate::api::anilist::{parse_media_item, query_anilist, AnimeCard};
use crate::db::Database;
use anyhow::Result;
use regex::Regex;
use serde_json::json;
use std::collections::HashSet;
use std::sync::LazyLock;

const SEARCH_QUERY: &str = r#"
query SearchAnime($search: String, $page: Int, $perPage: Int, $isAdult: Boolean) {
  Page(page: $page, perPage: $perPage) {
    media(search: $search, type: ANIME, sort: SEARCH_MATCH, isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
}
"#;

static RE_BRACKETS: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\[[^\]]*\]").unwrap());
static RE_PARENS: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\([^\)]*\)").unwrap());
static RE_NOISE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)\b(?:sub\s+indo(?:nesia)?|dub\s+indo(?:nesia)?|batch|bluray|bd|dvd|1080p|720p|480p|hd|season\s*\d+|s\d+|part\s*\d+|cour\s*\d+|episode\s*\d+|eps?\s*\d+)\b").unwrap()
});
static RE_SPECIAL: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[^\p{L}\p{N}\s-]").unwrap());
static RE_SPACES: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").unwrap());

pub fn normalize_query(query: &str) -> String {
    let without_brackets = RE_BRACKETS.replace_all(query, " ");
    let without_parens = RE_PARENS.replace_all(&without_brackets, " ");
    let without_noise = RE_NOISE.replace_all(&without_parens, " ");
    let cleaned_chars = RE_SPECIAL.replace_all(&without_noise, " ");
    RE_SPACES.replace_all(&cleaned_chars, " ").trim().to_string()
}

const STOP_WORDS: &[&str] = &[
    "the", "a", "an", "of", "in", "to", "for", "with", "on", "at", "by", "from",
    "and", "or", "is", "are", "was", "were", "be", "been", "that", "this", "it", "as",
    "no", "wa", "ga", "wo", "o", "ni", "de", "to", "ka", "na",
    "di", "ke", "dari", "yang", "dan", "untuk", "pada", "ini", "itu", "ada",
];

pub fn generate_fallback_candidates(normalized: &str) -> Vec<String> {
    let lower = normalized.to_lowercase();
    let tokens: Vec<&str> = lower
        .split_whitespace()
        .map(|w| w.trim_matches('-'))
        .filter(|w| !w.is_empty())
        .collect();

    let keywords: Vec<&str> = tokens
        .into_iter()
        .filter(|&w| w.len() > 1 && !STOP_WORDS.contains(&w))
        .collect();

    let mut fallbacks = Vec::new();

    if !keywords.is_empty() {
        let joined = keywords.join(" ");
        if joined != lower {
            fallbacks.push(joined);
        }
    }

    if keywords.len() >= 3 {
        for w in keywords.windows(2) {
            let pair = format!("{} {}", w[0], w[1]);
            if !fallbacks.contains(&pair) {
                fallbacks.push(pair);
            }
        }
    }

    let mut sorted_kw = keywords.clone();
    sorted_kw.sort_by(|a, b| b.len().cmp(&a.len()));
    for kw in sorted_kw {
        let kw_str = kw.to_string();
        if kw.len() >= 4 && !fallbacks.contains(&kw_str) {
            fallbacks.push(kw_str);
        }
    }

    fallbacks.truncate(3);
    fallbacks
}

async fn execute_search_query(
    search_term: &str,
    page: i32,
    per_page: i32,
    is_safe: bool,
) -> Result<Vec<AnimeCard>> {
    let mut variables = json!({
        "search": search_term,
        "page": page,
        "perPage": per_page
    });
    if is_safe {
        variables["isAdult"] = json!(false);
    }

    let res = query_anilist(SEARCH_QUERY, variables).await?;
    let media_arr = res
        .get("data")
        .and_then(|d| d.get("Page"))
        .and_then(|p| p.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();

    let cards: Vec<AnimeCard> = media_arr.iter().filter_map(parse_media_item).collect();
    Ok(cards)
}

pub async fn search_anime(
    query: &str,
    page: i32,
    per_page: i32,
    db: &Database,
) -> Result<Vec<AnimeCard>> {
    let normalized = normalize_query(query);
    if normalized.is_empty() {
        return Ok(Vec::new());
    }

    let is_safe = db.is_safe_mode();
    let cache_key = format!(
        "search:{}:{}:{}:{}",
        normalized.to_lowercase(),
        page,
        per_page,
        is_safe
    );
    if let Ok(Some(cached)) = db.get_search_cache(&cache_key, 3600 * 6) {
        if let Ok(cards) = serde_json::from_value::<Vec<AnimeCard>>(cached) {
            if !cards.is_empty() {
                return Ok(cards);
            }
        }
    }

    let mut cards = execute_search_query(&normalized, page, per_page, is_safe).await?;

    if cards.is_empty() && page == 1 {
        let fallbacks = generate_fallback_candidates(&normalized);
        for candidate in fallbacks {
            if let Ok(fallback_cards) = execute_search_query(&candidate, page, per_page, is_safe).await {
                if !fallback_cards.is_empty() {
                    cards = fallback_cards;
                    break;
                }
            }
        }
    }

    let mut seen = HashSet::new();
    cards.retain(|c| seen.insert(c.id));

    if !cards.is_empty() {
        if let Ok(val) = serde_json::to_value(&cards) {
            let _ = db.set_search_cache(&cache_key, &val);
        }
    }

    Ok(cards)
}
