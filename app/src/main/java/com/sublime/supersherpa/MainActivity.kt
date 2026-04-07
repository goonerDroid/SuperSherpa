package com.sublime.supersherpa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sublime.supersherpa.feature.transcription.TranscriptionHomeScreen
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SuperSherpaTheme {
                TranscriptionHomeScreen()
            }
        }
    }
}
