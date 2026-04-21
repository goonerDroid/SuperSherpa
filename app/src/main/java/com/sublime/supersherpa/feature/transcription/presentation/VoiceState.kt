package com.sublime.supersherpa.feature.transcription.presentation

import androidx.compose.runtime.Immutable

/**
 * Immutable UI state for the transcription flow.
 *
 * A sealed contract keeps invalid combinations out of the presentation layer
 * and gives Compose a more predictable state shape to observe.
 */
@Immutable
sealed interface VoiceState {
    val audioLevel: Float

    @Immutable
    data object Idle : VoiceState {
        override val audioLevel: Float = 0f
    }

    @Immutable
    data class Listening(
        override val audioLevel: Float = 0f,
        val partialTranscript: String = "",
    ) : VoiceState

    @Immutable
    data class Processing(
        override val audioLevel: Float = 0f,
    ) : VoiceState

    @Immutable
    data class Result(
        val transcript: String,
        override val audioLevel: Float = 0f,
    ) : VoiceState

    @Immutable
    data class Error(
        val errorMessage: String,
        override val audioLevel: Float = 0f,
    ) : VoiceState
}

enum class VoicePhase {
    Idle,
    Listening,
    Processing,
    Result,
    Error,
}

val VoiceState.phase: VoicePhase
    get() = when (this) {
        VoiceState.Idle -> VoicePhase.Idle
        is VoiceState.Listening -> VoicePhase.Listening
        is VoiceState.Processing -> VoicePhase.Processing
        is VoiceState.Result -> VoicePhase.Result
        is VoiceState.Error -> VoicePhase.Error
    }

val VoiceState.transcript: String
    get() = when (this) {
        is VoiceState.Listening -> partialTranscript
        is VoiceState.Result -> transcript
        else -> ""
    }

val VoiceState.errorMessage: String?
    get() = when (this) {
        is VoiceState.Error -> errorMessage
        else -> null
    }

fun VoiceState.withAudioLevel(level: Float): VoiceState =
    when (this) {
        VoiceState.Idle -> this
        is VoiceState.Listening -> copy(audioLevel = level, partialTranscript = partialTranscript)
        is VoiceState.Processing -> copy(audioLevel = level)
        is VoiceState.Result -> copy(audioLevel = level)
        is VoiceState.Error -> copy(audioLevel = level)
    }
