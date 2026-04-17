package com.sublime.supersherpa.core.ai.modeldelivery

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class InstalledModelRegistry(
    val activeVersion: String,
    val activePath: String,
    val revision: String,
    val files: List<RemoteModelFile>,
)

data class ResolvedModelLocation(
    val source: ModelSource,
    val modelDirectory: String? = null,
)

enum class ModelSource {
    Ota,
    Missing,
}

class ModelDirectoryResolver(
    filesDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val modelsRoot = File(filesDir, "models")
    private var cachedResolvedModelLocation: ResolvedModelLocation? = null

    fun resolveActiveModelPathOrNull(): String? {
        return resolveModelLocation().modelDirectory
    }

    fun resolveModelSource(): ModelSource {
        return resolveModelLocation().source
    }

    fun invalidateCache() {
        cachedResolvedModelLocation = null
    }

    internal fun resolveModelLocation(): ResolvedModelLocation {
        cachedResolvedModelLocation?.let { return it }

        val resolvedModelLocation = loadRegistry()
            ?.takeIf(::isInstalledModelValid)
            ?.let { registry ->
                ResolvedModelLocation(
                    source = ModelSource.Ota,
                    modelDirectory = registry.activePath,
                )
            }
            ?: fallbackModelLocation()

        cachedResolvedModelLocation = resolvedModelLocation
        return resolvedModelLocation
    }

    internal fun installedModelDirectory(version: String): File {
        return File(modelsRoot, version)
    }

    internal fun pendingModelDirectory(version: String): File {
        return File(modelsRoot, "$version.tmp")
    }

    internal fun backupModelDirectory(version: String): File {
        return File(modelsRoot, "$version.backup")
    }

    internal fun registryFile(): File {
        return File(modelsRoot, "registry.json")
    }

    internal fun activateModel(
        manifest: RemoteModelManifest,
        installedDirectory: File,
    ) {
        modelsRoot.mkdirs()
        registryFile().writeText(
            buildJsonObject {
                put("active_version", manifest.version)
                put("active_path", installedDirectory.absolutePath)
                put("revision", manifest.revision)
                put(
                    "files",
                    buildJsonArray {
                        manifest.files.forEach { file ->
                            add(
                                buildJsonObject {
                                    put("name", file.name)
                                    put("sha256", file.sha256)
                                },
                            )
                        }
                    },
                )
            }.toString(),
        )
        invalidateCache()
    }

    internal fun hasRequiredFiles(
        directory: File,
        requiredFiles: Set<String> = TranscriptionModelDelivery.REQUIRED_MODEL_FILES,
    ): Boolean {
        return requiredFiles.all { requiredFile ->
            File(directory, requiredFile).isFile
        }
    }

    internal fun verifyModelFiles(
        directory: File,
        files: List<RemoteModelFile>,
    ): Boolean {
        return files.all { file ->
            val candidate = File(directory, file.name)
            candidate.isFile && sha256(candidate) == file.sha256
        }
    }

    internal fun loadRegistry(): InstalledModelRegistry? {
        val file = registryFile()
        if (!file.isFile) return null

        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            InstalledModelRegistry(
                activeVersion = root["active_version"]?.jsonPrimitive?.content.orEmpty(),
                activePath = root["active_path"]?.jsonPrimitive?.content.orEmpty(),
                revision = root["revision"]?.jsonPrimitive?.content.orEmpty(),
                files = root["files"]?.jsonArray?.map { file ->
                    val item = file.jsonObject
                    RemoteModelFile(
                        name = item["name"]?.jsonPrimitive?.content.orEmpty(),
                        sha256 = item["sha256"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }.orEmpty(),
            )
        }.getOrNull()
    }

    private fun isInstalledModelValid(registry: InstalledModelRegistry): Boolean {
        if (registry.activeVersion.isBlank() || registry.activePath.isBlank() || registry.revision.isBlank()) {
            clearRegistry()
            return false
        }
        if (registry.files.isEmpty()) {
            clearRegistry()
            return false
        }

        val installedDirectory = File(registry.activePath)
        val isValid = installedDirectory.isDirectory &&
            hasRequiredFiles(installedDirectory) &&
            verifyModelFiles(installedDirectory, registry.files)

        if (!isValid) {
            clearRegistry()
        }
        return isValid
    }

    private fun fallbackModelLocation(): ResolvedModelLocation {
        return ResolvedModelLocation(source = ModelSource.Missing)
    }

    private fun clearRegistry() {
        if (registryFile().exists()) {
            registryFile().delete()
        }
        invalidateCache()
    }
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
