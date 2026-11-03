use crate::db::Database;
use anyhow::{anyhow, Result};
use serde_json::{json, Value};
use std::collections::HashMap;

use super::client::query_anilist;
use super::models::AnimeEdition;
use super::parser::{compute_episodes_released, slugify};

pub const EDITIONS_QUERY: &str = r#"
query AnimeRelations($id: Int) {
  Media(id: $id, type: ANIME) {
    id idMal format status episodes seasonYear averageScore
    startDate { year month day }
    title { romaji english }
    coverImage { large medium }
    nextAiringEpisode { episode }
    relations {
      edges {
        relationType
        node {
          id idMal type format status episodes seasonYear averageScore
          startDate { year month day }
          title { romaji english }
          coverImage { large medium }
          nextAiringEpisode { episode }
          relations {
            edges {
              relationType
              node {
                id idMal type format status episodes seasonYear averageScore
                startDate { year month day }
                title { romaji english }
                coverImage { large medium }
                nextAiringEpisode { episode }
              }
            }
          }
        }
      }
    }
  }
}
"#;

fn parse_node_into(m: &Value, map: &mut HashMap<i64, (Value, i64)>) {
    if let Some(id) = m.get("id").and_then(|v| v.as_i64()) {
        let m_type = m.get("type").and_then(|t| t.as_str()).unwrap_or("ANIME");
        if m_type == "ANIME" && !map.contains_key(&id) {
            let sd = m.get("startDate");
            let y = sd
                .and_then(|s| s.get("year"))
                .and_then(|v| v.as_i64())
                .unwrap_or(9999);
            let mo = sd
                .and_then(|s| s.get("month"))
                .and_then(|v| v.as_i64())
                .unwrap_or(1);
            let d = sd
                .and_then(|s| s.get("day"))
                .and_then(|v| v.as_i64())
                .unwrap_or(1);
            let date_val = y * 10000 + mo * 100 + d;
            map.insert(id, (m.clone(), date_val));
        }
    }
}

