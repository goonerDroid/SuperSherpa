package com.sublime.supersherpa.core.ai.modeldelivery.install

import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelArtifactDownloader
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelManifestProvider
import com.sublime.supersherpa.core.ai.modeldelivery.filesystem.cleanupDirectory
import com.sublime.supersherpa.core.ai.modeldelivery.filesystem.movePath
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelDirectoryResolver
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.model.ResolvedModelLocation
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class OtaModelDeliveryCoordinator(
    private val modelDirectoryResolver: ModelDirectoryResolver,
    private val remoteModelManifestProvider: RemoteModelManifestProvider,
    private val remoteModelArtifactDownloader: RemoteModelArtifactDownloader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val installMutex = Mutex()
    private var installJob: Job? = null
    private var refreshJob: Job? = null

    private val installer = ModelInstallationPipeline(
        modelDirectoryResolver = modelDirectoryResolver,
        remoteModelManifestProvider = remoteModelManifestProvider,
        remoteModelArtifactDownloader = remoteModelArtifactDownloader,
        dispatcher = dispatcher,
    )

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelDeliveryState> = _state.asStateFlow()

    private val _modelSource = MutableStateFlow(currentModelSource())
    val modelSource: StateFlow<ModelSource> = _modelSource.asStateFlow()

    fun refresh(): Job? {
        if (_state.value is ModelDeliveryState.Downloading) return null
        if (refreshJob?.isActive == true) return refreshJob

        refreshJob = scope.launch {
            installMutex.withLock {
                withContext(dispatcher) {
                    modelDirectoryResolver.invalidateCache()
                    updateResolvedLocation(modelDirectoryResolver.resolveModelLocation())
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (refreshJob === job) {
                    refreshJob = null
                }
            }
        }
        return refreshJob
    }

    fun installModel() {
        if (installJob?.isActive == true) return

        installJob = scope.launch {
            installModelNow()
        }.also { job ->
            job.invokeOnCompletion {
                installJob = null
            }
        }
    }

    suspend fun installModelNow() {
        installMutex.withLock {
            try {
                val resolvedLocation = installer.installModel { state ->
                    _state.value = state
                }
                updateResolvedLocation(resolvedLocation)
            } catch (error: Throwable) {
                _state.value = ModelDeliveryState.Failed(
                    message = error.message ?: "Model download failed.",
                )
                _modelSource.value = currentModelSource()
            }
        }
    }

    private fun initialState(): ModelDeliveryState {
        return if (modelDirectoryResolver.resolveModelSource() == ModelSource.Ota) {
            ModelDeliveryState.Installed
        } else {
            ModelDeliveryState.NotInstalled
        }
    }

    private fun currentModelSource(): ModelSource {
        return modelDirectoryResolver.resolveModelSource()
    }

    private fun updateResolvedLocation(resolvedLocation: ResolvedModelLocation) {
        _state.value = if (resolvedLocation.source == ModelSource.Ota) {
            ModelDeliveryState.Installed
        } else {
            ModelDeliveryState.NotInstalled
        }
        _modelSource.value = resolvedLocation.source
    }
}

internal class ModelInstallationPipeline(
    private val modelDirectoryResolver: ModelDirectoryResolver,
    private val remoteModelManifestProvider: RemoteModelManifestProvider,
    private val remoteModelArtifactDownloader: RemoteModelArtifactDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun installModel(
        onStateChanged: (ModelDeliveryState) -> Unit,
    ): ResolvedModelLocation {
        return withContext(dispatcher) {
            installModelInternal(onStateChanged)
            modelDirectoryResolver.resolveModelLocation()
        }
    }

    private suspend fun installModelInternal(
        onStateChanged: (ModelDeliveryState) -> Unit,
    ) {
        onStateChanged(
            ModelDeliveryState.Downloading(
                stepLabel = "Checking manifest",
                completedFiles = 0,
                totalFiles = 0,
            ),
        )
        val manifest = remoteModelManifestProvider.fetchManifest()
        val pendingDirectory = modelDirectoryResolver.pendingModelDirectory(manifest.version)
        val installedDirectory = modelDirectoryResolver.installedModelDirectory(manifest.version)
        val backupDirectory = modelDirectoryResolver.backupModelDirectory(manifest.version)
        val totalFiles = manifest.files.size
        var completedFiles = 0

        cleanupDirectory(pendingDirectory)
        cleanupDirectory(backupDirectory)
        pendingDirectory.mkdirs()

        try {
            manifest.files.forEach { file ->
                remoteModelArtifactDownloader.download(
                    file = file,
                    baseUrl = manifest.baseUrl,
                    destination = File(pendingDirectory, file.name),
                    onProgress = { bytesDownloaded, totalBytes ->
                        onStateChanged(
                            ModelDeliveryState.Downloading(
                                stepLabel = "Downloading ${file.name}",
                                completedFiles = completedFiles,
                                totalFiles = totalFiles,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                            ),
                        )
                    },
                )
                completedFiles += 1
            }

            onStateChanged(
                ModelDeliveryState.Downloading(
                    stepLabel = "Verifying model",
                    completedFiles = completedFiles,
                    totalFiles = totalFiles,
                ),
            )
            if (!modelDirectoryResolver.hasRequiredFiles(pendingDirectory)) {
                throw IOException("Downloaded model package is missing required files.")
            }
            if (!modelDirectoryResolver.verifyModelFiles(pendingDirectory, manifest.files)) {
                throw IOException("Downloaded model package failed checksum verification.")
            }

            onStateChanged(
                ModelDeliveryState.Downloading(
                    stepLabel = "Activating model",
                    completedFiles = completedFiles,
                    totalFiles = totalFiles,
                ),
            )
            promotePendingModel(
                pendingDirectory = pendingDirectory,
                installedDirectory = installedDirectory,
                backupDirectory = backupDirectory,
            )
            modelDirectoryResolver.activateModel(manifest, installedDirectory)
        } catch (error: Throwable) {
            cleanupDirectory(pendingDirectory)
            cleanupDirectory(backupDirectory)
            throw error
        }
    }

    private fun promotePendingModel(
        pendingDirectory: File,
        installedDirectory: File,
        backupDirectory: File,
    ) {
        var movedInstalledToBackup = false

        try {
            if (installedDirectory.exists()) {
                movePath(installedDirectory, backupDirectory, replaceExisting = true)
                movedInstalledToBackup = true
            }

            movePath(pendingDirectory, installedDirectory, replaceExisting = true)
            cleanupDirectory(backupDirectory)
        } catch (error: Throwable) {
            if (movedInstalledToBackup && backupDirectory.exists() && !installedDirectory.exists()) {
                movePath(backupDirectory, installedDirectory, replaceExisting = true)
            }
            throw error
        } finally {
            cleanupDirectory(pendingDirectory)
        }
    }
}
