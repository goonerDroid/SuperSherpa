package com.sublime.supersherpa.data.repository

import android.content.Context
import com.sublime.supersherpa.core.ai.TranscriptionEngine
import com.sublime.supersherpa.core.audio.MicRecorder
import com.sublime.supersherpa.domain.repository.NotesRepository
import com.sublime.supersherpa.domain.repository.TranscriptionRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

class DefaultTranscriptionRepository(
    private val recorder: MicRecorder,
    private val engine: TranscriptionEngine,
    private val notesRepository: NotesRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranscriptionRepository, Closeable {
    private val lock = Any()
    private val sessionIds = AtomicLong(0L)
    private var sessionActive: Boolean = false
    private var activeSessionId: Long = 0L
    private var audioChannel: Channel<ShortArray>? = null
    private var processorJob: kotlinx.coroutines.Job? = null

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override suspend fun initialize(context: Context): Result<Unit> {
        return engine.initialize(context)
    }

    override suspend fun start(): Result<Unit> {
        synchronized(lock) {
            if (sessionActive) {
                val error = IllegalStateException("A transcription session is already active.")
                _lastError.value = error.message
                return Result.failure(error)
            }
            sessionActive = true
            activeSessionId = sessionIds.incrementAndGet()
            _partialTranscript.value = ""
            _lastError.value = null
        }

        val startResult = engine.startStreaming()
        if (startResult.isFailure) {
            synchronized(lock) {
                sessionActive = false
                activeSessionId = sessionIds.incrementAndGet()
            }
            _lastError.value = startResult.exceptionOrNull()?.message
            return startResult
        }

        val sessionId = synchronized(lock) { activeSessionId }

        return try {
            val channel = Channel<ShortArray>(Channel.BUFFERED)
            val job = scope.launch {
                processAudioFrames(sessionId, channel)
            }
            synchronized(lock) {
                audioChannel = channel
                processorJob = job
            }
            recorder.startRecording { frame ->
                val currentChannel = synchronized(lock) { audioChannel }
                currentChannel?.trySend(frame.copyOf())?.onFailure {
                    _lastError.value = it?.message ?: "Unable to queue audio frame."
                }
            }
            Result.success(Unit)
        } catch (throwable: Throwable) {
            recorder.stopRecordingIfActive()
            try {
                engine.stop()
            } catch (_: Throwable) {
                // Best-effort cleanup.
            }
            synchronized(lock) {
                sessionActive = false
                activeSessionId = sessionIds.incrementAndGet()
                audioChannel?.close()
                audioChannel = null
                processorJob = null
            }
            _lastError.value = throwable.message
            Result.failure(throwable)
        }
    }

    override suspend fun stop(): Result<String> {
        val wasActive = synchronized(lock) {
            if (!sessionActive) {
                return Result.success(_partialTranscript.value)
            }

            sessionActive = false
            activeSessionId = sessionIds.incrementAndGet()
            true
        }

        if (!wasActive) {
            return Result.success(_partialTranscript.value)
        }

        recorder.stopRecordingIfActive()
        val processor = synchronized(lock) {
            audioChannel?.close()
            audioChannel = null
            val job = processorJob
            processorJob = null
            job
        }
        processor?.join()
        val stopResult = engine.stop()
        if (stopResult.isFailure) {
            val throwable = stopResult.exceptionOrNull()
                ?: IllegalStateException("Unable to finalize transcription.")
            _lastError.value = throwable.message
            return Result.failure(throwable)
        }

        val cleanedTranscript = stopResult.getOrThrow().trim()
        _partialTranscript.value = cleanedTranscript
        _lastError.value = null
        if (cleanedTranscript.isNotBlank()) {
            notesRepository.insert(
                TranscriptionNote(
                    text = cleanedTranscript,
                    createdAt = clock(),
                ),
            )
        }
        return Result.success(cleanedTranscript)
    }

    override fun observeNotes(): Flow<List<TranscriptionNote>> {
        return notesRepository.observeAllByCreatedAtDesc()
    }

    override fun currentTranscript(): String = _partialTranscript.value

    override fun close() {
        val processor = synchronized(lock) {
            sessionActive = false
            activeSessionId = sessionIds.incrementAndGet()
            audioChannel?.close()
            audioChannel = null
            val currentJob = processorJob
            processorJob = null
            currentJob
        }
        processor?.cancel()
        recorder.close()
        engine.close()
        scope.cancel()
        _lastError.value = null
        _partialTranscript.value = ""
    }

    private fun isCurrentSession(sessionId: Long): Boolean {
        return synchronized(lock) {
            sessionActive && activeSessionId == sessionId
        }
    }

    private suspend fun processAudioFrames(
        sessionId: Long,
        channel: Channel<ShortArray>,
    ) {
        for (frame in channel) {
            if (!isCurrentSession(sessionId)) {
                continue
            }

            val acceptResult = engine.acceptAudio(frame)
            if (acceptResult.isFailure) {
                _lastError.value = acceptResult.exceptionOrNull()?.message
                continue
            }

            engine.getResult()
                .onSuccess { transcript ->
                    if (isCurrentSession(sessionId)) {
                        _partialTranscript.value = transcript
                    }
                }
                .onFailure { throwable ->
                    _lastError.value = throwable.message
                }
        }
    }
}
