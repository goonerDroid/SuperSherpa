use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use jni::objects::{GlobalRef, JClass, JObject, JShortArray, JString};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::engines::parakeet::ParakeetEngine;
use crate::TranscriptionEngine;

static GLOBAL_ENGINE: Lazy<Mutex<Option<Arc<Mutex<ParakeetEngine>>>>> =
    Lazy::new(|| Mutex::new(None));
static GLOBAL_CONTEXT: Lazy<Mutex<Option<GlobalRef>>> = Lazy::new(|| Mutex::new(None));
static MODEL_DIRECTORY_OVERRIDE: Lazy<Mutex<Option<PathBuf>>> = Lazy::new(|| Mutex::new(None));
static VOICE_SESSION: Lazy<Mutex<Option<VoiceSessionState>>> = Lazy::new(|| Mutex::new(None));
static LOAD_STATE: Lazy<(Mutex<LoadState>, Condvar)> =
    Lazy::new(|| (Mutex::new(LoadState::Idle), Condvar::new()));

const MODEL_DIR_NAME: &str = "parakeet-tdt-0.6b-v3-int8";
const PARTIAL_WARMUP_MILLIS: u64 = 300;
const PARTIAL_INTERVAL_MILLIS: u64 = 250;
const PARTIAL_MIN_AUDIO_SAMPLES: usize = 3_200;
const PARTIAL_MIN_NEW_SAMPLES: usize = 1_600;
const PARTIAL_MAX_WINDOW_SAMPLES: usize = 16_000 * 8;
const REQUIRED_MODEL_FILES: [&str; 4] = [
    "vocab.txt",
    "encoder-model.int8.onnx",
    "decoder_joint-model.int8.onnx",
    "nemo128.onnx",
];

#[derive(Debug, Clone, PartialEq)]
enum LoadState {
    Idle,
    Loading,
    Done,
    Failed(String),
}

struct SendStream(#[allow(dead_code)] cpal::Stream);

unsafe impl Send for SendStream {}
unsafe impl Sync for SendStream {}

struct VoiceSessionState {
    stream: Option<SendStream>,
    audio_buffer: Arc<Mutex<Vec<f32>>>,
    jvm: Arc<jni::JavaVM>,
    target_ref: GlobalRef,
    last_level_sent: Arc<Mutex<std::time::Instant>>,
    partial_stop_flag: Arc<AtomicBool>,
    partial_session_id: Arc<AtomicU64>,
    last_partial_emitted: Arc<Mutex<String>>,
}

fn notify_status(env: &mut JNIEnv, obj: &jni::objects::JObject, msg: &str) {
    if let Ok(jmsg) = env.new_string(msg) {
        let _ = env.call_method(
            obj,
            "onStatusUpdate",
            "(Ljava/lang/String;)V",
            &[(&jmsg).into()],
        );
    }
}

fn get_engine() -> Option<Arc<Mutex<ParakeetEngine>>> {
    GLOBAL_ENGINE.lock().unwrap().clone()
}

fn is_engine_loaded() -> bool {
    GLOBAL_ENGINE.lock().unwrap().is_some()
}

fn reset_loaded_engine_state() {
    *GLOBAL_ENGINE.lock().unwrap() = None;
    let (lock, cvar) = &*LOAD_STATE;
    *lock.lock().unwrap() = LoadState::Idle;
    cvar.notify_all();
}

fn ensure_loaded_from_thread(jvm: &Arc<jni::JavaVM>, target_ref: &GlobalRef) -> Result<(), String> {
    if is_engine_loaded() {
        if let Ok(mut env) = jvm.attach_current_thread() {
            notify_status(&mut env, target_ref.as_obj(), "Ready");
        }
        return Ok(());
    }

    let (lock, cvar) = &*LOAD_STATE;
    let mut state = lock.lock().unwrap();

    if is_engine_loaded() {
        if let Ok(mut env) = jvm.attach_current_thread() {
            notify_status(&mut env, target_ref.as_obj(), "Ready");
        }
        return Ok(());
    }

    match &*state {
        LoadState::Loading => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                notify_status(&mut env, target_ref.as_obj(), "Waiting for model...");
            }
            while *state == LoadState::Loading {
                state = cvar.wait(state).unwrap();
            }
            drop(state);

            if is_engine_loaded() {
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_status(&mut env, target_ref.as_obj(), "Ready");
                }
                Ok(())
            } else {
                let msg = "Model failed to load".to_string();
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_status(&mut env, target_ref.as_obj(), &format!("Error: {}", msg));
                }
                Err(msg)
            }
        }
        LoadState::Done => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                notify_status(&mut env, target_ref.as_obj(), "Ready");
            }
            Ok(())
        }
        LoadState::Idle | LoadState::Failed(_) => {
            *state = LoadState::Loading;
            drop(state);

            let result = if let Ok(mut env) = jvm.attach_current_thread() {
                do_load(&mut env, target_ref.as_obj())
            } else {
                Err("Failed to attach JNI thread".to_string())
            };

            let mut state = lock.lock().unwrap();
            match &result {
                Ok(()) => *state = LoadState::Done,
                Err(msg) => *state = LoadState::Failed(msg.clone()),
            }
            cvar.notify_all();
            result
        }
    }
}

