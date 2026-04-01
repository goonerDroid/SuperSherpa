package com.sublime.supersherpa.domain.repository

import android.content.Context
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface TranscriptionRepository : Closeable {
    val partialTranscript: StateFlow<String>
    val lastError: StateFlow<String?>

    suspend fun initialize(context: Context): Result<Unit>

    suspend fun start(): Result<Unit>

    suspend fun stop(): Result<String>

    fun observeNotes(): Flow<List<TranscriptionNote>>

    fun currentTranscript(): String
}
