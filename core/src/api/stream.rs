use crate::api::providers::{self, StreamProvider};
use crate::db::{CachedEpisode, Database, SkipInterval, SubtitleTrack};
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::Arc;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ResolvedStream {
    pub slug: String,
    pub episode: i32,
    pub mode: String,
    pub master: String,
    pub referer: String,
    pub server: String,
    pub provider: String,
    pub subtitles: Vec<SubtitleTrack>,
    pub intro: SkipInterval,
    pub outro: SkipInterval,
    pub proxied_master_url: String,
}

pub fn clean_slug_to_title(slug: &str) -> String {
    let parts: Vec<&str> = slug.split('-').collect();
    if parts.len() > 1 && parts.last().map(|p| p.chars().all(|c| c.is_ascii_digit())).unwrap_or(false) {
        parts[..parts.len() - 1].join(" ")
    } else {
        slug.replace('-', " ")
    }
}

pub fn try_extract_anilist_id_from_slug(slug: &str) -> Option<i64> {
    let parts: Vec<&str> = slug.split('-').collect();
    if parts.len() > 1 {
        if let Some(last) = parts.last() {
            if let Ok(id) = last.parse::<i64>() {
                if id > 0 {
                    return Some(id);
                }
            }
        }
    }
    None
}

pub fn normalize_sub_lang(label: &str, raw_lang: &str) -> String {
    let lb = label.to_lowercase();
    const PATTERNS: &[(&str, &str)] = &[
        ("indonesia", "id"), ("english", "en"), ("arabic", "ar"), ("العربية", "ar"),
        ("spanish", "es"), ("español", "es"), ("portuguese", "pt"), ("french", "fr"),
        ("german", "de"), ("italian", "it"), ("russian", "ru"), ("japanese", "ja"),
        ("chinese", "zh"), ("vietnamese", "vi"), ("thai", "th"),
    ];
    for &(pat, code) in PATTERNS {
        if lb.contains(pat) {
            return code.to_string();
        }
    }
    raw_lang.to_string()
}

pub fn sort_subtitles(subs: &mut [SubtitleTrack]) {
    subs.sort_by(|a, b| {
        let a_id = a.lang == "id" || a.label.to_lowercase().contains("indonesia");
        let b_id = b.lang == "id" || b.label.to_lowercase().contains("indonesia");
        if a_id != b_id {
            return b_id.cmp(&a_id);
        }
        let a_en = a.lang == "en" || a.label.to_lowercase().contains("english");
        let b_en = b.lang == "en" || b.label.to_lowercase().contains("english");
        if a_en != b_en {
            return b_en.cmp(&a_en);
        }
        a.label.cmp(&b.label)
    });
}

fn process_subtitles(subtitles: &mut Vec<SubtitleTrack>, db: &Database) {
    for s in subtitles.iter_mut() {
        s.lang = normalize_sub_lang(&s.label, &s.lang);
    }

    let has_indo = subtitles.iter().any(|s| {
        let l = s.lang.to_lowercase();
        let lb = s.label.to_lowercase();
        l == "id" || l == "ind" || lb.contains("indonesia")
    });

    if !has_indo {
        let candidate_sub = subtitles.iter().find(|s| {
            let lb = s.label.to_lowercase();
            let l = s.lang.to_lowercase();
            (lb.contains("english") || l == "en")
                && !lb.contains("arabic") && !lb.contains("french") && !lb.contains("german")
                && !lb.contains("spanish") && !lb.contains("portuguese") && !lb.contains("russian")
        }).or_else(|| subtitles.first()).cloned();

        if let Some(src_sub) = candidate_sub {
            if !src_sub.url.is_empty() {
                let trans_url = format!("http://127.0.0.1:8767/api/player/sub?url={}&mode=gtx+", urlencoding::encode(&src_sub.url));
                subtitles.insert(0, SubtitleTrack {
                    label: "Indonesia (Otomatis)".to_string(),
                    lang: "id".to_string(),
                    url: trans_url,
                    default: true,
                });
                let prewarm_url = src_sub.url.clone();
                let db_clone = db.clone();
                tokio::spawn(async move {
                    let _ = crate::api::sub::get_or_translate_vtt(&prewarm_url, "gtx+", 0, &db_clone).await;
                });
            }
        }
    }

    let mut found_default = false;
    for s in subtitles.iter_mut() {
        let is_id = s.lang == "id" || s.label.to_lowercase().contains("indonesia");
        if is_id && !found_default {
            s.default = true;
            found_default = true;
        } else {
            s.default = false;
        }
        if !s.url.is_empty() && !s.url.starts_with("http://127.0.0.1:8767") {
            let sub_mode = if is_id { "gtx+" } else { "raw" };
            s.url = format!("http://127.0.0.1:8767/api/player/sub?url={}&mode={}", urlencoding::encode(&s.url), sub_mode);
        }
    }
    sort_subtitles(subtitles);
}

