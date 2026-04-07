package com.sublime.supersherpa.core.ime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.feature.transcription.VoicePhase
import com.sublime.supersherpa.feature.transcription.VoiceState

private val KeyboardBackground = Color(0xFF2B252E)
private val CardBackground = Color(0xFFF7F4F7)
private val AccentMuted = Color(0xFFD9D9D9)
private val IconTint = Color(0xFF2196F3)
private val TextPrimary = Color(0xFF111111)
private val TextSecondary = Color(0xFF8E8E8E)
private val ButtonText = Color(0xFF444444)

@Composable
fun TranscriptionKeyboard(
    voiceState: VoiceState,
    onToggleRecording: () -> Unit,
    onShowKeyboardPicker: () -> Unit,
    onCommitText: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = KeyboardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = voiceState.statusText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconButtonKey(
                        onClick = onToggleRecording,
                        contentDescription = when (voiceState.phase) {
                            VoicePhase.Listening -> "Stop recording"
                            VoicePhase.Processing -> "Transcription in progress"
                            else -> "Tap to record"
                        },
                        width = 64.dp,
                    ) {
                        Text(
                            text = voiceState.primaryButtonLabel,
                            color = if (voiceState.phase == VoicePhase.Listening) Color.White else IconTint,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = voiceState.bodyText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButtonKey(
                    onClick = onShowKeyboardPicker,
                    contentDescription = "Change keyboard",
                    width = 44.dp,
                ) {
                    Text(
                        text = "GLB",
                        color = ButtonText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                TextKey(
                    onClick = { onCommitText(" ") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = " ",
                        color = Color.Transparent,
                    )
                }

                IconButtonKey(
                    onClick = { onCommitText("\n") },
                    contentDescription = "Enter",
                    width = 44.dp,
                ) {
                    Text(
                        text = "ENT",
                        color = ButtonText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                IconButtonKey(
                    onClick = onDelete,
                    contentDescription = "Delete",
                    width = 44.dp,
                ) {
                    Text(
                        text = "DEL",
                        color = ButtonText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

private val VoiceState.statusText: String
    get() = when (phase) {
        VoicePhase.Idle -> "Tap to record"
        VoicePhase.Listening -> "Listening..."
        VoicePhase.Processing -> "Transcribing..."
        VoicePhase.Result -> "Transcript ready"
        VoicePhase.Error -> "Error: ${errorMessage.orEmpty()}"
    }

private val VoiceState.bodyText: String
    get() = when (phase) {
        VoicePhase.Idle -> "Tap to Record"
        VoicePhase.Listening -> "Recording in progress"
        VoicePhase.Processing -> "Transcription in progress"
        VoicePhase.Result -> transcript
        VoicePhase.Error -> errorMessage.orEmpty()
    }

private val VoiceState.primaryButtonLabel: String
    get() = when (phase) {
        VoicePhase.Listening -> "STOP"
        VoicePhase.Processing -> "WAIT"
        else -> "REC"
    }

@Composable
private fun IconButtonKey(
    onClick: () -> Unit,
    contentDescription: String,
    width: Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun TextKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AccentMuted,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(36.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