fn do_load(env: &mut JNIEnv, context: &jni::objects::JObject) -> Result<(), String> {
    let path = resolve_model_dir(env, context).map_err(|e| {
        let msg = format!("Asset error: {}", e);
        notify_status(env, context, &format!("Error: {}", msg));
        msg
    })?;

    notify_status(env, context, "Loading model...");
    let mut eng = ParakeetEngine::new();
    match eng.load_model_with_params(&path, crate::engines::parakeet::ParakeetModelParams::int8()) {
        Ok(_) => {
            *GLOBAL_ENGINE.lock().unwrap() = Some(Arc::new(Mutex::new(eng)));
            notify_status(env, context, "Ready");
            Ok(())
        }
        Err(e) => {
            let marker = path.join(".extraction_complete");
            if marker.exists() {
                let _ = std::fs::remove_file(&marker);
            }
            let msg = format!("Model error: {}", e);
            notify_status(env, context, &format!("Error: {}", msg));
            Err(msg)
        }
    }
}

fn resolve_model_dir(env: &mut JNIEnv, context: &jni::objects::JObject) -> anyhow::Result<PathBuf> {
    if let Some(model_dir) = MODEL_DIRECTORY_OVERRIDE.lock().unwrap().clone() {
        if has_complete_model(&model_dir) {
            return Ok(model_dir);
        }
        log::warn!(
            "Ignoring configured model directory because it is incomplete: {}",
            model_dir.display()
        );
    }

    notify_status(env, context, "Checking assets...");
    extract_assets(env, context)
}

fn has_complete_model(model_dir: &PathBuf) -> bool {
    REQUIRED_MODEL_FILES
        .iter()
        .all(|file_name| model_dir.join(file_name).is_file())
}

fn extract_assets(env: &mut JNIEnv, context: &jni::objects::JObject) -> anyhow::Result<PathBuf> {
    let files_dir_obj = env
        .call_method(context, "getFilesDir", "()Ljava/io/File;", &[])?
        .l()?;
    let path_str_obj = env
        .call_method(
            &files_dir_obj,
            "getAbsolutePath",
            "()Ljava/lang/String;",
            &[],
        )?
        .l()?;
    let path_string: String = env.get_string(&path_str_obj.into())?.into();

    let base_path = PathBuf::from(path_string);
    let model_dir = base_path.join(MODEL_DIR_NAME);
    let marker_file = model_dir.join(".extraction_complete");
    if marker_file.exists() {
        return Ok(model_dir);
    }

    if model_dir.exists() {
        let _ = std::fs::remove_dir_all(&model_dir);
    }

    std::fs::create_dir_all(&model_dir)?;

    let asset_manager_obj = env
        .call_method(
            context,
            "getAssets",
            "()Landroid/content/res/AssetManager;",
            &[],
        )?
        .l()?;
    copy_assets_recursively(env, &asset_manager_obj, MODEL_DIR_NAME, &base_path)?;
    std::fs::write(&marker_file, "ok")?;
    Ok(model_dir)
}

