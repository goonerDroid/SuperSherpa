package com.sublime.supersherpa.core.ai.modeldelivery

import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelFile
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelManifest
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.TranscriptionModelDelivery
import com.sublime.supersherpa.core.ai.modeldelivery.model.sha256
import java.io.File
import kotlin.io.path.createTempDirectory

fun testManifest(version: String): RemoteModelManifest {
    return RemoteModelManifest(
        modelId = TranscriptionModelDelivery.MODEL_ID,
        version = version,
        revision = "revision-$version",
        baseUrl = "https://example.com/$version",
        files = testModelFiles(),
    )
}

fun writeModelFiles(
    directory: File,
    manifest: RemoteModelManifest,
) {
    directory.mkdirs()
    manifest.files.forEach { file ->
        File(directory, file.name).apply {
            parentFile?.mkdirs()
            writeText("content:${file.name}")
        }
    }
}

private fun testModelFiles(): List<RemoteModelFile> {
    return listOf(
        "config.json",
        "vocab.txt",
        "encoder-model.int8.onnx",
        "decoder_joint-model.int8.onnx",
        "nemo128.onnx",
    ).map { fileName ->
        val content = "content:$fileName"
        RemoteModelFile(
            name = fileName,
            sha256 = sha256For(content),
        )
    }
}

private fun sha256For(content: String): String {
    val tempFile = createTempDirectory().resolve("hash.txt").toFile()
    tempFile.writeText(content)
    return sha256(tempFile)
}
