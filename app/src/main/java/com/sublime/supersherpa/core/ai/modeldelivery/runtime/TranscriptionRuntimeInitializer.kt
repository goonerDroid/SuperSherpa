package com.sublime.supersherpa.core.ai.modeldelivery.runtime

import android.content.Context
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelDirectoryResolver
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge

class TranscriptionRuntimeInitializer(
    private val modelDirectoryResolver: ModelDirectoryResolver,
) {
    fun initialize(context: Context, bridge: RustTranscriptionBridge) {
        val resolvedModelLocation = modelDirectoryResolver.resolveModelLocation()
        bridge.initNative(
            context = context,
            modelDirectory = resolvedModelLocation.modelDirectory,
        )
    }
}
