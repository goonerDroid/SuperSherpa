package com.sublime.supersherpa.feature.transcription

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
) {
    val isActive: Boolean
        get() = phase == VoicePhase.Listening || phase == VoicePhase.Processing
}

enum class VoicePhase {
    Idle,
    Listening,
    Processing,
    Result,
    Error,
}
