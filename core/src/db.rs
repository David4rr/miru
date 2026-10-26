use parking_lot::Mutex;
use rusqlite::{params, Connection, Result};
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
             );",
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
        // Remove prior entry for the same slug so it moves to top
        conn.execute("DELETE FROM watch_history WHERE slug = ?1", params![item.slug])?;
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
             FROM watch_history ORDER BY played_at DESC LIMIT ?1",
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

    // -------------------------------------------------------------
    // Watch Progress
    // -------------------------------------------------------------
    pub fn save_progress(&self, slug: &str, episode: i32, progress: i32, duration: i32) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO watch_progress (slug, episode, progress, duration, updated_at)
             VALUES (?1, ?2, ?3, ?4, CURRENT_TIMESTAMP)",
            params![slug, episode, progress, duration],
        )?;
        let _ = conn.execute(
            "UPDATE watch_history SET progress = ?1 WHERE slug = ?2 AND episode = ?3",
            params![progress, slug, episode],
        );
        Ok(())
    }

    pub fn get_progress(&self, slug: &str, episode: i32) -> Result<Option<WatchProgressItem>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT slug, episode, progress, duration, updated_at 
             FROM watch_progress WHERE slug = ?1 AND episode = ?2",
        )?;
        let mut rows = stmt.query(params![slug, episode])?;

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
}
