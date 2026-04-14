package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.model.TranscriptionHistoryItem
import kotlinx.coroutines.flow.Flow

interface TranscriptHistoryStore {
    fun observeHistory(): Flow<List<TranscriptionHistoryItem>>

    suspend fun addTranscript(text: String)

    suspend fun deleteTranscripts(ids: Collection<Long>)
}
