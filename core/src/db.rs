use parking_lot::Mutex;
use rusqlite::{params, Connection, OptionalExtension, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::path::Path;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

fn now_epoch() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

#[derive(Clone)]
pub struct Database {
    conn: Arc<Mutex<Connection>>,
}

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
impl Database {
    pub fn init<P: AsRef<Path>>(path: P) -> Result<Self> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA temp_store = MEMORY;
             PRAGMA cache_size = -2000;

             CREATE TABLE IF NOT EXISTS search_cache (
                 query TEXT PRIMARY KEY,
                 payload TEXT,
                 fetched_at INTEGER
             );

             CREATE TABLE IF NOT EXISTS episode_cache (
                 slug TEXT,
                 ep INTEGER,
                 mode TEXT,
                 master TEXT,
                 variants TEXT,
                 sub TEXT,
                 sub_lang TEXT,
                 referer TEXT,
                 server TEXT,
                 subtitles TEXT DEFAULT '[]',
                 intro TEXT DEFAULT '{}',
                 outro TEXT DEFAULT '{}',
                 fetched_at INTEGER,
                 PRIMARY KEY (slug, ep, mode)
             );

             CREATE TABLE IF NOT EXISTS watch_history (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 slug TEXT,
                 title TEXT,
                 episode INTEGER,
                 mode TEXT DEFAULT 'sub',
                 progress INTEGER DEFAULT 0,
                 poster TEXT DEFAULT '',
                 type TEXT DEFAULT '',
                 played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
             );

             CREATE TABLE IF NOT EXISTS watch_progress (
                 slug TEXT,
                 episode INTEGER,
                 progress INTEGER DEFAULT 0,
                 duration INTEGER DEFAULT 0,
                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                 PRIMARY KEY (slug, episode)
             );

             CREATE TABLE IF NOT EXISTS bookmarks (
                 slug TEXT PRIMARY KEY,
                 title TEXT,
                 poster TEXT DEFAULT '',
                 type TEXT DEFAULT '',
                 sub TEXT DEFAULT '',
                 eps TEXT DEFAULT '',
                 score REAL DEFAULT 0.0,
                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
             );

             CREATE TABLE IF NOT EXISTS settings (
                 key TEXT PRIMARY KEY,
                 value TEXT
             );

             CREATE TABLE IF NOT EXISTS browse_cache (
                 key TEXT PRIMARY KEY,
                 payload TEXT,
                 fetched_at INTEGER
             );

             CREATE TABLE IF NOT EXISTS subtitle_cache (
                 key TEXT PRIMARY KEY,
                 payload TEXT,
                 fetched_at INTEGER
             );

             CREATE TABLE IF NOT EXISTS subtitle_offsets (
                 slug TEXT,
                 episode INTEGER,
                 offset_ms INTEGER,
                 updated_at INTEGER,
                 PRIMARY KEY (slug, episode)
             );

             CREATE TABLE IF NOT EXISTS lexicon_rules (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 target_term TEXT NOT NULL UNIQUE,
                 replacement TEXT NOT NULL,
                 category TEXT DEFAULT 'anime',
                 is_regex INTEGER DEFAULT 0,
                 created_at INTEGER
             );
             CREATE TABLE IF NOT EXISTS stream_providers (
                 id TEXT PRIMARY KEY,
                 name TEXT NOT NULL,
                 base_url TEXT NOT NULL,
                 is_enabled INTEGER NOT NULL DEFAULT 1,
                 priority INTEGER NOT NULL DEFAULT 0,
                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
             );

             INSERT OR IGNORE INTO stream_providers (id, name, base_url, is_enabled, priority) VALUES
             ('hianime', 'HiAnime (Global)', 'https://hianime.at', 1, 0),
             ('anichan', 'AniChan (Indonesia)', 'https://anichan.net', 1, 1),
             ('otakudesu', 'Otakudesu (Indonesia)', 'https://otakudesu.cloud', 1, 2);",
        )?;

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    // -------------------------------------------------------------
    // Search Cache
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // Episode Cache
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // Watch History
    // -------------------------------------------------------------
    pub fn add_history(&self, item: &WatchHistoryItem) -> Result<()> {
        let conn = self.conn.lock();
        // Remove prior entry for the same slug and mode so it moves to top
        conn.execute("DELETE FROM watch_history WHERE slug = ?1 AND mode = ?2", params![item.slug, item.mode])?;
        conn.execute(
            "INSERT INTO watch_history (slug, title, episode, mode, progress, poster, type, played_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, CURRENT_TIMESTAMP)",
            params![
                item.slug,
                item.title,
                item.episode,
                item.mode,
                item.progress,
                item.poster,
                item.anime_type
            ],
        )?;
        Ok(())
    }

    pub fn get_history(&self, limit: i32) -> Result<Vec<WatchHistoryItem>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, slug, title, episode, mode, progress, poster, type, played_at 
             FROM watch_history ORDER BY played_at DESC, id DESC LIMIT ?1",
        )?;
        let rows = stmt.query_map(params![limit], |row| {
            Ok(WatchHistoryItem {
                id: row.get(0)?,
                slug: row.get(1)?,
                title: row.get(2)?,
                episode: row.get(3)?,
                mode: row.get(4)?,
                progress: row.get(5)?,
                poster: row.get(6)?,
                anime_type: row.get(7)?,
                played_at: row.get(8)?,
            })
        })?;

        let mut items = Vec::new();
        for r in rows {
            items.push(r?);
        }
        Ok(items)
    }

    pub fn clear_history(&self) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute("DELETE FROM watch_history", [])?;
        Ok(())
    }
    pub fn delete_history(&self, slug: &str) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute("DELETE FROM watch_history WHERE slug = ?1", params![slug])?;
        Ok(())
    }


    // -------------------------------------------------------------
    // Watch Progress
    // -------------------------------------------------------------
    pub fn save_progress(
        &self,
        slug: &str,
        episode: i32,
        mode: Option<&str>,
        progress: i32,
        duration: i32,
        title: Option<&str>,
        poster: Option<&str>,
        anime_type: Option<&str>,
    ) -> Result<()> {
        let active_mode = mode.unwrap_or("sub");
        let progress_slug = if active_mode == "sub" { slug.to_string() } else { format!("{}:{}", slug, active_mode) };
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO watch_progress (slug, episode, progress, duration, updated_at)
             VALUES (?1, ?2, ?3, ?4, CURRENT_TIMESTAMP)",
            params![progress_slug, episode, progress, duration],
        )?;
        let rows_updated = conn.execute(
            "UPDATE watch_history SET progress = ?1, episode = ?2, played_at = CURRENT_TIMESTAMP WHERE slug = ?3 AND mode = ?4",
            params![progress, episode, slug, active_mode],
        )?;
        if rows_updated == 0 {
            let bookmark: Option<(String, String, String)> = conn
                .query_row(
                    "SELECT title, poster, type FROM bookmarks WHERE slug = ?1",
                    params![slug],
                    |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
                )
                .optional()?;

            let (final_title, final_poster, final_type) = if let Some((b_title, b_poster, b_type)) = bookmark {
                (
                    title.unwrap_or(&b_title).to_string(),
                    poster.unwrap_or(&b_poster).to_string(),
                    anime_type.unwrap_or(&b_type).to_string(),
                )
            } else {
                (
                    title.unwrap_or("").to_string(),
                    poster.unwrap_or("").to_string(),
                    anime_type.unwrap_or("ANIME").to_string(),
                )
            };

            if !final_title.is_empty() || !final_poster.is_empty() {
                let _ = conn.execute(
                    "INSERT INTO watch_history (slug, title, episode, mode, progress, poster, type, played_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, CURRENT_TIMESTAMP)",
                    params![slug, final_title, episode, active_mode, progress, final_poster, final_type],
                );
            }
        }
        Ok(())
    }

    pub fn get_progress(&self, slug: &str, episode: i32, mode: Option<&str>) -> Result<Option<WatchProgressItem>> {
        let active_mode = mode.unwrap_or("sub");
        let progress_slug = if active_mode == "sub" { slug.to_string() } else { format!("{}:{}", slug, active_mode) };
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT slug, episode, progress, duration, updated_at 
             FROM watch_progress WHERE slug = ?1 AND episode = ?2",
        )?;
        let mut rows = stmt.query(params![progress_slug, episode])?;

        if let Some(row) = rows.next()? {
            Ok(Some(WatchProgressItem {
                slug: row.get(0)?,
                episode: row.get(1)?,
                progress: row.get(2)?,
                duration: row.get(3)?,
                updated_at: row.get(4)?,
            }))
        } else {
            Ok(None)
        }
    }

    // -------------------------------------------------------------
    // Bookmarks
    // -------------------------------------------------------------
    pub fn add_bookmark(&self, item: &BookmarkItem) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO bookmarks (slug, title, poster, type, sub, eps, score, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, CURRENT_TIMESTAMP)",
            params![
                item.slug,
                item.title,
                item.poster,
                item.anime_type,
                item.sub,
                item.eps,
                item.score
            ],
        )?;
        Ok(())
    }

    pub fn remove_bookmark(&self, slug: &str) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute("DELETE FROM bookmarks WHERE slug = ?1", params![slug])?;
        Ok(())
    }

    pub fn get_bookmarks(&self) -> Result<Vec<BookmarkItem>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT slug, title, poster, type, sub, eps, score, created_at 
             FROM bookmarks ORDER BY created_at DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(BookmarkItem {
                slug: row.get(0)?,
                title: row.get(1)?,
                poster: row.get(2)?,
                anime_type: row.get(3)?,
                sub: row.get(4)?,
                eps: row.get(5)?,
                score: row.get(6)?,
                created_at: row.get(7)?,
            })
        })?;

        let mut items = Vec::new();
        for r in rows {
            items.push(r?);
        }
        Ok(items)
    }

    pub fn is_bookmarked(&self, slug: &str) -> Result<bool> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT 1 FROM bookmarks WHERE slug = ?1")?;
        let exists = stmt.exists(params![slug])?;
        Ok(exists)
    }

    // -------------------------------------------------------------
    // Subtitle Cache
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------
    pub fn get_setting(&self, key: &str) -> Result<Option<String>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT value FROM settings WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;
        if let Some(row) = rows.next()? {
            let val: String = row.get(0)?;
            return Ok(Some(val));
        }
        Ok(None)
    }

    pub fn set_setting(&self, key: &str, value: &str) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO settings (key, value) VALUES (?1, ?2)",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn is_safe_mode(&self) -> bool {
        match self.get_setting("safe_mode") {
            Ok(Some(v)) => v != "false" && v != "0",
            _ => true,
        }
    }

    // -------------------------------------------------------------
    // Browse Cache
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // Cache Clearing & Stats
    // -------------------------------------------------------------
    pub fn clear_cache(&self, target: &str) -> Result<usize> {
        let conn = self.conn.lock();
        let mut count = 0;
        match target {
            "all" => {
                count += conn.execute("DELETE FROM search_cache", [])?;
                count += conn.execute("DELETE FROM browse_cache", [])?;
                count += conn.execute("DELETE FROM subtitle_cache", [])?;
                count += conn.execute("DELETE FROM episode_cache", [])?;
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
            "settings": count_table("settings"),
        }))
    }
    // -------------------------------------------------------------
    // Subtitle Offsets & Lexicon Rules
    // -------------------------------------------------------------
    pub fn get_subtitle_offset(&self, slug: &str, episode: i32) -> Result<Option<i32>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT offset_ms FROM subtitle_offsets WHERE slug = ?1 AND episode = ?2")?;
        let mut rows = stmt.query(params![slug, episode])?;
        if let Some(row) = rows.next()? {
            let offset: i32 = row.get(0)?;
            return Ok(Some(offset));
        }
        Ok(None)
    }

    pub fn set_subtitle_offset(&self, slug: &str, episode: i32, offset_ms: i32) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO subtitle_offsets (slug, episode, offset_ms, updated_at) VALUES (?1, ?2, ?3, ?4)",
            params![slug, episode, offset_ms, now_epoch()],
        )?;
        Ok(())
    }

    pub fn get_lexicon_rules(&self) -> Result<Vec<LexiconRule>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare("SELECT id, target_term, replacement, category, is_regex, created_at FROM lexicon_rules ORDER BY id ASC")?;
        let rows = stmt.query_map([], |row| {
            let is_regex_int: i32 = row.get(4)?;
            Ok(LexiconRule {
                id: row.get(0)?,
                target_term: row.get(1)?,
                replacement: row.get(2)?,
                category: row.get(3)?,
                is_regex: is_regex_int != 0,
                created_at: row.get(5)?,
            })
        })?;
        let mut result = Vec::new();
        for r in rows {
            result.push(r?);
        }
        Ok(result)
    }

    pub fn add_lexicon_rule(&self, target_term: &str, replacement: &str, category: &str, is_regex: bool) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO lexicon_rules (target_term, replacement, category, is_regex, created_at) VALUES (?1, ?2, ?3, ?4, ?5)",
            params![target_term, replacement, category, if is_regex { 1 } else { 0 }, now_epoch()],
        )?;
        Ok(())
    }

    pub fn delete_lexicon_rule(&self, id: i64) -> Result<bool> {
        let conn = self.conn.lock();
        let rows = conn.execute("DELETE FROM lexicon_rules WHERE id = ?1", params![id])?;
        Ok(rows > 0)
    }
}
