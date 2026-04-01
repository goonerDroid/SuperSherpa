package com.sublime.supersherpa.domain.repository

import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    suspend fun insert(note: TranscriptionNote)

    fun observeAllByCreatedAtDesc(): Flow<List<TranscriptionNote>>
}
