package com.sublime.supersherpa.core.ai.modeldelivery.ui

import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.core.ai.modeldelivery.state.ModelDeliveryState

fun modelSourceLabel(
    modelSource: ModelSource,
    modelDeliveryState: ModelDeliveryState,
): String {
    val sourceLabel = when {
        modelDeliveryState is ModelDeliveryState.Downloading -> "Downloading"
        modelSource == ModelSource.Ota -> "OTA"
        else -> "Missing"
    }

    return "Model source: $sourceLabel"
}
