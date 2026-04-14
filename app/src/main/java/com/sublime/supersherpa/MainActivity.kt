package com.sublime.supersherpa

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.Keep
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sublime.supersherpa.core.clipboard.AndroidTranscriptClipboard
import com.sublime.supersherpa.core.history.local.SuperSherpaDatabase
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge
import com.sublime.supersherpa.feature.transcription.AppScreen
import com.sublime.supersherpa.feature.transcription.TranscriptHistoryRepository
import com.sublime.supersherpa.feature.transcription.TranscriptionScreen
import com.sublime.supersherpa.feature.transcription.TranscriptionViewModel
import com.sublime.supersherpa.feature.transcription.VoicePhase
import com.sublime.supersherpa.ui.animation.animatedScreenTransition
import androidx.compose.material3.MaterialTheme
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
class MainActivity : ComponentActivity() {
    private companion object {
        const val MIC_PERMISSION_REQUESTED_PREF = "mic_permission_requested"
    }

    private val transcriptionViewModel by viewModels<TranscriptionViewModel>()
    private val bridge by lazy { RustTranscriptionBridge() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bridge.initNative(this)
        val permissionPrefs = getSharedPreferences("permission_state", MODE_PRIVATE)
        val historyRepository = TranscriptHistoryRepository(
            dao = SuperSherpaDatabase.getInstance(this).transcriptHistoryDao(),
        )
        transcriptionViewModel.attachHistoryRepository(historyRepository)

        setContent {
            val voiceState by transcriptionViewModel.voiceState.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current
            val transcriptClipboard = remember { AndroidTranscriptClipboard(this@MainActivity) }
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Recorder) }
            var hasRequestedMicPermission by rememberSaveable {
                mutableStateOf(permissionPrefs.getBoolean(MIC_PERMISSION_REQUESTED_PREF, false))
            }
            var hasMicPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                )
            }
            var isKeyboardAccessReady by remember { mutableStateOf(isKeyboardAccessReady()) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasMicPermission = granted
                hasRequestedMicPermission = true
                permissionPrefs.edit().putBoolean(MIC_PERMISSION_REQUESTED_PREF, true).apply()
            }

            val canRequestMicPermission =
                hasMicPermission ||
                    !hasRequestedMicPermission ||
                    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)

            LaunchedEffect(Unit) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasMicPermission) {
                    hasRequestedMicPermission = true
                    permissionPrefs.edit().putBoolean(MIC_PERMISSION_REQUESTED_PREF, true).apply()
                }
                isKeyboardAccessReady = isKeyboardAccessReady()
            }

            val copyText: (String) -> Unit = { text ->
                if (transcriptClipboard.copy(text).isSuccess) {
                    Toast.makeText(this@MainActivity, "Transcript copied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Copy failed", Toast.LENGTH_SHORT).show()
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasMicPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasMicPermission) {
                            hasRequestedMicPermission = true
                            permissionPrefs.edit().putBoolean(MIC_PERMISSION_REQUESTED_PREF, true).apply()
                        }
                        isKeyboardAccessReady = isKeyboardAccessReady()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            SuperSherpaTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    BackHandler(enabled = currentScreen != AppScreen.Recorder) {
                        currentScreen = AppScreen.Recorder
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            animatedScreenTransition(initialState, targetState)
                        },
                        label = "screen_transition",
                    ) { activeScreen ->
                        TranscriptionScreen(
                            screen = activeScreen,
                            voiceState = voiceState,
                            hasMicPermission = hasMicPermission,
                            canRequestMicPermission = canRequestMicPermission,
                            isKeyboardReady = isKeyboardAccessReady,
                            onRequestMicPermission = {
                                hasRequestedMicPermission = true
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenAppSettings = {
                                openAppSettings()
                            },
                            onPrimaryAction = {
                                if (!hasMicPermission) {
                                    if (canRequestMicPermission) {
                                        hasRequestedMicPermission = true
                                        permissionPrefs.edit().putBoolean(MIC_PERMISSION_REQUESTED_PREF, true).apply()
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        openAppSettings()
                                    }
                                } else {
                                    when (voiceState.phase) {
                                        VoicePhase.Idle,
                                        VoicePhase.Result,
                                        VoicePhase.Error,
                                        -> {
                                            transcriptionViewModel.setListening()
                                            bridge.startRecording()
                                        }
                                        VoicePhase.Listening -> {
                                            transcriptionViewModel.setProcessing()
                                            bridge.stopRecording()
                                        }
                                        VoicePhase.Processing -> {
                                            bridge.cancelRecording()
                                            transcriptionViewModel.reset()
                                        }
                                    }
                                }
                            },
                            onOpenKeyboardSettings = {
                                startActivity(
                                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            },
                            onNavigate = { destination ->
                                currentScreen = destination
                            },
                            onCopyText = copyText,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        bridge.cleanupNative()
        super.onDestroy()
    }

    @Keep
    fun onStatusUpdate(message: String) {
        runOnUiThread {
            transcriptionViewModel.applyNativeStatus(message)
        }
    }

    @Keep
    fun onAudioLevel(level: Float) {
        runOnUiThread {
            transcriptionViewModel.setAudioLevel(level)
        }
    }

    @Keep
    fun onTextTranscribed(text: String) {
        lifecycleScope.launch {
            transcriptionViewModel.applyTranscribedText(text)
        }
    }

    private fun isKeyboardAccessReady(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { info ->
            info.packageName == packageName &&
                info.serviceName == "com.sublime.supersherpa.core.ime.TranscriptionImeService"
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
