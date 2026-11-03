use anyhow::{anyhow, Result};
use base64::prelude::*;
use reqwest::Client;
use serde_json::Value;

pub fn unpack_packer(packed: &str) -> String {
    let re_args = regex::Regex::new(r#"\}\(\s*['"](.*?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"](.*?)['"]\.split"#).ok();
    let caps = match re_args.as_ref().and_then(|r| r.captures(packed)) {
        Some(c) => c,
        None => return String::new(),
    };

    let p = caps.get(1).map(|m| m.as_str()).unwrap_or("");
    let a: u32 = caps.get(2).and_then(|m| m.as_str().parse().ok()).unwrap_or(62);
    let k_str = caps.get(4).map(|m| m.as_str()).unwrap_or("");
    let words: Vec<&str> = k_str.split('|').collect();

    let re_word = regex::Regex::new(r"\b\w+\b").ok();
    if let Some(rw) = re_word {
        rw.replace_all(p, |w_caps: &regex::Captures| {
            let token = w_caps.get(0).map(|m| m.as_str()).unwrap_or("");
            let mut idx: usize = 0;
            for ch in token.chars() {
                let val = match ch {
                    '0'..='9' => ch as usize - '0' as usize,
                    'a'..='z' => ch as usize - 'a' as usize + 10,
                    'A'..='Z' => ch as usize - 'A' as usize + 36,
                    _ => 0,
                };
                idx = idx.saturating_mul(a as usize).saturating_add(val);
            }
            if idx < words.len() && !words[idx].is_empty() {
                words[idx].to_string()
            } else {
                token.to_string()
            }
        }).to_string()
    } else {
        p.to_string()
    }
}

pub async fn extract_direct_stream(client: &Client, embed_url: &str, referer: &str) -> Option<(String, String)> {
    if embed_url.is_empty() { return None; }
    if embed_url.contains(".mp4") || embed_url.contains(".m3u8") {
        return Some((embed_url.to_string(), referer.to_string()));
    }

    let resp = client.get(embed_url).header("Referer", referer).send().await.ok()?;
    let body = resp.text().await.ok()?;

    if embed_url.contains("mp4upload.com") {
        let re_mp4 = regex::Regex::new(r#"src:\s*['"](https?://[^'"]+\.mp4[^'"]*)['"]"#).ok()?;
        if let Some(cap) = re_mp4.captures(&body) {
            return Some((cap.get(1)?.as_str().to_string(), "https://www.mp4upload.com/".to_string()));
        }
    }

    let re_arc = regex::Regex::new(r#"file\s*:\s*['"](https?://archive\.org/[^'"]+)['"]"#).ok()?;
    if let Some(cap) = re_arc.captures(&body) {
        return Some((cap.get(1)?.as_str().to_string(), "https://archive.org/".to_string()));
    }

    let re_src = regex::Regex::new(r#"<source[^>]+src=['"]([^'"]+)['"]"#).ok()?;
    if let Some(cap) = re_src.captures(&body) {
        let stream = cap.get(1)?.as_str().replace("&amp;", "&");
        return Some((stream, embed_url.to_string()));
    }

    if body.contains("eval(function(p,a,c,k,e,d)") {
        let unpacked = unpack_packer(&body);
        let re_stream = regex::Regex::new(r#"(https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*)"#).ok()?;
        if let Some(cap) = re_stream.captures(&unpacked) {
            return Some((cap.get(1)?.as_str().to_string(), embed_url.to_string()));
        }
    }

    let re_iframe = regex::Regex::new(r#"<iframe[^>]+src=['"]([^'"]+)['"]"#).ok()?;
    if let Some(cap) = re_iframe.captures(&body) {
        let mut nested = cap.get(1)?.as_str().to_string();
        if nested.starts_with("//") { nested = format!("https:{}", nested); }
        if nested != embed_url {
            let re_mp4_u = regex::Regex::new(r#"src:\s*['"](https?://[^'"]+\.mp4[^'"]*)['"]"#).ok()?;
            if let Ok(n_resp) = client.get(&nested).header("Referer", embed_url).send().await {
                if let Ok(n_body) = n_resp.text().await {
                    if let Some(c) = re_mp4_u.captures(&n_body) {
                        return Some((c.get(1)?.as_str().to_string(), "https://www.mp4upload.com/".to_string()));
                    }
                }
            }
        }
    }

    None
}

pub async fn resolve_ajax_mirror(client: &Client, base_url: &str, payload_b64: &str, ep_url: &str) -> Result<String> {
    let ajax_url = format!("{}/wp-admin/admin-ajax.php", base_url.trim_end_matches('/'));
    let nonce_resp = client.post(&ajax_url)
        .header("Referer", ep_url)
        .header("X-Requested-With", "XMLHttpRequest")
        .form(&[("action", "aa1208d27f29ca340c92c66d1926f13f")])
        .send().await?.text().await?;
    let nonce_json: Value = serde_json::from_str(&nonce_resp)?;
    let nonce = nonce_json.get("data").and_then(|v| v.as_str()).ok_or_else(|| anyhow!("Nonce extraction failed"))?;

    let decoded_bytes = BASE64_STANDARD.decode(payload_b64.trim())?;
    let mut payload_map: serde_json::Map<String, Value> = serde_json::from_slice(&decoded_bytes)?;
    payload_map.insert("nonce".to_string(), Value::String(nonce.to_string()));
    payload_map.insert("action".to_string(), Value::String("2a3505c93b0035d3f455df82bf976b84".to_string()));

    let mut form_params = Vec::new();
    for (k, v) in payload_map {
        let s = match v {
            Value::String(val) => val,
            Value::Number(num) => num.to_string(),
            _ => v.to_string(),
        };
        form_params.push((k, s));
    }

    let mirror_resp = client.post(&ajax_url)
        .header("Referer", ep_url)
        .header("X-Requested-With", "XMLHttpRequest")
        .form(&form_params)
        .send().await?.text().await?;

    let mirror_json: Value = serde_json::from_str(&mirror_resp)?;
    let b64_html = mirror_json.get("data").and_then(|v| v.as_str()).ok_or_else(|| anyhow!("Mirror data missing"))?;
    let html_bytes = BASE64_STANDARD.decode(b64_html.trim())?;
    let html_str = String::from_utf8_lossy(&html_bytes);

    let re_iframe = regex::Regex::new(r#"src=['"]([^'"]+)['"]"#)?;
    let iframe_src = re_iframe.captures(&html_str)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .ok_or_else(|| anyhow!("Iframe not found in mirror HTML response"))?;

    Ok(iframe_src)
}
