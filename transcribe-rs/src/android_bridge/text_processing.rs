use super::state::{
    MAX_CONSECUTIVE_WORD_REPETITIONS, MERGE_MAX_OVERLAP_WORDS, VERIFY_CHAR_DISTANCE_THRESHOLD,
    VERIFY_WORD_DELTA_THRESHOLD,
};

pub(super) fn root_mean_square(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }

    let sum = samples.iter().map(|sample| sample * sample).sum::<f32>();
    (sum / samples.len() as f32).sqrt()
}

pub(super) fn merge_transcripts(existing: &str, incoming: &str) -> String {
    let existing_trimmed = existing.trim();
    let incoming_trimmed = incoming.trim();

    if existing_trimmed.is_empty() {
        return incoming_trimmed.to_string();
    }
    if incoming_trimmed.is_empty() {
        return existing_trimmed.to_string();
    }

    let existing_words: Vec<&str> = existing_trimmed.split_whitespace().collect();
    let incoming_words: Vec<&str> = incoming_trimmed.split_whitespace().collect();
    let overlap_limit = existing_words
        .len()
        .min(incoming_words.len())
        .min(MERGE_MAX_OVERLAP_WORDS);

    let mut overlap_words = 0usize;
    for candidate in (1..=overlap_limit).rev() {
        let existing_slice = &existing_words[existing_words.len() - candidate..];
        let incoming_slice = &incoming_words[..candidate];
        let matches = existing_slice
            .iter()
            .zip(incoming_slice.iter())
            .all(|(left, right)| left.eq_ignore_ascii_case(right));
        if matches {
            overlap_words = candidate;
            break;
        }
    }

    if overlap_words == 0 {
        return format!("{} {}", existing_trimmed, incoming_trimmed);
    }

    let suffix = incoming_words[overlap_words..].join(" ");
    if suffix.is_empty() {
        existing_trimmed.to_string()
    } else {
        format!("{} {}", existing_trimmed, suffix)
    }
}

fn normalize_token_for_repeat(token: &str) -> String {
    token
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric())
        .map(|ch| ch.to_ascii_lowercase())
        .collect()
}

pub(super) fn squash_repeated_words(input: &str) -> String {
    let mut out_tokens: Vec<&str> = Vec::new();
    let mut last_norm = String::new();
    let mut run_len = 0usize;

    for token in input.split_whitespace() {
        let normalized = normalize_token_for_repeat(token);
        if normalized.is_empty() {
            out_tokens.push(token);
            continue;
        }

        if normalized == last_norm {
            run_len += 1;
        } else {
            last_norm = normalized;
            run_len = 1;
        }

        if run_len <= MAX_CONSECUTIVE_WORD_REPETITIONS {
            out_tokens.push(token);
        }
    }

    out_tokens.join(" ")
}

fn normalize_text_for_diff(input: &str) -> String {
    let mut normalized = String::with_capacity(input.len());
    let mut previous_was_space = true;

    for ch in input.chars() {
        let mapped = if ch.is_ascii_alphanumeric() {
            Some(ch.to_ascii_lowercase())
        } else if ch.is_whitespace() {
            Some(' ')
        } else {
            None
        };

        if let Some(value) = mapped {
            if value == ' ' {
                if !previous_was_space {
                    normalized.push(' ');
                }
                previous_was_space = true;
            } else {
                normalized.push(value);
                previous_was_space = false;
            }
        }
    }

    normalized.trim().to_string()
}

fn levenshtein_distance(left: &str, right: &str) -> usize {
    if left == right {
        return 0;
    }
    if left.is_empty() {
        return right.chars().count();
    }
    if right.is_empty() {
        return left.chars().count();
    }

    let right_chars: Vec<char> = right.chars().collect();
    let left_chars: Vec<char> = left.chars().collect();
    let mut previous: Vec<usize> = (0..=right_chars.len()).collect();
    let mut current = vec![0usize; right_chars.len() + 1];

    for (i, left_char) in left_chars.iter().enumerate() {
        current[0] = i + 1;
        for (j, right_char) in right_chars.iter().enumerate() {
            let substitution_cost = usize::from(left_char != right_char);
            current[j + 1] = (previous[j + 1] + 1)
                .min(current[j] + 1)
                .min(previous[j] + substitution_cost);
        }
        std::mem::swap(&mut previous, &mut current);
    }

    previous[right_chars.len()]
}

pub(super) fn should_run_verification(pass_one_text: &str, preview_text: &str) -> bool {
    let normalized_pass_one = normalize_text_for_diff(pass_one_text);
    let normalized_preview = normalize_text_for_diff(preview_text);
    if normalized_preview.is_empty() {
        return false;
    }

    let max_char_len = normalized_pass_one
        .chars()
        .count()
        .max(normalized_preview.chars().count());
    let char_distance_ratio = if max_char_len == 0 {
        0f32
    } else {
        levenshtein_distance(&normalized_pass_one, &normalized_preview) as f32 / max_char_len as f32
    };

    let pass_one_word_count = normalized_pass_one.split_whitespace().count();
    let preview_word_count = normalized_preview.split_whitespace().count();
    let word_delta = pass_one_word_count.abs_diff(preview_word_count);

    char_distance_ratio >= VERIFY_CHAR_DISTANCE_THRESHOLD
        || word_delta >= VERIFY_WORD_DELTA_THRESHOLD
}
