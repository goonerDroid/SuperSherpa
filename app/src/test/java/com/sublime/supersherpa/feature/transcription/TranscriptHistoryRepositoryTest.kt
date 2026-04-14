package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.core.history.local.TranscriptHistoryDao
import com.sublime.supersherpa.core.history.local.TranscriptHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptHistoryRepositoryTest {
    @Test
    fun addTranscriptInsertsAndTrimsHistory() = runBlocking {
        val dao = FakeTranscriptHistoryDao()
        val repository = TranscriptHistoryRepository(dao)

        repository.addTranscript("hello world")

        assertEquals(1, dao.insertedEntries.size)
        assertEquals("hello world", dao.insertedEntries.single().text)
        assertTrue(dao.insertedEntries.single().createdAtEpochMillis > 0)
        assertEquals(listOf(250), dao.trimLimits)
    }

    @Test
    fun observeHistoryMapsDatabaseEntitiesToUiItems() = runBlocking {
        val dao = FakeTranscriptHistoryDao(
            historyFlow = flowOf(
                listOf(
                    TranscriptHistoryEntity(
                        id = 7,
                        text = "saved transcript",
                        createdAtEpochMillis = 1234L,
                    ),
                ),
            ),
        )
        val repository = TranscriptHistoryRepository(dao)

        val items = repository.observeHistory().first()

        assertEquals(1, items.size)
        assertEquals(7, items.single().id)
        assertEquals("saved transcript", items.single().text)
        assertEquals(1234L, items.single().createdAtEpochMillis)
    }

    private class FakeTranscriptHistoryDao(
        private val historyFlow: Flow<List<TranscriptHistoryEntity>> = flowOf(emptyList()),
    ) : TranscriptHistoryDao {
        val insertedEntries = mutableListOf<TranscriptHistoryEntity>()
        val trimLimits = mutableListOf<Int>()

        override fun observeHistory(): Flow<List<TranscriptHistoryEntity>> = historyFlow

        override suspend fun insert(entry: TranscriptHistoryEntity) {
            insertedEntries += entry
        }

        override suspend fun trimToLimit(limit: Int) {
            trimLimits += limit
        }
    }
}
