package com.sublime.supersherpa.core.audio

import android.media.AudioRecord

internal class AndroidAudioRecordingSession(
    private val audioRecord: AudioRecord,
    override val bufferSizeInShorts: Int,
) : AudioRecordingSession {
    private var started = false
    private var released = false

    override fun start() {
        check(!released) { "AudioRecordingSession has already been released." }
        audioRecord.startRecording()
        started = true
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        check(started) { "AudioRecordingSession must be started before reading." }
        return audioRecord.read(buffer, offset, size, AudioRecord.READ_BLOCKING)
    }

    override fun stop() {
        if (!started || released) {
            return
        }

        runCatching { audioRecord.stop() }
        started = false
    }

    override fun release() {
        if (released) {
            return
        }

        runCatching {
            if (started) {
                audioRecord.stop()
            }
        }
        audioRecord.release()
        started = false
        released = true
    }
}

