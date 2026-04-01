package com.sublime.supersherpa.data.repository

import com.sublime.supersherpa.data.local.NoteDao
import com.sublime.supersherpa.data.local.toDomainNotes
import com.sublime.supersherpa.data.local.toEntity
import com.sublime.supersherpa.domain.repository.NotesRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNotesRepository(
    private val noteDao: NoteDao,
) : NotesRepository {
    override suspend fun insert(note: TranscriptionNote) {
        noteDao.insert(note.toEntity())
    }

    override fun observeAllByCreatedAtDesc(): Flow<List<TranscriptionNote>> {
        return noteDao.observeAllByCreatedAtDesc().map { entities ->
            entities.toDomainNotes()
        }
    }
}
