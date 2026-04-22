package com.sublime.supersherpa.feature.transcription.domain

internal sealed interface NativeTranscriptionMessage {
    data object Listening : NativeTranscriptionMessage
    data object Processing : NativeTranscriptionMessage
    data object Ready : NativeTranscriptionMessage
    data object Canceled : NativeTranscriptionMessage
    data object Unknown : NativeTranscriptionMessage
    data class Error(val message: String) : NativeTranscriptionMessage
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
        else -> NativeTranscriptionMessage.Unknown
    }

internal fun normalizeNativeErrorMessage(message: String): String {
    val normalized = message.trim()
    return if (
        normalized.contains("onnx runtime error", ignoreCase = true) ||
        normalized.contains("does not exist", ignoreCase = true) ||
        normalized.contains("model error", ignoreCase = true)
    ) {
        "Model not available yet. Download it below to continue."
    } else {
        normalized.ifBlank { "Transcription failed." }
    }
}
