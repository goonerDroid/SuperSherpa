package com.sublime.supersherpa.data.repository

import com.sublime.supersherpa.core.ai.TranscriptionEngine
import com.sublime.supersherpa.core.audio.MicRecorder
import com.sublime.supersherpa.domain.repository.NotesRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTranscriptionRepositoryTest {
    @Test
    fun stopAutoSavesNonBlankTranscriptAsNote() = kotlinx.coroutines.runBlocking {
        val recorder = FakeMicRecorder()
        val engine = FakeTranscriptionEngine(
            partialResult = "Hello world",
            finalResult = "Hello world",
        )
        val notesRepository = FakeNotesRepository()
        val repository = DefaultTranscriptionRepository(
            recorder = recorder,
            engine = engine,
            notesRepository = notesRepository,
        )

        try {
            assertTrue(repository.start().isSuccess)
            recorder.emitFrame(shortArrayOf(1, 2, 3))
            awaitCondition {
                repository.currentTranscript() == "Hello world"
            }

            val stopResult = repository.stop()
            assertTrue(stopResult.isSuccess)
            assertEquals("Hello world", stopResult.getOrThrow())

            val notes = notesRepository.observeAllByCreatedAtDesc().first()
            assertEquals(1, notes.size)
            assertEquals("Hello world", notes.first().text)
        } finally {
            repository.close()
        }
    }

    @Test
    fun stopSkipsBlankTranscriptPersistence() = kotlinx.coroutines.runBlocking {
        val recorder = FakeMicRecorder()
        val engine = FakeTranscriptionEngine(
            partialResult = "",
            finalResult = "   ",
        )
        val notesRepository = FakeNotesRepository()
        val repository = DefaultTranscriptionRepository(
            recorder = recorder,
            engine = engine,
            notesRepository = notesRepository,
        )

        try {
            assertTrue(repository.start().isSuccess)
            recorder.emitFrame(shortArrayOf(7, 8, 9))
            awaitCondition {
                repository.currentTranscript().isEmpty()
            }

            val stopResult = repository.stop()
            assertTrue(stopResult.isSuccess)
            assertEquals("", stopResult.getOrThrow())

            val notes = notesRepository.observeAllByCreatedAtDesc().first()
            assertTrue(notes.isEmpty())
        } finally {
            repository.close()
        }
    }

    private fun awaitCondition(
        timeoutMs: Long = 2_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }

        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }
}

private class FakeMicRecorder : MicRecorder {
    private var callback: ((ShortArray) -> Unit)? = null

    override fun isRecording(): Boolean = callback != null

    override fun startRecording(onAudioFrame: (ShortArray) -> Unit) {
        check(callback == null) { "Recorder already started." }
        callback = onAudioFrame
    }

    override fun stopRecording() {
        if (!stopRecordingIfActive()) {
            throw IllegalStateException("Recorder is not recording.")
        }
    }

    override fun stopRecordingIfActive(): Boolean {
        val active = callback != null
        callback = null
        return active
    }

    override fun close() {
        callback = null
    }

    fun emitFrame(frame: ShortArray) {
        callback?.invoke(frame)
    }
}

private class FakeTranscriptionEngine(
    var partialResult: String,
    var finalResult: String,
) : TranscriptionEngine {
    override suspend fun initialize(context: android.content.Context): Result<Unit> {
        return Result.success(Unit)
    }

    override fun startStreaming(): Result<Unit> = Result.success(Unit)

    override fun acceptAudio(chunk: ShortArray): Result<Unit> = Result.success(Unit)

    override fun acceptAudio(chunk: ByteArray): Result<Unit> = Result.success(Unit)

    override suspend fun getResult(): Result<String> = Result.success(partialResult)

    override suspend fun stop(): Result<String> = Result.success(finalResult)

    override fun close() = Unit
}

private class FakeNotesRepository : NotesRepository {
    private val notesFlow = MutableStateFlow<List<TranscriptionNote>>(emptyList())

    override suspend fun insert(note: TranscriptionNote) {
        notesFlow.value = (notesFlow.value + note).sortedWith(
            compareByDescending<TranscriptionNote> { it.createdAt }
                .thenByDescending { it.id },
        )
    }

    override fun observeAllByCreatedAtDesc(): Flow<List<TranscriptionNote>> = notesFlow.asStateFlow()
}
