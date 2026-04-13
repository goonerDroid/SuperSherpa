package com.sublime.supersherpa.feature.transcription

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

private val RecorderBackground = Color(0xFF141317)
private val RecorderPanel = Color(0xFF1D1E22)
private val RecorderPanelMuted = Color(0xFF25272C)
private val RecorderText = Color(0xFFF4F2F4)
private val RecorderMutedText = Color(0xFF8D8A95)
private val RecorderWave = Color(0xFF7E7D84)
private val RecorderAccent = Color(0xFFE45E42)
private val RecorderAccentDark = Color(0xFF712A20)
private val RecorderWarm = Color(0xFFE2B24F)

@Composable
fun TranscriptionScreen(
    voiceState: VoiceState,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = RecorderBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            TopBar(
                title = "SuperSherpa",
            )

            Spacer(modifier = Modifier.height(56.dp))

            VoiceWaveSection(
                voiceState = voiceState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            BottomControls(
                voiceState = voiceState,
                hasMicPermission = hasMicPermission,
                onPrimaryAction = {
                    if (hasMicPermission) {
                        onPrimaryAction()
                    } else {
                        onRequestMicPermission()
                    }
                },
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = RecorderText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
            ),
        )
    }
}

@Composable
private fun VoiceWaveSection(
    voiceState: VoiceState,
    modifier: Modifier = Modifier,
) {
    val smoothedAudioLevel by animateFloatAsState(
        targetValue = voiceState.audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "audio_level",
    )
    val isCapturePhase = voiceState.phase == VoicePhase.Listening || voiceState.phase == VoicePhase.Processing
    val speechLevel = if (isCapturePhase) smoothedAudioLevel else 0f
    val speechDetected = speechLevel > 0.02f
    val speechIntensity = speechLevel.toDouble().pow(0.6).toFloat()

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedPhaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2f).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase_shift",
    )
    val animatedPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val phaseShift = if (speechDetected) animatedPhaseShift else 0f
    val pulse = if (speechDetected) animatedPulse else 1f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val centerY = size.height / 2f
            val barCount = 57
            val spacing = size.width / (barCount + 1)
            val baseAmplitude = when (voiceState.phase) {
                VoicePhase.Listening -> 0.035f
                VoicePhase.Processing -> 0.028f
                VoicePhase.Result -> 0.022f
                VoicePhase.Error -> 0.022f
                VoicePhase.Idle -> 0.018f
            }
            val speechAmplitude = (speechIntensity * 0.9f * pulse).coerceIn(0f, 1f)

            for (index in 0 until barCount) {
                val x = spacing * (index + 1)
                val distance = (index - (barCount / 2f)) / (barCount / 2f)
                val taper = (1f - distance.absoluteValue).coerceAtLeast(0.15f)
                val wave = if (speechDetected) sin((index * 0.45f) + phaseShift) else 0f
                val secondaryWave = if (speechDetected) sin((index * 0.21f) - phaseShift * 0.6f) else 0f
                val movement = if (speechDetected) {
                    0.5f + 0.5f * ((wave + secondaryWave) / 2f + 1f)
                } else {
                    1f
                }
                val amplitude = size.height * 0.32f * (baseAmplitude + speechAmplitude) * taper * movement
                val barHeight = amplitude.coerceAtLeast(3f)
                drawRoundRect(
                    color = RecorderWave.copy(alpha = 0.96f),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(3.5f, barHeight),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    voiceState: VoiceState,
    hasMicPermission: Boolean,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcript = voiceState.transcript.ifBlank { voiceState.errorMessage.orEmpty() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (transcript.isNotBlank()) {
            val transcriptScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .verticalScroll(transcriptScrollState),
            ) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (voiceState.phase == VoicePhase.Error) RecorderAccent else RecorderText,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = when {
                !hasMicPermission -> "Microphone permission required"
                voiceState.phase == VoicePhase.Listening || voiceState.phase == VoicePhase.Processing -> "Recording"
                voiceState.phase == VoicePhase.Result -> "Tap to record again"
                voiceState.phase == VoicePhase.Error -> voiceState.errorMessage ?: "Something went wrong"
                else -> "Tap to record"
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = RecorderMutedText,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        RecordButton(
            phase = voiceState.phase,
            enabled = hasMicPermission,
            onClick = onPrimaryAction,
        )
    }
}

@Composable
private fun RecordButton(
    phase: VoicePhase,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerColor by animateColorAsState(
        targetValue = if (enabled) RecorderAccent else RecorderMutedText.copy(alpha = 0.35f),
        animationSpec = tween(300),
        label = "record_outer",
    )
    val innerColor by animateColorAsState(
        targetValue = if (enabled) RecorderAccentDark else RecorderPanelMuted,
        animationSpec = tween(300),
        label = "record_inner",
    )

    Box(
        modifier = modifier
            .size(width = 124.dp, height = 72.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            drawRoundRect(
                color = outerColor,
                style = Stroke(width = 5f),
                cornerRadius = CornerRadius(40f, 40f),
            )
            drawRoundRect(
                color = innerColor,
                topLeft = Offset(7f, 7f),
                size = Size(size.width - 14f, size.height - 14f),
                cornerRadius = CornerRadius(34f, 34f),
            )
        }

        val isRecording = phase == VoicePhase.Listening || phase == VoicePhase.Processing
        Box(
            modifier = Modifier
                .size(if (isRecording) 28.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(outerColor),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(outerColor),
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(RecorderAccentDark),
                )
            }
        }
    }
}
