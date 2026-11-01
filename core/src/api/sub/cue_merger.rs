#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubtitleCue {
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
}

pub fn parse_vtt_timestamp_ms(ts: &str) -> i64 {
    let clean_ts = ts.trim();
    let parts: Vec<&str> = clean_ts.split(':').collect();
    if parts.len() == 3 {
        let hours: i64 = parts[0].parse().unwrap_or(0);
        let mins: i64 = parts[1].parse().unwrap_or(0);
        let sec_parts: Vec<&str> = parts[2].split('.').collect();
        let secs: i64 = sec_parts.first().and_then(|s| s.parse().ok()).unwrap_or(0);
        let millis: i64 = sec_parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(0);
        hours * 3600_000 + mins * 60_000 + secs * 1_000 + millis
    } else if parts.len() == 2 {
        let mins: i64 = parts[0].parse().unwrap_or(0);
        let sec_parts: Vec<&str> = parts[1].split('.').collect();
        let secs: i64 = sec_parts.first().and_then(|s| s.parse().ok()).unwrap_or(0);
        let millis: i64 = sec_parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(0);
        mins * 60_000 + secs * 1_000 + millis
    } else {
        0
    }
}

pub fn format_vtt_timestamp(ms: i64) -> String {
    let non_neg = ms.max(0);
    let hours = non_neg / 3600_000;
    let mins = (non_neg % 3600_000) / 60_000;
    let secs = (non_neg % 60_000) / 1_000;
    let millis = non_neg % 1_000;
    format!("{:02}:{:02}:{:02}.{:03}", hours, mins, secs, millis)
}

pub fn parse_vtt_cues(vtt: &str) -> Vec<SubtitleCue> {
    let mut cues = Vec::new();
    let lines: Vec<&str> = vtt.lines().collect();
    let mut i = 0;

    while i < lines.len() {
        let line = lines[i].trim();
        if line.contains("-->") {
            let parts: Vec<&str> = line.split("-->").collect();
            if parts.len() == 2 {
                let start_part = parts[0].trim().split_whitespace().next().unwrap_or("");
                let end_part = parts[1].trim().split_whitespace().next().unwrap_or("");
                let start_ms = parse_vtt_timestamp_ms(start_part);
                let end_ms = parse_vtt_timestamp_ms(end_part);

                let mut text_lines = Vec::new();
                i += 1;
                while i < lines.len() && !lines[i].trim().is_empty() && !lines[i].contains("-->") {
                    text_lines.push(lines[i].trim());
                    i += 1;
                }
                let text = text_lines.join("\n");
                if !text.is_empty() {
                    cues.push(SubtitleCue { start_ms, end_ms, text });
                }
                continue;
            }
        }
        i += 1;
    }
    cues
}

pub fn merge_overlapping_cues(mut cues: Vec<SubtitleCue>, threshold_ms: i64) -> Vec<SubtitleCue> {
    if cues.len() <= 1 {
        return cues;
    }
    cues.sort_by_key(|c| (c.start_ms, c.end_ms));

    let mut merged: Vec<SubtitleCue> = Vec::with_capacity(cues.len());
    let mut current = cues.remove(0);

    for next in cues {
        let diff_start = (next.start_ms - current.start_ms).abs();
        let overlaps = next.start_ms < current.end_ms;

        if overlaps && (diff_start <= threshold_ms || (next.end_ms - current.end_ms).abs() <= threshold_ms) {
            let combined_text = if current.text.contains(&next.text) {
                current.text
            } else if next.text.contains(&current.text) {
                next.text
            } else {
                format!("{}\n{}", current.text, next.text)
            };
            current = SubtitleCue {
                start_ms: current.start_ms.min(next.start_ms),
                end_ms: current.end_ms.max(next.end_ms),
                text: combined_text,
            };
        } else {
            merged.push(current);
            current = next;
        }
    }
    merged.push(current);
    merged
}

pub fn cues_to_vtt(cues: &[SubtitleCue]) -> String {
    let mut out = String::from("WEBVTT\n\n");
    for (idx, cue) in cues.iter().enumerate() {
        out.push_str(&format!(
            "{}\n{} --> {}\n{}\n\n",
            idx + 1,
            format_vtt_timestamp(cue.start_ms),
            format_vtt_timestamp(cue.end_ms),
            cue.text
        ));
    }
    out
}

pub fn merge_vtt_overlaps(vtt: &str, threshold_ms: i64) -> String {
    let cues = parse_vtt_cues(vtt);
    if cues.is_empty() {
        return vtt.to_string();
    }
    let merged = merge_overlapping_cues(cues, threshold_ms);
    cues_to_vtt(&merged)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_merge_simultaneous_cues() {
        let cues = vec![
            SubtitleCue { start_ms: 1000, end_ms: 4000, text: "Luffy: Serang!".into() },
            SubtitleCue { start_ms: 1100, end_ms: 4000, text: "Zoro: Siap!".into() },
        ];
        let merged = merge_overlapping_cues(cues, 200);
        assert_eq!(merged.len(), 1);
        assert_eq!(merged[0].start_ms, 1000);
        assert_eq!(merged[0].end_ms, 4000);
        assert_eq!(merged[0].text, "Luffy: Serang!\nZoro: Siap!");
    }
}
