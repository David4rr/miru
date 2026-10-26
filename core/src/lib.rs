use axum::{routing::get, Json, Router};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde_json::json;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::LazyLock;
use tokio::sync::Notify;

static IS_RUNNING: AtomicBool = AtomicBool::new(false);
static SHUTDOWN_NOTIFY: LazyLock<Notify> = LazyLock::new(Notify::new);

/// Core Axum router definition
pub fn create_router() -> Router {
    Router::new()
        .route("/api/health", get(health_check))
        .route("/api/home", get(home_catalog))
}

async fn health_check() -> Json<serde_json::Value> {
    Json(json!({
        "status": "ok",
        "version": env!("CARGO_PKG_VERSION"),
        "engine": "rust-axum"
    }))
}

async fn home_catalog() -> Json<serde_json::Value> {
    // Placeholder returning Crunchyroll-style catalog schema
    Json(json!({
        "hero": [],
        "continue_watching": [],
        "top_airing": [],
        "seasonal": []
    }))
}

/// Internal function to spawn the Axum daemon inside Tokio
pub fn start_server(port: u16) -> Result<(), anyhow::Error> {
    if IS_RUNNING.load(Ordering::SeqCst) {
        return Ok(());
    }

    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .thread_name("tatap-core-worker")
        .build()?;


    std::thread::spawn(move || {
        rt.block_on(async move {
            let app = create_router();
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

// ==========================================
// JNI Exports for Android Integration
// ==========================================

#[no_mangle]
pub extern "system" fn Java_id_tatap_app_NativeBridge_startDaemon(
    mut env: JNIEnv,
    _class: JClass,
    _data_dir: JString,
    port: jint,
) -> jboolean {
    let port_u16 = if port <= 0 || port > 65535 { 8767 } else { port as u16 };

    match start_server(port_u16) {
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
