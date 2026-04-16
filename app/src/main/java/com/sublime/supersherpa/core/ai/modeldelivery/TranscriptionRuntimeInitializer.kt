package com.sublime.supersherpa.core.ai.modeldelivery

import android.content.Context
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge

class TranscriptionRuntimeInitializer(
    private val modelDirectoryResolver: ModelDirectoryResolver,
) {
    fun initialize(context: Context, bridge: RustTranscriptionBridge) {
        bridge.initNative(
            context = context,
            modelDirectory = modelDirectoryResolver.resolveActiveModelPathOrNull(),
        )
    }
}
