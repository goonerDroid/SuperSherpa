package com.sublime.supersherpa

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sublime.supersherpa.feature.transcription.TranscriptionScreen
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
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
                    onOpenKeyboardSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
        }
    }
}
