package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sublime.supersherpa.core.ai.modeldelivery.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.ModelSource
import com.sublime.supersherpa.feature.settings.ui.SettingsScreen
import com.sublime.supersherpa.feature.transcription.domain.TranscriptionHistoryItem
import com.sublime.supersherpa.feature.transcription.presentation.AppScreen
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    screen: AppScreen,
    voiceState: VoiceState,
    history: List<TranscriptionHistoryItem>,
    hasMicPermission: Boolean,
    canRequestMicPermission: Boolean,
    isKeyboardReady: Boolean,
    modelDeliveryState: ModelDeliveryState,
    modelSource: ModelSource,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onInstallModel: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onCopyText: (String) -> Unit,
    onDeleteHistoryItems: (Collection<Long>) -> Unit,
    modifier: Modifier = Modifier,
    ) {
    when (screen) {
        AppScreen.Recorder -> RecorderScreen(
            voiceState = voiceState,
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
            onDeleteHistoryItems = onDeleteHistoryItems,
            modifier = modifier.fillMaxSize(),
        )
        AppScreen.Settings -> SettingsScreen(
            isKeyboardReady = isKeyboardReady,
            hasMicPermission = hasMicPermission,
            canRequestMicPermission = canRequestMicPermission,
            modelDeliveryState = modelDeliveryState,
            modelSource = modelSource,
            onBack = { onNavigate(AppScreen.Recorder) },
            onOpenKeyboardSettings = onOpenKeyboardSettings,
            onRequestMicPermission = onRequestMicPermission,
            onOpenAppSettings = onOpenAppSettings,
            onInstallModel = onInstallModel,
            modifier = modifier,
        )
    }
}
