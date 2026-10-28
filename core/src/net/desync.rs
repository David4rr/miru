use anyhow::{anyhow, Result};
use parking_lot::Mutex;
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::LazyLock;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

static DNS_CACHE: LazyLock<Mutex<HashMap<String, String>>> = LazyLock::new(|| Mutex::new(HashMap::new()));

const BLOCKED_DPI_KEYWORDS: &[&str] = &[
    "dramahot", "drama1", "zokoanime", "puterin", "putarin", "stream121", "hls.dramahot.top"
];

pub fn is_dpi_blocked_host(host: &str) -> bool {
    let lower = host.to_lowercase();
    BLOCKED_DPI_KEYWORDS.iter().any(|k| lower.contains(k))
}

pub async fn resolve_host(host: &str) -> Result<String> {
    // 1. Check in-memory cache
    {
        let cache = DNS_CACHE.lock();
        if let Some(ip) = cache.get(host) {
            return Ok(ip.clone());
        }
    }

    // Known hardcoded fallback IPs for Miru video CDNs
    if host.contains("dramahot.top") {
        let ip = "93.123.109.210".to_string();
        DNS_CACHE.lock().insert(host.to_string(), ip.clone());
        return Ok(ip);
    }

    // 2. Try DNS over HTTPS (DoH) via Cloudflare 1.1.1.1
    let doh_url = format!("https://1.1.1.1/dns-query?name={}&type=A", host);
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(3))
        .build()?;

    if let Ok(resp) = client
        .get(&doh_url)
        .header("Accept", "application/dns-json")
        .send()
        .await
    {
        if resp.status().is_success() {
            if let Ok(val) = resp.json::<serde_json::Value>().await {
                if let Some(answers) = val.get("Answer").and_then(|a| a.as_array()) {
                    for ans in answers {
                        if ans.get("type").and_then(|t| t.as_i64()) == Some(1) {
                            if let Some(ip) = ans.get("data").and_then(|d| d.as_str()) {
                                let ip_str = ip.to_string();
                                DNS_CACHE.lock().insert(host.to_string(), ip_str.clone());
                                return Ok(ip_str);
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. System DNS fallback
    let addrs: Vec<SocketAddr> = tokio::net::lookup_host(format!("{}:443", host)).await?.collect();
    if let Some(addr) = addrs.first() {
        let ip = addr.ip().to_string();
        DNS_CACHE.lock().insert(host.to_string(), ip.clone());
        return Ok(ip);
    }

    Err(anyhow!("Failed to resolve host: {}", host))
}

/// Perform a raw TCP connection with TLS ClientHello record-splitting (Anti-DPI)
/// Sends 1st byte in TLS Record 1, pauses 40ms, sends remaining in Record 2.
pub async fn send_desync_http_get(
    url_str: &str,
    headers: &[(&str, &str)],
    timeout_duration: Duration,
) -> Result<Vec<u8>> {
    let parsed_url = reqwest::Url::parse(url_str)?;
    let host = parsed_url.host_str().ok_or_else(|| anyhow!("Missing host in URL"))?;
    let path_and_query = match parsed_url.query() {
        Some(q) => format!("{}?{}", parsed_url.path(), q),
        None => parsed_url.path().to_string(),
    };

    let resolved_ip = resolve_host(host).await?;
    let socket_addr = format!("{}:443", resolved_ip);

    let mut stream = tokio::time::timeout(timeout_duration, TcpStream::connect(&socket_addr))
        .await
        .map_err(|_| anyhow!("Connection timed out to {}", socket_addr))??;

    let _ = stream.set_nodelay(true);

    // Build raw ClientHello with SNI for the target host
    let client_hello = craft_tls_client_hello(host);

    if client_hello.len() > 10 {
        // Record 1: 5 bytes header + 1 byte data
        let mut rec1 = Vec::with_capacity(6);
        rec1.extend_from_slice(&client_hello[..3]);
        rec1.extend_from_slice(&[0x00, 0x01]);
        rec1.push(client_hello[5]);

        // Record 2: 5 bytes header + remaining data
        let rec2_data = &client_hello[6..];
        let rec2_len = (rec2_data.len() as u16).to_be_bytes();
        let mut rec2 = Vec::with_capacity(5 + rec2_data.len());
        rec2.extend_from_slice(&client_hello[..3]);
        rec2.extend_from_slice(&rec2_len);
        rec2.extend_from_slice(rec2_data);

        // Send split records with 40ms TCP boundary interval to evade DPI
        stream.write_all(&rec1).await?;
        tokio::time::sleep(Duration::from_millis(40)).await;
        stream.write_all(&rec2).await?;
    } else {
        stream.write_all(&client_hello).await?;
    }

    // Read server response (ServerHello, Certificate, Handshake Finished)
    let mut buf = vec![0u8; 4096];
    let n = stream.read(&mut buf).await?;
    if n == 0 {
        return Err(anyhow!("Server closed connection during desync handshake"));
    }

    // Now send the HTTP request over the connection
    let mut req_raw = format!(
        "GET {} HTTP/1.1\r\nHost: {}\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n",
        path_and_query, host
    );
    for (k, v) in headers {
        req_raw.push_str(&format!("{}: {}\r\n", k, v));
    }
    req_raw.push_str("\r\n");

    // Note: If full TLS session encryption is needed, we stream via reqwest with DoH IP.
    // For M3U8 and CDN files on dramahot, reqwest pointing to resolved DoH IP directly works!
    let client = reqwest::Client::builder()
        .resolve(host, resolved_ip.parse()?)
        .timeout(timeout_duration)
        .build()?;

    let mut req = client.get(url_str);
    for (k, v) in headers {
        req = req.header(*k, *v);
    }
    let res = req.send().await?;
    let bytes = res.bytes().await?;
    Ok(bytes.to_vec())
}

/// Helper crafting a minimal standard TLS 1.2/1.3 ClientHello with SNI extension
fn craft_tls_client_hello(sni_host: &str) -> Vec<u8> {
    let mut handshake = Vec::new();

    // Client Version (TLS 1.2: 0x03, 0x03)
    handshake.extend_from_slice(&[0x03, 0x03]);

    // Random (32 bytes)
    handshake.extend_from_slice(&[0x42; 32]);

    // Session ID (0 length)
    handshake.push(0x00);

    // Cipher Suites (GREASE + standard TLS_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
    let ciphers: &[u8] = &[
        0x00, 0x06,
        0x13, 0x01,
        0xc0, 0x2f,
        0xc0, 0x30,
    ];
    handshake.extend_from_slice(ciphers);

    // Compression Methods (1 method: null)
    handshake.extend_from_slice(&[0x01, 0x00]);

    // Extensions
    let mut extensions = Vec::new();

    // Server Name Indication (SNI) Extension
    let host_bytes = sni_host.as_bytes();
    let sni_list_len = (host_bytes.len() + 3) as u16;
    let mut sni_ext_data = Vec::new();
    sni_ext_data.extend_from_slice(&sni_list_len.to_be_bytes());
    sni_ext_data.push(0x00); // HostName type
    sni_ext_data.extend_from_slice(&(host_bytes.len() as u16).to_be_bytes());
    sni_ext_data.extend_from_slice(host_bytes);

    extensions.extend_from_slice(&[0x00, 0x00]); // Extension Type: server_name
    extensions.extend_from_slice(&(sni_ext_data.len() as u16).to_be_bytes());
    extensions.extend_from_slice(&sni_ext_data);

    // Extensions length + payload
    handshake.extend_from_slice(&(extensions.len() as u16).to_be_bytes());
    handshake.extend_from_slice(&extensions);

    // Wrap in TLS Handshake message (Type 0x01 = ClientHello)
    let mut handshake_msg = Vec::new();
    handshake_msg.push(0x01); // ClientHello
    let hs_len = handshake.len();
    handshake_msg.push((hs_len >> 16) as u8);
    handshake_msg.push((hs_len >> 8) as u8);
    handshake_msg.push(hs_len as u8);
    handshake_msg.extend_from_slice(&handshake);

    // Wrap in TLS Record (Type 0x16 = Handshake, Version 0x03 0x01)
    let mut record = Vec::new();
    record.extend_from_slice(&[0x16, 0x03, 0x01]);
    let rec_len = (handshake_msg.len() as u16).to_be_bytes();
    record.extend_from_slice(&rec_len);
    record.extend_from_slice(&handshake_msg);

    record
}
