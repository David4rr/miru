use super::models::{WatchHistoryItem, WatchProgressItem};
use super::Database;
use rusqlite::{params, OptionalExtension, Result};

impl Database {
    pub fn add_history(&self, item: &WatchHistoryItem) -> Result<()> {
        let conn = self.conn.lock();
        conn.execute(
            "DELETE FROM watch_history WHERE slug = ?1 AND mode = ?2",
            params![item.slug, item.mode],
        )?;
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
        let progress_slug = if active_mode == "sub" {
            slug.to_string()
        } else {
            format!("{}:{}", slug, active_mode)
        };
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
        let progress_slug = if active_mode == "sub" {
            slug.to_string()
        } else {
            format!("{}:{}", slug, active_mode)
        };
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
}
