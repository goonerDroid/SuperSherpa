package com.sublime.supersherpa.feature.transcription.ui

internal fun formatDownloadSize(
    downloadedBytes: Long?,
    totalBytes: Long?,
): String? {
    val downloadedLabel = downloadedBytes?.let(::formatBytes)
    val totalLabel = totalBytes?.let(::formatBytes)

    return when {
        downloadedLabel != null && totalLabel != null -> "$downloadedLabel / $totalLabel"
        downloadedLabel != null -> downloadedLabel
        else -> null
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"

    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = -1

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return String.format("%.1f %s", value, units[unitIndex])
}
