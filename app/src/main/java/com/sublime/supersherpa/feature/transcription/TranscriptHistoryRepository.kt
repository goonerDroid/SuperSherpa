package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.core.history.local.TranscriptHistoryDao
import com.sublime.supersherpa.core.history.local.TranscriptHistoryEntity
import com.sublime.supersherpa.model.TranscriptionHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptHistoryRepository(
    private val dao: TranscriptHistoryDao,
) {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeHistory(): Flow<List<TranscriptionHistoryItem>> {
        return dao.observeHistory().map { entities ->
            entities.map { entity ->
                TranscriptionHistoryItem(
                    id = entity.id,
                    text = entity.text,
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                )
            }
        }
    }

    suspend fun addTranscript(text: String) {
        withContext(NonCancellable) {
            writeScope.launch {
                dao.insert(
                    TranscriptHistoryEntity(
                        text = text,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                dao.trimToLimit(MAX_HISTORY_SIZE)
            }.join()
        }
    }

    private companion object {
        const val MAX_HISTORY_SIZE = 250
    }
}
