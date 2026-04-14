package com.sublime.supersherpa.core.di

import android.content.Context
import com.sublime.supersherpa.core.history.local.SuperSherpaDatabase
import com.sublime.supersherpa.feature.transcription.TranscriptHistoryRepository
import com.sublime.supersherpa.feature.transcription.TranscriptHistoryStore

class AppContainer(context: Context) {
    val transcriptHistoryStore: TranscriptHistoryStore by lazy {
        TranscriptHistoryRepository(
            dao = SuperSherpaDatabase.getInstance(context).transcriptHistoryDao(),
        )
    }
}
