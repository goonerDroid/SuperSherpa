use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use jni::objects::JObject;
use jni::JNIEnv;

use crate::TranscriptionEngine;

use super::model::{do_load, ensure_loaded_from_thread, get_engine};
use super::notifications::{notify_level, notify_status, notify_text};
use super::partial_updates::{start_partial_updates, stop_partial_updates};
use super::state::{
    SendStream, VoiceSessionState, GLOBAL_CONTEXT, PARTIAL_MAX_WINDOW_SAMPLES, VOICE_SESSION,
};
use super::text_processing::{should_run_verification, squash_repeated_words};

pub(super) fn init_voice_session(env: JNIEnv, activity: JObject) -> Result<(), String> {
    let vm = env.get_java_vm().map_err(|e| e.to_string())?;
    let vm_arc = Arc::new(vm);
    let target_ref = env.new_global_ref(&activity).map_err(|e| e.to_string())?;

    let state = VoiceSessionState {
        stream: None,
        audio_buffer: Arc::new(Mutex::new(Vec::new())),
        partial_audio_window: Arc::new(Mutex::new(VecDeque::new())),
        jvm: vm_arc.clone(),
        target_ref: target_ref.clone(),
        last_level_sent: Arc::new(Mutex::new(std::time::Instant::now())),
        partial_stop_flag: Arc::new(AtomicBool::new(true)),
        partial_session_id: Arc::new(AtomicU64::new(0)),
        partial_samples_written: Arc::new(AtomicU64::new(0)),
        committed_samples: Arc::new(AtomicU64::new(0)),
        confirmed_transcript: Arc::new(Mutex::new(String::new())),
        last_partial_emitted: Arc::new(Mutex::new(String::new())),
    };

    let vm_clone = vm_arc.clone();
    let target_ref_clone = target_ref.clone();
    std::thread::spawn(move || {
        let _ = ensure_loaded_from_thread(&vm_clone, &target_ref_clone);
    });

    *GLOBAL_CONTEXT.lock().unwrap() = Some(target_ref);
    let mut previous_session = VOICE_SESSION.lock().unwrap();
    if let Some(existing) = previous_session.as_ref() {
        stop_partial_updates(existing);
    }
    *previous_session = Some(state);
    Ok(())
}

pub(super) fn voice_start_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
    stop_partial_updates(state);
    let host = cpal::default_host();
    let device = match host.default_input_device() {
        Some(device) => device,
        None => {
            notify_status(
                &mut env,
                state.target_ref.as_obj(),
                "Error: no microphone available. Check permissions.",
            );
            return;
        }
    };

    let config = cpal::StreamConfig {
        channels: 1,
        sample_rate: cpal::SampleRate(16_000),
        buffer_size: cpal::BufferSize::Default,
    };

    state.audio_buffer.lock().unwrap().clear();
    state.partial_audio_window.lock().unwrap().clear();
    state.partial_samples_written.store(0, Ordering::Release);
    state.committed_samples.store(0, Ordering::Release);
    state.confirmed_transcript.lock().unwrap().clear();
    let buffer_clone = state.audio_buffer.clone();
    let partial_window_clone = state.partial_audio_window.clone();
    let partial_samples_written = state.partial_samples_written.clone();
    let jvm = state.jvm.clone();
    let target_ref = state.target_ref.clone();
    let last_sent = state.last_level_sent.clone();

    let stream = device.build_input_stream(
        &config,
        move |data: &[f32], _: &_| {
            buffer_clone.lock().unwrap().extend_from_slice(data);
            if let Ok(mut partial_window) = partial_window_clone.try_lock() {
                partial_window.extend(data.iter().copied());
                let overflow = partial_window
                    .len()
                    .saturating_sub(PARTIAL_MAX_WINDOW_SAMPLES);
                if overflow > 0 {
                    partial_window.drain(..overflow);
                }
                partial_samples_written.fetch_add(data.len() as u64, Ordering::AcqRel);
            }

            let mut sum = 0.0f32;
            for &sample in data {
                sum += sample * sample;
            }
            let rms = if data.is_empty() {
                0.0
            } else {
                (sum / data.len() as f32).sqrt()
            };
            let level = (rms * 6.0).clamp(0.0, 1.0);

            let mut last = last_sent.lock().unwrap();
            if last.elapsed() >= std::time::Duration::from_millis(50) {
                *last = std::time::Instant::now();
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_level(&mut env, target_ref.as_obj(), level);
                }
            }
        },
        |err| log::error!("Stream err: {}", err),
        None,
    );

    match stream {
        Ok(stream) => {
            if stream.play().is_ok() {
                state.stream = Some(SendStream(stream));
                start_partial_updates(state);
                notify_status(&mut env, state.target_ref.as_obj(), "Listening...");
            } else {
                notify_status(
                    &mut env,
                    state.target_ref.as_obj(),
                    "Error: failed to start microphone stream.",
                );
            }
        }
        Err(err) => {
            notify_status(
                &mut env,
                state.target_ref.as_obj(),
                &format!("Error: failed to open microphone: {}", err),
            );
        }
    }
}

