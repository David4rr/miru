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
fn parse_schedule_item(item: &Value) -> Option<ScheduleItem> {
    let id = item.get("id")?.as_i64()?;
    let episode = item.get("episode")?.as_i64()? as i32;
    let airing_at = item.get("airingAt")?.as_i64()?;
    let media = item.get("media")?;
    let anime = parse_media_item(media)?;
    Some(ScheduleItem {
        id,
        episode,
        airing_at,
        anime,
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
query HomeCatalog($season: MediaSeason, $year: Int, $todayStart: Int, $todayEnd: Int) {
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
  airingToday: Page(page: 1, perPage: 20) {
    airingSchedules(airingAt_greater: $todayStart, airingAt_lesser: $todayEnd, sort: TIME) {
      id episode airingAt
      media {
        id idMal status episodes averageScore seasonYear genres bannerImage
        title { romaji english }
        coverImage { large }
        description(asHtml: false)
        nextAiringEpisode { episode }
      }
    }
  }
  topRated: Page(page: 1, perPage: 12) {
    media(sort: SCORE_DESC, type: ANIME, status_in: [RELEASING, FINISHED]) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  upcoming: Page(page: 1, perPage: 12) {
    media(status: NOT_YET_RELEASED, sort: POPULARITY_DESC, type: ANIME) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  allTime: Page(page: 1, perPage: 12) {
    media(sort: POPULARITY_DESC, type: ANIME) {
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
    let cached = db.get_search_cache("home_catalog_v2", 3600)?;
    if let Some(val) = cached {
        if let Ok(res) = serde_json::from_value::<HomeCatalogResponse>(val) {
            return Ok(res);
        }
    }

    use chrono::{Datelike, Utc};
    let now = Utc::now();
    let today_date = now.date_naive();
    let today_start = today_date.and_hms_opt(0, 0, 0).unwrap().and_utc().timestamp();
    let today_end = today_start + 86400;

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
        "year": year,
        "todayStart": today_start,
        "todayEnd": today_end
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

    let airing_today_arr = data.get("airingToday")
        .and_then(|a| a.get("airingSchedules"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let airing_today = airing_today_arr.iter().filter_map(parse_schedule_item).collect();

    let top_rated_arr = data.get("topRated")
        .and_then(|tr| tr.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let top_rated = top_rated_arr.iter().filter_map(parse_media_item).collect();

    let upcoming_arr = data.get("upcoming")
        .and_then(|u| u.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let upcoming = upcoming_arr.iter().filter_map(parse_media_item).collect();

    let all_time_arr = data.get("allTime")
        .and_then(|at| at.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let all_time_popular = all_time_arr.iter().filter_map(parse_media_item).collect();

    let history = db.get_history(10).unwrap_or_default();
    let continue_watching = history.into_iter().map(|h| serde_json::to_value(h).unwrap_or(Value::Null)).collect();

    let response = HomeCatalogResponse {
        hero,
        continue_watching,
        airing_today,
        top_airing,
        seasonal,
        top_rated,
        upcoming,
        all_time_popular,
    };

    if let Ok(json_res) = serde_json::to_value(&response) {
        let _ = db.set_search_cache("home_catalog_v2", &json_res);
    }

    Ok(response)
}

const WEEKLY_SCHEDULE_QUERY: &str = r#"
query WeeklySchedule($weekStart: Int, $weekEnd: Int, $page: Int) {
  Page(page: $page, perPage: 50) {
    pageInfo {
      hasNextPage
    }
    airingSchedules(airingAt_greater: $weekStart, airingAt_lesser: $weekEnd, sort: TIME) {
      id episode airingAt
      media {
        id idMal status episodes averageScore seasonYear genres bannerImage
        title { romaji english }
        coverImage { large }
        description(asHtml: false)
        nextAiringEpisode { episode }
      }
    }
  }
}
"#;

pub async fn fetch_schedule(db: &Database) -> Result<WeeklyScheduleResponse> {
    use chrono::{Datelike, Duration, Utc, Weekday};
    let now = Utc::now();
    let today_date = now.date_naive();
    let cache_key = format!("weekly_schedule_{}", today_date.format("%Y_%W"));

    if let Ok(Some(cached)) = db.get_search_cache(&cache_key, 3600) {
        if let Ok(res) = serde_json::from_value::<WeeklyScheduleResponse>(cached) {
            return Ok(res);
        }
    }

    let days_from_monday = match today_date.weekday() {
        Weekday::Mon => 0,
        Weekday::Tue => 1,
        Weekday::Wed => 2,
        Weekday::Thu => 3,
        Weekday::Fri => 4,
        Weekday::Sat => 5,
        Weekday::Sun => 6,
    };
    let week_start_date = today_date - Duration::days(days_from_monday);
    let week_start = week_start_date.and_hms_opt(0, 0, 0).unwrap().and_utc().timestamp();
    let week_end = week_start + 7 * 86400;

    let mut all_schedules = Vec::new();
    let mut page = 1;

    loop {
        let variables = json!({
            "weekStart": week_start,
            "weekEnd": week_end,
            "page": page
        });

        let res = query_anilist(WEEKLY_SCHEDULE_QUERY, variables).await?;
        let page_obj = res.get("data").and_then(|d| d.get("Page"));
        if let Some(p) = page_obj {
            if let Some(arr) = p.get("airingSchedules").and_then(|a| a.as_array()) {
                for item in arr {
                    if let Some(sched) = parse_schedule_item(item) {
                        all_schedules.push(sched);
                    }
                }
            }
            let has_next = p.get("pageInfo").and_then(|pi| pi.get("hasNextPage")).and_then(|h| h.as_bool()).unwrap_or(false);
            if !has_next || page >= 3 {
                break;
            }
            page += 1;
        } else {
            break;
        }
    }

    let day_configs = [
        ("Senin", "monday", 0),
        ("Selasa", "tuesday", 1),
        ("Rabu", "wednesday", 2),
        ("Kamis", "thursday", 3),
        ("Jumat", "friday", 4),
        ("Sabtu", "saturday", 5),
        ("Minggu", "sunday", 6),
    ];

    let mut days = Vec::new();

    for (day_name, day_key, day_offset) in day_configs {
        let current_day_date = week_start_date + Duration::days(day_offset);
        let day_start = current_day_date.and_hms_opt(0, 0, 0).unwrap().and_utc().timestamp();
        let day_end = day_start + 86400;
        let is_today = current_day_date == today_date;

        let month_name = match current_day_date.month() {
            1 => "Jan", 2 => "Feb", 3 => "Mar", 4 => "Apr",
            5 => "Mei", 6 => "Jun", 7 => "Jul", 8 => "Agt",
            9 => "Sep", 10 => "Okt", 11 => "Nov", _ => "Des",
        };
        let date_formatted = format!("{} {}", current_day_date.day(), month_name);

        let items: Vec<ScheduleItem> = all_schedules
            .iter()
            .filter(|s| s.airing_at >= day_start && s.airing_at < day_end)
            .cloned()
            .collect();

        days.push(DaySchedule {
            day_name: day_name.to_string(),
            day_key: day_key.to_string(),
            date_formatted,
            is_today,
            items,
        });
    }

    let response = WeeklyScheduleResponse { days };
    if let Ok(json_res) = serde_json::to_value(&response) {
        let _ = db.set_search_cache(&cache_key, &json_res);
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
