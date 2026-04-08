package com.sublime.supersherpa.feature.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStateTest {
    @Test
    fun resultCarriesTranscriptText() {
        val state = VoiceState(
            phase = VoicePhase.Result,
            transcript = "hello world",
        )

        assertEquals("hello world", state.transcript)
    }

    @Test
    fun errorCarriesMessage() {
        val state = VoiceState(
            phase = VoicePhase.Error,
            errorMessage = "missing model",
        )

        assertEquals("missing model", state.errorMessage)
    }

    @Test
    fun audioLevelDefaultsToZero() {
        val state = VoiceState()

        assertEquals(0f, state.audioLevel, 0f)
    }
}
