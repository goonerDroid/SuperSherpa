package com.sublime.supersherpa.core.ai.modeldelivery

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDirectoryResolverTest {
    @Test
    fun resolveActiveModelPathOrNull_returnsNull_whenModelIsIncomplete() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir)
        val modelDirectory = resolver.activeModelDirectory()

        modelDirectory.mkdirs()
        File(modelDirectory, "config.json").writeText("{}")

        assertNull(resolver.resolveActiveModelPathOrNull())
    }

    @Test
    fun resolveActiveModelPathOrNull_returnsActiveDirectory_whenModelIsComplete() {
        val filesDir = createTempDirectory().toFile()
        val resolver = ModelDirectoryResolver(filesDir)
        val modelDirectory = resolver.activeModelDirectory()

        modelDirectory.mkdirs()
        TranscriptionModelDelivery.REQUIRED_FILES.forEach { fileName ->
            File(modelDirectory, fileName).writeText("ok")
        }

        assertEquals(modelDirectory.absolutePath, resolver.resolveActiveModelPathOrNull())
    }
}
