use super::models::{AnimeCard, ScheduleItem};
use serde_json::Value;

pub fn clean_html_synopsis(raw: &str) -> String {
    let without_br = raw
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n");
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

pub fn compute_episodes_released(
    status: Option<&str>,
    episodes: Option<i32>,
    next_airing: Option<&Value>,
) -> Option<i32> {
    match status {
        Some("NOT_YET_RELEASED") => Some(0),
        Some("RELEASING") => {
            if let Some(na) = next_airing {
                if let Some(ep) = na.get("episode").and_then(|e| e.as_i64()) {
                    let released = (ep - 1).max(0) as i32;
                    return Some(if let Some(total) = episodes {
                        released.min(total)
                    } else {
                        released
                    });
                }
            }
            episodes
        }
        Some("FINISHED") => episodes,
        _ => {
            if let Some(na) = next_airing {
                if let Some(ep) = na.get("episode").and_then(|e| e.as_i64()) {
                    return Some((ep - 1).max(0) as i32);
                }
            }
            episodes
        }
    }
}

pub fn parse_media_item(m: &Value) -> Option<AnimeCard> {
    let id = m.get("id")?.as_i64()?;
    let id_mal = m.get("idMal").and_then(|v| v.as_i64());
    let title_obj = m.get("title")?;
    let romaji = title_obj
        .get("romaji")
        .and_then(|v| v.as_str())
        .unwrap_or("Untitled");
    let english = title_obj
        .get("english")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string());

    let main_title = english.clone().unwrap_or_else(|| romaji.to_string());
    let slug = format!("{}-{}", slugify(&main_title), id);

    let cover_img = m
        .get("coverImage")
        .and_then(|c| c.get("large").or_else(|| c.get("medium")))
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();

    let banner = m.get("bannerImage").and_then(|v| v.as_str()).map(|s| s.to_string());
    let score = m.get("averageScore").and_then(|v| v.as_f64()).map(|s| s / 10.0);
    let episodes = m.get("episodes").and_then(|v| v.as_i64()).map(|e| e as i32);

    let status = m.get("status").and_then(|v| v.as_str()).map(|s| s.to_string());
    let next_airing = m.get("nextAiringEpisode");
    let episodes_released = compute_episodes_released(status.as_deref(), episodes, next_airing);

    let season_year = m.get("seasonYear").and_then(|v| v.as_i64()).map(|y| y as i32);

    let genres = m
        .get("genres")
        .and_then(|g| g.as_array())
        .map(|arr| arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
        .unwrap_or_default();

    let synopsis = m
        .get("description")
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

pub fn parse_schedule_item(item: &Value) -> Option<ScheduleItem> {
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
