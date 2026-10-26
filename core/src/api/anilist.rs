use crate::db::Database;
use anyhow::{anyhow, Result};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::LazyLock;

const ANILIST_URL: &str = "https://graphql.anilist.co";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(std::time::Duration::from_secs(12))
        .build()
        .expect("Failed to initialize reqwest client")
});

#[derive(Debug, Serialize, Deserialize)]
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

#[derive(Debug, Serialize, Deserialize)]
pub struct HomeCatalogResponse {
    pub hero: Vec<AnimeCard>,
    pub continue_watching: Vec<Value>,
    pub top_airing: Vec<AnimeCard>,
    pub seasonal: Vec<AnimeCard>,
}

pub fn clean_html_synopsis(raw: &str) -> String {
    let without_br = raw.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n");
    let re = regex::Regex::new(r"<[^>]+>").unwrap_or_else(|_| regex::Regex::new("").unwrap());
    let stripped = re.replace_all(&without_br, "");
    html_escape::decode_html_entities(&stripped).trim().to_string()
}

pub fn slugify(title: &str) -> String {
    let lower = title.to_lowercase();
    let re = regex::Regex::new(r"[^a-z0-9]+").unwrap();
    let cleaned = re.replace_all(&lower, "-").trim_matches('-').to_string();
    if cleaned.is_empty() {
        "anime".to_string()
    } else {
        cleaned
    }
}

