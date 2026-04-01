package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.model.VoiceState

data class MicrophoneTestUiState(
    val permissionGranted: Boolean = false,
    val isRecording: Boolean = false,
    val voiceState: VoiceState = VoiceState.Idle,
    val frameCount: Int = 0,
    val lastFrameSize: Int = 0,
    val streamText: String = "",
    val lastError: String? = null,
)
