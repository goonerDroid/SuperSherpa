package com.sublime.supersherpa.model

/**
 * Shared UI state for the transcription flow.
 *
 * This is the single contract the presentation layer should use for mic/transcription status.
 */
sealed class VoiceState {
    data object Idle : VoiceState()

    data object Listening : VoiceState()

    data object Processing : VoiceState()

    data class Result(val text: String) : VoiceState()

    data class Error(val message: String) : VoiceState()
}

