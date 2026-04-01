package com.sublime.supersherpa.domain.usecase

import com.sublime.supersherpa.domain.repository.TranscriptionRepository

class StopAndFinalizeTranscriptionUseCase(
    private val repository: TranscriptionRepository,
) {
    suspend operator fun invoke(): Result<String> {
        return repository.stop()
    }
}
