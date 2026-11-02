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

/// Core Axum router definition with all Miru API routes
pub fn create_router(state: AppState) -> Router {
    Router::new()
        .route("/api/ping", get(ping_handler))
        .route("/api/version", get(version_handler))
        .route("/api/home", get(home_handler))
        .route("/api/schedule", get(schedule_handler))
        .route("/api/search", get(search_handler))
        .route("/api/anime/search", get(search_handler))
        .route("/api/anime/editions", get(editions_handler))
        .route("/api/stream/resolve", get(resolve_stream_handler))
        .route("/api/stream/providers", get(stream_providers_get_handler))
        .route("/api/stream/providers/default", post(stream_providers_default_post_handler))
        .route("/api/stream/servers", get(stream_servers_handler))
        .route("/api/stream/variants", get(stream_variants_handler))
        .route("/api/player/video", get(proxy::proxy_video_handler))
        .route("/api/player/sub", get(sub_handler))
        .route("/api/subtitles/sources/animetosho", get(api::sub::animetosho::animetosho_sources_handler))
        .route("/api/subtitles/sources/opensubtitles", get(api::sub::opensubtitles::opensubtitles_sources_handler))
        .route("/api/subtitles/ai/fansub", post(api::sub::ai_byok::ai_fansub_handler))
        .route("/api/subtitles/ai/providers", get(api::sub::ai_byok::ai_providers_get_handler).post(api::sub::ai_byok::ai_providers_post_handler))
        .route("/api/subtitles/lexicon", get(api::sub::lexicon::lexicon_get_handler).post(api::sub::lexicon::lexicon_post_handler).delete(api::sub::lexicon::lexicon_delete_handler))
        .route("/api/subtitles/offset", get(api::sub::offset_get_handler).post(api::sub::offset_post_handler))
        .route("/api/player/skip_times", get(skip_times_handler))
        .route("/api/translate", get(translate_handler))
        .route("/api/history", get(get_history_handler).post(add_history_handler).delete(clear_history_handler))
        .route("/api/history/:slug", delete(delete_history_item_handler))
        .route("/api/progress", get(get_progress_handler).post(save_progress_handler))
        .route("/api/bookmarks", get(get_bookmarks_handler).post(add_bookmark_handler))
        .route("/api/bookmarks/:slug", delete(remove_bookmark_handler))
        .route("/api/catalog/filters", get(api::catalog::catalog_filters_handler))
        .route("/api/catalog/browse", get(api::catalog::catalog_browse_handler))
        .route("/api/settings/safemode", get(get_safemode_handler).post(set_safemode_handler))
        .route("/api/settings/safe_mode", get(get_safemode_handler).post(set_safemode_handler))
        .route("/api/settings/:key", get(get_setting_handler).post(set_setting_handler))
        .route("/api/terminal/exec", post(api::terminal::terminal_exec_handler))
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
#[derive(Debug, Deserialize)]
struct SafeModePayload {
    safe_mode: Option<bool>,
    enabled: Option<bool>,
    value: Option<bool>,
}

async fn get_safemode_handler(State(state): State<AppState>) -> Response {
    let is_safe = state.db.is_safe_mode();
    Json(json!({ "success": true, "safe_mode": is_safe, "enabled": is_safe })).into_response()
}

async fn set_safemode_handler(
    State(state): State<AppState>,
    Json(payload): Json<SafeModePayload>,
) -> Response {
    let is_safe = payload.safe_mode
        .or(payload.enabled)
        .or(payload.value)
        .unwrap_or(true);
    let val_str = if is_safe { "true" } else { "false" };
    match state.db.set_setting("safe_mode", val_str) {
        Ok(_) => Json(json!({ "success": true, "safe_mode": is_safe, "enabled": is_safe })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct SettingPayload {
    value: String,
}

async fn get_setting_handler(
    State(state): State<AppState>,
    Path(key): Path<String>,
) -> Response {
    match state.db.get_setting(&key) {
        Ok(val) => Json(json!({ "success": true, "key": key, "value": val })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn set_setting_handler(
    State(state): State<AppState>,
    Path(key): Path<String>,
    Json(payload): Json<SettingPayload>,
) -> Response {
    match state.db.set_setting(&key, &payload.value) {
        Ok(_) => Json(json!({ "success": true, "key": key, "value": payload.value })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
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

    match api::search::search_anime(&params.q, page, per_page, &state.db).await {
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
    provider: Option<String>,
    server: Option<String>,
    mal_id: Option<i64>,
    title: Option<String>,
}

async fn resolve_stream_handler(
    State(state): State<AppState>,
    Query(params): Query<ResolveParams>,
) -> Response {
    if params.ep < 1 {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid episode number (must be >= 1)" })),
        ).into_response();
    }
    let mode = params.mode.unwrap_or_else(|| "sub".to_string());
    match api::stream::resolve_stream(
        &params.slug,
        params.ep,
        &mode,
        params.provider.as_deref(),
        params.server.as_deref(),
        params.mal_id,
        params.title.as_deref(),
        &state.db,
    ).await {
        Ok(stream) => Json(json!({ "success": true, "data": stream })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

async fn stream_providers_get_handler(State(state): State<AppState>) -> Response {
    let default_id = api::providers::get_default_provider_id(&state.db);
    let providers = api::providers::get_all_providers(&default_id);
    Json(json!({ "success": true, "data": providers })).into_response()
}

#[derive(Debug, Deserialize)]
struct SetDefaultProviderPayload {
    provider: Option<String>,
    id: Option<String>,
}

async fn stream_providers_default_post_handler(
    State(state): State<AppState>,
    Json(payload): Json<SetDefaultProviderPayload>,
) -> Response {
    let target = payload.provider.or(payload.id).unwrap_or_default();
    if target.trim().is_empty() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Missing provider id" })),
        ).into_response();
    }
    match api::providers::set_default_provider_id(&state.db, target.trim()) {
        Ok(_) => Json(json!({ "success": true, "default_provider": target.trim() })).into_response(),
        Err(err) => (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct StreamServersParams {
    provider: Option<String>,
    slug: String,
    ep: i32,
    mode: Option<String>,
}

async fn stream_servers_handler(
    State(state): State<AppState>,
    Query(params): Query<StreamServersParams>,
) -> Response {
    let default_id = api::providers::get_default_provider_id(&state.db);
    let provider_id = params.provider.as_deref().unwrap_or(&default_id);
    let provider = match api::providers::get_provider(provider_id) {
        Some(p) => p,
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": format!("Unknown provider: {}", provider_id) })),
            ).into_response();
        }
    };
    let mode = params.mode.as_deref().unwrap_or("sub");
    match provider.get_servers(&params.slug, params.ep, mode).await {
        Ok(servers) => Json(json!({ "success": true, "data": servers })).into_response(),
        Err(err) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct StreamVariantsParams {
    url: String,
    referer: Option<String>,
}

async fn stream_variants_handler(Query(params): Query<StreamVariantsParams>) -> Response {
    if params.url.trim().is_empty() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Missing url parameter" })),
        ).into_response();
    }
    if let Err(err) = crate::net::validator::is_safe_remote_url(&params.url) {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": format!("Disallowed URL: {}", err) })),
        ).into_response();
    }
    match proxy::fetch_hls_text(&params.url, params.referer.as_deref()).await {
        Ok(text) => {
            let variants = proxy::parse_hls_variants(&text);
            Json(json!({ "success": true, "data": variants })).into_response()
        }
        Err(err) => (
            StatusCode::BAD_GATEWAY,
            Json(json!({ "success": false, "error": err.to_string() })),
        ).into_response(),
    }
}

#[derive(Debug, Deserialize)]
struct EditionsParams {
    id: Option<i64>,
    slug: Option<String>,
}

async fn editions_handler(
    State(state): State<AppState>,
    Query(params): Query<EditionsParams>,
) -> Response {
    let target_id = match params.id {
        Some(i) if i > 0 => Some(i),
        _ => {
            if let Some(slug) = &params.slug {
                crate::api::stream::try_extract_anilist_id_from_slug(slug)
            } else {
                None
            }
        }
    };

    let target_id = match target_id {
        Some(i) => i,
        None => {
            if let Some(slug) = &params.slug {
                let title = crate::api::stream::clean_slug_to_title(slug);
                match api::anilist::search_anime(&title, 1, 1, &state.db).await {
                    Ok(cards) if !cards.is_empty() => cards[0].id,
                    _ => return (StatusCode::NOT_FOUND, Json(json!({ "success": false, "error": "Anime ID not found" }))).into_response(),
                }
            } else {
                return (StatusCode::BAD_REQUEST, Json(json!({ "success": false, "error": "Missing id or slug" }))).into_response();
            }
        }
    };

    match api::anilist::fetch_anime_editions(target_id, &state.db).await {
        Ok(editions) => Json(json!({ "success": true, "data": editions })).into_response(),
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
    offset_ms: Option<i64>,
}

async fn sub_handler(
    State(state): State<AppState>,
    Query(params): Query<SubParams>,
) -> Response {
    if let Err(err) = crate::net::validator::is_safe_remote_url(&params.url) {
        return (
            StatusCode::BAD_REQUEST,
            [
                (header::CONTENT_TYPE, "text/plain; charset=utf-8"),
                (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*"),
            ],
            format!("Disallowed subtitle target: {}", err),
        ).into_response();
    }
    let mode = params.mode.unwrap_or_else(|| "gtx+".to_string());
    let offset_ms = params.offset_ms.unwrap_or(0);
    let vtt_text = match api::sub::get_or_translate_vtt(&params.url, &mode, offset_ms, &state.db).await {
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
#[derive(Debug, Deserialize)]
struct TranslateParams {
    text: String,
    sl: Option<String>,
    tl: Option<String>,
}

async fn translate_handler(Query(params): Query<TranslateParams>) -> Response {
    let sl = params.sl.unwrap_or_else(|| "auto".to_string());
    let tl = params.tl.unwrap_or_else(|| "id".to_string());
    match api::sub::translate_text(&params.text, &sl, &tl).await {
        Ok(translated) => Json(json!({ "success": true, "data": translated })).into_response(),
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
    mode: Option<String>,
    progress: i32,
    duration: Option<i32>,
    title: Option<String>,
    poster: Option<String>,
    #[serde(rename = "type")]
    anime_type: Option<String>,
}

async fn save_progress_handler(
    State(state): State<AppState>,
    Json(payload): Json<ProgressPayload>,
) -> Response {
    let duration = payload.duration.unwrap_or(0);
    match state.db.save_progress(
        &payload.slug,
        payload.episode,
        payload.mode.as_deref(),
        payload.progress,
        duration,
        payload.title.as_deref(),
        payload.poster.as_deref(),
        payload.anime_type.as_deref(),
    ) {
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
    mode: Option<String>,
}

async fn get_progress_handler(
    State(state): State<AppState>,
    Query(params): Query<GetProgressParams>,
) -> Response {
    match state.db.get_progress(&params.slug, params.ep, params.mode.as_deref()) {
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
        .thread_name("miru-core-worker")
        .build()?;

    std::thread::spawn(move || {
        rt.block_on(async move {
            let app = create_router(state);
            let addr = SocketAddr::from(([127, 0, 0, 1], port));

            match tokio::net::TcpListener::bind(addr).await {
                Ok(listener) => {
                    IS_RUNNING.store(true, Ordering::SeqCst);
                    tracing::info!("Miru Rust Core listening on http://{}", addr);

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
pub extern "system" fn Java_id_miru_app_NativeBridge_startDaemon(
    env: JNIEnv,
    class: JClass,
    data_dir: JString,
    port: jint,
) -> jboolean {
    Java_id_tatap_app_NativeBridge_startDaemon(env, class, data_dir, port)
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
pub extern "system" fn Java_id_miru_app_NativeBridge_stopDaemon(
    env: JNIEnv,
    class: JClass,
) -> jboolean {
    Java_id_tatap_app_NativeBridge_stopDaemon(env, class)
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
#[no_mangle]
pub extern "system" fn Java_id_miru_app_NativeBridge_isDaemonRunning(
    env: JNIEnv,
    class: JClass,
) -> jboolean {
    Java_id_tatap_app_NativeBridge_isDaemonRunning(env, class)
}
