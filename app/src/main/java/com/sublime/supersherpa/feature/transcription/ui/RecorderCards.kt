package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.ui.modelSourceLabel
import com.sublime.supersherpa.feature.transcription.presentation.VoicePhase
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState
import com.sublime.supersherpa.feature.transcription.presentation.errorMessage
import com.sublime.supersherpa.feature.transcription.presentation.phase
import com.sublime.supersherpa.feature.transcription.presentation.transcript
import com.sublime.supersherpa.ui.theme.AppCornerRadius
import com.sublime.supersherpa.ui.theme.AppSpacing
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

private const val LIVE_TRANSCRIPT_SCROLL_TAG = "live_transcript_scroll"
private const val LIVE_TRANSCRIPT_TEXT_TAG = "live_transcript_text"
private const val TYPEWRITER_FRAME_DELAY_MILLIS = 14L
private const val FINAL_OVERWRITE_DELAY_MILLIS = 32L
private const val FINAL_OVERWRITE_CHUNK_SIZE = 5

@Composable
internal fun VoiceOverviewCard(
    voiceState: VoiceState,
    modelSource: ModelSource,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = voiceState.phase
    val statusModel = remember(voiceState) { voiceState.statusModel() }
    val smoothedAudioLevel by animateFloatAsState(
        targetValue = voiceState.audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "audio_level",
    )
    val isCapturePhase = phase == VoicePhase.Listening || phase == VoicePhase.Processing
    val speechLevel = if (isCapturePhase) smoothedAudioLevel else 0f
    val speechDetected = speechLevel > 0.02f
    val speechIntensity = speechLevel.toDouble().pow(0.6).toFloat()
    val phaseShift = if (speechDetected) (speechIntensity * PI).toFloat() else 0f
    val pulse = if (speechDetected) 1f + (speechIntensity * 0.25f) else 1f
    val barColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppCornerRadius.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardContentGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardItemGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AppCornerRadius.IconLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = statusModel.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardTightGap),
                ) {
                    Text(
                        text = statusModel.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = statusModel.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSpacing.CardTightGap),
            ) {
                if (phase == VoicePhase.Processing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
            ) {
                val centerY = size.height / 2f
                val barCount = 57
                val spacing = size.width / (barCount + 1)
                val baseAmplitude = when (phase) {
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
                        color = barColor.copy(alpha = 0.96f),
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(3.5f, barHeight),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                }
            }

            val displayTranscript = remember(voiceState, modelSource) {
                when {
                    phase == VoicePhase.Error && modelSource == ModelSource.Missing -> {
                        "Model not available yet. Download it below to continue."
                    }
                    else -> voiceState.transcript.ifBlank { voiceState.errorMessage.orEmpty() }
                }
            }
            var retainedLiveTranscript by remember { mutableStateOf("") }
            val isLiveTranscript = phase == VoicePhase.Listening && displayTranscript.isNotBlank()
            val animatedDisplayTranscript = key(isLiveTranscript) {
                rememberLiveTypewriterText(
                    targetText = displayTranscript,
                    enabled = isLiveTranscript,
                )
            }
            val finalOverwriteProgress = rememberFinalOverwriteProgress(
                finalText = displayTranscript,
                draftText = retainedLiveTranscript,
                enabled = phase == VoicePhase.Result &&
                    displayTranscript.isNotBlank() &&
                    retainedLiveTranscript.isNotBlank(),
            )
            val transcriptText = when {
                phase == VoicePhase.Listening && displayTranscript.isNotBlank() -> {
                    animatedDisplayTranscript.ifBlank { displayTranscript.take(1) }
                }
                phase == VoicePhase.Processing && displayTranscript.isBlank() -> retainedLiveTranscript
                phase == VoicePhase.Result && displayTranscript.isNotBlank() -> {
                    overwritePreviewText(
                        finalText = displayTranscript,
                        draftText = retainedLiveTranscript,
                        overwriteProgress = finalOverwriteProgress,
                    )
                }
                else -> displayTranscript
            }
            val isDraftTranscript = (phase == VoicePhase.Listening || phase == VoicePhase.Processing) &&
                transcriptText.isNotBlank()
            val transcriptScrollState = rememberScrollState()
            val transcriptTextColor = if (phase == VoicePhase.Error && modelSource != ModelSource.Missing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            LaunchedEffect(phase, displayTranscript, transcriptText) {
                when {
                    phase == VoicePhase.Listening && displayTranscript.isBlank() -> {
                        retainedLiveTranscript = ""
                    }
                    phase == VoicePhase.Listening && transcriptText.isNotBlank() -> {
                        retainedLiveTranscript = transcriptText
                    }
                    phase == VoicePhase.Idle || phase == VoicePhase.Error -> {
                        retainedLiveTranscript = ""
                    }
                }
            }
            LaunchedEffect(phase, transcriptText.length) {
                if (phase == VoicePhase.Listening && transcriptText.isNotBlank()) {
                    transcriptScrollState.scrollTo(transcriptScrollState.maxValue)
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp),
                shape = AppCornerRadius.Card,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(LIVE_TRANSCRIPT_SCROLL_TAG)
                            .verticalScroll(transcriptScrollState)
                            .padding(
                                start = AppSpacing.CardPaddingCompact,
                                top = AppSpacing.CardPaddingCompact + 4.dp,
                                end = AppSpacing.CardPaddingCompact,
                                bottom = when {
                                    transcriptText.isBlank() -> AppSpacing.CardPaddingCompact
                                    phase == VoicePhase.Result -> 72.dp
                                    else -> AppSpacing.CardPaddingCompact + 8.dp
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardSubItemGap),
                    ) {
                        if (transcriptText.isBlank()) {
                            Text(
                                text = "Your transcription will appear here.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            if (isDraftTranscript) {
                                Text(
                                    text = "Draft preview",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Text(
                                modifier = Modifier.testTag(LIVE_TRANSCRIPT_TEXT_TAG),
                                text = if (phase == VoicePhase.Result && retainedLiveTranscript.isNotBlank()) {
                                    overwrittenTranscriptText(
                                        finalText = displayTranscript,
                                        draftText = retainedLiveTranscript,
                                        overwriteProgress = finalOverwriteProgress,
                                        finalStyle = SpanStyle(color = transcriptTextColor),
                                        draftStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    )
                                } else {
                                    AnnotatedString(transcriptText)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDraftTranscript) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    transcriptTextColor
                                },
                            )
                        }
                    }

                    if (phase == VoicePhase.Result && displayTranscript.isNotBlank()) {
                        val transcriptContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            transcriptContainerColor.copy(alpha = 0f),
                                            transcriptContainerColor,
                                        ),
                                    ),
                                ),
                        ) {
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = AppSpacing.CardPaddingCompact, bottom = AppSpacing.CardSubItemGap)
                                    .size(48.dp),
                                onClick = { onCopyText(displayTranscript) },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy transcription",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberFinalOverwriteProgress(
    finalText: String,
    draftText: String,
    enabled: Boolean,
): Int {
    var overwriteProgress by remember { mutableStateOf(finalText.length) }

    LaunchedEffect(enabled, finalText, draftText) {
        if (!enabled || finalText.isBlank() || draftText.isBlank()) {
            overwriteProgress = finalText.length
            return@LaunchedEffect
        }

        overwriteProgress = commonPrefixLength(draftText, finalText)
        while (overwriteProgress < finalText.length) {
            delay(FINAL_OVERWRITE_DELAY_MILLIS)
            overwriteProgress = (overwriteProgress + FINAL_OVERWRITE_CHUNK_SIZE)
                .coerceAtMost(finalText.length)
        }
    }

    return overwriteProgress
}

