package com.sublime.supersherpa.core.ai.modeldelivery

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
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

private const val ConnectTimeoutMillis = 15_000
private const val ReadTimeoutMillis = 30_000

sealed interface ModelDeliveryState {
    data object NotInstalled : ModelDeliveryState
    data object Installed : ModelDeliveryState
    data class Downloading(
        val stepLabel: String,
        val completedFiles: Int,
        val totalFiles: Int,
        val bytesDownloaded: Long? = null,
        val totalBytes: Long? = null,
    ) : ModelDeliveryState

    data class Failed(val message: String) : ModelDeliveryState
}

fun interface PackagedModelAssetCopier {
    fun copy(assetPath: String, destination: File)
}

interface RemoteModelArtifactDownloader {
    suspend fun download(
        artifact: RemoteModelArtifact,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    )
}

class AndroidPackagedModelAssetCopier(
    private val context: Context,
) : PackagedModelAssetCopier {
    override fun copy(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

class HttpRemoteModelArtifactDownloader(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteModelArtifactDownloader {
    override suspend fun download(
        artifact: RemoteModelArtifact,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        withContext(dispatcher) {
            downloadVerifiedFile(artifact, destination, onProgress)
        }
    }

    private fun downloadVerifiedFile(
        artifact: RemoteModelArtifact,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        tempFile.delete()

        val connection = (URL(artifact.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode while downloading ${artifact.fileName}")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var bytesDownloaded = 0L
            onProgress(bytesDownloaded, totalBytes)
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(bytesDownloaded, totalBytes)
                        }
                        read = input.read(buffer)
                    }
                }
            }

            val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actualHash != artifact.sha256) {
                throw IOException(
                    "Checksum mismatch for ${artifact.fileName}: expected ${artifact.sha256}, got $actualHash",
                )
            }

            movePath(tempFile, destination, replaceExisting = true)
        } finally {
            connection.disconnect()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

class OtaModelDeliveryManager(
    private val modelDirectoryResolver: ModelDirectoryResolver,
    private val packagedAssetCopier: PackagedModelAssetCopier,
    private val remoteModelArtifactDownloader: RemoteModelArtifactDownloader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val installMutex = Mutex()
    private var installJob: Job? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelDeliveryState> = _state.asStateFlow()
    private val _modelSource = MutableStateFlow(currentModelSource())
    val modelSource: StateFlow<ModelSource> = _modelSource.asStateFlow()

    fun refresh() {
        if (_state.value is ModelDeliveryState.Downloading) return
        _state.value = initialState()
        _modelSource.value = currentModelSource()
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
                withContext(dispatcher) {
                    installModelInternal()
                }
                _state.value = ModelDeliveryState.Installed
                _modelSource.value = currentModelSource()
            } catch (error: Throwable) {
                cleanupDirectory(modelDirectoryResolver.pendingModelDirectory())
                _state.value = ModelDeliveryState.Failed(
                    message = error.message ?: "Model download failed.",
                )
                _modelSource.value = currentModelSource()
            }
        }
    }

    private fun initialState(): ModelDeliveryState {
        return if (modelDirectoryResolver.resolveActiveModelPathOrNull() != null) {
            ModelDeliveryState.Installed
        } else {
            ModelDeliveryState.NotInstalled
        }
    }

    private fun currentModelSource(): ModelSource {
        return modelDirectoryResolver.resolveModelSource()
    }

    private suspend fun installModelInternal() {
        val pendingDirectory = modelDirectoryResolver.pendingModelDirectory()
        val activeDirectory = modelDirectoryResolver.activeModelDirectory()
        val backupDirectory = modelDirectoryResolver.backupModelDirectory()
        val totalFiles = TranscriptionModelDelivery.deliveryFileCount
        var completedFiles = 0

        cleanupDirectory(pendingDirectory)
        cleanupDirectory(backupDirectory)
        pendingDirectory.mkdirs()

        TranscriptionModelDelivery.packagedAssetFiles.forEach { fileName ->
            _state.value = ModelDeliveryState.Downloading(
                stepLabel = "Preparing $fileName",
                completedFiles = completedFiles,
                totalFiles = totalFiles,
            )
            packagedAssetCopier.copy(
                assetPath = TranscriptionModelDelivery.packagedAssetPath(fileName),
                destination = File(pendingDirectory, fileName),
            )
            completedFiles += 1
        }

        TranscriptionModelDelivery.remoteArtifacts.forEach { artifact ->
            remoteModelArtifactDownloader.download(
                artifact = artifact,
                destination = File(pendingDirectory, artifact.fileName),
                onProgress = { bytesDownloaded, totalBytes ->
                    _state.value = ModelDeliveryState.Downloading(
                        stepLabel = "Downloading ${artifact.fileName}",
                        completedFiles = completedFiles,
                        totalFiles = totalFiles,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                    )
                },
            )
            completedFiles += 1
        }

        if (!modelDirectoryResolver.hasCompleteModel(pendingDirectory)) {
            throw IOException("Downloaded model package is incomplete.")
        }

        _state.value = ModelDeliveryState.Downloading(
            stepLabel = "Activating model",
            completedFiles = completedFiles,
            totalFiles = totalFiles,
        )
        promotePendingModel(
            pendingDirectory = pendingDirectory,
            activeDirectory = activeDirectory,
            backupDirectory = backupDirectory,
        )
    }

    private fun promotePendingModel(
        pendingDirectory: File,
        activeDirectory: File,
        backupDirectory: File,
    ) {
        var movedActiveToBackup = false

        try {
            if (activeDirectory.exists()) {
                movePath(activeDirectory, backupDirectory)
                movedActiveToBackup = true
            }

            movePath(pendingDirectory, activeDirectory)
            cleanupDirectory(backupDirectory)
        } catch (error: Throwable) {
            if (movedActiveToBackup && backupDirectory.exists() && !activeDirectory.exists()) {
                movePath(backupDirectory, activeDirectory)
            }
            throw error
        } finally {
            cleanupDirectory(pendingDirectory)
        }
    }
}

private fun cleanupDirectory(directory: File) {
    if (directory.exists()) {
        directory.deleteRecursively()
    }
}

private fun movePath(
    source: File,
    destination: File,
    replaceExisting: Boolean = false,
) {
    destination.parentFile?.mkdirs()

    try {
        if (replaceExisting) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } else {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    } catch (_: AtomicMoveNotSupportedException) {
        if (replaceExisting) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            Files.move(
                source.toPath(),
                destination.toPath(),
            )
        }
    }
}
