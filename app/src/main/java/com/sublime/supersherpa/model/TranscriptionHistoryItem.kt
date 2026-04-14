package com.sublime.supersherpa.model

import androidx.compose.runtime.Immutable

@Immutable
data class TranscriptionHistoryItem(
    val id: Long,
    val text: String,
    val createdAtEpochMillis: Long,
)
