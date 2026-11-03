use super::models::BookmarkItem;
use super::Database;
use rusqlite::{params, Result};

impl Database {
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
}
