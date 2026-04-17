package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.core.ai.modeldelivery.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.ModelSource
import com.sublime.supersherpa.feature.transcription.presentation.VoicePhase
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState
import com.sublime.supersherpa.feature.transcription.presentation.errorMessage
import com.sublime.supersherpa.feature.transcription.presentation.phase
import com.sublime.supersherpa.feature.transcription.presentation.transcript
import com.sublime.supersherpa.ui.theme.AppCornerRadius
import com.sublime.supersherpa.ui.theme.AppSpacing
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecorderScreen(
    voiceState: VoiceState,
    hasMicPermission: Boolean,
    canRequestMicPermission: Boolean,
    modelDeliveryState: ModelDeliveryState,
    modelSource: ModelSource,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onCopyText: (String) -> Unit,
    onInstallModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SuperSherpa",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Open history",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            RecorderFab(
                phase = voiceState.phase,
                onPrimaryAction = onPrimaryAction,
            )
        },
    ) { paddingValues ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues),
            contentPadding = PaddingValues(
                start = AppSpacing.ScreenHorizontal,
                top = paddingValues.calculateTopPadding() + AppSpacing.ScreenTop,
                end = AppSpacing.ScreenHorizontal,
                bottom = paddingValues.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardContentGap),
        ) {
            if (modelSource == ModelSource.Missing || modelDeliveryState != ModelDeliveryState.Installed) {
                item {
                    ModelDownloadCard(
                        modelDeliveryState = modelDeliveryState,
                        modelSource = modelSource,
                        onInstallModel = onInstallModel,
                    )
                }
            }

            if (!hasMicPermission) {
                item {
                    PermissionCard(
                        canRequestMicPermission = canRequestMicPermission,
                        onRequestMicPermission = onRequestMicPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
            }

            item {
                VoiceOverviewCard(
                    voiceState = voiceState,
                    modelSource = modelSource,
                    onCopyText = onCopyText,
                )
            }

            item {
                HistoryActionCard(onOpenHistory = onOpenHistory)
            }
        }
    }
}

@Composable
private fun VoiceOverviewCard(
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
            val transcriptScrollState = rememberScrollState()
            val transcriptTextColor = if (phase == VoicePhase.Error && modelSource != ModelSource.Missing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
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
                            .verticalScroll(transcriptScrollState)
                            .padding(
                                start = AppSpacing.CardPaddingCompact,
                                top = AppSpacing.CardPaddingCompact,
                                end = AppSpacing.CardPaddingCompact,
                                bottom = if (displayTranscript.isBlank()) AppSpacing.CardPaddingCompact else 72.dp,
                            ),
                    ) {
                        if (displayTranscript.isBlank()) {
                            Text(
                                text = "Your transcription will appear here.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = displayTranscript,
                                style = MaterialTheme.typography.bodyLarge,
                                color = transcriptTextColor,
                            )
                        }
                    }

                    if (displayTranscript.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f),
                                            MaterialTheme.colorScheme.surfaceContainerLow,
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
private fun ModelDownloadCard(
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
        ModelDeliveryState.NotInstalled -> "No transcription model is installed yet. Download the OTA Parakeet package to restore transcription."
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
                text = "Model source: ${if (modelSource == ModelSource.Ota) "OTA" else "Missing"}",
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

private fun formatDownloadSize(
    downloadedBytes: Long?,
    totalBytes: Long?,
): String? {
    val downloadedLabel = downloadedBytes?.let(::formatBytes)
    val totalLabel = totalBytes?.let(::formatBytes)

    return when {
        downloadedLabel != null && totalLabel != null -> "$downloadedLabel / $totalLabel"
        downloadedLabel != null -> downloadedLabel
        else -> null
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"

    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = -1

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return String.format("%.1f %s", value, units[unitIndex])
}

@Composable
private fun PermissionCard(
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
private fun HistoryActionCard(
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
private fun RecorderFab(
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

@Immutable
private data class VoiceStatusModel(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun VoiceState.statusModel(): VoiceStatusModel =
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
