package com.sublime.supersherpa.feature.transcription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sublime.supersherpa.core.ai.SherpaEngine
import com.sublime.supersherpa.core.audio.AudioRecorder
import com.sublime.supersherpa.core.clipboard.AndroidTranscriptClipboard
import com.sublime.supersherpa.data.local.AppDatabase
import com.sublime.supersherpa.data.repository.DefaultTranscriptionRepository
import com.sublime.supersherpa.data.repository.RoomNotesRepository
import com.sublime.supersherpa.domain.repository.TranscriptionRepository
import com.sublime.supersherpa.domain.usecase.CopyTranscriptUseCase
import com.sublime.supersherpa.domain.usecase.InitializeTranscriptionUseCase
import com.sublime.supersherpa.domain.usecase.ObserveNotesUseCase
import com.sublime.supersherpa.domain.usecase.ObservePartialTranscriptUseCase
import com.sublime.supersherpa.domain.usecase.StartTranscriptionStreamUseCase
import com.sublime.supersherpa.domain.usecase.StopAndFinalizeTranscriptionUseCase
import com.sublime.supersherpa.model.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscriptionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val database: AppDatabase
    private val transcriptionRepository: TranscriptionRepository
    private val initializeTranscriptionUseCase: InitializeTranscriptionUseCase
    private val startTranscriptionStreamUseCase: StartTranscriptionStreamUseCase
    private val stopAndFinalizeTranscriptionUseCase: StopAndFinalizeTranscriptionUseCase
    private val observePartialTranscriptUseCase: ObservePartialTranscriptUseCase
    private val observeNotesUseCase: ObserveNotesUseCase
    private val copyTranscriptUseCase: CopyTranscriptUseCase

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    init {
        database = Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "supersherpa.db",
        ).build()

        transcriptionRepository = DefaultTranscriptionRepository(
            recorder = AudioRecorder(),
            engine = SherpaEngine(),
            notesRepository = RoomNotesRepository(database.noteDao()),
        )
        initializeTranscriptionUseCase = InitializeTranscriptionUseCase(transcriptionRepository)
        startTranscriptionStreamUseCase = StartTranscriptionStreamUseCase(transcriptionRepository)
        stopAndFinalizeTranscriptionUseCase = StopAndFinalizeTranscriptionUseCase(transcriptionRepository)
        observePartialTranscriptUseCase = ObservePartialTranscriptUseCase(transcriptionRepository)
        observeNotesUseCase = ObserveNotesUseCase(transcriptionRepository)
        copyTranscriptUseCase = CopyTranscriptUseCase(
            clipboard = AndroidTranscriptClipboard(application),
            repository = transcriptionRepository,
        )

        viewModelScope.launch(Dispatchers.IO) {
            initializeTranscriptionUseCase(application.applicationContext)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(engineReady = true, lastError = null)
                    }
                }
                .onFailure { throwable ->
                    val message = throwable.message ?: "Unable to initialize transcription engine."
                    _uiState.update { state ->
                        state.copy(
                            engineReady = false,
                            lastError = message,
                            voiceState = VoiceState.Error(message),
                        )
                    }
                }
        }

        viewModelScope.launch {
            observePartialTranscriptUseCase().collectLatest { transcript ->
                _uiState.update { state ->
                    state.copy(partialTranscript = transcript)
                }
            }
        }

        viewModelScope.launch {
            observeNotesUseCase().collectLatest { notes ->
                _uiState.update { state ->
                    state.copy(notes = notes)
                }
            }
        }

        viewModelScope.launch {
            transcriptionRepository.lastError.collectLatest { error ->
                if (error != null) {
                    _uiState.update { state ->
                        state.copy(
                            lastError = error,
                            voiceState = VoiceState.Error(error),
                        )
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            stopIfNeeded(finalizeResult = false)
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
            _uiState.update { it.copy(lastError = "Microphone permission is required.") }
            return
        }
        if (!current.engineReady) {
            val message = "Transcription engine is not ready yet."
            _uiState.update {
                it.copy(
                    lastError = message,
                    voiceState = VoiceState.Error(message),
                )
            }
            return
        }

        if (current.isRecording) {
            stopIfNeeded(finalizeResult = true)
        } else {
            startRecording()
        }
    }

    fun onCopyClicked() {
        val copyResult = copyTranscriptUseCase()
        copyResult.onFailure { throwable ->
            _uiState.update { state ->
                state.copy(lastError = throwable.message ?: "Unable to copy transcript.")
            }
        }
    }

    private fun startRecording() {
        _uiState.update { current ->
            current.copy(
                isRecording = true,
                voiceState = VoiceState.Listening,
                lastError = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            startTranscriptionStreamUseCase()
                .onFailure { throwable ->
                    val message = throwable.message ?: "Unable to start transcription."
                    _uiState.update { state ->
                        state.copy(
                            isRecording = false,
                            voiceState = VoiceState.Error(message),
                            lastError = message,
                        )
                    }
                }
        }
    }

    private fun stopIfNeeded(finalizeResult: Boolean) {
        val current = _uiState.value
        if (!current.isRecording) {
            _uiState.update { state ->
                if (finalizeResult) {
                    state.copy(
                        voiceState = VoiceState.Result(state.partialTranscript),
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
                voiceState = VoiceState.Processing,
                lastError = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            stopAndFinalizeTranscriptionUseCase()
                .onSuccess { finalText ->
                    _uiState.update { state ->
                        state.copy(
                            voiceState = if (finalizeResult && finalText.isNotBlank()) {
                                VoiceState.Result(finalText)
                            } else {
                                VoiceState.Idle
                            },
                            partialTranscript = finalText,
                            lastError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = throwable.message ?: "Unable to finalize transcription."
                    _uiState.update { state ->
                        state.copy(
                            voiceState = VoiceState.Error(message),
                            lastError = message,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        transcriptionRepository.close()
        database.close()
        super.onCleared()
    }
}
