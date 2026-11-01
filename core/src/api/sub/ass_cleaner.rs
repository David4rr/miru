use regex::Regex;
use std::sync::LazyLock;

static RE_DRAWING: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?s)\{\\p[1-9]\}.*?\{\\p0\}").expect("invalid drawing regex")
});
static RE_KARAOKE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\{\\[kK][of]?\d+\}").expect("invalid karaoke regex")
});
static RE_POS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\\(pos|move|org|clip|fad|an|q)\([^)]*\)").expect("invalid pos regex")
});
static RE_STYLE_TAGS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\\[1234]?[cCaA](&H[0-9a-fA-F]+&)?|\\fn[^\}\\]+|\\fs\d+|\\(bord|shad|blur|be)\d+(\.\d+)?|\\an\d+")
        .expect("invalid style tags regex")
});
static RE_ITALIC_ON: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\i1\}").unwrap());
static RE_ITALIC_OFF: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\i0\}").unwrap());
static RE_BOLD_ON: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\b1\}").unwrap());
static RE_BOLD_OFF: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\b0\}").unwrap());
static RE_UNDERLINE_ON: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\u1\}").unwrap());
static RE_UNDERLINE_OFF: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{\\u0\}").unwrap());
static RE_REMAINING_TAGS: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\{[^}]*\}").unwrap());

/// Converts an ASS/SSA timestamp `H:MM:SS.cc` into a WebVTT timestamp `HH:MM:SS.mmm`.
pub fn ass_time_to_vtt(time_str: &str) -> String {
    let parts: Vec<&str> = time_str.trim().split(':').collect();
    if parts.len() != 3 {
        return "00:00:00.000".to_string();
    }
    let hours: u32 = parts[0].parse().unwrap_or(0);
    let mins: u32 = parts[1].parse().unwrap_or(0);
    let sec_parts: Vec<&str> = parts[2].split('.').collect();
    let secs: u32 = sec_parts.first().and_then(|s| s.parse().ok()).unwrap_or(0);
    let centis: u32 = sec_parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(0);
    let millis = centis * 10;
    format!("{:02}:{:02}:{:02}.{:03}", hours, mins, secs, millis)
}

/// Cleans raw ASS dialogue text into readable plain/styled text.
pub fn clean_ass_dialogue(text: &str) -> String {
    let no_drawings = RE_DRAWING.replace_all(text, "");
    let no_karaoke = RE_KARAOKE.replace_all(&no_drawings, "");
    let no_pos = RE_POS.replace_all(&no_karaoke, "");
    let no_styles = RE_STYLE_TAGS.replace_all(&no_pos, "");

    let italic = RE_ITALIC_ON.replace_all(&no_styles, "<i>");
    let italic = RE_ITALIC_OFF.replace_all(&italic, "</i>");
    let bold = RE_BOLD_ON.replace_all(&italic, "<b>");
    let bold = RE_BOLD_OFF.replace_all(&bold, "</b>");
    let underline = RE_UNDERLINE_ON.replace_all(&bold, "<u>");
    let underline = RE_UNDERLINE_OFF.replace_all(&underline, "</u>");

    let clean_tags = RE_REMAINING_TAGS.replace_all(&underline, "");
    clean_tags
        .replace(r"\N", "\n")
        .replace(r"\n", "\n")
        .replace(r"\h", " ")
        .trim()
        .to_string()
}

/// Parses full ASS/SSA document and converts to standardized mobile WebVTT.
pub fn clean_ass_to_vtt(ass_content: &str) -> String {
    let mut vtt_output = String::from("WEBVTT\n\n");
    let mut cue_count = 0;
    let mut in_events = false;

    for line in ass_content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with('[') && trimmed.ends_with(']') {
            in_events = trimmed.eq_ignore_ascii_case("[Events]");
            continue;
        }
        if !in_events || !trimmed.starts_with("Dialogue:") {
            continue;
        }

        // Format is typically: Dialogue: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        let after_prefix = trimmed.trim_start_matches("Dialogue:").trim_start();
        let parts: Vec<&str> = after_prefix.splitn(10, ',').collect();
        if parts.len() < 10 {
            continue;
        }

        let start_time = ass_time_to_vtt(parts[1]);
        let end_time = ass_time_to_vtt(parts[2]);
        let raw_text = parts[9];
        let cleaned_text = clean_ass_dialogue(raw_text);

        if cleaned_text.is_empty() {
            continue;
        }

        cue_count += 1;
        vtt_output.push_str(&format!("{}\n{} --> {}\n{}\n\n", cue_count, start_time, end_time, cleaned_text));
    }

    if cue_count == 0 {
        return "WEBVTT\n\n".to_string();
    }

    vtt_output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ass_time_conversion() {
        assert_eq!(ass_time_to_vtt("0:01:23.40"), "00:01:23.400");
        assert_eq!(ass_time_to_vtt("1:20:05.12"), "01:20:05.120");
    }

    #[test]
    fn test_clean_ass_dialogue() {
        let input = r"{\pos(192,240)\i1}Luffy-senpai!{\i0}\N{\k20}Kapal{\k30} mendekat!";
        let cleaned = clean_ass_dialogue(input);
        assert_eq!(cleaned, "<i>Luffy-senpai!</i>\nKapal mendekat!");
    }

    #[test]
    fn test_clean_ass_drawings() {
        let input = r"{\p1}m 0 0 l 100 0 100 100 0 100{\p0}Hello world!";
        let cleaned = clean_ass_dialogue(input);
        assert_eq!(cleaned, "Hello world!");
    }
}
