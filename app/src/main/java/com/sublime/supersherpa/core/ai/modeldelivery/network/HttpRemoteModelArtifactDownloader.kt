package com.sublime.supersherpa.core.ai.modeldelivery

import com.sublime.supersherpa.core.ai.modeldelivery.filesystem.movePath
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelFile
import com.sublime.supersherpa.core.ai.modeldelivery.model.sha256
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelArtifactDownloader
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ConnectTimeoutMillis = 15_000
private const val ReadTimeoutMillis = 30_000

class HttpRemoteModelArtifactDownloader(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteModelArtifactDownloader {
    override suspend fun download(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        withContext(dispatcher) {
            downloadVerifiedFile(file, baseUrl, destination, onProgress)
        }
    }

    private fun downloadVerifiedFile(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ) {
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        tempFile.delete()

        val connection = (URL(file.resolvedDownloadUrl(baseUrl)).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode while downloading ${file.name}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var bytesDownloaded = 0L
            onProgress(bytesDownloaded, totalBytes)

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(bytesDownloaded, totalBytes)
                        }
                        read = input.read(buffer)
                    }
                }
            }

            val actualHash = sha256(tempFile)
            if (actualHash != file.sha256) {
                throw IOException(
                    "Checksum mismatch for ${file.name}: expected ${file.sha256}, got $actualHash",
                )
            }

            movePath(tempFile, destination, replaceExisting = true)
        } finally {
            connection.disconnect()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
