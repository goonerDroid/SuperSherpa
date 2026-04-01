package com.sublime.supersherpa.core.ai

import android.content.Context
import java.io.Closeable

interface TranscriptionEngine : Closeable {
    suspend fun initialize(context: Context): Result<Unit>

    fun startStreaming(): Result<Unit>

    fun acceptAudio(chunk: ShortArray): Result<Unit>

    fun acceptAudio(chunk: ByteArray): Result<Unit>

    suspend fun getResult(): Result<String>

    suspend fun stop(): Result<String>
}
