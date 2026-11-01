use std::net::{Ipv4Addr, Ipv6Addr};

/// Validates that an outbound URL uses safe remote protocols (http/https)
/// and strictly prevents SSRF to local, loopback, link-local, and private networks.
pub fn is_safe_remote_url(raw_url: &str) -> Result<reqwest::Url, &'static str> {
    let parsed = reqwest::Url::parse(raw_url).map_err(|_| "Malformed URL")?;
    let scheme = parsed.scheme().to_lowercase();
    if scheme != "http" && scheme != "https" {
        return Err("Disallowed URL scheme: only http and https are permitted");
    }

    let host_str = match parsed.host_str() {
        Some(h) => h,
        None => return Err("URL is missing a valid host"),
    };

    if let Ok(ipv4) = host_str.parse::<Ipv4Addr>() {
        if is_restricted_ipv4(ipv4) {
            return Err("Access to internal or private IPv4 network is forbidden");
        }
    } else if let Ok(ipv6) = host_str.trim_start_matches('[').trim_end_matches(']').parse::<Ipv6Addr>() {
        if is_restricted_ipv6(ipv6) {
            return Err("Access to internal or private IPv6 network is forbidden");
        }
    } else {
        let lower = host_str.to_lowercase();
        if is_restricted_domain(&lower) {
            return Err("Access to internal or private domain is forbidden");
        }
    }

    Ok(parsed)
}

fn is_restricted_ipv4(ip: Ipv4Addr) -> bool {
    ip.is_loopback()
        || ip.is_private()
        || ip.is_link_local()
        || ip.is_unspecified()
        || ip.is_broadcast()
        || ip.is_documentation()
        || (ip.octets()[0] == 100 && (ip.octets()[1] & 0xc0) == 64)
}

fn is_restricted_ipv6(ip: Ipv6Addr) -> bool {
    ip.is_loopback()
        || ip.is_unspecified()
        || ((ip.segments()[0] & 0xfe00) == 0xfc00)
        || ((ip.segments()[0] & 0xffc0) == 0xfe80)
}

fn is_restricted_domain(domain: &str) -> bool {
    domain == "localhost"
        || domain.ends_with(".localhost")
        || domain.ends_with(".local")
        || domain.ends_with(".internal")
        || domain.ends_with(".lan")
        || domain == "metadata.google.internal"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_remote_urls() {
        assert!(is_safe_remote_url("https://example.com/playlist.m3u8").is_ok());
        assert!(is_safe_remote_url("http://cdn.megacloud.tv/stream.m3u8").is_ok());
        assert!(is_safe_remote_url("https://feed.animetosho.org/json?q=naruto").is_ok());
    }

    #[test]
    fn test_reject_internal_and_loopback() {
        assert!(is_safe_remote_url("http://127.0.0.1:8767/api/settings").is_err());
        assert!(is_safe_remote_url("http://127.0.0.2:8080/test").is_err());
        assert!(is_safe_remote_url("http://localhost:8767/api/ping").is_err());
        assert!(is_safe_remote_url("http://myhost.local/test").is_err());
        assert!(is_safe_remote_url("http://router.internal/admin").is_err());
        assert!(is_safe_remote_url("http://169.254.169.254/latest/meta-data").is_err());
        assert!(is_safe_remote_url("http://192.168.1.1/").is_err());
        assert!(is_safe_remote_url("http://10.0.0.1/secret").is_err());
        assert!(is_safe_remote_url("http://172.16.0.1/dashboard").is_err());
        assert!(is_safe_remote_url("http://0.0.0.0:80/").is_err());
        assert!(is_safe_remote_url("http://[::1]:8080/").is_err());
    }

    #[test]
    fn test_reject_disallowed_schemes() {
        assert!(is_safe_remote_url("file:///etc/passwd").is_err());
        assert!(is_safe_remote_url("ftp://example.com/file").is_err());
        assert!(is_safe_remote_url("gopher://example.com").is_err());
        assert!(is_safe_remote_url("javascript:alert(1)").is_err());
        assert!(is_safe_remote_url("data:text/plain;base64,SGVsbG8=").is_err());
    }
}