fn extract_relations_into(node: &Value, map: &mut HashMap<i64, (Value, i64)>) {
    if let Some(edges) = node
        .get("relations")
        .and_then(|r| r.get("edges"))
        .and_then(|e| e.as_array())
    {
        for edge in edges {
            let rel = edge.get("relationType").and_then(|r| r.as_str()).unwrap_or("");
            if matches!(rel, "PREQUEL" | "SEQUEL") {
                if let Some(n) = edge.get("node") {
                    parse_node_into(n, map);
                    if let Some(sub_edges) = n
                        .get("relations")
                        .and_then(|r| r.get("edges"))
                        .and_then(|e| e.as_array())
                    {
                        for sub_edge in sub_edges {
                            let sub_rel =
                                sub_edge.get("relationType").and_then(|r| r.as_str()).unwrap_or("");
                            if matches!(sub_rel, "PREQUEL" | "SEQUEL") {
                                if let Some(sub_node) = sub_edge.get("node") {
                                    parse_node_into(sub_node, map);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

pub async fn fetch_anime_editions(target_id: i64, db: &Database) -> Result<Vec<AnimeEdition>> {
    let cache_key = format!("anime_editions_{}", target_id);
    if let Ok(Some(cached)) = db.get_anilist_cache(&cache_key, 86400) {
        if let Ok(editions) = serde_json::from_value::<Vec<AnimeEdition>>(cached) {
            return Ok(editions);
        }
    }

    let variables = json!({ "id": target_id });
    let res = query_anilist(EDITIONS_QUERY, variables).await?;
    let root = res
        .get("data")
        .and_then(|d| d.get("Media"))
        .ok_or_else(|| anyhow!("Anime not found for editions query"))?;

    let mut map: HashMap<i64, (Value, i64)> = HashMap::new();
    parse_node_into(root, &mut map);
    extract_relations_into(root, &mut map);

    for _ in 0..2 {
        let mut sorted_keys: Vec<(i64, i64)> = map.iter().map(|(&k, (_, d))| (k, *d)).collect();
        sorted_keys.sort_by_key(|(_, d)| *d);
        if let Some(&(latest_id, _)) = sorted_keys.last() {
            if latest_id != target_id {
                let before_len = map.len();
                if let Ok(extra_val) = query_anilist(EDITIONS_QUERY, json!({ "id": latest_id })).await {
                    if let Some(extra_node) = extra_val.get("data").and_then(|d| d.get("Media")) {
                        extract_relations_into(extra_node, &mut map);
                    }
                }
                if map.len() == before_len {
                    break;
                }
            } else {
                break;
            }
        } else {
            break;
        }
    }

    for _ in 0..2 {
        let mut sorted_keys: Vec<(i64, i64)> = map.iter().map(|(&k, (_, d))| (k, *d)).collect();
        sorted_keys.sort_by_key(|(_, d)| *d);
        if let Some(&(earliest_id, _)) = sorted_keys.first() {
            if earliest_id != target_id {
                let before_len = map.len();
                if let Ok(extra_val) = query_anilist(EDITIONS_QUERY, json!({ "id": earliest_id })).await {
                    if let Some(extra_node) = extra_val.get("data").and_then(|d| d.get("Media")) {
                        extract_relations_into(extra_node, &mut map);
                    }
                }
                if map.len() == before_len {
                    break;
                }
            } else {
                break;
            }
        } else {
            break;
        }
    }

    let mut list: Vec<(Value, i64)> = map.into_values().collect();
    list.sort_by_key(|(_, d)| *d);

    let mut season_counter = 1;
    let mut editions = Vec::with_capacity(list.len());

    for (node, date_val) in list {
        let id = node.get("id").and_then(|v| v.as_i64()).unwrap_or(0);
        let id_mal = node.get("idMal").and_then(|v| v.as_i64());
        let format = node
            .get("format")
            .and_then(|f| f.as_str())
            .unwrap_or("TV")
            .to_string();
        let status = node.get("status").and_then(|s| s.as_str()).map(|s| s.to_string());
        let episodes = node.get("episodes").and_then(|e| e.as_i64()).map(|e| e as i32);
        let season_year = node.get("seasonYear").and_then(|y| y.as_i64()).map(|y| y as i32);
        let next_airing = node.get("nextAiringEpisode");
        let episodes_released = compute_episodes_released(status.as_deref(), episodes, next_airing);
        let score = node.get("averageScore").and_then(|s| s.as_f64()).map(|s| s / 10.0);

        let title_obj = node.get("title");
        let romaji = title_obj
            .and_then(|t| t.get("romaji"))
            .and_then(|r| r.as_str())
            .unwrap_or("Anime");
        let english = title_obj
            .and_then(|t| t.get("english"))
            .and_then(|e| e.as_str())
            .map(|e| e.to_string());
        let main_title = english.clone().unwrap_or_else(|| romaji.to_string());
        let slug = format!("{}-{}", slugify(&main_title), id);

        let poster = node
            .get("coverImage")
            .and_then(|c| c.get("large").or_else(|| c.get("medium")))
            .and_then(|u| u.as_str())
            .unwrap_or("")
            .to_string();

        let edition_label = match format.as_str() {
            "TV" | "TV_SHORT" => {
                let lbl = format!("S{}", season_counter);
                season_counter += 1;
                lbl
            }
            "MOVIE" => "Movie".to_string(),
            "OVA" => "OVA".to_string(),
            "ONA" => "ONA".to_string(),
            _ => "Special".to_string(),
        };

        editions.push(AnimeEdition {
            id,
            id_mal,
            slug,
            title: main_title,
            title_english: english,
            format,
            status,
            season_year,
            episodes,
            episodes_released,
            poster,
            score,
            edition_label,
            is_current: (id == target_id),
            date_order: date_val,
        });
    }

    if let Ok(json_res) = serde_json::to_value(&editions) {
        let _ = db.set_anilist_cache(&cache_key, &json_res);
    }

    Ok(editions)
}
