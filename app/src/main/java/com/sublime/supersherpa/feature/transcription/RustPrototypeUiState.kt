package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.model.VoiceState

data class RustPrototypeUiState(
    val permissionGranted: Boolean = false,
    val isRecording: Boolean = false,
    val voiceState: VoiceState = VoiceState.Idle,
    val nativeStatus: String = "Idle",
    val transcript: String = "",
    val audioLevel: Float = 0f,
    val lastError: String? = null,
)