private fun overwritePreviewText(
    finalText: String,
    draftText: String,
    overwriteProgress: Int,
): String {
    if (draftText.isBlank()) return finalText

    val boundedProgress = overwriteProgress.coerceIn(0, finalText.length)
    return buildString {
        append(finalText.take(boundedProgress))
        if (boundedProgress < draftText.length) {
            append(draftText.substring(boundedProgress))
        }
    }
}

private fun overwrittenTranscriptText(
    finalText: String,
    draftText: String,
    overwriteProgress: Int,
    finalStyle: SpanStyle,
    draftStyle: SpanStyle,
): AnnotatedString {
    if (draftText.isBlank()) return AnnotatedString(finalText)

    val boundedProgress = overwriteProgress.coerceIn(0, finalText.length)
    return buildAnnotatedString {
        withStyle(finalStyle) {
            append(finalText.take(boundedProgress))
        }
        if (boundedProgress < draftText.length) {
            withStyle(draftStyle) {
                append(draftText.substring(boundedProgress))
            }
        }
    }
}

@Composable
private fun rememberLiveTypewriterText(
    targetText: String,
    enabled: Boolean,
): String {
    var renderedText by remember { mutableStateOf("") }
    val latestTargetText by rememberUpdatedState(targetText)

    LaunchedEffect(enabled) {
        if (!enabled) {
            renderedText = latestTargetText
            return@LaunchedEffect
        }

        while (true) {
            val currentTarget = latestTargetText
            val nextText = when {
                currentTarget.isBlank() -> ""
                currentTarget == renderedText -> renderedText
                currentTarget.startsWith(renderedText) && renderedText.length < currentTarget.length -> {
                    currentTarget.substring(0, renderedText.length + 1)
                }
                else -> {
                    val prefixLength = commonPrefixLength(renderedText, currentTarget)
                    currentTarget.substring(0, prefixLength)
                }
            }

            if (nextText != renderedText) {
                renderedText = nextText
            }
            delay(TYPEWRITER_FRAME_DELAY_MILLIS)
        }
    }

    return if (enabled) renderedText else targetText
}

