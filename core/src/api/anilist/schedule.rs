use crate::db::Database;
use anyhow::Result;
use chrono::{Datelike, Utc, Weekday};
use serde_json::json;

use super::client::query_anilist;
use super::models::{DaySchedule, WeeklyScheduleResponse};
use super::parser::parse_schedule_item;

pub const WEEKLY_SCHEDULE_QUERY: &str = r#"
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
    const WIB_OFFSET_SECS: i64 = 7 * 3600;
    let now_ts = Utc::now().timestamp();
    let now_wib_ts = now_ts + WIB_OFFSET_SECS;
    let day_secs = now_wib_ts.rem_euclid(86400);
    let today_start = now_wib_ts - day_secs - WIB_OFFSET_SECS;

    let today_date_wib = chrono::DateTime::from_timestamp(today_start + WIB_OFFSET_SECS, 0)
        .unwrap_or_else(|| Utc::now().into())
        .date_naive();
    let cache_key = format!("weekly_schedule_{}", today_date_wib.format("%Y_%W"));

    if let Ok(Some(cached)) = db.get_anilist_cache(&cache_key, 3600) {
        if let Ok(res) = serde_json::from_value::<WeeklyScheduleResponse>(cached) {
            return Ok(res);
        }
    }

    let days_from_monday = match today_date_wib.weekday() {
        Weekday::Mon => 0,
        Weekday::Tue => 1,
        Weekday::Wed => 2,
        Weekday::Thu => 3,
        Weekday::Fri => 4,
        Weekday::Sat => 5,
        Weekday::Sun => 6,
    };
    let week_start = today_start - days_from_monday * 86400;
    let week_end = week_start + 7 * 86400;

    let mut all_schedules = Vec::new();
    let mut page = 1;

    let fetch_res: Result<()> = async {
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
                let has_next = p
                    .get("pageInfo")
                    .and_then(|pi| pi.get("hasNextPage"))
                    .and_then(|h| h.as_bool())
                    .unwrap_or(false);
                if !has_next || page >= 3 {
                    break;
                }
                page += 1;
            } else {
                break;
            }
        }
        Ok(())
    }
    .await;

    if let Err(e) = fetch_res {
        if let Ok(Some(stale)) = db.get_anilist_cache(&cache_key, 86400 * 30) {
            if let Ok(res) = serde_json::from_value::<WeeklyScheduleResponse>(stale) {
                return Ok(res);
            }
        }
        return Err(e);
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
        let day_start = week_start + day_offset * 86400;
        let day_end = day_start + 86400;
        let is_today = day_offset == days_from_monday;

        let date_obj = chrono::DateTime::from_timestamp(day_start + WIB_OFFSET_SECS, 0)
            .unwrap_or_else(|| Utc::now().into())
            .date_naive();
        let date_formatted = date_obj.format("%d %b").to_string();

        let mut day_items: Vec<_> = all_schedules
            .iter()
            .filter(|s| s.airing_at >= day_start && s.airing_at < day_end)
            .cloned()
            .collect();
        day_items.sort_by_key(|s| s.airing_at);

        days.push(DaySchedule {
            day_name: day_name.to_string(),
            day_key: day_key.to_string(),
            date_formatted,
            is_today,
            items: day_items,
        });
    }

    let response = WeeklyScheduleResponse { days };
    if let Ok(json_res) = serde_json::to_value(&response) {
        let _ = db.set_anilist_cache(&cache_key, &json_res);
    }

    Ok(response)
}
