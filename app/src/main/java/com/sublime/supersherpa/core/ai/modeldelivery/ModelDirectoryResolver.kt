package com.sublime.supersherpa.core.ai.modeldelivery

import java.io.File

private const val ActiveModelDirectoryName = "active"
private const val PendingModelDirectorySuffix = ".pending"
private const val BackupModelDirectorySuffix = ".backup"

data class RemoteModelArtifact(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
)

enum class ModelSource {
    Ota,
    Bundled,
}

object TranscriptionModelDelivery {
    const val MODEL_ID = "parakeet-tdt-0.6b-v3-int8"
    private const val HuggingFaceRepo =
        "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main"

    val packagedAssetFiles = listOf(
        "config.json",
        "vocab.txt",
    )

    val remoteArtifacts = listOf(
        RemoteModelArtifact(
            fileName = "encoder-model.int8.onnx",
            downloadUrl = "$HuggingFaceRepo/encoder-model.int8.onnx?download=true",
            sha256 = "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09",
        ),
        RemoteModelArtifact(
            fileName = "decoder_joint-model.int8.onnx",
            downloadUrl = "$HuggingFaceRepo/decoder_joint-model.int8.onnx?download=true",
            sha256 = "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70",
        ),
        RemoteModelArtifact(
            fileName = "nemo128.onnx",
            downloadUrl = "$HuggingFaceRepo/nemo128.onnx?download=true",
            sha256 = "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f",
        ),
    )

    val REQUIRED_FILES = setOf(
        "config.json",
        "vocab.txt",
        "encoder-model.int8.onnx",
        "decoder_joint-model.int8.onnx",
        "nemo128.onnx",
    )

    val deliveryFileCount: Int
        get() = packagedAssetFiles.size + remoteArtifacts.size

    fun packagedAssetPath(fileName: String): String = "$MODEL_ID/$fileName"
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

    fun resolveModelSource(): ModelSource {
        return if (resolveActiveModelPathOrNull() != null) {
            ModelSource.Ota
        } else {
            ModelSource.Bundled
        }
    }

    internal fun activeModelDirectory(): File {
        return File(modelDeliveryRoot, "$ActiveModelDirectoryName/$modelId")
    }

    internal fun pendingModelDirectory(): File {
        return File(activeModelParentDirectory(), "$modelId$PendingModelDirectorySuffix")
    }

    internal fun backupModelDirectory(): File {
        return File(activeModelParentDirectory(), "$modelId$BackupModelDirectorySuffix")
    }

    private fun activeModelParentDirectory(): File {
        return File(modelDeliveryRoot, ActiveModelDirectoryName)
    }

    internal fun hasCompleteModel(directory: File): Boolean {
        return requiredFiles.all { requiredFile ->
            File(directory, requiredFile).isFile
        }
    }
}