pub(super) fn voice_stop_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
    let latest_preview = state.last_partial_emitted.lock().unwrap().clone();
    stop_partial_updates(state);
    let stop_session_id = state.partial_session_id.load(Ordering::Acquire);
    state.stream = None;
    state.partial_audio_window.lock().unwrap().clear();
    state.partial_samples_written.store(0, Ordering::Release);

    let buffer = state.audio_buffer.lock().unwrap().clone();
    state.committed_samples.store(0, Ordering::Release);
    state.confirmed_transcript.lock().unwrap().clear();
    if buffer.is_empty() {
        notify_status(
            &mut env,
            state.target_ref.as_obj(),
            "Error: no audio recorded. Check microphone permissions.",
        );
        return;
    }

    let jvm = state.jvm.clone();
    let target_ref = state.target_ref.clone();
    let partial_session_id = state.partial_session_id.clone();
    let preview_at_stop = latest_preview;
    notify_status(&mut env, target_ref.as_obj(), "Transcribing...");

    std::thread::spawn(move || {
        let mut env = match jvm.attach_current_thread() {
            Ok(env) => env,
            Err(_) => return,
        };
        let obj = target_ref.as_obj();

        if get_engine().is_none() {
            if let Err(err) = GLOBAL_CONTEXT
                .lock()
                .unwrap()
                .clone()
                .ok_or_else(|| "Rust bridge has not been initialized.".to_string())
                .and_then(|context_ref| do_load(&mut env, context_ref.as_obj()))
            {
                notify_status(&mut env, obj, &format!("Error: {}", err));
                return;
            }
        }

        if partial_session_id.load(Ordering::Acquire) != stop_session_id {
            return;
        }

        let engine = match get_engine() {
            Some(engine) => engine,
            None => {
                notify_status(&mut env, obj, "Error: model not loaded");
                return;
            }
        };

        let verification_audio = buffer.clone();
        let final_result = {
            let mut eng = engine.lock().unwrap();
            eng.transcribe_samples(buffer, None)
        };

        match final_result {
            Ok(pass_one_transcription) => {
                if partial_session_id.load(Ordering::Acquire) != stop_session_id {
                    return;
                }

                let mut final_text = squash_repeated_words(pass_one_transcription.text.as_str());
                if should_run_verification(final_text.as_str(), preview_at_stop.as_str()) {
                    let verification_result = {
                        let mut eng = engine.lock().unwrap();
                        eng.transcribe_samples(verification_audio, None)
                    };
                    match verification_result {
                        Ok(verified_transcription) => {
                            final_text =
                                squash_repeated_words(verified_transcription.text.as_str());
                        }
                        Err(_) => {}
                    }
                }

                if partial_session_id.load(Ordering::Acquire) != stop_session_id {
                    return;
                }

                notify_status(&mut env, obj, "Ready");
                notify_text(&mut env, obj, final_text.as_str());
            }
            Err(err) => notify_status(&mut env, obj, &format!("Error: {}", err)),
        }
    });
}

pub(super) fn voice_cancel_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
    stop_partial_updates(state);
    state.stream = None;
    state.audio_buffer.lock().unwrap().clear();
    state.partial_audio_window.lock().unwrap().clear();
    state.partial_samples_written.store(0, Ordering::Release);
    state.committed_samples.store(0, Ordering::Release);
    state.confirmed_transcript.lock().unwrap().clear();
    notify_status(&mut env, state.target_ref.as_obj(), "Canceled");
}
