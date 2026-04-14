package com.sublime.supersherpa.feature.transcription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sublime.supersherpa.feature.settings.SettingsScreen
import com.sublime.supersherpa.model.TranscriptionHistoryItem

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
            onBack = { onNavigate(AppScreen.Recorder) },
            onOpenKeyboardSettings = onOpenKeyboardSettings,
            onRequestMicPermission = onRequestMicPermission,
            onOpenAppSettings = onOpenAppSettings,
            modifier = modifier,
        )
    }
}
