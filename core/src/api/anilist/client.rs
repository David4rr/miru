use anyhow::{anyhow, Result};
use reqwest::Client;
use serde_json::{json, Value};
use std::sync::LazyLock;

pub const ANILIST_URL: &str = "https://graphql.anilist.co";
pub const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

pub static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .user_agent(USER_AGENT)
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .expect("Failed to initialize reqwest client")
});

pub async fn query_anilist(query: &str, variables: Value) -> Result<Value> {
    let payload = json!({
        "query": query,
        "variables": variables
    });

    let resp_res = HTTP_CLIENT
        .post(ANILIST_URL)
        .json(&payload)
        .send()
        .await;

    let resp = match resp_res {
        Ok(r) => r,
        Err(e) if e.to_string().contains("dispatch task") => {
            Client::builder()
                .user_agent(USER_AGENT)
                .timeout(std::time::Duration::from_secs(30))
                .build()?
                .post(ANILIST_URL)
                .json(&payload)
                .send()
                .await?
        }
        Err(e) => return Err(e.into()),
    };

    if !resp.status().is_success() {
        return Err(anyhow!("AniList GraphQL returned status {}", resp.status()));
    }

    let json_val: Value = resp.json().await?;
    if let Some(errors) = json_val.get("errors") {
        return Err(anyhow!("AniList GraphQL errors: {}", errors));
    }

    Ok(json_val)
}

pub async fn get_mal_id_by_anilist_id(anilist_id: i64) -> Result<Option<i64>> {
    const QUERY: &str = r#"
    query ($id: Int) {
      Media(id: $id, type: ANIME) {
        idMal
      }
    }
    "#;
    let variables = json!({ "id": anilist_id });
    let val = query_anilist(QUERY, variables).await?;
    let id_mal = val
        .get("data")
        .and_then(|d| d.get("Media"))
        .and_then(|m| m.get("idMal"))
        .and_then(|v| v.as_i64());
    Ok(id_mal)
}
