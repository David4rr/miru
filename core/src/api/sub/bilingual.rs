use super::cue_merger::{format_vtt_timestamp, SubtitleCue};

/// Formats a dual-language line with Indonesian on top (#FFFFFF) and English on bottom (<small> #A0A0A0).
pub fn format_bilingual_line(id_text: &str, en_text: &str) -> String {
    let clean_id = id_text.trim();
    let clean_en = en_text.trim();

    if clean_id.is_empty() {
        return clean_en.to_string();
    }
    if clean_en.is_empty() || clean_id.eq_ignore_ascii_case(clean_en) {
        return clean_id.to_string();
    }

    format!("{}\n<font color=\"#A0A0A0\"><small>{}</small></font>", clean_id, clean_en)
}

/// Assembles dual-language WebVTT cues from matching Indonesian and English cue lists.
pub fn assemble_bilingual_vtt(id_cues: &[SubtitleCue], en_cues: &[SubtitleCue]) -> String {
    let mut out = String::from("WEBVTT\n\n");

    // If lengths match 1:1 (standard MT flow where each cue is translated)
    if id_cues.len() == en_cues.len() {
        for (idx, id_cue) in id_cues.iter().enumerate() {
            let en_cue = &en_cues[idx];
            let combined = format_bilingual_line(&id_cue.text, &en_cue.text);
            out.push_str(&format!(
                "{}\n{} --> {}\n{}\n\n",
                idx + 1,
                format_vtt_timestamp(id_cue.start_ms),
                format_vtt_timestamp(id_cue.end_ms),
                combined
            ));
        }
        return out;
    }

    // Fallback: match by closest time overlap
    for (idx, id_cue) in id_cues.iter().enumerate() {
        let matching_en = en_cues.iter().find(|en| {
            let overlap = (en.end_ms.min(id_cue.end_ms) - en.start_ms.max(id_cue.start_ms)).max(0);
            overlap > 0
        });

        let en_text = matching_en.map(|e| e.text.as_str()).unwrap_or("");
        let combined = format_bilingual_line(&id_cue.text, en_text);

        out.push_str(&format!(
            "{}\n{} --> {}\n{}\n\n",
            idx + 1,
            format_vtt_timestamp(id_cue.start_ms),
            format_vtt_timestamp(id_cue.end_ms),
            combined
        ));
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bilingual_formatting() {
        let id_cue = SubtitleCue {
            start_ms: 1000,
            end_ms: 3000,
            text: "Kapal musuh mendekat!".into(),
        };
        let en_cue = SubtitleCue {
            start_ms: 1000,
            end_ms: 3000,
            text: "Enemy ship approaching!".into(),
        };
        let vtt = assemble_bilingual_vtt(&[id_cue], &[en_cue]);
        assert!(vtt.contains("Kapal musuh mendekat!"));
        assert!(vtt.contains("<font color=\"#A0A0A0\"><small>Enemy ship approaching!</small></font>"));
    }
}
