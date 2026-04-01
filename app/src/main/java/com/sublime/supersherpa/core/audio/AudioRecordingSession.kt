package com.sublime.supersherpa.core.audio

interface AudioRecordingSessionFactory {
    fun create(): AudioRecordingSession
}

interface AudioRecordingSession {
    val bufferSizeInShorts: Int

    fun start()

    fun read(buffer: ShortArray, offset: Int, size: Int): Int

    fun stop()

    fun release()
}

