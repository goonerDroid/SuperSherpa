package com.sublime.supersherpa.feature.transcription

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.sublime.supersherpa.feature.settings.SettingsScreen
import com.sublime.supersherpa.model.TranscriptionHistoryItem
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    screen: AppScreen,
    voiceState: VoiceState,
    history: List<TranscriptionHistoryItem>,
    hasMicPermission: Boolean,
    canRequestMicPermission: Boolean,
    isKeyboardReady: Boolean,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
    ) {
    when (screen) {
        AppScreen.Recorder -> RecorderScreen(
            phase = voiceState.phase,
            transcript = voiceState.transcript,
            errorMessage = voiceState.errorMessage,
            audioLevel = voiceState.audioLevel,
            hasMicPermission = hasMicPermission,
            canRequestMicPermission = canRequestMicPermission,
            onRequestMicPermission = onRequestMicPermission,
            onOpenAppSettings = onOpenAppSettings,
            onPrimaryAction = onPrimaryAction,
            onOpenSettings = { onNavigate(AppScreen.Settings) },
            onOpenHistory = { onNavigate(AppScreen.History) },
            onCopyText = onCopyText,
            modifier = modifier.fillMaxSize(),
        )
        AppScreen.History -> HistoryScreen(
            history = history,
            onClose = { onNavigate(AppScreen.Recorder) },
            onCopyText = onCopyText,
            modifier = modifier.fillMaxSize(),
        )
        AppScreen.Settings -> SettingsScreen(
            isKeyboardReady = isKeyboardReady,
            hasMicPermission = hasMicPermission,
            canRequestMicPermission = canRequestMicPermission,
            onBack = { onNavigate(AppScreen.Recorder) },
            onOpenKeyboardSettings = onOpenKeyboardSettings,
            onRequestMicPermission = onRequestMicPermission,
            onOpenAppSettings = onOpenAppSettings,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecorderScreen(
    phase: VoicePhase,
    transcript: String,
    errorMessage: String?,
    audioLevel: Float,
    hasMicPermission: Boolean,
    canRequestMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
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
                phase = phase,
                onPrimaryAction = onPrimaryAction,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                VoiceOverviewCard(
                    phase = phase,
                    transcript = transcript,
                    errorMessage = errorMessage,
                    audioLevel = audioLevel,
                    onCopyText = onCopyText,
                )
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
                HistoryActionCard(onOpenHistory = onOpenHistory)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    history: List<TranscriptionHistoryItem>,
    onClose: () -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MicNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No transcription available yet",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Your completed transcriptions will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = onClose) {
                            Text(text = "Back to recorder")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { item -> item.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onCopyText = onCopyText,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceOverviewCard(
    phase: VoicePhase,
    transcript: String,
    errorMessage: String?,
    audioLevel: Float,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusModel = remember(phase, errorMessage) { phase.statusModel(errorMessage) }
    val smoothedAudioLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    .height(4.dp),
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

            val displayTranscript = remember(transcript, errorMessage) {
                transcript.ifBlank { errorMessage.orEmpty() }
            }
            val transcriptScrollState = rememberScrollState()
            val transcriptTextColor = if (phase == VoicePhase.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = if (displayTranscript.isBlank()) 16.dp else 72.dp,
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
                                    .padding(end = 16.dp, bottom = 12.dp)
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            FilledTonalButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Open history")
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: TranscriptionHistoryItem,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedTime = remember(item.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(item.createdAtEpochMillis))
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { onCopyText(item.text) },
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy history item",
                )
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

private data class VoiceStatusModel(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun VoicePhase.statusModel(errorMessage: String?): VoiceStatusModel =
    when (this) {
        VoicePhase.Idle -> VoiceStatusModel(
            title = "Ready",
            subtitle = "Tap start when you want a local transcription.",
            icon = Icons.Filled.Mic,
        )

        VoicePhase.Listening -> VoiceStatusModel(
            title = "Listening",
            subtitle = "Audio is streaming into the transcription engine.",
            icon = Icons.Outlined.GraphicEq,
        )

        VoicePhase.Processing -> VoiceStatusModel(
            title = "Processing",
            subtitle = "The on-device model is converting speech to text.",
            icon = Icons.Filled.Autorenew,
        )

        VoicePhase.Result -> VoiceStatusModel(
            title = "Result ready",
            subtitle = "The latest transcription is ready to review.",
            icon = Icons.Filled.CheckCircle,
        )

        VoicePhase.Error -> VoiceStatusModel(
            title = "Error",
            subtitle = errorMessage ?: "Transcription failed.",
            icon = Icons.Outlined.ErrorOutline,
        )
    }
