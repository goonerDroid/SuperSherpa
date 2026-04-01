package com.sublime.supersherpa.core.audio

import android.media.AudioFormat
import android.media.MediaRecorder

data class AudioRecorderConfig(
    val sampleRateHz: Int = AudioRecorderDefaults.SAMPLE_RATE_HZ,
    val channelMask: Int = AudioRecorderDefaults.CHANNEL_MASK,
    val encoding: Int = AudioRecorderDefaults.ENCODING,
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
)

object AudioRecorderDefaults {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
}

