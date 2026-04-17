package com.sublime.supersherpa.core.ai.modeldelivery

import com.sublime.supersherpa.core.ai.modeldelivery.install.OtaModelDeliveryCoordinator
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelDirectoryResolver
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelArtifactDownloader
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelManifestProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OtaModelDeliveryManager(
    private val modelDirectoryResolver: ModelDirectoryResolver,
    private val remoteModelManifestProvider: RemoteModelManifestProvider,
    private val remoteModelArtifactDownloader: RemoteModelArtifactDownloader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val coordinator = OtaModelDeliveryCoordinator(
        modelDirectoryResolver = modelDirectoryResolver,
        remoteModelManifestProvider = remoteModelManifestProvider,
        remoteModelArtifactDownloader = remoteModelArtifactDownloader,
        scope = scope,
        dispatcher = dispatcher,
    )

    val state = coordinator.state
    val modelSource = coordinator.modelSource

    fun refresh() = coordinator.refresh()

    fun installModel() = coordinator.installModel()

    suspend fun installModelNow() = coordinator.installModelNow()
}
