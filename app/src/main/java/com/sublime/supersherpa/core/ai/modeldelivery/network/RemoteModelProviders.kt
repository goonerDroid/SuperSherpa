package com.sublime.supersherpa.core.ai.modeldelivery.network

import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelFile
import com.sublime.supersherpa.core.ai.modeldelivery.manifest.RemoteModelManifest
import java.io.File

interface RemoteModelManifestProvider {
    suspend fun fetchManifest(): RemoteModelManifest
}

interface RemoteModelArtifactDownloader {
    suspend fun download(
        file: RemoteModelFile,
        baseUrl: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    )
}
