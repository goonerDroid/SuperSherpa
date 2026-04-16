package com.sublime.supersherpa.core.ai.modeldelivery

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
        val resolver = ModelDirectoryResolver(filesDir)
        val requestedAssets = mutableListOf<String>()
        val downloadedArtifacts = mutableListOf<String>()
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            packagedAssetCopier = PackagedModelAssetCopier { assetPath, destination ->
                requestedAssets += assetPath
                destination.parentFile?.mkdirs()
                destination.writeText("asset:$assetPath")
            },
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    artifact: RemoteModelArtifact,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    downloadedArtifacts += artifact.fileName
                    destination.parentFile?.mkdirs()
                    onProgress(256L, 1024L)
                    destination.writeText("remote:${artifact.fileName}")
                }
            },
        )

        manager.installModelNow()

        assertEquals(ModelDeliveryState.Installed, manager.state.value)
        assertEquals(ModelSource.Ota, manager.modelSource.value)
        assertEquals(
            TranscriptionModelDelivery.packagedAssetFiles.map(TranscriptionModelDelivery::packagedAssetPath),
            requestedAssets,
        )
        assertEquals(
            TranscriptionModelDelivery.remoteArtifacts.map(RemoteModelArtifact::fileName),
            downloadedArtifacts,
        )
        assertTrue(resolver.hasCompleteModel(resolver.activeModelDirectory()))
    }

    @Test
    fun installModelNow_preservesExistingActiveModel_whenDownloadFails() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir)
        val existingActiveDirectory = resolver.activeModelDirectory()

        existingActiveDirectory.mkdirs()
        TranscriptionModelDelivery.REQUIRED_FILES.forEach { fileName ->
            File(existingActiveDirectory, fileName).writeText("existing:$fileName")
        }

        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            packagedAssetCopier = PackagedModelAssetCopier { assetPath, destination ->
                destination.parentFile?.mkdirs()
                destination.writeText("asset:$assetPath")
            },
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    artifact: RemoteModelArtifact,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    if (artifact.fileName == "decoder_joint-model.int8.onnx") {
                        error("boom")
                    }
                    destination.parentFile?.mkdirs()
                    onProgress(128L, 1024L)
                    destination.writeText("remote:${artifact.fileName}")
                }
            },
        )

        manager.installModelNow()

        assertEquals(
            ModelDeliveryState.Failed("boom"),
            manager.state.value,
        )
        assertEquals(ModelSource.Ota, manager.modelSource.value)
        assertTrue(resolver.hasCompleteModel(existingActiveDirectory))
        assertEquals(
            "existing:decoder_joint-model.int8.onnx",
            File(existingActiveDirectory, "decoder_joint-model.int8.onnx").readText(),
        )
    }

    @Test
    fun managerDefaultsToBundledSource_withoutActiveOtaModel() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir)
        val manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            packagedAssetCopier = PackagedModelAssetCopier { _, _ -> },
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    artifact: RemoteModelArtifact,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) = Unit
            },
        )

        assertEquals(ModelSource.Bundled, manager.modelSource.value)
    }

    @Test
    fun installModelNow_reportsByteProgress_whenDownloaderProvidesIt() = runBlocking {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir)
        var sawProgressState: ModelDeliveryState.Downloading? = null
        lateinit var manager: OtaModelDeliveryManager
        manager = OtaModelDeliveryManager(
            modelDirectoryResolver = resolver,
            packagedAssetCopier = PackagedModelAssetCopier { assetPath, destination ->
                destination.parentFile?.mkdirs()
                destination.writeText("asset:$assetPath")
            },
            remoteModelArtifactDownloader = object : RemoteModelArtifactDownloader {
                override suspend fun download(
                    artifact: RemoteModelArtifact,
                    destination: File,
                    onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
                ) {
                    onProgress(512L, 2048L)
                    sawProgressState = manager.state.value as? ModelDeliveryState.Downloading
                    destination.parentFile?.mkdirs()
                    destination.writeText("remote:${artifact.fileName}")
                }
            },
        )

        manager.installModelNow()

        assertNotNull(sawProgressState)
        assertEquals(512L, sawProgressState?.bytesDownloaded)
        assertEquals(2048L, sawProgressState?.totalBytes)
    }
}
