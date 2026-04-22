use std::collections::VecDeque;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64};
use std::sync::{Arc, Condvar, Mutex};

use jni::objects::GlobalRef;
use once_cell::sync::Lazy;

use crate::engines::parakeet::ParakeetEngine;

pub(super) static GLOBAL_ENGINE: Lazy<Mutex<Option<Arc<Mutex<ParakeetEngine>>>>> =
    Lazy::new(|| Mutex::new(None));
pub(super) static GLOBAL_CONTEXT: Lazy<Mutex<Option<GlobalRef>>> = Lazy::new(|| Mutex::new(None));
pub(super) static MODEL_DIRECTORY_OVERRIDE: Lazy<Mutex<Option<PathBuf>>> =
    Lazy::new(|| Mutex::new(None));
pub(super) static VOICE_SESSION: Lazy<Mutex<Option<VoiceSessionState>>> =
    Lazy::new(|| Mutex::new(None));
pub(super) static LOAD_STATE: Lazy<(Mutex<LoadState>, Condvar)> =
    Lazy::new(|| (Mutex::new(LoadState::Idle), Condvar::new()));

pub(super) const MODEL_DIR_NAME: &str = "parakeet-tdt-0.6b-v3-int8";
pub(super) const PARTIAL_WARMUP_MILLIS: u64 = 300;
pub(super) const PARTIAL_INTERVAL_MILLIS: u64 = 750;
pub(super) const PARTIAL_MIN_AUDIO_SAMPLES: usize = 3_200;
pub(super) const PARTIAL_MAX_WINDOW_SAMPLES: usize = 16_000 * 8;
pub(super) const PARTIAL_MIN_RMS: f32 = 0.003;
pub(super) const PARTIAL_COMMIT_CHUNK_SAMPLES: usize = 16_000 * 8;
pub(super) const PARTIAL_COMMIT_OVERLAP_SAMPLES: usize = 16_000;
pub(super) const PARTIAL_COMMIT_STEP_SAMPLES: usize =
    PARTIAL_COMMIT_CHUNK_SAMPLES - PARTIAL_COMMIT_OVERLAP_SAMPLES;
pub(super) const MERGE_MAX_OVERLAP_WORDS: usize = 12;
pub(super) const VERIFY_CHAR_DISTANCE_THRESHOLD: f32 = 0.06;
pub(super) const VERIFY_WORD_DELTA_THRESHOLD: usize = 3;
pub(super) const MAX_CONSECUTIVE_WORD_REPETITIONS: usize = 1;
pub(super) const REQUIRED_MODEL_FILES: [&str; 4] = [
    "vocab.txt",
    "encoder-model.int8.onnx",
    "decoder_joint-model.int8.onnx",
    "nemo128.onnx",
];

#[derive(Debug, Clone, PartialEq)]
pub(super) enum LoadState {
    Idle,
    Loading,
    Done,
    Failed(String),
}

pub(super) struct SendStream(#[allow(dead_code)] pub(super) cpal::Stream);

unsafe impl Send for SendStream {}
unsafe impl Sync for SendStream {}

pub(super) struct VoiceSessionState {
    pub(super) stream: Option<SendStream>,
    pub(super) audio_buffer: Arc<Mutex<Vec<f32>>>,
    pub(super) partial_audio_window: Arc<Mutex<VecDeque<f32>>>,
    pub(super) jvm: Arc<jni::JavaVM>,
    pub(super) target_ref: GlobalRef,
    pub(super) last_level_sent: Arc<Mutex<std::time::Instant>>,
    pub(super) partial_stop_flag: Arc<AtomicBool>,
    pub(super) partial_session_id: Arc<AtomicU64>,
    pub(super) partial_samples_written: Arc<AtomicU64>,
    pub(super) committed_samples: Arc<AtomicU64>,
    pub(super) confirmed_transcript: Arc<Mutex<String>>,
    pub(super) last_partial_emitted: Arc<Mutex<String>>,
}