fn parse_media_item(m: &Value) -> Option<AnimeCard> {
    let id = m.get("id")?.as_i64()?;
    let id_mal = m.get("idMal").and_then(|v| v.as_i64());
    let title_obj = m.get("title")?;
    let romaji = title_obj.get("romaji").and_then(|v| v.as_str()).unwrap_or("Untitled");
    let english = title_obj.get("english").and_then(|v| v.as_str()).map(|s| s.to_string());
    
    let main_title = english.clone().unwrap_or_else(|| romaji.to_string());
    let slug = format!("{}-{}", slugify(&main_title), id);

    let cover_img = m.get("coverImage")
        .and_then(|c| c.get("large").or_else(|| c.get("medium")))
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();

    let banner = m.get("bannerImage").and_then(|v| v.as_str()).map(|s| s.to_string());
    let score = m.get("averageScore").and_then(|v| v.as_f64()).map(|s| s / 10.0);
    let episodes = m.get("episodes").and_then(|v| v.as_i64()).map(|e| e as i32);

    let next_airing = m.get("nextAiringEpisode");
    let episodes_released = if let Some(na) = next_airing {
        na.get("episode").and_then(|e| e.as_i64()).map(|e| (e - 1) as i32)
    } else {
        episodes
    };

    let status = m.get("status").and_then(|v| v.as_str()).map(|s| s.to_string());
    let season_year = m.get("seasonYear").and_then(|v| v.as_i64()).map(|y| y as i32);

    let genres = m.get("genres")
        .and_then(|g| g.as_array())
        .map(|arr| arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
        .unwrap_or_default();

    let synopsis = m.get("description")
        .and_then(|d| d.as_str())
        .map(clean_html_synopsis);

    Some(AnimeCard {
        id,
        id_mal,
        slug,
        title: main_title,
        title_english: english,
        poster: cover_img,
        banner,
        score,
        episodes,
        episodes_released,
        status,
        season_year,
        genres,
        synopsis,
    })
}

pub async fn query_anilist(query: &str, variables: Value) -> Result<Value> {
    let payload = json!({
        "query": query,
        "variables": variables
    });

    let resp = HTTP_CLIENT
        .post(ANILIST_URL)
        .json(&payload)
        .send()
        .await?;

    if !resp.status().is_success() {
        return Err(anyhow!("AniList GraphQL returned status {}", resp.status()));
    }

    let json_val: Value = resp.json().await?;
    if let Some(errors) = json_val.get("errors") {
        return Err(anyhow!("AniList GraphQL errors: {}", errors));
    }

    Ok(json_val)
}

pub async fn get_mal_id_by_anilist_id(anilist_id: i64) -> Result<Option<i64>> {
    const QUERY: &str = r#"
    query ($id: Int) {
      Media(id: $id, type: ANIME) {
        idMal
      }
    }
    "#;
    let variables = json!({ "id": anilist_id });
    let val = query_anilist(QUERY, variables).await?;
    let id_mal = val.get("data")
        .and_then(|d| d.get("Media"))
        .and_then(|m| m.get("idMal"))
        .and_then(|v| v.as_i64());
    Ok(id_mal)
}

const HOME_QUERY: &str = r#"
query HomeCatalog($season: MediaSeason, $year: Int) {
  trending: Page(page: 1, perPage: 10) {
    media(sort: TRENDING_DESC, type: ANIME) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  popular: Page(page: 1, perPage: 12) {
    media(sort: POPULARITY_DESC, type: ANIME, status: RELEASING) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  seasonal: Page(page: 1, perPage: 12) {
    media(season: $season, seasonYear: $year, sort: POPULARITY_DESC, type: ANIME) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
}
"#;

pub async fn fetch_home_catalog(db: &Database) -> Result<HomeCatalogResponse> {
    let cached = db.get_search_cache("home_catalog_v1", 3600)?;
    if let Some(val) = cached {
        if let Ok(res) = serde_json::from_value::<HomeCatalogResponse>(val) {
            return Ok(res);
        }
    }

    // Determine current season
    use chrono::{Datelike, Utc};
    let now = Utc::now();
    let month = now.month();
    let year = now.year();
    let season = match month {
        1..=3 => "WINTER",
        4..=6 => "SPRING",
        7..=9 => "SUMMER",
        _ => "FALL",
    };

    let variables = json!({
        "season": season,
        "year": year
    });

    let res = query_anilist(HOME_QUERY, variables).await?;
    let data = res.get("data").ok_or_else(|| anyhow!("Missing data in response"))?;

    let trending_arr = data.get("trending")
        .and_then(|t| t.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let hero = trending_arr.iter().filter_map(parse_media_item).collect();

    let popular_arr = data.get("popular")
        .and_then(|p| p.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let top_airing = popular_arr.iter().filter_map(parse_media_item).collect();

    let seasonal_arr = data.get("seasonal")
        .and_then(|s| s.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let seasonal = seasonal_arr.iter().filter_map(parse_media_item).collect();

    let history = db.get_history(10).unwrap_or_default();
    let continue_watching = history.into_iter().map(|h| serde_json::to_value(h).unwrap_or(Value::Null)).collect();

    let response = HomeCatalogResponse {
        hero,
        continue_watching,
        top_airing,
        seasonal,
    };

    if let Ok(json_res) = serde_json::to_value(&response) {
        let _ = db.set_search_cache("home_catalog_v1", &json_res);
    }

    Ok(response)
}

const SEARCH_QUERY: &str = r#"
query SearchAnime($search: String, $page: Int, $perPage: Int) {
  Page(page: $page, perPage: $perPage) {
    media(search: $search, type: ANIME, sort: SEARCH_MATCH) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
}
"#;

pub async fn search_anime(query: &str, page: i32, per_page: i32, db: &Database) -> Result<Vec<AnimeCard>> {
    let cache_key = format!("search:{}:{}:{}", query.trim().to_lowercase(), page, per_page);
    if let Ok(Some(cached)) = db.get_search_cache(&cache_key, 3600 * 6) {
        if let Ok(cards) = serde_json::from_value::<Vec<AnimeCard>>(cached) {
            return Ok(cards);
        }
    }

    let variables = json!({
        "search": query,
        "page": page,
        "perPage": per_page
    });

    let res = query_anilist(SEARCH_QUERY, variables).await?;
    let media_arr = res.get("data")
        .and_then(|d| d.get("Page"))
        .and_then(|p| p.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();

    let cards: Vec<AnimeCard> = media_arr.iter().filter_map(parse_media_item).collect();

    if let Ok(val) = serde_json::to_value(&cards) {
        let _ = db.set_search_cache(&cache_key, &val);
    }

    Ok(cards)
}
