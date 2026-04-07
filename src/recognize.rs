use std::sync::Mutex;

use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::voice_session::{self, VoiceSessionState};

static RECOG_STATE: Lazy<Mutex<Option<VoiceSessionState>>> = Lazy::new(|| Mutex::new(None));

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_initNative(
    env: JNIEnv,
    _class: JClass,
    activity: JObject,
) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );

    let _ = ort::init().commit();

    let state = voice_session::init_session(env, activity);
    *RECOG_STATE.lock().unwrap() = Some(state);
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_cleanupNative(
    _env: JNIEnv,
    _class: JClass,
) {
    *RECOG_STATE.lock().unwrap() = None;
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_startRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = RECOG_STATE.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        voice_session::start_recording(env, state);
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_stopRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = RECOG_STATE.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        voice_session::stop_recording(env, state);
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_sublime_supersherpa_core_rust_RustTranscriptionBridge_cancelRecording(
    env: JNIEnv,
    _class: JClass,
) {
    let mut guard = RECOG_STATE.lock().unwrap();
    if let Some(state) = guard.as_mut() {
        crate::voice_session::cancel_recording(env, state);
    }
}
