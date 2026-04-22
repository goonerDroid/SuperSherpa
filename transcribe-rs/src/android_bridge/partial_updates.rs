use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::Duration;

use crate::TranscriptionEngine;

use super::model::get_engine;
use super::notifications::notify_partial_text;
use super::state::{
    VoiceSessionState, PARTIAL_COMMIT_CHUNK_SAMPLES, PARTIAL_COMMIT_OVERLAP_SAMPLES,
    PARTIAL_COMMIT_STEP_SAMPLES, PARTIAL_INTERVAL_MILLIS, PARTIAL_MIN_AUDIO_SAMPLES,
    PARTIAL_MIN_RMS, PARTIAL_WARMUP_MILLIS,
};
use super::text_processing::{merge_transcripts, root_mean_square, squash_repeated_words};

fn is_active_session(
    partial_stop_flag: &AtomicBool,
    partial_session_id: &AtomicU64,
    session_id: u64,
) -> bool {
    !partial_stop_flag.load(Ordering::Acquire)
        && partial_session_id.load(Ordering::Acquire) == session_id
}

pub(super) fn stop_partial_updates(state: &VoiceSessionState) {
    state.partial_stop_flag.store(true, Ordering::Release);
    let _ = state.partial_session_id.fetch_add(1, Ordering::AcqRel);
    state.last_partial_emitted.lock().unwrap().clear();
}

pub(super) fn start_partial_updates(state: &VoiceSessionState) {
    state.partial_stop_flag.store(false, Ordering::Release);
    let session_id = state.partial_session_id.fetch_add(1, Ordering::AcqRel) + 1;
    state.last_partial_emitted.lock().unwrap().clear();

    let partial_stop_flag = state.partial_stop_flag.clone();
    let partial_session_id = state.partial_session_id.clone();
    let audio_buffer = state.audio_buffer.clone();
    let partial_audio_window = state.partial_audio_window.clone();
    let partial_samples_written = state.partial_samples_written.clone();
    let jvm = state.jvm.clone();
    let target_ref = state.target_ref.clone();
    let last_partial_emitted = state.last_partial_emitted.clone();
    let committed_samples = state.committed_samples.clone();
    let confirmed_transcript = state.confirmed_transcript.clone();

    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(PARTIAL_WARMUP_MILLIS));
        let mut next_chunk_start: usize = 0;

        loop {
            if !is_active_session(
                partial_stop_flag.as_ref(),
                partial_session_id.as_ref(),
                session_id,
            ) {
                break;
            }

            let total_samples_written = partial_samples_written.load(Ordering::Acquire) as usize;

            while total_samples_written >= next_chunk_start + PARTIAL_COMMIT_CHUNK_SAMPLES {
                let chunk_end = next_chunk_start + PARTIAL_COMMIT_CHUNK_SAMPLES;
                let chunk = {
                    let buffer = audio_buffer.lock().unwrap();
                    if buffer.len() < chunk_end {
                        break;
                    }
                    buffer[next_chunk_start..chunk_end].to_vec()
                };

                let mut chunk_committed = false;
                if let Some(engine) = get_engine() {
                    let result = if let Ok(mut eng) = engine.try_lock() {
                        eng.transcribe_samples(chunk, None)
                    } else {
                        break;
                    };

                    if !is_active_session(
                        partial_stop_flag.as_ref(),
                        partial_session_id.as_ref(),
                        session_id,
                    ) {
                        return;
                    }

                    if let Ok(transcription) = result {
                        let cleaned_text = squash_repeated_words(transcription.text.trim());
                        if !cleaned_text.is_empty() {
                            let mut confirmed = confirmed_transcript.lock().unwrap();
                            let merged =
                                merge_transcripts(confirmed.as_str(), cleaned_text.as_str());
                            *confirmed = merged;
                        }
                        chunk_committed = true;
                    }
                }

                if !chunk_committed {
                    // Do not advance the committed cursor when chunk transcription
                    // did not complete; otherwise stop() may only process the tail.
                    break;
                }
                next_chunk_start += PARTIAL_COMMIT_STEP_SAMPLES;
                let unique_committed =
                    (next_chunk_start + PARTIAL_COMMIT_OVERLAP_SAMPLES).min(total_samples_written);
                committed_samples.store(unique_committed as u64, Ordering::Release);
                last_partial_emitted.lock().unwrap().clear();
            }

            if total_samples_written >= PARTIAL_MIN_AUDIO_SAMPLES {
                let recent_samples = {
                    partial_audio_window
                        .lock()
                        .unwrap()
                        .iter()
                        .copied()
                        .collect::<Vec<f32>>()
                };

                if root_mean_square(&recent_samples) < PARTIAL_MIN_RMS {
                    last_partial_emitted.lock().unwrap().clear();
                    std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
                    continue;
                }

                let committed = committed_samples.load(Ordering::Acquire) as usize;
                let partial_start = committed.saturating_sub(PARTIAL_COMMIT_OVERLAP_SAMPLES);
                let partial_samples = {
                    let buffer = audio_buffer.lock().unwrap();
                    if buffer.len() <= partial_start {
                        Vec::new()
                    } else {
                        buffer[partial_start..].to_vec()
                    }
                };

                if partial_samples.len() < PARTIAL_MIN_AUDIO_SAMPLES {
                    std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
                    continue;
                }

                if let Some(engine) = get_engine() {
                    let result = if let Ok(mut eng) = engine.try_lock() {
                        eng.transcribe_samples(partial_samples, None)
                    } else {
                        std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
                        continue;
                    };

                    if !is_active_session(
                        partial_stop_flag.as_ref(),
                        partial_session_id.as_ref(),
                        session_id,
                    ) {
                        break;
                    }

                    if let Ok(transcription) = result {
                        let tail_text = squash_repeated_words(transcription.text.trim());
                        let confirmed_snapshot = confirmed_transcript.lock().unwrap().clone();
                        let merged_text = squash_repeated_words(
                            merge_transcripts(confirmed_snapshot.as_str(), tail_text.as_str())
                                .as_str(),
                        );
                        let partial_text = {
                            let mut previous = last_partial_emitted.lock().unwrap();
                            if merged_text.is_empty() || merged_text == previous.as_str() {
                                None
                            } else {
                                *previous = merged_text.clone();
                                Some(merged_text)
                            }
                        };

                        if let Some(partial) = partial_text {
                            if let Ok(mut env) = jvm.attach_current_thread() {
                                notify_partial_text(
                                    &mut env,
                                    target_ref.as_obj(),
                                    partial.as_str(),
                                );
                            }
                        }
                    }
                }
            }

            std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
        }
    });
}
