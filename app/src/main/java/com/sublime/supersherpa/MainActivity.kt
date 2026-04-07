package com.sublime.supersherpa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.sublime.supersherpa.feature.transcription.RustPrototypeScreen
import com.sublime.supersherpa.feature.transcription.RustPrototypeViewModel
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class MainActivity : ComponentActivity() {
    private val rustViewModel by lazy {
        ViewModelProvider(this).get(RustPrototypeViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        rustViewModel.attachNative(this)

        setContent {
            SuperSherpaTheme {
                RustPrototypeScreen(viewModel = rustViewModel)
            }
        }
    }

    override fun onDestroy() {
        rustViewModel.cleanupNative()
        super.onDestroy()
    }

    @Suppress("unused")
    fun onStatusUpdate(message: String) {
        rustViewModel.onNativeStatus(message)
    }

    @Suppress("unused")
    fun onAudioLevel(level: Float) {
        rustViewModel.onNativeAudioLevel(level)
    }

    @Suppress("unused")
    fun onTextTranscribed(text: String) {
        rustViewModel.onNativeText(text)
    }

    @Suppress("unused")
    fun onSubtitleText(text: String) {
        // Intentionally ignored. Live subtitles are not part of this prototype.
    }
}
