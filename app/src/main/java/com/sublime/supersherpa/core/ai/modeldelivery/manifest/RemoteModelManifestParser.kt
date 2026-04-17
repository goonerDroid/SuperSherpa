package com.sublime.supersherpa.core.ai.modeldelivery.manifest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val ManifestSchemaVersion = 1

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
