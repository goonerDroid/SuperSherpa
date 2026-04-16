package com.sublime.supersherpa.core.di

import android.content.Context
import com.sublime.supersherpa.core.ai.modeldelivery.ModelDirectoryResolver
import com.sublime.supersherpa.core.ai.modeldelivery.TranscriptionRuntimeInitializer
import com.sublime.supersherpa.feature.transcription.data.TranscriptHistoryRepository
import com.sublime.supersherpa.feature.transcription.data.local.SuperSherpaDatabase
import com.sublime.supersherpa.feature.transcription.domain.TranscriptHistoryStore

class AppContainer(context: Context) {
    val transcriptHistoryStore: TranscriptHistoryStore by lazy {
        TranscriptHistoryRepository(
            dao = SuperSherpaDatabase.getInstance(context).transcriptHistoryDao(),
        )
    }

    val transcriptionRuntimeInitializer: TranscriptionRuntimeInitializer by lazy {
        TranscriptionRuntimeInitializer(
            modelDirectoryResolver = ModelDirectoryResolver(context.filesDir),
        )
    }
}