fn copy_assets_recursively(
    env: &mut JNIEnv,
    asset_manager: &jni::objects::JObject,
    path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    use jni::objects::JObjectArray;

    let path_jstring = env.new_string(path)?;
    let list_array_obj = env
        .call_method(
            asset_manager,
            "list",
            "(Ljava/lang/String;)[Ljava/lang/String;",
            &[(&path_jstring).into()],
        )?
        .l()?;

    let list_array: JObjectArray = list_array_obj.into();
    let len = env.get_array_length(&list_array)?;

    if len == 0 {
        return copy_asset_file(env, asset_manager, path, target_root);
    }

    let target_dir = target_root.join(path);
    std::fs::create_dir_all(&target_dir)?;

    for i in 0..len {
        let file_name_obj = env.get_object_array_element(&list_array, i)?;
        let file_name: String = env.get_string(&file_name_obj.into())?.into();
        let child_path = if path.is_empty() {
            file_name
        } else {
            format!("{}/{}", path, file_name)
        };
        copy_assets_recursively(env, asset_manager, &child_path, target_root)?;
    }
    Ok(())
}

fn copy_asset_file(
    env: &mut JNIEnv,
    asset_manager: &jni::objects::JObject,
    asset_path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    let path_jstring = env.new_string(asset_path)?;
    let result = env.call_method(
        asset_manager,
        "open",
        "(Ljava/lang/String;)Ljava/io/InputStream;",
        &[(&path_jstring).into()],
    );

    match result {
        Ok(stream_val) => {
            let stream_obj = stream_val.l()?;
            let target_file_path = target_root.join(asset_path);
            let mut file = std::fs::File::create(&target_file_path)?;
            let mut buffer = [0u8; 8192];
            let buffer_j = env.new_byte_array(8192)?;

            loop {
                let bytes_read = env
                    .call_method(&stream_obj, "read", "([B)I", &[(&buffer_j).into()])?
                    .i()?;
                if bytes_read == -1 {
                    break;
                }
                let bytes_read_usize = bytes_read as usize;
                let buffer_slice = unsafe {
                    std::slice::from_raw_parts_mut(buffer.as_mut_ptr() as *mut i8, bytes_read_usize)
                };
                env.get_byte_array_region(&buffer_j, 0, buffer_slice)?;
                use std::io::Write;
                file.write_all(&buffer[0..bytes_read_usize])?;
            }

            env.call_method(&stream_obj, "close", "()V", &[])?;
            Ok(())
        }
        Err(_) => Ok(()),
    }
}

fn init_native(
    env: JNIEnv,
    activity: JObject,
    model_dir_override: Option<String>,
) -> Result<(), String> {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    let _ = ort::init().commit();
    let next_model_dir_override = model_dir_override
        .map(|path| path.trim().to_string())
        .filter(|path| !path.is_empty())
        .map(PathBuf::from);
    let mut current_model_dir_override = MODEL_DIRECTORY_OVERRIDE.lock().unwrap();
    if *current_model_dir_override != next_model_dir_override {
        *current_model_dir_override = next_model_dir_override;
        drop(current_model_dir_override);
        reset_loaded_engine_state();
    } else {
        *current_model_dir_override = next_model_dir_override;
    }
    init_voice_session(env, activity)
}

fn transcribe_audio(
    env: &mut JNIEnv,
    samples_array: JShortArray,
    length: i32,
) -> Result<String, String> {
    if length <= 0 {
        return Err("No audio data to transcribe".to_string());
    }

    if get_engine().is_none() {
        let context_ref = GLOBAL_CONTEXT
            .lock()
            .unwrap()
            .clone()
            .ok_or_else(|| "Rust bridge has not been initialized.".to_string())?;
        if !is_engine_loaded() {
            do_load(env, context_ref.as_obj())?;
        }
    }

    let mut buffer = vec![0i16; length as usize];
    env.get_short_array_region(&samples_array, 0, &mut buffer)
        .map_err(|e| e.to_string())?;
    let samples: Vec<f32> = buffer
        .into_iter()
        .map(|s| s as f32 / i16::MAX as f32)
        .collect();

    let engine = get_engine().ok_or_else(|| "Model not loaded".to_string())?;
    let result = {
        let mut eng = engine.lock().unwrap();
        eng.transcribe_samples(samples, None)
            .map_err(|e| e.to_string())?
    };
    Ok(result.text)
}

