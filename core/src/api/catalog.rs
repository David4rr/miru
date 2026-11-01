use crate::api::anilist::{parse_media_item, query_anilist, AnimeCard};
use crate::db::Database;
use anyhow::{anyhow, Result};
use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Debug, Serialize)]
pub struct SortOption {
    pub label: &'static str,
    pub value: &'static str,
}

#[derive(Debug, Serialize)]
pub struct CatalogFiltersResponse {
    pub genres: Vec<&'static str>,
    pub seasons: Vec<&'static str>,
    pub formats: Vec<&'static str>,
    pub statuses: Vec<&'static str>,
    pub years: Vec<i32>,
    pub sort_options: Vec<SortOption>,
}

pub fn get_available_filters() -> CatalogFiltersResponse {
    let current_year = 2026;
    let mut years = Vec::new();
    for y in (1990..=current_year).rev() {
        years.push(y);
    }

    CatalogFiltersResponse {
        genres: vec![
            "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life",
            "Sports", "Supernatural", "Thriller",
        ],
        seasons: vec!["WINTER", "SPRING", "SUMMER", "FALL"],
        formats: vec!["TV", "MOVIE", "TV_SHORT", "OVA", "ONA", "SPECIAL"],
        statuses: vec!["RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"],
        years,
        sort_options: vec![
            SortOption { label: "Paling Populer", value: "POPULARITY_DESC" },
            SortOption { label: "Skor Tertinggi", value: "SCORE_DESC" },
            SortOption { label: "Sedang Trending", value: "TRENDING_DESC" },
            SortOption { label: "Paling Baru Ditambahkan", value: "START_DATE_DESC" },
            SortOption { label: "Paling Lama", value: "START_DATE" },
            SortOption { label: "Judul (A - Z)", value: "TITLE_ROMAJI" },
        ],
    }
}

pub async fn catalog_filters_handler() -> Response {
    Json(json!({
        "success": true,
        "data": get_available_filters()
    })).into_response()
}

#[derive(Debug, Deserialize)]
pub struct BrowseParams {
    pub genre: Option<String>,
    pub season: Option<String>,
    pub year: Option<i32>,
    pub format: Option<String>,
    pub status: Option<String>,
    pub sort: Option<String>,
    pub page: Option<i32>,
    pub per_page: Option<i32>,
}

const BROWSE_QUERY: &str = r#"
query BrowseCatalog(
  $page: Int,
  $perPage: Int,
  $genre: String,
  $season: MediaSeason,
  $seasonYear: Int,
  $format: MediaFormat,
  $status: MediaStatus,
  $sort: [MediaSort],
  $isAdult: Boolean
) {
  Page(page: $page, perPage: $perPage) {
    pageInfo {
      hasNextPage
      currentPage
    }
    media(
      type: ANIME,
      genre: $genre,
      season: $season,
      seasonYear: $seasonYear,
      format: $format,
      status: $status,
      sort: $sort,
      isAdult: $isAdult
    ) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
}
"#;

pub async fn browse_anime(params: &BrowseParams, db: &Database) -> Result<Vec<AnimeCard>> {
    let page = params.page.unwrap_or(1);
    let per_page = params.per_page.unwrap_or(20);
    let is_safe = db.is_safe_mode();

    let cache_key = format!(
        "browse:{}:{}:{}:{}:{}:{}:{}:{}",
        params.genre.as_deref().unwrap_or(""),
        params.season.as_deref().unwrap_or(""),
        params.year.unwrap_or(0),
        params.format.as_deref().unwrap_or(""),
        params.status.as_deref().unwrap_or(""),
        params.sort.as_deref().unwrap_or("POPULARITY_DESC"),
        page,
        is_safe
    );

    if let Ok(Some(cached)) = db.get_browse_cache(&cache_key, 3600) {
        if let Ok(cards) = serde_json::from_value::<Vec<AnimeCard>>(cached) {
            return Ok(cards);
        }
    }

    let mut variables = json!({
        "page": page,
        "perPage": per_page,
    });

    if let Some(g) = &params.genre {
        if !g.trim().is_empty() {
            variables["genre"] = json!(g.trim());
        }
    }
    if let Some(s) = &params.season {
        if !s.trim().is_empty() {
            variables["season"] = json!(s.trim().to_uppercase());
        }
    }
    if let Some(y) = params.year {
        if y > 1900 {
            variables["seasonYear"] = json!(y);
        }
    }
    if let Some(f) = &params.format {
        if !f.trim().is_empty() {
            variables["format"] = json!(f.trim().to_uppercase());
        }
    }
    if let Some(st) = &params.status {
        if !st.trim().is_empty() {
            variables["status"] = json!(st.trim().to_uppercase());
        }
    }
    let sort_val = params.sort.as_deref().unwrap_or("POPULARITY_DESC");
    variables["sort"] = json!([sort_val]);

    if is_safe {
        variables["isAdult"] = json!(false);
    }

    let res = query_anilist(BROWSE_QUERY, variables).await?;
    let media_arr = res
        .get("data")
        .and_then(|d| d.get("Page"))
        .and_then(|p| p.get("media"))
        .and_then(|m| m.as_array())
        .ok_or_else(|| anyhow!("Invalid browse response from AniList"))?;

    let cards: Vec<AnimeCard> = media_arr.iter().filter_map(parse_media_item).collect();

    if let Ok(val) = serde_json::to_value(&cards) {
        let _ = db.set_browse_cache(&cache_key, &val);
    }

    Ok(cards)
}

pub async fn catalog_browse_handler(
    State(state): State<crate::AppState>,
    Query(params): Query<BrowseParams>,
) -> Response {
    match browse_anime(&params, &state.db).await {
        Ok(cards) => Json(json!({ "success": true, "data": cards })).into_response(),
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
    fn test_catalog_filter_options() {
        let filters = get_available_filters();
        assert!(filters.genres.contains(&"Action"));
        assert!(filters.seasons.contains(&"WINTER"));
        assert!(filters.formats.contains(&"TV"));
        assert!(filters.statuses.contains(&"RELEASING"));
        assert!(!filters.years.is_empty());
        assert!(!filters.sort_options.is_empty());
    }
}

