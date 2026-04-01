package com.sublime.supersherpa.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sublime.supersherpa.data.repository.RoomNotesRepository
import com.sublime.supersherpa.domain.repository.NotesRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    @Test
    fun insertPersistsNotesNewestFirst() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repository: NotesRepository = RoomNotesRepository(database.noteDao())
            val olderNote = TranscriptionNote(
                text = "Older note",
                createdAt = 1_000L,
            )
            val newerNote = TranscriptionNote(
                text = "Newer note",
                createdAt = 2_000L,
            )

            repository.insert(olderNote)
            repository.insert(newerNote)
            val notes = repository.observeAllByCreatedAtDesc().first()

            assertEquals(listOf("Newer note", "Older note"), notes.map { it.text })
            assertEquals(listOf(2_000L, 1_000L), notes.map { it.createdAt })
        } finally {
            database.close()
        }
    }
}
