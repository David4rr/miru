pub mod api;
pub mod db;
pub mod net;
pub mod proxy;

use axum::{
    extract::{Path, Query, State},
    http::{header, StatusCode},
    response::{IntoResponse, Response},
    routing::{delete, get, post},
    Json, Router,
};
use db::{BookmarkItem, Database, WatchHistoryItem};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Deserialize;
use serde_json::json;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::LazyLock;
use tokio::sync::Notify;
use tower_http::cors::CorsLayer;

static IS_RUNNING: AtomicBool = AtomicBool::new(false);
static SHUTDOWN_NOTIFY: LazyLock<Notify> = LazyLock::new(Notify::new);

#[derive(Clone)]
pub struct AppState {
    pub db: Database,
}

/// Core Axum router definition with all Tatap API routes
pub fn create_router(state: AppState) -> Router {
    Router::new()
        .route("/api/ping", get(ping_handler))
        .route("/api/version", get(version_handler))
        .route("/api/home", get(home_handler))
        .route("/api/schedule", get(schedule_handler))
        .route("/api/search", get(search_handler))
        .route("/api/stream/resolve", get(resolve_stream_handler))
        .route("/api/player/video", get(proxy::proxy_video_handler))
        .route("/api/player/sub", get(sub_handler))
        .route("/api/player/skip_times", get(skip_times_handler))
        .route("/api/history", get(get_history_handler).post(add_history_handler).delete(clear_history_handler))
        .route("/api/history/:slug", delete(delete_history_item_handler))
        .route("/api/bookmarks", get(get_bookmarks_handler).post(add_bookmark_handler))
        .route("/api/bookmarks/:slug", delete(remove_bookmark_handler))
        .layer(CorsLayer::permissive())
        .with_state(state)
}

// -------------------------------------------------------------
// Route Handlers
// -------------------------------------------------------------

async fn ping_handler() -> Json<serde_json::Value> {
    Json(json!({ "status": "running", "engine": "rust-axum" }))
}

async fn version_handler() -> Json<serde_json::Value> {
    Json(json!({ "version": env!("CARGO_PKG_VERSION") }))
}

