use axum::body::Body;
use axum::http::{Request, StatusCode};
use miru_core::api::search::{generate_fallback_candidates, normalize_query, search_anime};
use miru_core::db::Database;
use miru_core::{create_router, AppState};
use tower::ServiceExt;

#[test]
fn test_query_normalization() {
    assert_eq!(normalize_query("  saiki  "), "saiki");
    assert_eq!(normalize_query("[1080p] the disastrous saiki"), "the disastrous saiki");
    assert_eq!(normalize_query("the disastrous saiki (2016)"), "the disastrous saiki");
    assert_eq!(normalize_query("saiki sub indo"), "saiki");
    assert_eq!(normalize_query("saiki season 1"), "saiki");
    assert_eq!(normalize_query("saiki s1"), "saiki");
    assert_eq!(normalize_query("attack on titan season 4 part 2"), "attack on titan");
    assert_eq!(normalize_query("kaguya-sama: love is war!"), "kaguya-sama love is war");
    assert_eq!(normalize_query("the disastrous saiki k."), "the disastrous saiki k");
}

#[test]
fn test_fallback_candidates_generation() {
    let fallbacks = generate_fallback_candidates("the disastrous saiki kusuo");
    assert!(fallbacks.contains(&"disastrous saiki".to_string()));
    assert!(fallbacks.contains(&"saiki kusuo".to_string()));

    let fallbacks_of = generate_fallback_candidates("disastrous of saiki");
    assert!(fallbacks_of.contains(&"disastrous saiki".to_string()));

    let empty = generate_fallback_candidates("the");
    assert!(empty.is_empty());
}

#[tokio::test]
async fn test_search_flow_and_api_endpoints() {
    let temp_db_path = format!("{}/test_search_{}.db", std::env::temp_dir().display(), std::process::id());
    let _ = std::fs::remove_file(&temp_db_path);
    let db = Database::init(&temp_db_path).expect("Failed to init temp db");

    // 1. Single keyword search directly
    let results_saiki = search_anime("saiki", 1, 10, &db).await.expect("Search saiki failed");
    assert!(!results_saiki.is_empty(), "Search for 'saiki' must return results");
    let found_saiki = results_saiki.iter().any(|c| {
        c.title.to_lowercase().contains("saiki")
            || c.title_english.as_deref().unwrap_or("").to_lowercase().contains("saiki")
    });
    assert!(found_saiki, "Should find Saiki Kusuo in search results");

    // 2. English title search ("the disastrous saiki")
    let results_eng = search_anime("the disastrous saiki", 1, 10, &db)
        .await
        .expect("Search 'the disastrous saiki' failed");
    assert!(!results_eng.is_empty(), "Search for 'the disastrous saiki' must return results");
    let found_disastrous = results_eng.iter().any(|c| {
        c.title_english.as_deref().unwrap_or("").to_lowercase().contains("saiki")
            || c.title.to_lowercase().contains("saiki")
    });
    assert!(found_disastrous, "Should find Saiki in results for 'the disastrous saiki'");

    // 3. Test Axum router /api/search?q=saiki
    let app = create_router(AppState { db });
    let req = Request::builder()
        .uri("/api/search?q=saiki")
        .body(Body::empty())
        .unwrap();

    let resp = app.clone().oneshot(req).await.unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body_bytes = axum::body::to_bytes(resp.into_body(), usize::MAX).await.unwrap();
    let json: serde_json::Value = serde_json::from_slice(&body_bytes).unwrap();
    assert_eq!(json["success"], true);
    let arr = json["data"].as_array().expect("data must be array");
    assert!(!arr.is_empty(), "/api/search?q=saiki must return non-empty array");

    // 4. Test Axum router /api/anime/search?q=the+disastrous+saiki alias
    let req_alias = Request::builder()
        .uri("/api/anime/search?q=the+disastrous+saiki")
        .body(Body::empty())
        .unwrap();

    let resp_alias = app.oneshot(req_alias).await.unwrap();
    assert_eq!(resp_alias.status(), StatusCode::OK);
    let body_bytes_alias = axum::body::to_bytes(resp_alias.into_body(), usize::MAX).await.unwrap();
    let json_alias: serde_json::Value = serde_json::from_slice(&body_bytes_alias).unwrap();
    assert_eq!(json_alias["success"], true);
    let arr_alias = json_alias["data"].as_array().expect("data must be array");
    assert!(!arr_alias.is_empty(), "/api/anime/search must return non-empty array");

    let _ = std::fs::remove_file(&temp_db_path);
}
