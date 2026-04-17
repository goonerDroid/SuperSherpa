package com.sublime.supersherpa.core.ai.modeldelivery

import android.content.Context
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelManifest
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelManifestParser
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.TranscriptionModelDelivery
import com.sublime.supersherpa.core.ai.modeldelivery.network.RemoteModelManifestProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ConnectTimeoutMillis = 15_000
private const val ReadTimeoutMillis = 30_000

class AndroidRemoteModelManifestProvider(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val manifestUrl: String = TranscriptionModelDelivery.MANIFEST_URL,
    private val fallbackAssetPath: String = TranscriptionModelDelivery.MANIFEST_ASSET_PATH,
) : RemoteModelManifestProvider {
    override suspend fun fetchManifest(): RemoteModelManifest {
        return withContext(dispatcher) {
            try {
                if (manifestUrl.isBlank()) {
                    fetchFallbackAssetManifest()
                } else {
                    fetchRemoteManifest()
                }
            } catch (_: IOException) {
                fetchFallbackAssetManifest()
            }
        }
    }

    private fun fetchRemoteManifest(): RemoteModelManifest {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode while downloading model manifest.")
            }

            val manifestJson = connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
            return RemoteModelManifestParser.parse(manifestJson)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFallbackAssetManifest(): RemoteModelManifest {
        val manifestJson = context.assets.open(fallbackAssetPath).bufferedReader().use { reader ->
            reader.readText()
        }
        return RemoteModelManifestParser.parse(manifestJson)
    }
}
