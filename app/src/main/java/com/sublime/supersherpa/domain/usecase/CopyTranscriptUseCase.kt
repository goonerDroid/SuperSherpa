package com.sublime.supersherpa.domain.usecase

import com.sublime.supersherpa.core.clipboard.TranscriptClipboard
import com.sublime.supersherpa.domain.repository.TranscriptionRepository

class CopyTranscriptUseCase(
    private val clipboard: TranscriptClipboard,
    private val repository: TranscriptionRepository,
) {
    operator fun invoke(): Result<Unit> {
        val transcript = repository.currentTranscript().trim()
        if (transcript.isBlank()) {
            return Result.failure(IllegalStateException("There is no transcript to copy."))
        }

        return clipboard.copy(transcript)
    }
}
