package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState
import com.sublime.supersherpa.feature.transcription.presentation.errorMessage

@Immutable
internal data class VoiceStatusModel(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

internal fun VoiceState.statusModel(): VoiceStatusModel =
    when (this) {
        VoiceState.Idle -> VoiceStatusModel(
            title = "Ready",
            subtitle = "Tap start when you want a local transcription.",
            icon = Icons.Filled.Mic,
        )

        is VoiceState.Listening -> VoiceStatusModel(
            title = "Listening",
            subtitle = "Audio is streaming into the transcription engine.",
            icon = Icons.Outlined.GraphicEq,
        )

        is VoiceState.Processing -> VoiceStatusModel(
            title = "Processing",
            subtitle = "The on-device model is converting speech to text.",
            icon = Icons.Filled.Autorenew,
        )

        is VoiceState.Result -> VoiceStatusModel(
            title = "Result ready",
            subtitle = "The latest transcription is ready to review.",
            icon = Icons.Filled.CheckCircle,
        )

        is VoiceState.Error -> {
            val isModelMissing = errorMessage.contains("model not available yet", ignoreCase = true)
            VoiceStatusModel(
                title = if (isModelMissing) "Model not available" else "Error",
                subtitle = if (isModelMissing) {
                    "Download the model below to start transcription."
                } else {
                    errorMessage.ifBlank { "Transcription failed." }
                },
                icon = Icons.Outlined.ErrorOutline,
            )
        }
    }
