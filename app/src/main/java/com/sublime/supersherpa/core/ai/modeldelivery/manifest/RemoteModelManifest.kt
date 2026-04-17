package com.sublime.supersherpa.core.ai.modeldelivery.manifest

private val Sha256Regex = Regex("[0-9a-f]{64}")

object TranscriptionModelDelivery {
    const val MODEL_ID = "parakeet-tdt-0.6b-v3-int8"
    const val MANIFEST_URL = ""
    const val MANIFEST_ASSET_PATH = "model_delivery/manifest.json"

    val REQUIRED_MODEL_FILES = setOf(
        "vocab.txt",
        "encoder-model.int8.onnx",
        "decoder_joint-model.int8.onnx",
        "nemo128.onnx",
    )
}

data class RemoteModelFile(
    val name: String,
    val sha256: String,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
) {
    fun resolvedDownloadUrl(baseUrl: String): String {
        return downloadUrl ?: "${baseUrl.trimEnd('/')}/$name?download=true"
    }
}

data class RemoteModelManifest(
    val modelId: String,
    val version: String,
    val revision: String,
    val baseUrl: String,
    val files: List<RemoteModelFile>,
) {
    init {
        require(modelId.isNotBlank()) { "Manifest model_id is required." }
        require(version.isNotBlank()) { "Manifest version is required." }
        require(revision.isNotBlank()) { "Manifest revision is required." }
        require(!revision.equals("main", ignoreCase = true)) {
            "Manifest revision must be pinned, not main."
        }
        require(baseUrl.isNotBlank()) { "Manifest base_url is required." }
        require(!baseUrl.contains("/resolve/main")) { "Manifest base_url must be pinned, not main." }
        require(files.isNotEmpty()) { "Manifest must list at least one file." }
        require(files.map(RemoteModelFile::name).distinct().size == files.size) {
            "Manifest file names must be unique."
        }
        require(TranscriptionModelDelivery.REQUIRED_MODEL_FILES.all(requiredFileNames()::contains)) {
            "Manifest is missing one or more required model files."
        }
        files.forEach { file ->
            require(file.name.isNotBlank()) { "Manifest file name is required." }
            require(file.sha256.matches(Sha256Regex)) {
                "Manifest SHA-256 is invalid for ${file.name}."
            }
            file.downloadUrl?.let { url ->
                require(url.isNotBlank()) { "Manifest download_url is invalid for ${file.name}." }
                require(!url.contains("/resolve/main")) {
                    "Manifest download_url must be pinned, not main."
                }
            }
        }
    }

    fun requiredFileNames(): Set<String> = files.mapTo(linkedSetOf(), RemoteModelFile::name)
}
