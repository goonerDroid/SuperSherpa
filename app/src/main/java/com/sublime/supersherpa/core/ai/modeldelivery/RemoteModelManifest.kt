package com.sublime.supersherpa.core.ai.modeldelivery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val ManifestSchemaVersion = 1
private val Sha256Regex = Regex("[0-9a-f]{64}")

object TranscriptionModelDelivery {
    const val MODEL_ID = "parakeet-tdt-0.6b-v3-int8"
    const val MANIFEST_URL =
        "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/8f23f0c/manifest.json?download=true"
    const val MANIFEST_ASSET_PATH = "model_delivery/manifest.json"

    val REQUIRED_MODEL_FILES = setOf(
        "vocab.txt",
        "encoder-model.int8.onnx",
        "decoder_joint-model.int8.onnx",
        "nemo128.onnx",
    )

    fun packagedAssetPath(fileName: String): String = "$MODEL_ID/$fileName"
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

object RemoteModelManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): RemoteModelManifest {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val schemaVersion = root.requiredInt("schema_version")
        require(schemaVersion == ManifestSchemaVersion) {
            "Unsupported manifest schema_version=$schemaVersion."
        }

        return RemoteModelManifest(
            modelId = root.requiredString("model_id"),
            version = root.requiredString("version"),
            revision = root.requiredString("revision"),
            baseUrl = root.requiredString("base_url"),
            files = root.requiredArray("files").map { it.asRemoteModelFile() },
        )
    }

    fun serialize(manifest: RemoteModelManifest): String {
        return buildJsonObject {
            put("schema_version", ManifestSchemaVersion)
            put("model_id", manifest.modelId)
            put("version", manifest.version)
            put("revision", manifest.revision)
            put("base_url", manifest.baseUrl)
            put(
                "files",
                buildJsonArray {
                    manifest.files.forEach { file ->
                        add(
                            buildJsonObject {
                                put("name", file.name)
                                put("sha256", file.sha256)
                                file.sizeBytes?.let { put("size_bytes", JsonPrimitive(it)) }
                                file.downloadUrl?.let { put("download_url", it) }
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    private fun JsonElement.asRemoteModelFile(): RemoteModelFile {
        val file = jsonObject
        return RemoteModelFile(
            name = file.requiredString("name"),
            sha256 = file.requiredString("sha256"),
            sizeBytes = file["size_bytes"]?.jsonPrimitive?.content?.toLongOrNull(),
            downloadUrl = file["download_url"]?.jsonPrimitive?.content,
        )
    }
}

private fun JsonObject.requiredString(key: String): String {
    return this[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: error("Manifest field '$key' is required.")
}

private fun JsonObject.requiredInt(key: String): Int {
    return this[key]?.jsonPrimitive?.intOrNull
        ?: error("Manifest field '$key' must be an integer.")
}

private fun JsonObject.requiredArray(key: String): JsonArray {
    return this[key]?.jsonArray ?: error("Manifest field '$key' must be an array.")
}
