package com.sublime.supersherpa.feature.transcription.domain

internal sealed interface NativeTranscriptionMessage {
    data object Listening : NativeTranscriptionMessage
    data object Processing : NativeTranscriptionMessage
    data object Ready : NativeTranscriptionMessage
    data object Canceled : NativeTranscriptionMessage
    data class Error(val message: String) : NativeTranscriptionMessage
    data class Transcript(val text: String) : NativeTranscriptionMessage
}

internal fun parseNativeTranscriptionMessage(message: String): NativeTranscriptionMessage =
    when {
        message.startsWith("Error:") -> NativeTranscriptionMessage.Error(
            message.removePrefix("Error:").trim().ifBlank {
                "Transcription failed."
            },
        )
        message == "Listening..." -> NativeTranscriptionMessage.Listening
        message == "Transcribing..." -> NativeTranscriptionMessage.Processing
        message == "Ready" -> NativeTranscriptionMessage.Ready
        message == "Canceled" -> NativeTranscriptionMessage.Canceled
        else -> NativeTranscriptionMessage.Transcript(message.trim())
    }
