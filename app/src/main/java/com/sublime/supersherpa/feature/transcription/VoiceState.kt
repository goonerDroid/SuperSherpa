package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.model.TranscriptionHistoryItem

/**
 * Immutable UI state for the transcription flow.
 *
 * The presentation layer observes a single state object instead of separate
 * booleans and ad-hoc callbacks.
 */
data class VoiceState(
    val phase: VoicePhase = VoicePhase.Idle,
    val transcript: String = "",
    val errorMessage: String? = null,
    val audioLevel: Float = 0f,
    val history: List<TranscriptionHistoryItem> = emptyList(),
)

enum class VoicePhase {
    Idle,
    Listening,
    Processing,
    Result,
    Error,
}
