package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.model.TranscriptionNote
import com.sublime.supersherpa.model.VoiceState

data class TranscriptionUiState(
    val permissionGranted: Boolean = false,
    val engineReady: Boolean = false,
    val isRecording: Boolean = false,
    val voiceState: VoiceState = VoiceState.Idle,
    val partialTranscript: String = "",
    val notes: List<TranscriptionNote> = emptyList(),
    val lastError: String? = null,
)
