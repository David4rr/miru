use axum::body::Body;
use axum::http::{Request, StatusCode};
use miru_core::db::Database;
use miru_core::{create_router, AppState};
use tower::ServiceExt;

#[tokio::test]
async fn test_home_endpoint_live_and_caching() {
    let temp_db_path = format!(
        "{}/test_home_live_{}.db",
        std::env::temp_dir().display(),
        std::process::id()
    );
    let _ = std::fs::remove_file(&temp_db_path);
    let db = Database::init(&temp_db_path).expect("Failed to init temp db");

    let app = create_router(AppState { db: db.clone() });

    // 1. Initial live fetch
    let req = Request::builder()
        .uri("/api/home")
        .body(Body::empty())
        .unwrap();

    let resp = app.clone().oneshot(req).await.unwrap();
    assert_eq!(resp.status(), StatusCode::OK);

    let body_bytes = axum::body::to_bytes(resp.into_body(), usize::MAX).await.unwrap();
    let json: serde_json::Value = serde_json::from_slice(&body_bytes).unwrap();
    assert_eq!(json["success"], true);

    let data = &json["data"];
    assert!(!data["hero"].as_array().unwrap().is_empty(), "Hero must not be empty");
    assert!(!data["top_airing"].as_array().unwrap().is_empty(), "Top airing must not be empty");
    assert!(!data["seasonal"].as_array().unwrap().is_empty(), "Seasonal must not be empty");
    assert!(!data["top_rated"].as_array().unwrap().is_empty(), "Top rated must not be empty");
    assert!(!data["upcoming"].as_array().unwrap().is_empty(), "Upcoming must not be empty");
    assert!(!data["all_time_popular"].as_array().unwrap().is_empty(), "All time popular must not be empty");

    // 2. Second fetch hits cache immediately
    let t0 = std::time::Instant::now();
    let req_cached = Request::builder()
        .uri("/api/home")
        .body(Body::empty())
        .unwrap();
    let resp_cached = app.clone().oneshot(req_cached).await.unwrap();
    let elapsed = t0.elapsed();
    assert_eq!(resp_cached.status(), StatusCode::OK);
    assert!(
        elapsed < std::time::Duration::from_millis(150),
        "Cached response must be fast (<150ms), got {:?}",
        elapsed
    );

    let _ = std::fs::remove_file(&temp_db_path);
}

#[tokio::test]
async fn test_home_stale_cache_resilience() {
    let temp_db_path = format!(
        "{}/test_home_stale_{}.db",
        std::env::temp_dir().display(),
        std::process::id()
    );
    let _ = std::fs::remove_file(&temp_db_path);
    let db = Database::init(&temp_db_path).expect("Failed to init temp db");

    // Pre-populate stale cache with old timestamp (3 days ago)
    let fake_catalog = serde_json::json!({
        "hero": [],
        "continue_watching": [],
        "airing_today": [],
        "top_airing": [],
        "seasonal": [],
        "top_rated": [],
        "upcoming": [],
        "all_time_popular": []
    });
    db.set_anilist_cache("home_catalog_v2_all", &fake_catalog).unwrap();

    let app = create_router(AppState { db: db.clone() });
    let req = Request::builder()
        .uri("/api/home")
        .body(Body::empty())
        .unwrap();

    let resp = app.oneshot(req).await.unwrap();
    assert_eq!(resp.status(), StatusCode::OK);

    let _ = std::fs::remove_file(&temp_db_path);
}
