use super::cue_merger::{cues_to_vtt, parse_vtt_cues, SubtitleCue};

/// Calculates the optimal temporal drift (in milliseconds) between reference cues
/// and external cues over a window of `[-window_ms, +window_ms]` (default +/- 30,000ms).
pub fn calculate_temporal_drift(
    reference: &[SubtitleCue],
    external: &[SubtitleCue],
    window_ms: i64,
    step_ms: i64,
) -> i64 {
    if reference.is_empty() || external.is_empty() {
        return 0;
    }

    let mut best_offset = 0i64;
    let mut best_score = 0i64;

    let mut candidate_dt = -window_ms;
    while candidate_dt <= window_ms {
        let mut score = 0i64;

        for ext in external {
            let shifted_start = ext.start_ms + candidate_dt;
            let shifted_end = ext.end_ms + candidate_dt;

            if shifted_end <= 0 {
                continue;
            }

            for r in reference {
                if r.start_ms > shifted_end {
                    break;
                }
                if r.end_ms < shifted_start {
                    continue;
                }
                let overlap = (r.end_ms.min(shifted_end) - r.start_ms.max(shifted_start)).max(0);
                score += overlap;
            }
        }

        if score > best_score {
            best_score = score;
            best_offset = candidate_dt;
        }

        candidate_dt += step_ms.max(50);
    }

    best_offset
}

/// Applies a millisecond offset to a list of subtitle cues, ensuring non-negative start/end.
pub fn apply_offset(cues: &[SubtitleCue], offset_ms: i64) -> Vec<SubtitleCue> {
    cues.iter()
        .map(|c| SubtitleCue {
            start_ms: (c.start_ms + offset_ms).max(0),
            end_ms: (c.end_ms + offset_ms).max(0),
            text: c.text.clone(),
        })
        .collect()
}

/// Automatically aligns external WebVTT against a reference WebVTT using cross-correlation.
/// Returns (aligned_vtt_content, calculated_offset_ms).
pub fn align_vtt(reference_vtt: &str, external_vtt: &str, window_ms: i64) -> (String, i64) {
    let ref_cues = parse_vtt_cues(reference_vtt);
    let ext_cues = parse_vtt_cues(external_vtt);

    if ref_cues.is_empty() || ext_cues.is_empty() {
        return (external_vtt.to_string(), 0);
    }

    let drift = calculate_temporal_drift(&ref_cues, &ext_cues, window_ms, 200);
    let aligned = apply_offset(&ext_cues, drift);
    (cues_to_vtt(&aligned), drift)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_drift_calculation() {
        // Reference cues at 5000-8000 and 10000-14000
        let ref_cues = vec![
            SubtitleCue { start_ms: 5000, end_ms: 8000, text: "Ref 1".into() },
            SubtitleCue { start_ms: 10000, end_ms: 14000, text: "Ref 2".into() },
        ];
        // External cues are delayed by +2000ms (7000-10000 and 12000-16000)
        let ext_cues = vec![
            SubtitleCue { start_ms: 7000, end_ms: 10000, text: "Ext 1".into() },
            SubtitleCue { start_ms: 12000, end_ms: 16000, text: "Ext 2".into() },
        ];

        // Optimal drift to align ext to ref should be -2000ms
        let drift = calculate_temporal_drift(&ref_cues, &ext_cues, 10000, 200);
        assert_eq!(drift, -2000);

        let aligned = apply_offset(&ext_cues, drift);
        assert_eq!(aligned[0].start_ms, 5000);
        assert_eq!(aligned[0].end_ms, 8000);
    }
}
