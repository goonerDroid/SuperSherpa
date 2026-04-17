package com.sublime.supersherpa.core.ai.modeldelivery

import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelFile
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelManifest
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelDirectoryResolver
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelArtifactDownloader
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelManifestProvider
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaModelDeliveryManagerTest {
    @Test
    fun installModelNow_downloadsAndActivatesModel() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val manifest = testManifest(version = "parakeet-v2")
        val downloadedArtifacts = mutableListOf<String>()
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            remoteModelManifestProvider = FakeRemoteModelManifestProvider(manifest),
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    file: RemoteModelFile,
                    baseUrl: String,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    downloadedArtifacts += file.name
                    destination.parentFile?.mkdirs()
                    onProgress(256L, 1024L)
                    destination.writeText("content:${file.name}")
                }
            },
        )

        manager.installModelNow()

        val installedDirectory = resolver.installedModelDirectory(manifest.version)
        assertEquals(ModelDeliveryState.Installed, manager.state.value)
        assertEquals(ModelSource.Ota, manager.modelSource.value)
        assertEquals(manifest.files.map(RemoteModelFile::name), downloadedArtifacts)
        assertTrue(resolver.hasRequiredFiles(installedDirectory))
        assertTrue(resolver.verifyModelFiles(installedDirectory, manifest.files))
        assertEquals(manifest.version, resolver.loadRegistry()?.activeVersion)
        assertEquals(installedDirectory.absolutePath, resolver.loadRegistry()?.activePath)
    }

    @Test
    fun installModelNow_preservesExistingActiveModel_whenDownloadFails() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val existingManifest = testManifest(version = "parakeet-v1")
        val existingDirectory = resolver.installedModelDirectory(existingManifest.version)
        writeModelFiles(existingDirectory, existingManifest)
        resolver.activateModel(existingManifest, existingDirectory)

        val nextManifest = testManifest(version = "parakeet-v2")
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            remoteModelManifestProvider = FakeRemoteModelManifestProvider(nextManifest),
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    file: RemoteModelFile,
                    baseUrl: String,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    if (file.name == "decoder_joint-model.int8.onnx") {
                        error("boom")
                    }
                    destination.parentFile?.mkdirs()
                    onProgress(128L, 1024L)
                    destination.writeText("content:${file.name}")
                }
            },
        )

        manager.installModelNow()

        assertEquals(ModelDeliveryState.Failed("boom"), manager.state.value)
        assertEquals(ModelSource.Ota, manager.modelSource.value)
        assertEquals(existingDirectory.absolutePath, resolver.resolveActiveModelPathOrNull())
        assertEquals(existingManifest.version, resolver.loadRegistry()?.activeVersion)
        assertEquals(
            "content:decoder_joint-model.int8.onnx",
            File(existingDirectory, "decoder_joint-model.int8.onnx").readText(),
        )
    }

    @Test
    fun managerDefaultsToMissingSource_withoutActiveModel() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            remoteModelManifestProvider = FakeRemoteModelManifestProvider(testManifest(version = "parakeet-v1")),
            remoteModelArtifactDownloader = NoOpRemoteModelArtifactDownloader(),
        )

        assertEquals(ModelSource.Missing, manager.modelSource.value)
    }

    @Test
    fun refreshUpdatesModelSourceWithoutBlockingCallers() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val manifest = testManifest(version = "parakeet-v1")
        val installedDirectory = resolver.installedModelDirectory(manifest.version)
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            remoteModelManifestProvider = FakeRemoteModelManifestProvider(manifest),
            remoteModelArtifactDownloader = NoOpRemoteModelArtifactDownloader(),
        )

        writeModelFiles(installedDirectory, manifest)
        resolver.activateModel(manifest, installedDirectory)

        val refreshJob = manager.refresh()
        refreshJob?.join()

        assertEquals(ModelSource.Ota, manager.modelSource.value)
        assertEquals(ModelDeliveryState.Installed, manager.state.value)
    }

    @Test
    fun installModelNow_reportsByteProgress_whenDownloaderProvidesIt() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        var sawProgressState: ModelDeliveryState.Downloading? = null
        lateinit var manager: OtaModelDeliveryManager
        manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            remoteModelManifestProvider = FakeRemoteModelManifestProvider(testManifest(version = "parakeet-v1")),
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    file: RemoteModelFile,
                    baseUrl: String,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    onProgress(512L, 2048L)
                    sawProgressState = manager.state.value as? ModelDeliveryState.Downloading
                    destination.parentFile?.mkdirs()
                    destination.writeText("content:${file.name}")
                }
            },
        )

        manager.installModelNow()

        assertNotNull(sawProgressState)
        assertEquals(512L, sawProgressState?.bytesDownloaded)
        assertEquals(2048L, sawProgressState?.totalBytes)
    }
}

private class FakeRemoteModelManifestProvider(
    private val manifest: RemoteModelManifest,
) : RemoteModelManifestProvider {
    override suspend fun fetchManifest(): RemoteModelManifest = manifest
}

private class NoOpRemoteModelArtifactDownloader : RemoteModelArtifactDownloader {
    override suspend fun download(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) = Unit
}
