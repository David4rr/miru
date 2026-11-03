use super::models::{CachedEpisode, SkipInterval};
use super::{now_epoch, Database};
use rusqlite::{params, Result};
use serde_json::Value;

impl Database {
    pub fn get_search_cache(&self, query: &str, ttl_secs: i64) -> Result<Option<Value>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT payload, fetched_at FROM search_cache WHERE query = ?1")?;
        let mut rows = stmt.query(params![query])?;

        if let Some(row) = rows.next()? {
            let fetched_at: i64 = row.get(1)?;
            if now_epoch() - fetched_at <= ttl_secs {
                let payload: String = row.get(0)?;
                if let Ok(val) = serde_json::from_str(&payload) {
                    return Ok(Some(val));
                }
            }
        }
        Ok(None)
    }

    pub fn set_search_cache(&self, query: &str, payload: &Value) -> Result<()> {
        let conn = self.conn.lock();
        let json_str = payload.to_string();
        conn.execute(
            "INSERT OR REPLACE INTO search_cache (query, payload, fetched_at) VALUES (?1, ?2, ?3)",
            params![query, json_str, now_epoch()],
        )?;
        Ok(())
    }

    pub fn get_episode_cache(&self, slug: &str, ep: i32, mode: &str, ttl_secs: i64) -> Result<Option<CachedEpisode>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT slug, ep, mode, master, variants, sub, sub_lang, referer, server, subtitles, intro, outro, fetched_at
             FROM episode_cache WHERE slug = ?1 AND ep = ?2 AND mode = ?3",
        )?;
        let mut rows = stmt.query(params![slug, ep, mode])?;

        if let Some(row) = rows.next()? {
            let fetched_at: i64 = row.get(12)?;
            if now_epoch() - fetched_at <= ttl_secs {
                let variants_str: String = row.get(4).unwrap_or_else(|_| "[]".to_string());
                let subtitles_str: String = row.get(9).unwrap_or_else(|_| "[]".to_string());
                let intro_str: String = row.get(10).unwrap_or_else(|_| "{}".to_string());
                let outro_str: String = row.get(11).unwrap_or_else(|_| "{}".to_string());

                let cached = CachedEpisode {
                    slug: row.get(0)?,
                    ep: row.get(1)?,
                    mode: row.get(2)?,
                    master: row.get(3)?,
                    variants: serde_json::from_str(&variants_str).unwrap_or(Value::Array(vec![])),
                    sub: row.get(5)?,
                    sub_lang: row.get(6)?,
                    referer: row.get(7)?,
                    server: row.get(8)?,
                    subtitles: serde_json::from_str(&subtitles_str).unwrap_or_default(),
                    intro: serde_json::from_str(&intro_str).unwrap_or(SkipInterval { start: 0.0, end: 0.0 }),
                    outro: serde_json::from_str(&outro_str).unwrap_or(SkipInterval { start: 0.0, end: 0.0 }),
                    fetched_at,
                };
                return Ok(Some(cached));
            }
        }
        Ok(None)
    }

    pub fn set_episode_cache(&self, item: &CachedEpisode) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO episode_cache 
             (slug, ep, mode, master, variants, sub, sub_lang, referer, server, subtitles, intro, outro, fetched_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
            params![
                item.slug,
                item.ep,
                item.mode,
                item.master,
                item.variants.to_string(),
                item.sub,
                item.sub_lang,
                item.referer,
                item.server,
                serde_json::to_string(&item.subtitles).unwrap_or_else(|_| "[]".to_string()),
                serde_json::to_string(&item.intro).unwrap_or_else(|_| "{}".to_string()),
                serde_json::to_string(&item.outro).unwrap_or_else(|_| "{}".to_string()),
                now_epoch()
            ],
        )?;
        Ok(())
    }

    pub fn get_subtitle_cache(&self, key: &str, ttl_secs: i64) -> Result<Option<String>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT payload, fetched_at FROM subtitle_cache WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;

        if let Some(row) = rows.next()? {
            let fetched_at: i64 = row.get(1)?;
            if now_epoch() - fetched_at <= ttl_secs {
                let payload: String = row.get(0)?;
                return Ok(Some(payload));
            }
        }
        Ok(None)
    }

    pub fn set_subtitle_cache(&self, key: &str, payload: &str) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO subtitle_cache (key, payload, fetched_at) VALUES (?1, ?2, ?3)",
            params![key, payload, now_epoch()],
        )?;
        Ok(())
    }

    pub fn get_browse_cache(&self, key: &str, ttl_secs: i64) -> Result<Option<Value>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT payload, fetched_at FROM browse_cache WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;

        if let Some(row) = rows.next()? {
            let fetched_at: i64 = row.get(1)?;
            if now_epoch() - fetched_at <= ttl_secs {
                let payload: String = row.get(0)?;
                if let Ok(val) = serde_json::from_str(&payload) {
                    return Ok(Some(val));
                }
            }
        }
        Ok(None)
    }

    pub fn set_browse_cache(&self, key: &str, payload: &Value) -> Result<()> {
        let conn = self.conn.lock();
        let serialized = serde_json::to_string(payload).unwrap_or_default();
        conn.execute(
            "INSERT OR REPLACE INTO browse_cache (key, payload, fetched_at) VALUES (?1, ?2, ?3)",
            params![key, serialized, now_epoch()],
        )?;
        Ok(())
    }

    pub fn get_anilist_cache(&self, key: &str, ttl_secs: i64) -> Result<Option<Value>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT payload, fetched_at FROM anilist_cache WHERE cache_key = ?1")?;
        let mut rows = stmt.query(params![key])?;

        if let Some(row) = rows.next()? {
            let fetched_at: i64 = row.get(1)?;
            if now_epoch() - fetched_at <= ttl_secs {
                let payload: String = row.get(0)?;
                if let Ok(val) = serde_json::from_str(&payload) {
                    return Ok(Some(val));
                }
            }
        }
        Ok(None)
    }

    pub fn set_anilist_cache(&self, key: &str, payload: &Value) -> Result<()> {
        let conn = self.conn.lock();
        let json_str = payload.to_string();
        conn.execute(
            "INSERT OR REPLACE INTO anilist_cache (cache_key, payload, fetched_at) VALUES (?1, ?2, ?3)",
            params![key, json_str, now_epoch()],
        )?;
        Ok(())
    }

    pub fn clear_cache(&self, target: &str) -> Result<usize> {
        let conn = self.conn.lock();
        let mut count = 0;
        match target {
            "all" => {
                count += conn.execute("DELETE FROM search_cache", [])?;
                count += conn.execute("DELETE FROM browse_cache", [])?;
                count += conn.execute("DELETE FROM subtitle_cache", [])?;
                count += conn.execute("DELETE FROM episode_cache", [])?;
                count += conn.execute("DELETE FROM anilist_cache", [])?;
            }
            "search" => {
                count += conn.execute("DELETE FROM search_cache", [])?;
            }
            "browse" => {
                count += conn.execute("DELETE FROM browse_cache", [])?;
            }
            "sub" | "subtitles" => {
                count += conn.execute("DELETE FROM subtitle_cache", [])?;
            }
            "ep" | "episode" => {
                count += conn.execute("DELETE FROM episode_cache", [])?;
            }
            "anilist" => {
                count += conn.execute("DELETE FROM anilist_cache", [])?;
            }
            _ => {
                count += conn.execute("DELETE FROM search_cache", [])?;
                count += conn.execute("DELETE FROM browse_cache", [])?;
            }
        }
        Ok(count)
    }

    pub fn get_table_counts(&self) -> Result<Value> {
        let conn = self.conn.lock();
        let count_table = |tbl: &str| -> i64 {
            let sql = format!("SELECT COUNT(*) FROM {}", tbl);
            conn.query_row(&sql, [], |r| r.get(0)).unwrap_or(0)
        };
        Ok(serde_json::json!({
            "watch_history": count_table("watch_history"),
            "watch_progress": count_table("watch_progress"),
            "bookmarks": count_table("bookmarks"),
            "episode_cache": count_table("episode_cache"),
            "search_cache": count_table("search_cache"),
            "browse_cache": count_table("browse_cache"),
            "subtitle_cache": count_table("subtitle_cache"),
            "anilist_cache": count_table("anilist_cache"),
            "settings": count_table("settings"),
        }))
    }
}
