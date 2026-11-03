use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Serialize, Deserialize)]
pub struct WatchHistoryItem {
    pub id: Option<i64>,
    pub slug: String,
    pub title: String,
    pub episode: i32,
    pub mode: String,
    pub progress: i32,
    pub poster: String,
    #[serde(rename = "type")]
    pub anime_type: String,
    pub played_at: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct WatchProgressItem {
    pub slug: String,
    pub episode: i32,
    pub progress: i32,
    pub duration: i32,
    pub updated_at: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BookmarkItem {
    pub slug: String,
    pub title: String,
    pub poster: String,
    #[serde(rename = "type")]
    pub anime_type: String,
    pub sub: String,
    pub eps: String,
    pub score: f64,
    pub created_at: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SubtitleTrack {
    pub label: String,
    #[serde(default)]
    pub lang: String,
    pub url: String,
    #[serde(default)]
    pub default: bool,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SkipInterval {
    #[serde(default)]
    pub start: f64,
    #[serde(default)]
    pub end: f64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CachedEpisode {
    pub slug: String,
    pub ep: i32,
    pub mode: String,
    pub master: String,
    pub variants: Value,
    pub sub: String,
    pub sub_lang: String,
    pub referer: String,
    pub server: String,
    pub subtitles: Vec<SubtitleTrack>,
    pub intro: SkipInterval,
    pub outro: SkipInterval,
    pub fetched_at: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct LexiconRule {
    pub id: i64,
    pub target_term: String,
    pub replacement: String,
    pub category: String,
    pub is_regex: bool,
    pub created_at: i64,
}