pub async fn resolve_stream(
    slug: &str,
    episode: i32,
    mode: &str,
    provider_param: Option<&str>,
    server_param: Option<&str>,
    mal_id: Option<i64>,
    title_hint: Option<&str>,
    db: &Database,
) -> Result<ResolvedStream> {
    let default_id = providers::get_default_provider_id(db);
    let provider_id = provider_param.unwrap_or(&default_id);
    let provider: Arc<dyn StreamProvider> = providers::get_provider(provider_id)
        .ok_or_else(|| anyhow!("Unknown streaming provider: '{}'", provider_id))?;

    let is_dub = mode.eq_ignore_ascii_case("dub");
    if is_dub && !provider.has_dub() {
        return Err(anyhow!("Provider '{}' does not support DUB mode", provider.name()));
    }

    let active_mode = if is_dub { "dub" } else { "sub" };
    let cache_slug = if provider_id == "hianime" { slug.to_string() } else { format!("{}:{}", provider_id, slug) };

    if let Ok(Some(cached)) = db.get_episode_cache(&cache_slug, episode, active_mode, 7200) {
        let proxied = format!(
            "http://127.0.0.1:8767/api/player/video?url={}&referer={}",
            urlencoding::encode(&cached.master),
            urlencoding::encode(&cached.referer)
        );
        let mut proxied_subtitles = cached.subtitles;
        process_subtitles(&mut proxied_subtitles, db);

        return Ok(ResolvedStream {
            slug: slug.to_string(),
            episode: cached.ep,
            mode: cached.mode,
            master: cached.master,
            referer: cached.referer,
            server: cached.server,
            provider: provider_id.to_string(),
            subtitles: proxied_subtitles,
            intro: cached.intro,
            outro: cached.outro,
            proxied_master_url: proxied,
        });
    }

    let search_title = match title_hint {
        Some(t) if !t.trim().is_empty() => t.trim().to_string(),
        _ => clean_slug_to_title(slug),
    };

    let target_mal_id = match mal_id {
        Some(m) if m > 0 => Some(m),
        _ => {
            if let Some(al_id) = try_extract_anilist_id_from_slug(slug) {
                crate::api::anilist::get_mal_id_by_anilist_id(al_id).await.unwrap_or(None)
            } else {
                crate::api::anilist::search_anime(&search_title, 1, 1, db).await.ok()
                    .and_then(|cards| cards.into_iter().next())
                    .and_then(|c| c.id_mal)
            }
        }
    };

    let res = provider.resolve_episode(slug, episode, active_mode, server_param, target_mal_id, Some(&search_title)).await?;
    let mut subtitles = res.subtitles;
    process_subtitles(&mut subtitles, db);

    let (intro, outro) = (SkipInterval { start: 0.0, end: 0.0 }, SkipInterval { start: 0.0, end: 0.0 });
    let cached = CachedEpisode {
        slug: cache_slug.clone(),
        ep: episode,
        mode: active_mode.to_string(),
        master: res.master.clone(),
        variants: Value::Array(vec![]),
        sub: subtitles.first().map(|s| s.url.clone()).unwrap_or_default(),
        sub_lang: subtitles.first().map(|s| s.label.clone()).unwrap_or_default(),
        referer: res.referer.clone(),
        server: res.server.clone(),
        subtitles: subtitles.clone(),
        intro: SkipInterval { start: intro.start, end: intro.end },
        outro: SkipInterval { start: outro.start, end: outro.end },
        fetched_at: 0,
    };
    let _ = db.set_episode_cache(&cached);

    let proxied = format!(
        "http://127.0.0.1:8767/api/player/video?url={}&referer={}",
        urlencoding::encode(&res.master),
        urlencoding::encode(&res.referer)
    );

    Ok(ResolvedStream {
        slug: slug.to_string(),
        episode,
        mode: active_mode.to_string(),
        master: res.master,
        referer: res.referer,
        server: res.server,
        provider: provider_id.to_string(),
        subtitles,
        intro,
        outro,
        proxied_master_url: proxied,
    })
}
