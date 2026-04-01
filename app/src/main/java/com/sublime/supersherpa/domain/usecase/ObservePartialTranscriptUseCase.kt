package com.sublime.supersherpa.domain.usecase

import com.sublime.supersherpa.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.StateFlow

class ObservePartialTranscriptUseCase(
    private val repository: TranscriptionRepository,
) {
    operator fun invoke(): StateFlow<String> {
        return repository.partialTranscript
    }
}
