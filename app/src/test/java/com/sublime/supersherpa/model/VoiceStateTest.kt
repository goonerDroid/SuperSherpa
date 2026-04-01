package com.sublime.supersherpa.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStateTest {
    @Test
    fun resultCarriesTranscriptText() {
        val state = VoiceState.Result("hello world")

        assertEquals("hello world", state.text)
    }

    @Test
    fun errorCarriesMessage() {
        val state = VoiceState.Error("missing model")

        assertEquals("missing model", state.message)
    }
}
