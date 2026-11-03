use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnimeCard {
    pub id: i64,
    pub id_mal: Option<i64>,
    pub slug: String,
    pub title: String,
    pub title_english: Option<String>,
    pub poster: String,
    pub banner: Option<String>,
    pub score: Option<f64>,
    pub episodes: Option<i32>,
    pub episodes_released: Option<i32>,
    pub status: Option<String>,
    pub season_year: Option<i32>,
    pub genres: Vec<String>,
    pub synopsis: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScheduleItem {
    pub id: i64,
    pub episode: i32,
    pub airing_at: i64,
    pub anime: AnimeCard,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DaySchedule {
    pub day_name: String,
    pub day_key: String,
    pub date_formatted: String,
    pub is_today: bool,
    pub items: Vec<ScheduleItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WeeklyScheduleResponse {
    pub days: Vec<DaySchedule>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HomeCatalogResponse {
    pub hero: Vec<AnimeCard>,
    pub continue_watching: Vec<Value>,
    pub airing_today: Vec<ScheduleItem>,
    pub top_airing: Vec<AnimeCard>,
    pub seasonal: Vec<AnimeCard>,
    pub top_rated: Vec<AnimeCard>,
    pub upcoming: Vec<AnimeCard>,
    pub all_time_popular: Vec<AnimeCard>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnimeEdition {
    pub id: i64,
    pub id_mal: Option<i64>,
    pub slug: String,
    pub title: String,
    pub title_english: Option<String>,
    pub format: String,
    pub status: Option<String>,
    pub season_year: Option<i32>,
    pub episodes: Option<i32>,
    pub episodes_released: Option<i32>,
    pub poster: String,
    pub score: Option<f64>,
    pub edition_label: String,
    pub is_current: bool,
    pub date_order: i64,
}
