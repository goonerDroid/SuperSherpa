package com.sublime.supersherpa.core.ai.modeldelivery.state

sealed interface ModelDeliveryState {
    data object NotInstalled : ModelDeliveryState
    data object Installed : ModelDeliveryState
    data class Downloading(
        val stepLabel: String,
        val completedFiles: Int,
        val totalFiles: Int,
        val bytesDownloaded: Long? = null,
        val totalBytes: Long? = null,
    ) : ModelDeliveryState

    data class Failed(val message: String) : ModelDeliveryState
}
