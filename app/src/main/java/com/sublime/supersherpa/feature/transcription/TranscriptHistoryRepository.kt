package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.core.history.local.TranscriptHistoryDao
import com.sublime.supersherpa.core.history.local.TranscriptHistoryEntity
import com.sublime.supersherpa.model.TranscriptionHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class TranscriptHistoryRepository(
    private val dao: TranscriptHistoryDao,
) : TranscriptHistoryStore {
    override fun observeHistory(): Flow<List<TranscriptionHistoryItem>> {
        return dao.observeHistory().map { entities ->
            entities.map { entity ->
                TranscriptionHistoryItem(
                    id = entity.id,
                    text = entity.text,
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                )
            }
        }.distinctUntilChanged()
    }

    override suspend fun addTranscript(text: String) {
        withContext(Dispatchers.IO) {
            dao.insert(
                TranscriptHistoryEntity(
                    text = text,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            dao.trimToLimit(MAX_HISTORY_SIZE)
        }
    }

    override suspend fun deleteTranscripts(ids: Collection<Long>) {
        val transcriptIds = ids.toList()
        if (transcriptIds.isEmpty()) return

        withContext(Dispatchers.IO) {
            dao.deleteByIds(transcriptIds)
        }
    }

    private companion object {
        const val MAX_HISTORY_SIZE = 250
    }
}
