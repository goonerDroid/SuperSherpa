mod model;
mod notifications;
mod partial_updates;
mod recording;
mod state;
mod text_processing;

use std::path::PathBuf;

use jni::objects::{JClass, JObject, JShortArray, JString};
use jni::JNIEnv;

use crate::TranscriptionEngine;

use model::{do_load, get_engine, is_engine_loaded, reset_loaded_engine_state};
use partial_updates::stop_partial_updates;
use recording::{voice_cancel_recording, voice_start_recording, voice_stop_recording};
use state::{GLOBAL_CONTEXT, MODEL_DIRECTORY_OVERRIDE, VOICE_SESSION};

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
    recording::init_voice_session(env, activity)
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
