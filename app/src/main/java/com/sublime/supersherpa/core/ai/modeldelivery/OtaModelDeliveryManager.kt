package com.sublime.supersherpa.core.ai.modeldelivery

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

interface RemoteModelManifestProvider {
    suspend fun fetchManifest(): RemoteModelManifest
}

interface RemoteModelArtifactDownloader {
    suspend fun download(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    )
}

class AndroidRemoteModelManifestProvider(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val manifestUrl: String = TranscriptionModelDelivery.MANIFEST_URL,
    private val fallbackAssetPath: String = TranscriptionModelDelivery.MANIFEST_ASSET_PATH,
) : RemoteModelManifestProvider {
    override suspend fun fetchManifest(): RemoteModelManifest {
        return withContext(dispatcher) {
            try {
                if (manifestUrl.isBlank()) {
                    fetchFallbackAssetManifest()
                } else {
                    fetchRemoteManifest()
                }
            } catch (_: IOException) {
                fetchFallbackAssetManifest()
            }
        }
    }

    private fun fetchRemoteManifest(): RemoteModelManifest {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode while downloading model manifest.")
            }

            val manifestJson = connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
            return RemoteModelManifestParser.parse(manifestJson)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFallbackAssetManifest(): RemoteModelManifest {
        val manifestJson = context.assets.open(fallbackAssetPath).bufferedReader().use { reader ->
            reader.readText()
        }
        return RemoteModelManifestParser.parse(manifestJson)
    }
}

class HttpRemoteModelArtifactDownloader(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteModelArtifactDownloader {
    override suspend fun download(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        withContext(dispatcher) {
            downloadVerifiedFile(file, baseUrl, destination, onProgress)
        }
    }

    private fun downloadVerifiedFile(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        tempFile.delete()

        val connection = (URL(file.resolvedDownloadUrl(baseUrl)).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode while downloading ${file.name}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var bytesDownloaded = 0L
            onProgress(bytesDownloaded, totalBytes)

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(bytesDownloaded, totalBytes)
                        }
                        read = input.read(buffer)
                    }
                }
            }

            val actualHash = sha256(tempFile)
            if (actualHash != file.sha256) {
                throw IOException(
                    "Checksum mismatch for ${file.name}: expected ${file.sha256}, got $actualHash",
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

class AndroidBundledModelAvailabilityChecker(
    private val context: Context,
) : BundledModelAvailabilityChecker {
    override fun isBundledModelAvailable(): Boolean {
        return TranscriptionModelDelivery.REQUIRED_MODEL_FILES.all { fileName ->
            runCatching {
                context.assets.open(TranscriptionModelDelivery.packagedAssetPath(fileName)).use { input ->
                    input.read()
                }
                true
            }.getOrDefault(false)
        }
    }
}

class OtaModelDeliveryManager(
    private val modelDirectoryResolver: ModelDirectoryResolver,
    private val remoteModelManifestProvider: RemoteModelManifestProvider,
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
        modelDirectoryResolver.invalidateCache()
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

    private suspend fun installModelInternal() {
        _state.value = ModelDeliveryState.Downloading(
            stepLabel = "Checking manifest",
            completedFiles = 0,
            totalFiles = 0,
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
                        _state.value = ModelDeliveryState.Downloading(
                            stepLabel = "Downloading ${file.name}",
                            completedFiles = completedFiles,
                            totalFiles = totalFiles,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                        )
                    },
                )
                completedFiles += 1
            }

            _state.value = ModelDeliveryState.Downloading(
                stepLabel = "Verifying model",
                completedFiles = completedFiles,
                totalFiles = totalFiles,
            )
            if (!modelDirectoryResolver.hasRequiredFiles(pendingDirectory)) {
                throw IOException("Downloaded model package is missing required files.")
            }
            if (!modelDirectoryResolver.verifyModelFiles(pendingDirectory, manifest.files)) {
                throw IOException("Downloaded model package failed checksum verification.")
            }

            _state.value = ModelDeliveryState.Downloading(
                stepLabel = "Activating model",
                completedFiles = completedFiles,
                totalFiles = totalFiles,
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