fn init_voice_session(env: JNIEnv, activity: JObject) -> Result<(), String> {
    let vm = env.get_java_vm().map_err(|e| e.to_string())?;
    let vm_arc = Arc::new(vm);
    let target_ref = env.new_global_ref(&activity).map_err(|e| e.to_string())?;

    let state = VoiceSessionState {
        stream: None,
        audio_buffer: Arc::new(Mutex::new(Vec::new())),
        jvm: vm_arc.clone(),
        target_ref: target_ref.clone(),
        last_level_sent: Arc::new(Mutex::new(std::time::Instant::now())),
        partial_stop_flag: Arc::new(AtomicBool::new(true)),
        partial_session_id: Arc::new(AtomicU64::new(0)),
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

fn notify_level(env: &mut JNIEnv, obj: &JObject, level: f32) {
    let _ = env.call_method(obj, "onAudioLevel", "(F)V", &[level.into()]);
}

fn notify_text(env: &mut JNIEnv, obj: &JObject, text: &str) {
    if let Ok(jtxt) = env.new_string(text) {
        let _ = env.call_method(
            obj,
            "onTextTranscribed",
            "(Ljava/lang/String;)V",
            &[(&jtxt).into()],
        );
    }
}

fn notify_partial_text(env: &mut JNIEnv, obj: &JObject, text: &str) {
    if let Ok(jtxt) = env.new_string(text) {
        let _ = env.call_method(
            obj,
            "onPartialTextTranscribed",
            "(Ljava/lang/String;)V",
            &[(&jtxt).into()],
        );
    }
}

fn char_count(text: &str) -> usize {
    text.chars().count()
}

fn prefix_chars(text: &str, count: usize) -> String {
    text.chars().take(count).collect()
}

fn suffix_chars(text: &str, count: usize) -> String {
    let total = char_count(text);
    text.chars().skip(total.saturating_sub(count)).collect()
}

fn drop_prefix_chars(text: &str, count: usize) -> String {
    text.chars().skip(count).collect()
}

fn merge_partial_transcript(previous: &str, candidate: &str) -> String {
    let previous_trimmed = previous.trim();
    let candidate_trimmed = candidate.trim();

    if candidate_trimmed.is_empty() {
        return previous_trimmed.to_string();
    }

    if previous_trimmed.is_empty() {
        return candidate_trimmed.to_string();
    }

    if candidate_trimmed == previous_trimmed {
        return previous_trimmed.to_string();
    }

    if candidate_trimmed.starts_with(previous_trimmed) {
        return candidate_trimmed.to_string();
    }

    let previous_len = char_count(previous_trimmed);
    let candidate_len = char_count(candidate_trimmed);

    if previous_trimmed.starts_with(candidate_trimmed) && candidate_len + 6 < previous_len {
        return previous_trimmed.to_string();
    }

    let max_overlap = previous_len.min(candidate_len);
    for overlap_len in (3..=max_overlap).rev() {
        let previous_suffix = suffix_chars(previous_trimmed, overlap_len);
        let candidate_prefix = prefix_chars(candidate_trimmed, overlap_len);
        if previous_suffix.eq_ignore_ascii_case(candidate_prefix.as_str()) {
            let candidate_suffix = drop_prefix_chars(candidate_trimmed, overlap_len);
            return format!("{previous_trimmed}{candidate_suffix}")
                .trim()
                .to_string();
        }
    }

    if candidate_len + 2 >= previous_len {
        return candidate_trimmed.to_string();
    }

    previous_trimmed.to_string()
}

fn partial_window_samples(samples: &[f32]) -> Vec<f32> {
    if samples.len() <= PARTIAL_MAX_WINDOW_SAMPLES {
        return samples.to_vec();
    }

    samples[samples.len() - PARTIAL_MAX_WINDOW_SAMPLES..].to_vec()
}

fn stop_partial_updates(state: &VoiceSessionState) {
    state.partial_stop_flag.store(true, Ordering::Release);
    let _ = state.partial_session_id.fetch_add(1, Ordering::AcqRel);
    state.last_partial_emitted.lock().unwrap().clear();
}

fn start_partial_updates(state: &VoiceSessionState) {
    state.partial_stop_flag.store(false, Ordering::Release);
    let session_id = state.partial_session_id.fetch_add(1, Ordering::AcqRel) + 1;
    state.last_partial_emitted.lock().unwrap().clear();

    let partial_stop_flag = state.partial_stop_flag.clone();
    let partial_session_id = state.partial_session_id.clone();
    let audio_buffer = state.audio_buffer.clone();
    let jvm = state.jvm.clone();
    let target_ref = state.target_ref.clone();
    let last_partial_emitted = state.last_partial_emitted.clone();

    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(PARTIAL_WARMUP_MILLIS));
        let mut last_snapshot_len: usize = 0;

        loop {
            let is_active = !partial_stop_flag.load(Ordering::Acquire)
                && partial_session_id.load(Ordering::Acquire) == session_id;
            if !is_active {
                break;
            }

            let (total_samples, samples_snapshot) = {
                let buffer = audio_buffer.lock().unwrap();
                let total = buffer.len();
                if total < PARTIAL_MIN_AUDIO_SAMPLES {
                    (total, Vec::new())
                } else {
                    (total, partial_window_samples(buffer.as_slice()))
                }
            };

            if total_samples >= PARTIAL_MIN_AUDIO_SAMPLES {
                let new_samples = total_samples.saturating_sub(last_snapshot_len);
                if last_snapshot_len > 0 && new_samples < PARTIAL_MIN_NEW_SAMPLES {
                    std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
                    continue;
                }

                if let Some(engine) = get_engine() {
                    let result = if let Ok(mut eng) = engine.try_lock() {
                        last_snapshot_len = total_samples;
                        eng.transcribe_samples(samples_snapshot, None)
                    } else {
                        std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
                        continue;
                    };

                    let still_active = !partial_stop_flag.load(Ordering::Acquire)
                        && partial_session_id.load(Ordering::Acquire) == session_id;
                    if !still_active {
                        break;
                    }

                    if let Ok(transcription) = result {
                        let cleaned_text = transcription.text.trim().to_string();
                        let merged_text = {
                            let mut previous = last_partial_emitted.lock().unwrap();
                            let merged =
                                merge_partial_transcript(previous.as_str(), cleaned_text.as_str());
                            if merged.is_empty() || merged == previous.as_str() {
                                None
                            } else {
                                *previous = merged.clone();
                                Some(merged)
                            }
                        };

                        if let Some(merged) = merged_text {
                            if let Ok(mut env) = jvm.attach_current_thread() {
                                notify_partial_text(&mut env, target_ref.as_obj(), merged.as_str());
                            }
                        }
                    }
                }
            }

            std::thread::sleep(Duration::from_millis(PARTIAL_INTERVAL_MILLIS));
        }
    });
}

