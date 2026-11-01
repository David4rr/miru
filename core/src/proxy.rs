use axum::{
    body::Body,
    extract::Query,
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::sync::LazyLock;
use std::time::Duration;

static PROXY_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(45)).tcp_nodelay(true)
        .pool_max_idle_per_host(20).pool_idle_timeout(Duration::from_secs(60))
        .build().expect("Failed to initialize proxy reqwest client")
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
    let parsed_base = reqwest::Url::parse(base_url).ok();
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
                    let abs_url = if let Some(base_u) = &parsed_base {
                        base_u.join(raw_uri).map(|u| u.to_string()).unwrap_or_else(|_| raw_uri.to_string())
                    } else if raw_uri.starts_with("http://") || raw_uri.starts_with("https://") {
                        raw_uri.to_string()
                    } else {
                        format!("{}{}", base, raw_uri)
                    };
                    format!(
                        r#"URI="http://127.0.0.1:8767/api/player/video?url={}&referer={}""#,
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
        let abs_url = if let Some(base_u) = &parsed_base {
            base_u.join(trimmed).map(|u| u.to_string()).unwrap_or_else(|_| trimmed.to_string())
        } else if trimmed.starts_with("http://") || trimmed.starts_with("https://") {
            trimmed.to_string()
        } else {
            format!("{}{}", base, trimmed)
        };

        out.push(format!(
            "http://127.0.0.1:8767/api/player/video?url={}&referer={}",
            urlencoding::encode(&abs_url),
            urlencoding::encode(referer)
        ));
    }

    out.join("\n")
}

pub async fn proxy_video_handler(
    client_headers: HeaderMap,
    Query(params): Query<VideoProxyParams>,
) -> Response {
    if let Err(err) = crate::net::validator::is_safe_remote_url(&params.url) {
        return (StatusCode::BAD_REQUEST, format!("Disallowed URL: {}", err)).into_response();
    }
    let url = params.url;
    let referer = params.referer.unwrap_or_default();
    let effective_referer = if !referer.is_empty() {
        referer
    } else if url.contains("drama") || url.contains("zoko") {
        "https://zokoanime.video/".to_string()
    } else {
        "https://hianime.at/".to_string()
    };

    // High performance HTTP/2 streaming via PROXY_CLIENT
    let mut req = PROXY_CLIENT
        .get(&url)
        .header("User-Agent", USER_AGENT)
        .header("Referer", &effective_referer);

    if let Some(range) = client_headers.get(header::RANGE) {
        req = req.header(header::RANGE, range);
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
        if !status.is_success() {
            return (
                status,
                format!("Upstream M3U8 returned HTTP {}", status),
            ).into_response();
        }

        // Read full M3U8 manifest and rewrite segment URLs
        match upstream.text().await {
            Ok(body_text) => {
                let rewritten = rewrite_m3u8(&body_text, &url, &effective_referer);
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
        if url.contains(".ts") || url.contains(".mp4") || url.contains(".m4s") {
            headers.insert(header::CONTENT_TYPE, "video/mp2t".parse().unwrap());
        } else if let Some(ct) = upstream.headers().get(header::CONTENT_TYPE) {
            let ct_str = ct.to_str().unwrap_or("");
            if ct_str.contains("image/") {
                headers.insert(header::CONTENT_TYPE, "video/mp2t".parse().unwrap());
            } else {
                headers.insert(header::CONTENT_TYPE, ct.clone());
            }
        } else {
            headers.insert(header::CONTENT_TYPE, "video/mp2t".parse().unwrap());
        }
        for h in [header::CONTENT_LENGTH, header::CONTENT_RANGE, header::ACCEPT_RANGES] {
            if let Some(val) = upstream.headers().get(&h) {
                headers.insert(h, val.clone());
            }
        }

        let body = Body::from_stream(upstream.bytes_stream());
        (status, headers, body).into_response()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct VideoVariant {
    pub resolution: String,
    pub width: u32,
    pub height: u32,
    pub bandwidth: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub codecs: Option<String>,
    pub url: String,
}

pub fn parse_hls_variants(body: &str) -> Vec<VideoVariant> {
    let re_bw = regex::Regex::new(r"BANDWIDTH=(\d+)").ok();
    let re_res = regex::Regex::new(r"RESOLUTION=(\d+)[xX](\d+)").ok();
    let re_codecs = regex::Regex::new(r#"CODECS="([^"]+)""#).ok();
    let mut variants = Vec::new();
    let mut pending: Option<(u64, Option<(u32, u32)>, Option<String>)> = None;

    for line in body.lines() {
        let t = line.trim();
        if t.is_empty() { continue; }
        if t.starts_with("#EXT-X-STREAM-INF:") {
            let bw = re_bw.as_ref().and_then(|r| r.captures(t)).and_then(|c| c.get(1)).and_then(|m| m.as_str().parse().ok()).unwrap_or(0);
            let res = re_res.as_ref().and_then(|r| r.captures(t)).and_then(|c| {
                let w = c.get(1)?.as_str().parse().ok()?;
                let h = c.get(2)?.as_str().parse().ok()?;
                Some((w, h))
            });
            let cod = re_codecs.as_ref().and_then(|r| r.captures(t)).and_then(|c| c.get(1)).map(|m| m.as_str().to_string());
            pending = Some((bw, res, cod));
        } else if !t.starts_with('#') {
            if let Some((bw, Some((w, h)), cod)) = pending.take() {
                if w > 0 && h > 0 {
                    variants.push(VideoVariant {
                        resolution: format!("{}p", h),
                        width: w, height: h, bandwidth: bw, codecs: cod, url: t.to_string(),
                    });
                }
            } else {
                pending = None;
            }
        }
    }
    variants.sort_by(|a, b| b.height.cmp(&a.height).then_with(|| b.bandwidth.cmp(&a.bandwidth)));
    variants
}

pub async fn fetch_hls_text(url: &str, referer: Option<&str>) -> Result<String, anyhow::Error> {
    crate::net::validator::is_safe_remote_url(url)
        .map_err(|e| anyhow::anyhow!("Disallowed HLS target: {}", e))?;
    let fallback = if url.contains("drama") || url.contains("zoko") { "https://zokoanime.video/" } else { "https://hianime.at/" };
    let eff_ref = referer.unwrap_or(fallback);
    let text = PROXY_CLIENT.get(url).header("User-Agent", USER_AGENT).header("Referer", eff_ref).send().await?.text().await?;
    Ok(text)
}