private fun commonPrefixLength(first: String, second: String): Int {
    val minLength = minOf(first.length, second.length)
    var index = 0
    while (index < minLength && first[index] == second[index]) {
        index += 1
    }
    return index
}

@Composable
internal fun ModelDownloadCard(
    modelDeliveryState: ModelDeliveryState,
    modelSource: ModelSource,
    onInstallModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = when (modelDeliveryState) {
        ModelDeliveryState.NotInstalled -> "Not downloaded"
        ModelDeliveryState.Installed -> "Installed"
        is ModelDeliveryState.Downloading -> "Downloading ${modelDeliveryState.completedFiles} of ${modelDeliveryState.totalFiles}"
        is ModelDeliveryState.Failed -> "Download failed"
    }
    val description = when (modelDeliveryState) {
        ModelDeliveryState.NotInstalled -> "No transcription model is installed yet. Download the OTA package to restore transcription."
        ModelDeliveryState.Installed -> "The OTA model package is stored locally and ready for transcription."
        is ModelDeliveryState.Downloading -> modelDeliveryState.stepLabel
        is ModelDeliveryState.Failed -> modelDeliveryState.message
    }
    val actionLabel = when (modelDeliveryState) {
        ModelDeliveryState.NotInstalled -> "Download model"
        ModelDeliveryState.Installed -> "Reinstall model"
        is ModelDeliveryState.Downloading -> "Downloading..."
        is ModelDeliveryState.Failed -> "Retry download"
    }
    val progress = (modelDeliveryState as? ModelDeliveryState.Downloading)
        ?.totalBytes
        ?.takeIf { it > 0L }
        ?.let { totalBytes ->
            (modelDeliveryState.bytesDownloaded ?: 0L).toFloat() / totalBytes.toFloat()
        }
        ?.coerceIn(0f, 1f)

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppCornerRadius.Card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardContentGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardItemGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AppCornerRadius.IconLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardTightGap),
                ) {
                    Text(
                        text = "Offline model package",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = modelSourceLabel(
                    modelSource = modelSource,
                    modelDeliveryState = modelDeliveryState,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (modelDeliveryState is ModelDeliveryState.Downloading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardSubItemGap),
                ) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val sizeLabel = formatDownloadSize(
                        downloadedBytes = modelDeliveryState.bytesDownloaded,
                        totalBytes = modelDeliveryState.totalBytes,
                    )
                    if (sizeLabel != null) {
                        Text(
                            text = sizeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(AppCornerRadius.Pill)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onInstallModel,
                enabled = modelDeliveryState !is ModelDeliveryState.Downloading,
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
internal fun PermissionCard(
    canRequestMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = if (canRequestMicPermission) {
        "Allow microphone"
    } else {
        "Open app settings"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppCornerRadius.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardItemGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardItemGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AppCornerRadius.IconLarge)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardTightGap),
                ) {
                    Text(
                        text = "Microphone permission required",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Grant access to start real-time speech capture.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = if (canRequestMicPermission) onRequestMicPermission else onOpenAppSettings,
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
internal fun HistoryActionCard(
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppCornerRadius.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardItemGap),
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Review previous transcriptions and reuse text that is already stored locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenHistory,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(AppSpacing.CardSubItemGap))
                Text(text = "Open history")
            }
        }
    }
}

@Composable
internal fun RecorderFab(
    phase: VoicePhase,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when {
        phase == VoicePhase.Listening -> Icons.Filled.StopCircle
        else -> Icons.Filled.Mic
    }
    val contentDescription = if (phase == VoicePhase.Listening) {
        "Stop recording"
    } else {
        "Start recording"
    }

    FloatingActionButton(
        modifier = modifier,
        onClick = onPrimaryAction,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}
