package com.sublime.supersherpa

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.Keep
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge
import com.sublime.supersherpa.feature.transcription.TranscriptionScreen
import com.sublime.supersherpa.feature.transcription.TranscriptionViewModel
import com.sublime.supersherpa.feature.transcription.VoicePhase
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class MainActivity : ComponentActivity() {
    private val transcriptionViewModel by viewModels<TranscriptionViewModel>()
    private val bridge by lazy { RustTranscriptionBridge() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bridge.initNative(this)

        setContent {
            val voiceState by transcriptionViewModel.voiceState.collectAsState()
            var hasMicPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasMicPermission = granted
            }

            LaunchedEffect(Unit) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            SuperSherpaTheme {
                TranscriptionScreen(
                    voiceState = voiceState,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onPrimaryAction = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                )
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
        runOnUiThread {
            transcriptionViewModel.applyTranscribedText(text)
        }
    }
}
