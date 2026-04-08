package com.sublime.supersherpa.feature.transcription

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranscriptionViewModel : ViewModel() {
    private val _voiceState = MutableStateFlow(VoiceState())

    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    val currentState: VoiceState
        get() = _voiceState.value

    fun setListening() {
        _voiceState.value = VoiceState(phase = VoicePhase.Listening)
    }

    fun setProcessing() {
        _voiceState.value = VoiceState(phase = VoicePhase.Processing)
    }

    fun setResult(text: String) {
        _voiceState.value = VoiceState(
            phase = VoicePhase.Result,
            transcript = text,
        )
    }

    fun setError(message: String) {
        _voiceState.value = VoiceState(
            phase = VoicePhase.Error,
            errorMessage = message,
        )
    }

    fun setAudioLevel(level: Float) {
        _voiceState.value = _voiceState.value.copy(
            audioLevel = level.coerceIn(0f, 1f),
        )
    }

    fun reset() {
        _voiceState.value = VoiceState()
    }

    fun applyNativeStatus(message: String) {
        when {
            message.startsWith("Error:") -> {
                setError(
                    message.removePrefix("Error:").trim().ifBlank {
                        "Transcription failed."
                    },
                )
            }
            message == "Listening..." -> {
                setListening()
                setAudioLevel(0f)
            }
            message == "Transcribing..." -> {
                setProcessing()
                setAudioLevel(0f)
            }
            message == "Ready" -> setAudioLevel(0f)
            message == "Canceled" -> reset()
        }
    }

    fun applyTranscribedText(text: String): Boolean {
        if (currentState.phase != VoicePhase.Processing) {
            return false
        }

        val cleanedText = text.trim()
        return if (cleanedText.isBlank()) {
            reset()
            false
        } else {
            setResult(cleanedText)
            true
        }
    }
}
