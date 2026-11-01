use super::ai_byok::{AiProviderConfig, DEFAULT_SYSTEM_PROMPT};
use anyhow::{anyhow, Result};
use reqwest::Client;
use serde_json::{json, Value};
use std::sync::LazyLock;

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| Client::new());

pub async fn call_llm(provider: &AiProviderConfig, prompt: &str) -> Result<String> {
    let ptype = provider.provider_type.to_lowercase();
    let sys_prompt = provider.prompt_template.as_deref().unwrap_or(DEFAULT_SYSTEM_PROMPT);

    if ptype == "gemini" {
        let key = provider.api_key.as_deref().unwrap_or_default();
        let base = provider.base_url.as_deref().unwrap_or("https://generativelanguage.googleapis.com");
        let url = format!("{}/v1beta/models/{}:generateContent?key={}", base.trim_end_matches('/'), provider.model_id, key);
        let body = json!({
            "contents": [{"parts": [{"text": format!("{}\n\nText:\n{}", sys_prompt, prompt)}]}],
            "generationConfig": { "temperature": 0.3 }
        });
        let res = HTTP_CLIENT.post(&url).json(&body).send().await?;
        let res_json: Value = res.json().await?;
        let txt = res_json["candidates"][0]["content"]["parts"][0]["text"].as_str().ok_or_else(|| anyhow!("Gemini invalid response"))?;
        Ok(txt.to_string())
    } else if ptype == "groq" || ptype == "openai" {
        let base = provider.base_url.as_deref().unwrap_or_else(|| {
            if ptype == "groq" { "https://api.groq.com/openai/v1" } else { "https://api.openai.com/v1" }
        });
        let url = format!("{}/chat/completions", base.trim_end_matches('/'));
        let mut req = HTTP_CLIENT.post(&url).header("Content-Type", "application/json");
        if let Some(key) = &provider.api_key {
            req = req.header("Authorization", format!("Bearer {}", key));
        }
        let body = json!({
            "model": provider.model_id,
            "messages": [
                {"role": "system", "content": sys_prompt},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.3
        });
        let res = req.json(&body).send().await?;
        let res_json: Value = res.json().await?;
        let txt = res_json["choices"][0]["message"]["content"].as_str().ok_or_else(|| anyhow!("LLM invalid response"))?;
        Ok(txt.to_string())
    } else {
        let base = provider.base_url.as_deref().unwrap_or("http://127.0.0.1:11434");
        let url = format!("{}/api/chat", base.trim_end_matches('/'));
        let body = json!({
            "model": provider.model_id,
            "messages": [
                {"role": "system", "content": sys_prompt},
                {"role": "user", "content": prompt}
            ],
            "stream": false
        });
        let res = HTTP_CLIENT.post(&url).json(&body).send().await?;
        let res_json: Value = res.json().await?;
        let txt = res_json["message"]["content"].as_str().ok_or_else(|| anyhow!("Ollama invalid response"))?;
        Ok(txt.to_string())
    }
}
