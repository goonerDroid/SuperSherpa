package com.sublime.supersherpa.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.core.ai.modeldelivery.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.TranscriptionModelDelivery
import com.sublime.supersherpa.ui.theme.AppCornerRadius
import com.sublime.supersherpa.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isKeyboardReady: Boolean,
    hasMicPermission: Boolean,
    canRequestMicPermission: Boolean,
    modelDeliveryState: ModelDeliveryState,
    modelSource: ModelSource,
    onBack: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onInstallModel: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paddingValues),
                contentPadding = PaddingValues(
                    start = AppSpacing.ScreenHorizontal,
                    top = paddingValues.calculateTopPadding() + AppSpacing.ScreenTop,
                    end = AppSpacing.ScreenHorizontal,
                    bottom = paddingValues.calculateBottomPadding() + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.CardContentGap),
            ) {
                item {
                    AccessCard(
                        title = "Keyboard access",
                        description = "Enable the input method so SuperSherpa can appear as a keyboard.",
                        enabled = isKeyboardReady,
                        icon = Icons.Filled.Keyboard,
                        readyLabel = "Enabled",
                        actionLabel = "Open keyboard settings",
                        onAction = onOpenKeyboardSettings,
                        readyActionLabel = "Open keyboard settings",
                        readyOnAction = onOpenKeyboardSettings,
                    )
                }

                item {
                    AccessCard(
                        title = "Microphone permission",
                        description = "Grant recording access for offline speech transcription.",
                        enabled = hasMicPermission,
                        icon = Icons.Filled.Mic,
                        readyLabel = "Permission granted",
                        actionLabel = when {
                            hasMicPermission -> "Open app settings"
                            canRequestMicPermission -> "Request permission"
                            else -> "Open app settings"
                        },
                        onAction = when {
                            hasMicPermission -> onOpenAppSettings
                            canRequestMicPermission -> onRequestMicPermission
                            else -> onOpenAppSettings
                        },
                        actionEnabled = !hasMicPermission,
                        readyActionLabel = "Open app settings",
                        readyOnAction = onOpenAppSettings,
                    )
                }

                item {
                    ModelDeliveryCard(
                        state = modelDeliveryState,
                        modelSource = modelSource,
                        onInstallModel = onInstallModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelDeliveryCard(
    state: ModelDeliveryState,
    modelSource: ModelSource,
    onInstallModel: () -> Unit,
) {
    val statusIconColor = when (state) {
        ModelDeliveryState.Installed -> MaterialTheme.colorScheme.primary
        ModelDeliveryState.NotInstalled -> MaterialTheme.colorScheme.onSecondaryContainer
        is ModelDeliveryState.Downloading -> MaterialTheme.colorScheme.onPrimaryContainer
        is ModelDeliveryState.Failed -> MaterialTheme.colorScheme.onErrorContainer
    }
    val statusTextColor = when (state) {
        ModelDeliveryState.NotInstalled -> MaterialTheme.colorScheme.onSecondaryContainer
        is ModelDeliveryState.Downloading -> MaterialTheme.colorScheme.onPrimaryContainer
        is ModelDeliveryState.Failed -> MaterialTheme.colorScheme.onErrorContainer
        ModelDeliveryState.Installed -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (state) {
        ModelDeliveryState.NotInstalled -> "Not downloaded"
        ModelDeliveryState.Installed -> "Installed"
        is ModelDeliveryState.Downloading -> "Downloading ${state.completedFiles} of ${state.totalFiles}"
        is ModelDeliveryState.Failed -> "Download failed"
    }
    val description = when (state) {
        ModelDeliveryState.NotInstalled -> {
            "No transcription model is installed yet. Download the OTA Parakeet package to restore transcription."
        }
        ModelDeliveryState.Installed -> {
            "The OTA model package is stored locally and ready for the next transcription runtime initialization."
        }
        is ModelDeliveryState.Downloading -> state.stepLabel
        is ModelDeliveryState.Failed -> state.message
    }
    val actionLabel = when (state) {
        ModelDeliveryState.NotInstalled -> "Download model"
        ModelDeliveryState.Installed -> "Reinstall model"
        is ModelDeliveryState.Downloading -> "Downloading..."
        is ModelDeliveryState.Failed -> "Retry download"
    }
    val actionEnabled = state !is ModelDeliveryState.Downloading

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCornerRadius.Card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardContentGap),
        ) {
            ModelDeliveryHeader(
                title = "Offline model package",
                description = description,
            )
            SourceRow(modelSource = modelSource)
            if (state is ModelDeliveryState.Installed) {
                InstalledModelRow(modelName = TranscriptionModelDelivery.MODEL_ID)
            }

            StatusRow(
                statusLabel = statusLabel,
                iconTint = statusIconColor,
                textColor = statusTextColor,
            )

            if (state is ModelDeliveryState.Downloading) {
                DownloadProgressSection(state = state)
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onInstallModel,
                enabled = actionEnabled,
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun DownloadProgressSection(
    state: ModelDeliveryState.Downloading,
) {
    val progress = state.totalBytes
        ?.takeIf { it > 0L }
        ?.let { totalBytes ->
            (state.bytesDownloaded ?: 0L).toFloat() / totalBytes.toFloat()
        }
        ?.coerceIn(0f, 1f)

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
            downloadedBytes = state.bytesDownloaded,
            totalBytes = state.totalBytes,
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
private fun ModelDeliveryHeader(
    title: String,
    description: String,
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
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardTightGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(
    statusLabel: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardSubItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = iconTint,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

@Composable
private fun SourceRow(modelSource: ModelSource) {
    val label = when (modelSource) {
        ModelSource.Ota -> "Model source: OTA"
        ModelSource.Missing -> "Model source: Missing"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InstalledModelRow(modelName: String) {
    Text(
        text = "Installed model: $modelName",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AccessCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    readyLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    readyActionLabel: String,
    readyOnAction: () -> Unit,
) {
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
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardTightGap),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardSubItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = if (enabled) readyLabel else description,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (enabled) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = readyOnAction,
                ) {
                    Text(text = readyActionLabel)
                }
            } else {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAction,
                    enabled = actionEnabled,
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
