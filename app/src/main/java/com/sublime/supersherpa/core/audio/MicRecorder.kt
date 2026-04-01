package com.sublime.supersherpa.core.audio

import java.io.Closeable

interface MicRecorder : Closeable {
    fun isRecording(): Boolean

    fun startRecording(onAudioFrame: (ShortArray) -> Unit)

    fun stopRecording()

    fun stopRecordingIfActive(): Boolean
}

