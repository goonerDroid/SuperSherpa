package com.sublime.supersherpa.core.ai.modeldelivery

import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState
import com.sublime.supersherpa.core.ai.modeldelivery.ui.modelSourceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDeliveryUiTextTest {
    @Test
    fun modelSourceLabel_returnsDownloading_whenDeliveryIsInProgress() {
        val label = modelSourceLabel(
            modelSource = ModelSource.Missing,
            modelDeliveryState = ModelDeliveryState.Downloading(
                stepLabel = "Downloading model",
                completedFiles = 1,
                totalFiles = 3,
            ),
        )

        assertEquals("Model source: Downloading", label)
    }

    @Test
    fun modelSourceLabel_returnsOta_whenModelIsInstalled() {
        val label = modelSourceLabel(
            modelSource = ModelSource.Ota,
            modelDeliveryState = ModelDeliveryState.Installed,
        )

        assertEquals("Model source: OTA", label)
    }

    @Test
    fun modelSourceLabel_returnsMissing_whenModelIsUnavailable() {
        val label = modelSourceLabel(
            modelSource = ModelSource.Missing,
            modelDeliveryState = ModelDeliveryState.NotInstalled,
        )

        assertEquals("Model source: Missing", label)
    }
}