#[cfg(test)]
mod tests {
    use super::{merge_partial_transcript, partial_window_samples, PARTIAL_MAX_WINDOW_SAMPLES};

    #[test]
    fn merge_partial_extension_uses_candidate() {
        let merged = merge_partial_transcript("hello", "hello world");
        assert_eq!(merged, "hello world");
    }

    #[test]
    fn merge_partial_overlap_stitches_text() {
        let merged = merge_partial_transcript("hello wor", "world today");
        assert_eq!(merged, "hello world today");
    }

    #[test]
    fn merge_partial_accepts_similar_length_correction() {
        let merged = merge_partial_transcript("hello wrld", "hello world");
        assert_eq!(merged, "hello world");
    }

    #[test]
    fn merge_partial_ignores_short_regression_chunk() {
        let merged = merge_partial_transcript("hello world from android", "hello world");
        assert_eq!(merged, "hello world from android");
    }

    #[test]
    fn merge_partial_ignores_blank_candidate() {
        let merged = merge_partial_transcript("hello world", "   ");
        assert_eq!(merged, "hello world");
    }

    #[test]
    fn partial_window_keeps_full_when_under_limit() {
        let samples = vec![0.0f32; PARTIAL_MAX_WINDOW_SAMPLES - 10];
        let window = partial_window_samples(samples.as_slice());
        assert_eq!(window.len(), samples.len());
    }

