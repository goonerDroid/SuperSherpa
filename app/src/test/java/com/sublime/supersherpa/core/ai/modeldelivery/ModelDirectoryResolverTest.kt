package com.sublime.supersherpa.core.ai.modeldelivery

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDirectoryResolverTest {
    @Test
    fun resolveActiveModelPathOrNull_returnsActiveDirectory_whenRegistryAndChecksumsAreValid() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val manifest = testManifest(version = "parakeet-v1")
        val installedDirectory = resolver.installedModelDirectory(manifest.version)

        writeModelFiles(installedDirectory, manifest)
        resolver.activateModel(manifest, installedDirectory)

        assertEquals(installedDirectory.absolutePath, resolver.resolveActiveModelPathOrNull())
        assertEquals(ModelSource.Ota, resolver.resolveModelSource())
    }

    @Test
    fun resolveModelSource_returnsMissing_whenNoRegistryExists() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)

        assertNull(resolver.resolveActiveModelPathOrNull())
        assertEquals(ModelSource.Missing, resolver.resolveModelSource())
    }

    @Test
    fun resolveModelSource_returnsMissing_whenNoValidModelExists() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)

        assertNull(resolver.resolveActiveModelPathOrNull())
        assertEquals(ModelSource.Missing, resolver.resolveModelSource())
    }

    @Test
    fun resolveModelSource_clearsRegistry_whenInstalledModelIsCorrupt() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir = filesDir)
        val manifest = testManifest(version = "parakeet-v1")
        val installedDirectory = resolver.installedModelDirectory(manifest.version)

        writeModelFiles(installedDirectory, manifest)
        resolver.activateModel(manifest, installedDirectory)
        File(installedDirectory, "nemo128.onnx").writeText("corrupt")

        resolver.invalidateCache()

        assertNull(resolver.resolveActiveModelPathOrNull())
        assertEquals(ModelSource.Missing, resolver.resolveModelSource())
        assertFalse(resolver.registryFile().exists())
    }
}
