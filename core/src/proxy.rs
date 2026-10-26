use crate::net::desync::{is_dpi_blocked_host, send_desync_http_get};
use axum::{
    body::Body,
    extract::Query,
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
};
use reqwest::Client;
use serde::Deserialize;
use std::sync::LazyLock;
use std::time::Duration;

static PROXY_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .expect("Failed to initialize proxy reqwest client")
});

const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

#[derive(Debug, Deserialize)]
pub struct VideoProxyParams {
    pub url: String,
    pub referer: Option<String>,
}

pub fn is_m3u8(url: &str) -> bool {
    let lower = url.to_lowercase();
    lower.contains(".m3u8") || lower.contains("/manifest") || lower.contains("/master")
}

pub fn rewrite_m3u8(content: &str, base_url: &str, referer: &str) -> String {
    let base = match base_url.rsplit_once('/') {
        Some((prefix, _)) => format!("{}/", prefix),
        None => base_url.to_string(),
    };

    let re_codecs = regex::Regex::new(r#"CODECS="([^"]+)""#).unwrap();
    let re_uri = regex::Regex::new(r#"URI=(?:"([^"]+)"|'([^']+)'|([^,\s]+))"#).unwrap();

    let mut out = Vec::new();

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            out.push(line.to_string());
            continue;
        }

        // Ensure audio codecs (mp4a) are present in STREAM-INF so player prepares audio buffer
        if trimmed.starts_with("#EXT-X-STREAM-INF:") {
            let mut modified = trimmed.to_string();
            if !modified.contains("CODECS=") {
                modified.push_str(r#",CODECS="avc1.64001f,mp4a.40.2""#);
            } else if !modified.to_lowercase().contains("mp4a") && !modified.to_lowercase().contains("aac") {
                modified = re_codecs.replace(&modified, r#"CODECS="$1,mp4a.40.2""#).to_string();
            }
            out.push(modified);
            continue;
        }

        // Rewrite tags containing URI="..." (such as #EXT-X-MEDIA, #EXT-X-MAP, #EXT-X-KEY)
        if trimmed.starts_with('#') {
            if trimmed.contains("URI=") {
                let rewritten = re_uri.replace_all(trimmed, |caps: &regex::Captures| {
                    let raw_uri = caps.get(1).or_else(|| caps.get(2)).or_else(|| caps.get(3)).map(|m| m.as_str()).unwrap_or("");
                    let abs_url = if raw_uri.starts_with("http://") || raw_uri.starts_with("https://") {
                        raw_uri.to_string()
                    } else {
                        format!("{}{}", base, raw_uri)
                    };
                    format!(
                        r#"URI="/api/player/video?url={}&referer={}""#,
                        urlencoding::encode(&abs_url),
                        urlencoding::encode(referer)
                    )
                }).to_string();
                out.push(rewritten);
            } else {
                out.push(trimmed.to_string());
            }
            continue;
        }

        // Line is a video segment or playlist variant URL
        let abs_url = if trimmed.starts_with("http://") || trimmed.starts_with("https://") {
            trimmed.to_string()
        } else {
            format!("{}{}", base, trimmed)
        };

        out.push(format!(
            "/api/player/video?url={}&referer={}",
            urlencoding::encode(&abs_url),
            urlencoding::encode(referer)
        ));
    }

    out.join("\n")
}

pub async fn proxy_video_handler(Query(params): Query<VideoProxyParams>) -> Response {
    let url = params.url;
    let referer = params.referer.unwrap_or_default();

    let host = reqwest::Url::parse(&url)
        .ok()
        .and_then(|u| u.host_str().map(|h| h.to_string()))
        .unwrap_or_default();

    // 1. Anti-DPI check: if target host is blocked by ISP DPI, use TLS desync engine
    if is_dpi_blocked_host(&host) {
        let headers = [
            ("User-Agent", USER_AGENT),
            ("Referer", referer.as_str()),
        ];
        match send_desync_http_get(&url, &headers, Duration::from_secs(10)).await {
            Ok(bytes) => {
                if is_m3u8(&url) {
                    if let Ok(txt) = std::str::from_utf8(&bytes) {
                        let rewritten = rewrite_m3u8(txt, &url, &referer);
                        return (
                            StatusCode::OK,
                            [
                                (header::CONTENT_TYPE, "application/vnd.apple.mpegurl"),
                                (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*"),
                            ],
                            rewritten,
                        ).into_response();
                    }
                }
                return (
                    StatusCode::OK,
                    [
                        (header::CONTENT_TYPE, "video/mp2t"),
                        (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*"),
                    ],
                    bytes,
                ).into_response();
            }
            Err(err) => {
                tracing::warn!("TLS Desync failed for {}: {}", url, err);
            }
        }
    }

    // 2. Standard proxy fetch via reqwest
    let mut req = PROXY_CLIENT.get(&url).header("User-Agent", USER_AGENT);
    if !referer.is_empty() {
        req = req.header("Referer", &referer);
    }

    let upstream = match req.send().await {
        Ok(res) => res,
        Err(err) => {
            return (
                StatusCode::BAD_GATEWAY,
                format!("Failed to connect to upstream: {}", err),
            ).into_response();
        }
    };

    let status = upstream.status();

    if is_m3u8(&url) {
        // Read full M3U8 manifest and rewrite segment URLs
        match upstream.text().await {
            Ok(body_text) => {
                let rewritten = rewrite_m3u8(&body_text, &url, &referer);
                (
                    StatusCode::OK,
                    [
                        (header::CONTENT_TYPE, "application/vnd.apple.mpegurl"),
                        (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*"),
                    ],
                    rewritten,
                ).into_response()
            }
            Err(_) => (StatusCode::BAD_GATEWAY, "Failed to read upstream manifest").into_response(),
        }
    } else {
        // Stream video/audio chunks directly to client
        let mut headers = HeaderMap::new();
        headers.insert(header::ACCESS_CONTROL_ALLOW_ORIGIN, "*".parse().unwrap());
        if let Some(ct) = upstream.headers().get(header::CONTENT_TYPE) {
            headers.insert(header::CONTENT_TYPE, ct.clone());
        } else {
            headers.insert(header::CONTENT_TYPE, "video/mp2t".parse().unwrap());
        }
        if let Some(cl) = upstream.headers().get(header::CONTENT_LENGTH) {
            headers.insert(header::CONTENT_LENGTH, cl.clone());
        }
        if let Some(cr) = upstream.headers().get(header::CONTENT_RANGE) {
            headers.insert(header::CONTENT_RANGE, cr.clone());
        }
        if let Some(ar) = upstream.headers().get(header::ACCEPT_RANGES) {
            headers.insert(header::ACCEPT_RANGES, ar.clone());
        }

        let body = Body::from_stream(upstream.bytes_stream());
        (status, headers, body).into_response()
    }
}
