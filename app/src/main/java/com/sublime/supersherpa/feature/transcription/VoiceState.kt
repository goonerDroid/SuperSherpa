package com.sublime.supersherpa.feature.transcription

import androidx.compose.runtime.Immutable

/**
 * Immutable UI state for the transcription flow.
 *
 * The presentation layer observes a single state object instead of separate
 * booleans and ad-hoc callbacks.
 */
@Immutable
data class VoiceState(
    val phase: VoicePhase = VoicePhase.Idle,
    val transcript: String = "",
    val errorMessage: String? = null,
    val audioLevel: Float = 0f,
)

enum class VoicePhase {
    Idle,
    Listening,
    Processing,
    Result,
    Error,
}
