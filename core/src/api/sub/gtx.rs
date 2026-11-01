use super::cue_merger::SubtitleCue;
use super::lexicon::{shield_terms, unshield_terms};
use crate::db::LexiconRule;
use anyhow::Result;
use reqwest::Client;
use serde_json::Value;
use std::sync::LazyLock;
use std::time::Duration;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(10))
        .tcp_nodelay(true)
        .build()
        .expect("Failed to initialize GTX HTTP client")
});

pub async fn translate_cues_gtx(
    cues: &[SubtitleCue],
    lexicon_rules: &[LexiconRule],
) -> Result<Vec<SubtitleCue>> {
    let mut join_set = tokio::task::JoinSet::new();

    for (chunk_idx, chunk) in cues.chunks(40).enumerate() {
        let texts: Vec<String> = chunk.iter().map(|c| c.text.clone()).collect();
        let joined = texts.join(" ~~~ ");
        let (shielded, map) = shield_terms(&joined, lexicon_rules);
        let rules_clone = lexicon_rules.to_vec();

        join_set.spawn(async move {
            let res = translate_text(&shielded, "auto", "id").await;
            (chunk_idx, res, texts, map, rules_clone)
        });
    }

    let mut translated_chunks: Vec<(usize, Vec<String>)> = Vec::new();
    while let Some(res) = join_set.join_next().await {
        if let Ok((idx, task_res, originals, map, rules)) = res {
            match task_res {
                Ok(tr_text) => {
                    let unshielded = unshield_terms(&tr_text, &map, &rules);
                    let parts: Vec<String> = unshielded.split("~~~").map(|s| s.trim().to_string()).collect();
                    let final_lines: Vec<String> = (0..originals.len())
                        .map(|i| parts.get(i).cloned().unwrap_or_else(|| originals[i].clone()))
                        .collect();
                    translated_chunks.push((idx, final_lines));
                }
                Err(_) => {
                    translated_chunks.push((idx, originals));
                }
            }
        }
    }

    translated_chunks.sort_by_key(|(idx, _)| *idx);
    let mut flat = Vec::with_capacity(cues.len());
    for (_, chunk) in translated_chunks {
        flat.extend(chunk);
    }

    let result_cues: Vec<SubtitleCue> = cues
        .iter()
        .enumerate()
        .map(|(i, c)| SubtitleCue {
            start_ms: c.start_ms,
            end_ms: c.end_ms,
            text: flat.get(i).cloned().unwrap_or_else(|| c.text.clone()),
        })
        .collect();

    Ok(result_cues)
}

pub async fn translate_text(text: &str, sl: &str, tl: &str) -> Result<String> {
    if text.trim().is_empty() {
        return Ok(String::new());
    }

    let api_url = format!(
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl={}&tl={}&q={}",
        sl, tl, urlencoding::encode(text)
    );

    let res = HTTP_CLIENT.get(&api_url).send().await?;
    if res.status().is_success() {
        let val: Value = res.json().await?;
        if let Some(arr) = val.as_array() {
            let mut pieces = Vec::new();
            for item in arr {
                if let Some(inner) = item.as_array() {
                    if let Some(t) = inner.first().and_then(|v| v.as_str()) {
                        pieces.push(t.to_string());
                    }
                } else if let Some(t) = item.as_str() {
                    pieces.push(t.to_string());
                }
            }
            if !pieces.is_empty() {
                return Ok(pieces.join(" "));
            }
        }
    }

    Ok(text.to_string())
}
