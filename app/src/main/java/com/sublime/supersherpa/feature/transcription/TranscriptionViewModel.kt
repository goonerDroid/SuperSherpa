package com.sublime.supersherpa.feature.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranscriptionViewModel : ViewModel() {
    private val _voiceState = MutableStateFlow(VoiceState())
    private var historyRepository: TranscriptHistoryRepository? = null
    private var historyCollectionJob: Job? = null

    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    val currentState: VoiceState
        get() = _voiceState.value

    fun attachHistoryRepository(repository: TranscriptHistoryRepository) {
        historyRepository = repository
        if (historyCollectionJob != null) {
            return
        }

        historyCollectionJob = viewModelScope.launch {
            repository.observeHistory().collect { items ->
                _voiceState.value = _voiceState.value.copy(history = items)
            }
        }
    }

    fun setListening() {
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.Listening,
            transcript = "",
            errorMessage = null,
            audioLevel = 0f,
        )
    }

    fun setProcessing() {
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.Processing,
            transcript = "",
            errorMessage = null,
            audioLevel = 0f,
        )
    }

    fun setResult(text: String) {
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.Result,
            transcript = text,
            errorMessage = null,
            audioLevel = 0f,
        )
    }

    fun setError(message: String) {
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.Error,
            errorMessage = message,
            transcript = "",
            audioLevel = 0f,
        )
    }

    fun setAudioLevel(level: Float) {
        _voiceState.value = _voiceState.value.copy(
            audioLevel = level.coerceIn(0f, 1f),
        )
    }

    fun reset() {
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.Idle,
            transcript = "",
            errorMessage = null,
            audioLevel = 0f,
        )
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

    suspend fun applyTranscribedText(text: String): Boolean {
        val cleanedText = text.trim()
        return if (cleanedText.isBlank()) {
            reset()
            false
        } else {
            setResult(cleanedText)
            try {
                historyRepository?.addTranscript(cleanedText)
            } catch (_: Throwable) {
                // Keep the live transcript visible even if history persistence fails.
            }
            true
        }
    }
}
