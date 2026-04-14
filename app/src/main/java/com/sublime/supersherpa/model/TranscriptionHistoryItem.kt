package com.sublime.supersherpa.model

data class TranscriptionHistoryItem(
    val id: Long,
    val text: String,
    val createdAtEpochMillis: Long,
)
