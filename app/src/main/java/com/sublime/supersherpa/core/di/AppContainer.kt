package com.sublime.supersherpa.core.di

import android.content.Context
import com.sublime.supersherpa.core.ai.modeldelivery.AndroidRemoteModelManifestProvider
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelDirectoryResolver
import com.sublime.supersherpa.core.ai.modeldelivery.HttpRemoteModelArtifactDownloader
import com.sublime.supersherpa.core.ai.modeldelivery.OtaModelDeliveryManager
import com.sublime.supersherpa.core.ai.modeldelivery.runtime.TranscriptionRuntimeInitializer
import com.sublime.supersherpa.feature.transcription.data.TranscriptHistoryRepository
import com.sublime.supersherpa.feature.transcription.data.local.SuperSherpaDatabase
import com.sublime.supersherpa.feature.transcription.domain.TranscriptHistoryStore

class AppContainer(context: Context) {
    private val modelDirectoryResolver: ModelDirectoryResolver by lazy(LazyThreadSafetyMode.NONE) {
        ModelDirectoryResolver(
            filesDir = context.filesDir,
        )
    }

    val transcriptHistoryStore: TranscriptHistoryStore by lazy {
        TranscriptHistoryRepository(
            dao = SuperSherpaDatabase.getInstance(context).transcriptHistoryDao(),
        )
    }

    val transcriptionRuntimeInitializer: TranscriptionRuntimeInitializer by lazy {
        TranscriptionRuntimeInitializer(
            modelDirectoryResolver = modelDirectoryResolver,
        )
    }

    val otaModelDeliveryManager: OtaModelDeliveryManager by lazy(LazyThreadSafetyMode.NONE) {
        OtaModelDeliveryManager(
            modelDirectoryResolver = modelDirectoryResolver,
            remoteModelManifestProvider = AndroidRemoteModelManifestProvider(context),
            remoteModelArtifactDownloader = HttpRemoteModelArtifactDownloader(),
        )
    }
}
