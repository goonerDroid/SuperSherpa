package com.sublime.supersherpa.core.rust

import android.content.Context

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

    external fun initNative(context: Context, modelDirectory: String?)

    external fun cleanupNative()

    external fun startRecording()

    external fun stopRecording()

    external fun cancelRecording()

}
