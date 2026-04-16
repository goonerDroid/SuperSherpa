package com.sublime.supersherpa.core.ai.modeldelivery

import java.io.File

private const val ActiveModelDirectoryName = "active"

object TranscriptionModelDelivery {
    const val MODEL_ID = "parakeet-tdt-0.6b-v3-int8"

    val REQUIRED_FILES = setOf(
        "config.json",
        "vocab.txt",
        "encoder-model.int8.onnx",
        "decoder_joint-model.int8.onnx",
        "nemo128.onnx",
    )
}

class ModelDirectoryResolver(
    filesDir: File,
    private val modelId: String = TranscriptionModelDelivery.MODEL_ID,
    private val requiredFiles: Set<String> = TranscriptionModelDelivery.REQUIRED_FILES,
) {
    private val modelDeliveryRoot = File(filesDir, "model_delivery")

    fun resolveActiveModelPathOrNull(): String? {
        val activeDirectory = activeModelDirectory()
        return activeDirectory.takeIf(::hasCompleteModel)?.absolutePath
    }

    internal fun activeModelDirectory(): File {
        return File(modelDeliveryRoot, "$ActiveModelDirectoryName/$modelId")
    }

    internal fun hasCompleteModel(directory: File): Boolean {
        return requiredFiles.all { requiredFile ->
            File(directory, requiredFile).isFile
        }
    }
}