async fn home_handler(State(state): State<AppState>) -> Response {
    match api::anilist::fetch_home_catalog(&state.db).await {
        Ok(catalog) => Json(json!({ "success": true, "data": catalog })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}
async fn schedule_handler(State(state): State<AppState>) -> Response {
    match api::anilist::fetch_schedule(&state.db).await {
        Ok(schedule) => Json(json!({ "success": true, "data": schedule })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}


#[derive(Debug, Deserialize)]
struct SearchParams {
    q: String,
    page: Option<i32>,
    per_page: Option<i32>,
}

async fn search_handler(
    State(state): State<AppState>,
    Query(params): Query<SearchParams>,
) -> Response {
    let page = params.page.unwrap_or(1);
    let per_page = params.per_page.unwrap_or(20);

    match api::anilist::search_anime(&params.q, page, per_page, &state.db).await {
        Ok(cards) => Json(json!({ "success": true, "data": cards })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct ResolveParams {
    slug: String,
    ep: i32,
    mode: Option<String>,
    mal_id: Option<i64>,
}

async fn resolve_stream_handler(
    State(state): State<AppState>,
    Query(params): Query<ResolveParams>,
) -> Response {
    let mode = params.mode.unwrap_or_else(|| "sub".to_string());
    match api::stream::resolve_stream(&params.slug, params.ep, &mode, params.mal_id, &state.db).await {
        Ok(stream) => Json(json!({ "success": true, "data": stream })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct SubParams {
    url: String,
    mode: Option<String>,
}

async fn sub_handler(
    State(state): State<AppState>,
    Query(params): Query<SubParams>,
) -> Response {
    let mode = params.mode.unwrap_or_else(|| "gtx+".to_string());
    let vtt_text = match api::sub::get_or_translate_vtt(&params.url, &mode, &state.db).await {
        Ok(t) => t,
        Err(_) => "WEBVTT\n\n".to_string(),
    };
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "text/vtt; charset=utf-8"),
            (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*"),
        ],
        vtt_text,
    ).into_response()
}

#[derive(Debug, Deserialize)]
struct SkipTimesParams {
    mal_id: i64,
    ep: i32,
    length: Option<f64>,
}

async fn skip_times_handler(Query(params): Query<SkipTimesParams>) -> Response {
    let length = params.length.unwrap_or(1440.0);
    match api::sub::fetch_aniskip_intervals(params.mal_id, params.ep, length).await {
        Ok(res) => Json(json!({ "success": true, "data": res })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

// -------------------------------------------------------------
// History & Progress Handlers
// -------------------------------------------------------------

async fn get_history_handler(State(state): State<AppState>) -> Response {
    match state.db.get_history(50) {
        Ok(items) => Json(json!({ "success": true, "data": items })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn add_history_handler(
    State(state): State<AppState>,
    Json(payload): Json<WatchHistoryItem>,
) -> Response {
    match state.db.add_history(&payload) {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}
async fn delete_history_item_handler(
    State(state): State<AppState>,
    Path(slug): Path<String>,
) -> Response {
    match state.db.delete_history(&slug) {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn clear_history_handler(State(state): State<AppState>) -> Response {
    match state.db.clear_history() {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}


#[derive(Debug, Deserialize)]
struct ProgressPayload {
    slug: String,
    episode: i32,
    progress: i32,
    duration: Option<i32>,
}

async fn save_progress_handler(
    State(state): State<AppState>,
    Json(payload): Json<ProgressPayload>,
) -> Response {
    let duration = payload.duration.unwrap_or(0);
    match state.db.save_progress(&payload.slug, payload.episode, payload.progress, duration) {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct GetProgressParams {
    slug: String,
    ep: i32,
}

async fn get_progress_handler(
    State(state): State<AppState>,
    Query(params): Query<GetProgressParams>,
) -> Response {
    match state.db.get_progress(&params.slug, params.ep) {
        Ok(item) => Json(json!({ "success": true, "data": item })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

// -------------------------------------------------------------
// Bookmarks Handlers
// -------------------------------------------------------------

async fn get_bookmarks_handler(State(state): State<AppState>) -> Response {
    match state.db.get_bookmarks() {
        Ok(items) => Json(json!({ "success": true, "data": items })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn add_bookmark_handler(
    State(state): State<AppState>,
    Json(payload): Json<BookmarkItem>,
) -> Response {
    match state.db.add_bookmark(&payload) {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn remove_bookmark_handler(
    State(state): State<AppState>,
    Path(slug): Path<String>,
) -> Response {
    match state.db.remove_bookmark(&slug) {
        Ok(_) => Json(json!({ "success": true })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

// -------------------------------------------------------------
// Server Lifecycle & JNI Exports
// -------------------------------------------------------------

pub fn start_server(data_dir: &str, port: u16) -> Result<(), anyhow::Error> {
    if IS_RUNNING.load(Ordering::SeqCst) {
        return Ok(());
    }

    let db_path = if data_dir.is_empty() {
        "anime.db".to_string()
    } else {
        format!("{}/anime.db", data_dir)
    };

    let db = Database::init(&db_path)?;
    let state = AppState { db };

    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .thread_name("tatap-core-worker")
        .build()?;

    std::thread::spawn(move || {
        rt.block_on(async move {
            let app = create_router(state);
            let addr = SocketAddr::from(([127, 0, 0, 1], port));

            match tokio::net::TcpListener::bind(addr).await {
                Ok(listener) => {
                    IS_RUNNING.store(true, Ordering::SeqCst);
                    tracing::info!("Tatap Rust Core listening on http://{}", addr);

                    let _ = axum::serve(listener, app)
                        .with_graceful_shutdown(async move {
                            SHUTDOWN_NOTIFY.notified().await;
                        })
                        .await;

                    IS_RUNNING.store(false, Ordering::SeqCst);
                }
                Err(err) => {
                    tracing::error!("Failed to bind server to {}: {}", addr, err);
                    IS_RUNNING.store(false, Ordering::SeqCst);
                }
            }
        });
    });

    Ok(())
}

#[no_mangle]
pub extern "system" fn Java_id_tatap_app_NativeBridge_startDaemon(
    mut env: JNIEnv,
    _class: JClass,
    data_dir: JString,
    port: jint,
) -> jboolean {
    let dir_str: String = match env.get_string(&data_dir) {
        Ok(s) => s.into(),
        Err(_) => "".to_string(),
    };

    let port_u16 = if port <= 0 || port > 65535 { 8767 } else { port as u16 };

    match start_server(&dir_str, port_u16) {
        Ok(_) => JNI_TRUE,
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_id_tatap_app_NativeBridge_stopDaemon(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if IS_RUNNING.load(Ordering::SeqCst) {
        SHUTDOWN_NOTIFY.notify_waiters();
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_id_tatap_app_NativeBridge_isDaemonRunning(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if IS_RUNNING.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
