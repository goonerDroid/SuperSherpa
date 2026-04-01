package com.sublime.supersherpa.domain.usecase

import com.sublime.supersherpa.domain.repository.TranscriptionRepository

class StartTranscriptionStreamUseCase(
    private val repository: TranscriptionRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.start()
    }
}
