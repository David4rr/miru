pub mod bookmarks;
pub mod cache;
pub mod history;
pub mod models;
pub mod settings;

pub use models::*;

use parking_lot::Mutex;
use rusqlite::{Connection, Result};
use std::path::Path;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

pub(crate) fn now_epoch() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

#[derive(Clone)]
pub struct Database {
    pub(crate) conn: Arc<Mutex<Connection>>,
}

impl Database {
    pub fn init<P: AsRef<Path>>(path: P) -> Result<Self> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA temp_store = MEMORY;
             PRAGMA cache_size = -64000;

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

             CREATE TABLE IF NOT EXISTS anilist_cache (
                 cache_key TEXT PRIMARY KEY,
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

             CREATE INDEX IF NOT EXISTS idx_history_played ON watch_history(played_at DESC);
             CREATE INDEX IF NOT EXISTS idx_history_slug ON watch_history(slug);
             CREATE INDEX IF NOT EXISTS idx_history_updated ON watch_history(played_at DESC);
             CREATE INDEX IF NOT EXISTS idx_bookmarks_updated ON bookmarks(created_at DESC);
             CREATE INDEX IF NOT EXISTS idx_search_query ON search_cache(query);
             CREATE INDEX IF NOT EXISTS idx_anilist_key ON anilist_cache(cache_key);
             CREATE INDEX IF NOT EXISTS idx_browse_key ON browse_cache(key);
             CREATE INDEX IF NOT EXISTS idx_episode_slug ON episode_cache(slug);

             INSERT OR IGNORE INTO stream_providers (id, name, base_url, is_enabled, priority) VALUES
             ('hianime', 'HiAnime (Global)', 'https://hianime.at', 1, 0),
             ('anichan', 'AniChan (Indonesia)', 'https://anichan.net', 1, 1),
             ('otakudesu', 'Otakudesu (Indonesia)', 'https://otakudesu.cloud', 1, 2);",
        )?;

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }
}
