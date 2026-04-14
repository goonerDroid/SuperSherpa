package com.sublime.supersherpa.feature.transcription.domain

import androidx.compose.runtime.Immutable

@Immutable
data class TranscriptionHistoryItem(
    val id: Long,
    val text: String,
    val createdAtEpochMillis: Long,
)
