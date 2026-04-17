package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState
import com.sublime.supersherpa.feature.transcription.presentation.phase
import com.sublime.supersherpa.ui.theme.AppSpacing

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
                phase = voiceState.phase,
                onPrimaryAction = onPrimaryAction,
            )
        },
    ) { paddingValues ->
        LazyColumn(
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
