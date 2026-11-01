use crate::db::Database;
use axum::{
    extract::State,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Debug, Deserialize)]
pub struct TerminalExecRequest {
    pub command: Option<String>,
    pub cmd: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct TerminalExecResponse {
    pub success: bool,
    pub command: String,
    pub output: String,
}

pub fn execute_command(raw_input: &str, db: &Database) -> String {
    let input = raw_input.trim();
    if input.is_empty() {
        return "miru: command line empty. Type 'help' for available commands.".to_string();
    }

    let parts: Vec<&str> = input.split_whitespace().collect();
    let cmd = parts[0].to_lowercase();

    match cmd.as_str() {
        "ping" => format!(
            "PONG - Miru Daemon v{} OK (Axum/Tokio Native Daemon)",
            env!("CARGO_PKG_VERSION")
        ),
        "help" => [
            "Miru Diagnostic Terminal Commands:",
            "  ping                   Check daemon health and responsiveness",
            "  stats                  Display daemon and database status",
            "  db stats               Display SQLite table record counts",
            "  cache clear [target]   Clear caches (all | search | browse | sub | ep)",
            "  config get <key>       Read setting value from SQLite",
            "  config set <key> <val> Write setting value to SQLite",
            "  help                   Show this help menu",
        ]
        .join("\n"),
        "stats" => match db.get_table_counts() {
            Ok(counts) => format!(
                "Miru Daemon Stats:\n  Version: v{}\n  Engine: Axum + Rusqlite\n  Tables:\n{}",
                env!("CARGO_PKG_VERSION"),
                serde_json::to_string_pretty(&counts).unwrap_or_default()
            ),
            Err(e) => format!("Error getting daemon stats: {}", e),
        },
        "db" => {
            if parts.get(1).map(|s| s.to_lowercase()) == Some("stats".to_string()) {
                match db.get_table_counts() {
                    Ok(counts) => {
                        format!("SQLite Database Record Counts:\n{}", serde_json::to_string_pretty(&counts).unwrap_or_default())
                    }
                    Err(e) => format!("Error reading table counts: {}", e),
                }
            } else {
                "Usage: db stats".to_string()
            }
        }
        "cache" => {
            if parts.get(1).map(|s| s.to_lowercase()) == Some("clear".to_string()) {
                let target = parts.get(2).map(|s| s.to_lowercase()).unwrap_or_else(|| "all".to_string());
                match db.clear_cache(&target) {
                    Ok(cleared) => format!("Success: Cleared {} entries from cache [{}].", cleared, target),
                    Err(e) => format!("Error clearing cache: {}", e),
                }
            } else {
                "Usage: cache clear [all|search|browse|sub|ep]".to_string()
            }
        }
        "config" => {
            let sub = parts.get(1).map(|s| s.to_lowercase());
            match sub.as_deref() {
                Some("get") => {
                    if let Some(key) = parts.get(2) {
                        match db.get_setting(key) {
                            Ok(Some(v)) => format!("{} = {}", key, v),
                            Ok(None) => format!("{} is not set.", key),
                            Err(e) => format!("Error reading config: {}", e),
                        }
                    } else {
                        "Usage: config get <key>".to_string()
                    }
                }
                Some("set") => {
                    if parts.len() >= 4 {
                        let key = parts[2];
                        let val = parts[3..].join(" ");
                        match db.set_setting(key, &val) {
                            Ok(_) => format!("Success: {} set to '{}'.", key, val),
                            Err(e) => format!("Error setting config: {}", e),
                        }
                    } else {
                        "Usage: config set <key> <val>".to_string()
                    }
                }
                _ => "Usage: config get <key> | config set <key> <val>".to_string(),
            }
        }
        _ => format!(
            "Command not recognized: '{}'. Type 'help' for available commands.",
            cmd
        ),
    }
}

pub async fn terminal_exec_handler(
    State(state): State<crate::AppState>,
    Json(payload): Json<TerminalExecRequest>,
) -> Response {
    let raw_cmd = payload.command.or(payload.cmd).unwrap_or_default();
    let output = execute_command(&raw_cmd, &state.db);
    Json(json!({
        "success": true,
        "command": raw_cmd,
        "output": output
    }))
    .into_response()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_terminal_ping_and_help() {
        let db = Database::init(":memory:").expect("in-memory db");
        let out_ping = execute_command("ping", &db);
        assert!(out_ping.contains("PONG"));

        let out_help = execute_command("help", &db);
        assert!(out_help.contains("ping"));
        assert!(out_help.contains("cache clear"));
    }

    #[test]
    fn test_terminal_config_and_stats() {
        let db = Database::init(":memory:").expect("in-memory db");
        let set_res = execute_command("config set test_key 12345", &db);
        assert!(set_res.contains("Success"));

        let get_res = execute_command("config get test_key", &db);
        assert_eq!(get_res, "test_key = 12345");

        let db_stats = execute_command("db stats", &db);
        assert!(db_stats.contains("watch_history"));

        let cache_clear = execute_command("cache clear all", &db);
        assert!(cache_clear.contains("Cleared 0"));
    }
}

