package com.sublime.supersherpa.data.local

import com.sublime.supersherpa.model.TranscriptionNote

fun NoteEntity.toDomain(): TranscriptionNote {
    return TranscriptionNote(
        id = id,
        text = text,
        createdAt = createdAt,
    )
}

fun TranscriptionNote.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        text = text,
        createdAt = createdAt,
    )
}

fun List<NoteEntity>.toDomainNotes(): List<TranscriptionNote> {
    return map { it.toDomain() }
}
