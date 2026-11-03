use super::models::LexiconRule;
use super::{now_epoch, Database};
use rusqlite::{params, Result};

impl Database {
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
