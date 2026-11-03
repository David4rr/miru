use crate::db::Database;
use anyhow::{anyhow, Result};
use chrono::{Datelike, Utc};
use serde_json::{json, Value};

use super::client::query_anilist;
use super::models::{HomeCatalogResponse, ScheduleItem};
use super::parser::{parse_media_item, parse_schedule_item};

pub const HOME_QUERY: &str = r#"
query HomeCatalog($season: MediaSeason, $year: Int, $todayStart: Int, $todayEnd: Int, $isAdult: Boolean) {
  trending: Page(page: 1, perPage: 10) {
    media(sort: TRENDING_DESC, type: ANIME, isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  popular: Page(page: 1, perPage: 12) {
    media(sort: POPULARITY_DESC, type: ANIME, status: RELEASING, isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
      nextAiringEpisode { episode }
    }
  }
  seasonal: Page(page: 1, perPage: 12) {
    media(season: $season, seasonYear: $year, sort: POPULARITY_DESC, type: ANIME, isAdult: $isAdult) {
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
    media(sort: SCORE_DESC, type: ANIME, status_in: [RELEASING, FINISHED], isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
    }
  }
  upcoming: Page(page: 1, perPage: 12) {
    media(status: NOT_YET_RELEASED, sort: POPULARITY_DESC, type: ANIME, isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
    }
  }
  allTime: Page(page: 1, perPage: 12) {
    media(sort: POPULARITY_DESC, type: ANIME, isAdult: $isAdult) {
      id idMal status episodes averageScore seasonYear genres bannerImage
      title { romaji english }
      coverImage { large }
      description(asHtml: false)
    }
  }
}
"#;

pub async fn fetch_home_catalog(db: &Database) -> Result<HomeCatalogResponse> {
    let is_safe = db.is_safe_mode();
    let cache_key = if is_safe {
        "home_catalog_v2_safe"
    } else {
        "home_catalog_v2_all"
    };

    if let Ok(Some(val)) = db.get_anilist_cache(cache_key, 3600) {
        if let Ok(mut res) = serde_json::from_value::<HomeCatalogResponse>(val) {
            let history = db.get_history(10).unwrap_or_default();
            res.continue_watching = history
                .into_iter()
                .map(|h| serde_json::to_value(h).unwrap_or(Value::Null))
                .collect();
            return Ok(res);
        }
    }

    const WIB_OFFSET_SECS: i64 = 7 * 3600;
    let now = Utc::now();
    let now_ts = now.timestamp();
    let now_wib_ts = now_ts + WIB_OFFSET_SECS;
    let day_secs = now_wib_ts.rem_euclid(86400);
    let today_start = now_wib_ts - day_secs - WIB_OFFSET_SECS;
    let today_end = today_start + 86400;

    let month = now.month();
    let year = now.year();
    let season = match month {
        1..=3 => "WINTER",
        4..=6 => "SPRING",
        7..=9 => "SUMMER",
        _ => "FALL",
    };

    let mut variables = json!({
        "season": season,
        "year": year,
        "todayStart": today_start,
        "todayEnd": today_end
    });
    if is_safe {
        variables["isAdult"] = json!(false);
    }

    let res = match query_anilist(HOME_QUERY, variables).await {
        Ok(val) => val,
        Err(err) => {
            if let Ok(Some(stale)) = db.get_anilist_cache(cache_key, 86400 * 30) {
                if let Ok(mut res) = serde_json::from_value::<HomeCatalogResponse>(stale) {
                    let history = db.get_history(10).unwrap_or_default();
                    res.continue_watching = history
                        .into_iter()
                        .map(|h| serde_json::to_value(h).unwrap_or(Value::Null))
                        .collect();
                    return Ok(res);
                }
            }
            return Err(err);
        }
    };

    let data = res
        .get("data")
        .ok_or_else(|| anyhow!("Missing data in response"))?;

    let trending_arr = data
        .get("trending")
        .and_then(|t| t.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let hero = trending_arr.iter().filter_map(parse_media_item).collect();

    let popular_arr = data
        .get("popular")
        .and_then(|p| p.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let top_airing = popular_arr.iter().filter_map(parse_media_item).collect();

    let seasonal_arr = data
        .get("seasonal")
        .and_then(|s| s.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let seasonal = seasonal_arr.iter().filter_map(parse_media_item).collect();

    let airing_today_arr = data
        .get("airingToday")
        .and_then(|a| a.get("airingSchedules"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let mut airing_today: Vec<ScheduleItem> = airing_today_arr
        .iter()
        .filter_map(parse_schedule_item)
        .collect();
    airing_today.sort_by_key(|s| s.airing_at);

    let top_rated_arr = data
        .get("topRated")
        .and_then(|tr| tr.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let top_rated = top_rated_arr.iter().filter_map(parse_media_item).collect();

    let upcoming_arr = data
        .get("upcoming")
        .and_then(|u| u.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let upcoming = upcoming_arr.iter().filter_map(parse_media_item).collect();

    let all_time_arr = data
        .get("allTime")
        .and_then(|at| at.get("media"))
        .and_then(|m| m.as_array())
        .cloned()
        .unwrap_or_default();
    let all_time_popular = all_time_arr.iter().filter_map(parse_media_item).collect();

    let history = db.get_history(10).unwrap_or_default();
    let continue_watching = history
        .into_iter()
        .map(|h| serde_json::to_value(h).unwrap_or(Value::Null))
        .collect();

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
        let _ = db.set_anilist_cache(cache_key, &json_res);
    }

    Ok(response)
}
