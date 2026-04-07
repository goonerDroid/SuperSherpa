package com.sublime.supersherpa.core.rust

import android.app.Activity

/**
 * Thin JNI bridge to the Rust native prototype.
 *
 * The Rust library owns mic capture and transcription. The Kotlin side keeps
 * the UI in MVVM form and forwards lifecycle/user events into native code.
 */
class RustTranscriptionBridge {
    init {
        System.loadLibrary("transcribe_rs")
    }

    external fun initNative(activity: Activity)

    external fun cleanupNative()

    external fun startRecording()

    external fun stopRecording()

    external fun cancelRecording()

    external fun transcribeAudio(samples: ShortArray, length: Int): String
}
