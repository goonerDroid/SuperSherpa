package com.sublime.supersherpa.feature.transcription

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sublime.supersherpa.model.VoiceState

@Composable
fun MicrophoneTestScreen(
    modifier: Modifier = Modifier,
    viewModel: MicrophoneTestViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(hasMicPermission) {
        viewModel.onPermissionResult(hasMicPermission)
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "On-device microphone test")
        Text(text = "This screen exercises the real AudioRecord path.")

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = "Permission: ${if (uiState.permissionGranted) "granted" else "missing"}")
                Text(text = "State: ${uiState.voiceState.toDisplayText()}")
                Text(text = "Frames captured: ${uiState.frameCount}")
                Text(text = "Last frame size: ${uiState.lastFrameSize}")
                uiState.lastError?.let { error ->
                    Text(text = "Error: $error")
                }
            }
        }

        Button(
            onClick = {
                if (uiState.permissionGranted) {
                    viewModel.onMicToggleClicked()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        ) {
            Text(
                text = when {
                    !uiState.permissionGranted -> "Grant mic permission"
                    uiState.isRecording -> "Stop recording"
                    else -> "Start recording"
                }
            )
        }

        Button(
            onClick = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        ) {
            Text("Request permission again")
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = "Audio stream")
                SelectionContainer {
                    Text(
                        text = uiState.streamText.ifBlank {
                            "No frames captured yet."
                        }
                    )
                }
            }
        }
    }
}

private fun VoiceState.toDisplayText(): String = when (this) {
    VoiceState.Idle -> "Idle"
    VoiceState.Listening -> "Listening"
    VoiceState.Processing -> "Processing"
    is VoiceState.Result -> "Result: $text"
    is VoiceState.Error -> "Error: $message"
}
