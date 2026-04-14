package com.sublime.supersherpa.core.history.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcript_history")
data class TranscriptHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val createdAtEpochMillis: Long,
)
