package com.sublime.supersherpa.feature.transcription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sublime.supersherpa.core.clipboard.AndroidTranscriptClipboard
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge
import com.sublime.supersherpa.model.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RustPrototypeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bridge = RustTranscriptionBridge()
    private val clipboard = AndroidTranscriptClipboard(application)

    private val _uiState = MutableStateFlow(RustPrototypeUiState())
    val uiState: StateFlow<RustPrototypeUiState> = _uiState.asStateFlow()

    fun attachNative(activity: Activity) {
        runCatching {
            bridge.initNative(activity)
        }.onFailure { throwable ->
            publishStatus(
                "Error: ${throwable.message ?: "Failed to initialize Rust bridge."}",
                VoiceState.Error(throwable.message ?: "Failed to initialize Rust bridge."),
                isRecording = false,
            )
        }
    }

    fun cleanupNative() {
        runCatching { bridge.cleanupNative() }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            runCatching { bridge.cancelRecording() }
        }

        _uiState.update { current ->
            current.copy(
                permissionGranted = granted,
                isRecording = if (granted) current.isRecording else false,
                voiceState = if (granted) current.voiceState else VoiceState.Idle,
                lastError = if (granted) null else "Microphone permission is required.",
            )
        }
    }

    fun onPrimaryActionClicked() {
        val current = _uiState.value
        if (!current.permissionGranted) {
            publishStatus(
                "Error: Microphone permission is required.",
                VoiceState.Error("Microphone permission is required."),
            )
            return
        }

        if (current.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bridge.cancelRecording() }
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        voiceState = VoiceState.Idle,
                        transcript = "",
                        nativeStatus = "Canceled",
                        audioLevel = 0f,
                        lastError = null,
                    )
                }
            }
        }
    }

    fun onCopyClicked() {
        val transcript = _uiState.value.transcript.trim()
        if (transcript.isBlank()) {
            publishStatus(
                "Error: Nothing to copy yet.",
                VoiceState.Error("Nothing to copy yet."),
            )
            return
        }

        clipboard.copy(transcript).onFailure { throwable ->
            publishStatus(
                "Error: ${throwable.message ?: "Unable to copy transcript."}",
                VoiceState.Error(throwable.message ?: "Unable to copy transcript."),
            )
        }.onSuccess {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _uiState.update { current ->
                    current.copy(lastError = null, nativeStatus = "Transcript copied")
                }
            }
        }
    }

    fun onNativeStatus(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update { current ->
                when {
                    message.startsWith("Error:") -> {
                        val errorText = message.removePrefix("Error:").trim().ifBlank { message }
                        current.copy(
                            nativeStatus = message,
                            voiceState = VoiceState.Error(errorText),
                            isRecording = false,
                            lastError = errorText,
                        )
                    }
                    message == "Ready" -> current.copy(
                        nativeStatus = message,
                        voiceState = if (current.voiceState == VoiceState.Processing) {
                            VoiceState.Processing
                        } else {
                            VoiceState.Idle
                        },
                        lastError = null,
                    )
                    else -> current.copy(
                        nativeStatus = message,
                        lastError = null,
                    )
                }
            }
        }
    }

    fun onNativeAudioLevel(level: Float) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update { current ->
                current.copy(audioLevel = level.coerceIn(0f, 1f))
            }
        }
    }

    fun onNativeText(text: String) {
        val cleanedText = text.trim()
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update { current ->
                current.copy(
                    isRecording = false,
                    voiceState = if (cleanedText.isBlank()) {
                        VoiceState.Idle
                    } else {
                        VoiceState.Result(cleanedText)
                    },
                    transcript = cleanedText,
                    nativeStatus = "Ready",
                    audioLevel = 0f,
                    lastError = null,
                )
            }
        }
    }

    private fun startRecording() {
        _uiState.update { current ->
            current.copy(
                isRecording = true,
                voiceState = VoiceState.Listening,
                nativeStatus = "Recording...",
                transcript = "",
                audioLevel = 0f,
                lastError = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bridge.startRecording() }
                .onFailure { throwable ->
                    publishStatus(
                        "Error: ${throwable.message ?: "Unable to start recording."}",
                        VoiceState.Error(throwable.message ?: "Unable to start recording."),
                        isRecording = false,
                    )
                }
        }
    }

    private fun stopRecording() {
        _uiState.update { current ->
            current.copy(
                isRecording = false,
                voiceState = VoiceState.Processing,
                nativeStatus = "Transcribing...",
                lastError = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bridge.stopRecording() }
                .onFailure { throwable ->
                    publishStatus(
                        "Error: ${throwable.message ?: "Unable to stop recording."}",
                        VoiceState.Error(throwable.message ?: "Unable to stop recording."),
                        isRecording = false,
                    )
                }
        }
    }

    private fun publishStatus(
        message: String,
        voiceState: VoiceState,
        isRecording: Boolean = _uiState.value.isRecording,
    ) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update { current ->
                current.copy(
                    nativeStatus = message,
                    voiceState = voiceState,
                    isRecording = isRecording,
                    lastError = if (voiceState is VoiceState.Error) voiceState.message else null,
                )
            }
        }
    }

    override fun onCleared() {
        runCatching { bridge.cleanupNative() }
        super.onCleared()
    }
}
