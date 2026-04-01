package com.sublime.supersherpa.domain.usecase

import android.content.Context
import com.sublime.supersherpa.core.clipboard.TranscriptClipboard
import com.sublime.supersherpa.domain.repository.TranscriptionRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTranscriptUseCaseTest {
    @Test
    fun copiesCurrentTranscriptToClipboard() {
        val repository = FakeTranscriptionRepository("Copy this text")
        val clipboard = FakeTranscriptClipboard()
        val useCase = CopyTranscriptUseCase(
            clipboard = clipboard,
            repository = repository,
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals("Copy this text", clipboard.copiedText)
    }
}

private class FakeTranscriptClipboard : TranscriptClipboard {
    var copiedText: String? = null
        private set

    override fun copy(text: String): Result<Unit> {
        copiedText = text
        return Result.success(Unit)
    }
}

private class FakeTranscriptionRepository(
    transcript: String,
) : TranscriptionRepository {
    override val partialTranscript: StateFlow<String> = MutableStateFlow(transcript).asStateFlow()
    override val lastError: StateFlow<String?> = MutableStateFlow(null).asStateFlow()

    override suspend fun initialize(context: Context): Result<Unit> = Result.success(Unit)

    override suspend fun start(): Result<Unit> = Result.success(Unit)

    override suspend fun stop(): Result<String> = Result.success(partialTranscript.value)

    override fun observeNotes(): Flow<List<TranscriptionNote>> = emptyFlow()

    override fun currentTranscript(): String = partialTranscript.value

    override fun close() = Unit
}
