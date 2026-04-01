package com.sublime.supersherpa.feature.transcription

import androidx.lifecycle.ViewModel
import com.sublime.supersherpa.core.audio.AudioRecorder
import com.sublime.supersherpa.core.audio.MicRecorder
import com.sublime.supersherpa.model.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MicrophoneTestViewModel(
    private val recorder: MicRecorder = AudioRecorder(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MicrophoneTestUiState())
    val uiState: StateFlow<MicrophoneTestUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            stopRecordingInternal(finalizeResult = false)
        }

        _uiState.update { current ->
            current.copy(
                permissionGranted = granted,
                voiceState = if (granted) current.voiceState else VoiceState.Idle,
                lastError = if (granted) null else "Microphone permission is required.",
                isRecording = if (granted) current.isRecording else false,
            )
        }
    }

    fun onMicToggleClicked() {
        val current = _uiState.value
        if (!current.permissionGranted) {
            _uiState.update {
                it.copy(lastError = "Microphone permission is required.")
            }
            return
        }

        if (current.isRecording) {
            stopRecordingInternal(finalizeResult = true)
        } else {
            startRecordingInternal()
        }
    }

    private fun startRecordingInternal() {
        _uiState.update { current ->
            current.copy(
                isRecording = true,
                voiceState = VoiceState.Listening,
                frameCount = 0,
                lastFrameSize = 0,
                streamText = "",
                lastError = null,
            )
        }

        try {
            recorder.startRecording { frame ->
                _uiState.update { current ->
                    if (!current.isRecording) {
                        current
                    } else {
                        current.copy(
                            frameCount = current.frameCount + 1,
                            lastFrameSize = frame.size,
                            streamText = buildStreamText(current.streamText, frame),
                            voiceState = VoiceState.Listening,
                        )
                    }
                }
            }
        } catch (exception: IllegalStateException) {
            _uiState.update { current ->
                current.copy(
                    isRecording = false,
                    voiceState = VoiceState.Error(exception.message ?: "Unable to start recorder."),
                    lastError = exception.message ?: "Unable to start recorder.",
                )
            }
        }
    }

    private fun stopRecordingInternal(finalizeResult: Boolean) {
        val current = _uiState.value
        if (!current.isRecording) {
            _uiState.update { state ->
                if (finalizeResult) {
                    state.copy(
                        voiceState = VoiceState.Result("Captured ${state.frameCount} frame(s)."),
                        lastError = null,
                    )
                } else {
                    state.copy(
                        isRecording = false,
                        voiceState = VoiceState.Idle,
                        lastError = null,
                    )
                }
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                isRecording = false,
                voiceState = if (finalizeResult) {
                    VoiceState.Result("Captured ${state.frameCount} frame(s).")
                } else {
                    VoiceState.Idle
                },
                lastError = null,
            )
        }

        recorder.stopRecordingIfActive()
    }

    override fun onCleared() {
        recorder.close()
        super.onCleared()
    }

    private fun buildStreamText(
        existingText: String,
        frame: ShortArray,
    ): String {
        val nextLine = frame.joinToString(prefix = "[", postfix = "]", limit = 64)
        val lines = existingText
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()
        lines.add(nextLine)
        return lines.takeLast(20).joinToString(separator = "\n")
    }
}
