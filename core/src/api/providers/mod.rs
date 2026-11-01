use crate::db::{Database, SubtitleTrack};
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;

pub mod anichan;
pub mod hianime;
pub mod otakudesu;

pub type BoxFuture<'a, T> = Pin<Box<dyn Future<Output = T> + Send + 'a>>;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamServer {
    pub server_id: String,
    pub server_name: String,
    pub latency_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderInfo {
    pub id: String,
    pub name: String,
    pub has_sub: bool,
    pub has_dub: bool,
    pub is_default: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderSearchResult {
    pub id: String,
    pub title: String,
    pub url: String,
    pub poster: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResolvedStreamSource {
    pub master: String,
    pub referer: String,
    pub server: String,
    pub subtitles: Vec<SubtitleTrack>,
}

pub trait StreamProvider: Send + Sync {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn has_sub(&self) -> bool {
        true
    }
    fn has_dub(&self) -> bool {
        false
    }
    fn search<'a>(&'a self, query: &'a str) -> BoxFuture<'a, Result<Vec<ProviderSearchResult>>>;
    fn get_servers<'a>(
        &'a self,
        slug: &'a str,
        ep: i32,
        mode: &'a str,
    ) -> BoxFuture<'a, Result<Vec<StreamServer>>>;
    fn resolve_episode<'a>(
        &'a self,
        slug: &'a str,
        ep: i32,
        mode: &'a str,
        server: Option<&'a str>,
        mal_id: Option<i64>,
        title_hint: Option<&'a str>,
    ) -> BoxFuture<'a, Result<ResolvedStreamSource>>;
}

pub fn get_provider(id: &str) -> Option<Arc<dyn StreamProvider>> {
    match id.to_lowercase().as_str() {
        "hianime" | "zokoanime" => Some(Arc::new(hianime::HiAnimeProvider::new())),
        "anichan" => Some(Arc::new(anichan::AniChanProvider::new())),
        "otakudesu" => Some(Arc::new(otakudesu::OtakudesuProvider::new())),
        _ => None,
    }
}

pub fn get_all_providers(default_id: &str) -> Vec<ProviderInfo> {
    vec![
        ProviderInfo {
            id: "hianime".to_string(),
            name: "HiAnime (Global)".to_string(),
            has_sub: true,
            has_dub: true,
            is_default: default_id == "hianime",
        },
        ProviderInfo {
            id: "anichan".to_string(),
            name: "AniChan (Indonesia)".to_string(),
            has_sub: true,
            has_dub: false,
            is_default: default_id == "anichan",
        },
        ProviderInfo {
            id: "otakudesu".to_string(),
            name: "Otakudesu (Indonesia)".to_string(),
            has_sub: true,
            has_dub: false,
            is_default: default_id == "otakudesu",
        },
    ]
}

pub fn get_default_provider_id(db: &Database) -> String {
    db.get_setting("default_provider")
        .ok()
        .flatten()
        .filter(|p| !p.trim().is_empty())
        .unwrap_or_else(|| "hianime".to_string())
}

pub fn set_default_provider_id(db: &Database, id: &str) -> Result<()> {
    if get_provider(id).is_none() {
        return Err(anyhow!("Unknown stream provider: {}", id));
    }
    Ok(db.set_setting("default_provider", id)?)
}
