package com.sublime.supersherpa.feature.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublime.supersherpa.model.TranscriptionHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranscriptionViewModel(
    private val historyStore: TranscriptHistoryStore? = null,
) : ViewModel() {
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    private val _history = MutableStateFlow<List<TranscriptionHistoryItem>>(emptyList())

    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    val history: StateFlow<List<TranscriptionHistoryItem>> = _history.asStateFlow()

    val currentState: VoiceState
        get() = _voiceState.value

    init {
        historyStore?.let { repository ->
            viewModelScope.launch {
                repository.observeHistory().collect { items ->
                    _history.value = items
                }
            }
        }
    }

    fun setListening() {
        _voiceState.value = VoiceState.Listening()
    }

    fun setProcessing() {
        _voiceState.value = VoiceState.Processing()
    }

    fun setResult(text: String) {
        _voiceState.value = VoiceState.Result(transcript = text)
    }

    fun setError(message: String) {
        _voiceState.value = VoiceState.Error(errorMessage = message)
    }

    fun setAudioLevel(level: Float) {
        _voiceState.value = _voiceState.value.withAudioLevel(level.coerceIn(0f, 1f))
    }

    fun reset() {
        _voiceState.value = VoiceState.Idle
    }

    fun applyNativeStatus(message: String) {
        when (val event = parseNativeTranscriptionMessage(message)) {
            is NativeTranscriptionMessage.Error -> {
                setError(
                    event.message,
                )
            }
            NativeTranscriptionMessage.Listening -> {
                setListening()
                setAudioLevel(0f)
            }
            NativeTranscriptionMessage.Processing -> {
                setProcessing()
                setAudioLevel(0f)
            }
            NativeTranscriptionMessage.Ready -> setAudioLevel(0f)
            NativeTranscriptionMessage.Canceled -> reset()
            is NativeTranscriptionMessage.Transcript -> Unit
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
                historyStore?.addTranscript(cleanedText)
            } catch (_: Throwable) {
                // Keep the live transcript visible even if history persistence fails.
            }
            true
        }
    }

    fun deleteHistoryItems(ids: Collection<Long>) {
        val transcriptIds = ids.toSet()
        if (transcriptIds.isEmpty()) return

        viewModelScope.launch {
            try {
                historyStore?.deleteTranscripts(transcriptIds)
            } catch (_: Throwable) {
                // History deletion is best-effort; the list will refresh from the source of truth.
            }
        }
    }
}
