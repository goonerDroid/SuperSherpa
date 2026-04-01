package com.sublime.supersherpa.domain.usecase

import com.sublime.supersherpa.domain.repository.TranscriptionRepository
import com.sublime.supersherpa.model.TranscriptionNote
import kotlinx.coroutines.flow.Flow

class ObserveNotesUseCase(
    private val repository: TranscriptionRepository,
) {
    operator fun invoke(): Flow<List<TranscriptionNote>> {
        return repository.observeNotes()
    }
}
