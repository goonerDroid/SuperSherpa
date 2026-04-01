package com.sublime.supersherpa.domain.usecase

import android.content.Context
import com.sublime.supersherpa.domain.repository.TranscriptionRepository

class InitializeTranscriptionUseCase(
    private val repository: TranscriptionRepository,
) {
    suspend operator fun invoke(context: Context): Result<Unit> {
        return repository.initialize(context)
    }
}
