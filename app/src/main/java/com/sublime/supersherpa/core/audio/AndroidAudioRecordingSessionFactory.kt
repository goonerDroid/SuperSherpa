package com.sublime.supersherpa.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlin.math.max

internal class AndroidAudioRecordingSessionFactory(
    private val config: AudioRecorderConfig = AudioRecorderConfig(),
) : AudioRecordingSessionFactory {
    override fun create(): AudioRecordingSession {
        val minBufferSizeInBytes = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelMask,
            config.encoding,
        )
        require(minBufferSizeInBytes > 0) {
            "Unable to determine a valid AudioRecord buffer size: $minBufferSizeInBytes"
        }

        val minBufferSizeInShorts = minBufferSizeInBytes / Short.SIZE_BYTES
        val targetBufferSizeInShorts = max(minBufferSizeInShorts, config.sampleRateHz / 10)
        val targetBufferSizeInBytes = targetBufferSizeInShorts * Short.SIZE_BYTES

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(config.sampleRateHz)
            .setChannelMask(config.channelMask)
            .setEncoding(config.encoding)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(config.audioSource)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(targetBufferSizeInBytes)
            .build()

        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize."
        }

        return AndroidAudioRecordingSession(
            audioRecord = audioRecord,
            bufferSizeInShorts = targetBufferSizeInShorts,
        )
    }
}

