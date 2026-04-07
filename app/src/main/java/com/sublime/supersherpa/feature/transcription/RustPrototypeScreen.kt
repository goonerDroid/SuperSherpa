package com.sublime.supersherpa.feature.transcription

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sublime.supersherpa.model.VoiceState

@Composable
fun RustPrototypeScreen(
    viewModel: RustPrototypeViewModel,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Rust-connected transcription prototype")
        Text(text = "The Rust native library owns capture and transcription.")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = "Permission: ${if (uiState.permissionGranted) "granted" else "missing"}")
                Text(text = "State: ${uiState.voiceState.toDisplayText()}")
                Text(text = "Native status: ${uiState.nativeStatus}")
                uiState.lastError?.let { error ->
                    Text(text = "Error: $error")
                }
                Text(text = "Mic level: ${(uiState.audioLevel * 100).toInt()}%")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (uiState.permissionGranted) {
                        viewModel.onPrimaryActionClicked()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            ) {
                Text(
                    text = when {
                        !uiState.permissionGranted -> "Grant mic permission"
                        uiState.isRecording -> "Stop"
                        else -> "Start"
                    }
                )
            }

            Button(
                onClick = viewModel::onCancelClicked,
            ) {
                Text("Cancel")
            }
        }

        Button(
            onClick = viewModel::onCopyClicked,
            enabled = uiState.transcript.isNotBlank(),
        ) {
            Text("Copy transcript")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = "Transcript")
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = uiState.transcript.ifBlank {
                            "Tap Start, speak into the mic, then stop to get the final Rust transcript."
                        },
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