    #[test]
    fn partial_window_trims_to_recent_tail() {
        let samples: Vec<f32> = (0..(PARTIAL_MAX_WINDOW_SAMPLES + 50))
            .map(|index| index as f32)
            .collect();
        let window = partial_window_samples(samples.as_slice());
        assert_eq!(window.len(), PARTIAL_MAX_WINDOW_SAMPLES);
        assert_eq!(window[0], 50f32);
    }
}

fn voice_start_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
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
    let buffer_clone = state.audio_buffer.clone();
    let jvm = state.jvm.clone();
    let target_ref = state.target_ref.clone();
    let last_sent = state.last_level_sent.clone();

    let stream = device.build_input_stream(
        &config,
        move |data: &[f32], _: &_| {
            buffer_clone.lock().unwrap().extend_from_slice(data);

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

fn voice_stop_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
    stop_partial_updates(state);
    state.stream = None;

    let buffer = state.audio_buffer.lock().unwrap().clone();
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

        let engine = match get_engine() {
            Some(engine) => engine,
            None => {
                notify_status(&mut env, obj, "Error: model not loaded");
                return;
            }
        };

        let samples = buffer;
        let result = {
            let mut eng = engine.lock().unwrap();
            eng.transcribe_samples(samples, None)
        };

        match result {
            Ok(transcription) => {
                notify_status(&mut env, obj, "Ready");
                notify_text(&mut env, obj, &transcription.text);
            }
            Err(err) => notify_status(&mut env, obj, &format!("Error: {}", err)),
        }
    });
}

fn voice_cancel_recording(mut env: JNIEnv, state: &mut VoiceSessionState) {
    stop_partial_updates(state);
    state.stream = None;
    state.audio_buffer.lock().unwrap().clear();
    notify_status(&mut env, state.target_ref.as_obj(), "Canceled");
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_initNative(
    mut env: JNIEnv,
    _class: JClass,
    activity: JObject,
    model_dir: JString,
) {
    let model_dir_override = if model_dir.is_null() {
        None
    } else {
        env.get_string(&model_dir).ok().map(Into::into)
    };
    let _ = init_native(env, activity, model_dir_override);
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_cleanupNative(
    env: JNIEnv,
    _class: JClass,
    target: JObject,
) {
    let should_cleanup = GLOBAL_CONTEXT
        .lock()
        .unwrap()
        .as_ref()
        .map(|current_target| {
            env.is_same_object(current_target.as_obj(), &target)
                .unwrap_or(false)
        })
        .unwrap_or(true);

    if should_cleanup {
        reset_loaded_engine_state();
        let mut guard = VOICE_SESSION.lock().unwrap();
        if let Some(state) = guard.as_ref() {
            stop_partial_updates(state);
        }
        *guard = None;
        *GLOBAL_CONTEXT.lock().unwrap() = None;
        *MODEL_DIRECTORY_OVERRIDE.lock().unwrap() = None;
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_startRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = VOICE_SESSION.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        voice_start_recording(env, state);
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_stopRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = VOICE_SESSION.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        voice_stop_recording(env, state);
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_cancelRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = VOICE_SESSION.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        voice_cancel_recording(env, state);
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_transcribeAudio(
    env: JNIEnv,
    _class: JClass,
    samples_array: JShortArray,
    length: i32,
) -> jni::sys::jstring {
    let mut env = env;
    let result = transcribe_audio(&mut env, samples_array, length);
    match result {
        Ok(text) => match env.new_string(text) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(err) => match env.new_string(format!("Error: {}", err)) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
    }
}
