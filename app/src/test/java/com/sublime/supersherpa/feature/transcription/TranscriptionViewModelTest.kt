package com.sublime.supersherpa.feature.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TranscriptionViewModelTest {
    @Test
    fun nativeStatusTransitionsUpdateVoiceState() {
        val viewModel = TranscriptionViewModel()

        viewModel.applyNativeStatus("Listening...")
        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)
        assertEquals(0f, viewModel.currentState.audioLevel, 0f)

        viewModel.applyNativeStatus("Transcribing...")
        assertEquals(VoicePhase.Processing, viewModel.currentState.phase)
        assertEquals(0f, viewModel.currentState.audioLevel, 0f)

        viewModel.applyNativeStatus("Ready")
        assertEquals(VoicePhase.Processing, viewModel.currentState.phase)

        viewModel.applyNativeStatus("Canceled")
        assertEquals(VoicePhase.Idle, viewModel.currentState.phase)
    }

    @Test
    fun transcriptTextIsCapturedAndShownWhenNativeTextArrives() {
        runBlocking {
            val viewModel = TranscriptionViewModel()

            viewModel.setProcessing()
            viewModel.applyNativeStatus("Ready")

            assertTrue(viewModel.applyTranscribedText(" hello world "))
            assertEquals(VoicePhase.Result, viewModel.currentState.phase)
            assertEquals("hello world", viewModel.currentState.transcript)
        }
    }

    @Test
    fun audioLevelIsClamped() {
        val viewModel = TranscriptionViewModel()

        viewModel.setListening()
        viewModel.setAudioLevel(1.4f)
        assertEquals(1f, viewModel.currentState.audioLevel, 0f)
        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)

        viewModel.setAudioLevel(-0.5f)
        assertEquals(0f, viewModel.currentState.audioLevel, 0f)
        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)
    }

    @Test
    fun errorsStripNativePrefix() {
        val viewModel = TranscriptionViewModel()

        viewModel.applyNativeStatus("Error: microphone unavailable")

        assertEquals(
            VoicePhase.Error,
            viewModel.currentState.phase,
        )
        assertEquals("microphone unavailable", viewModel.currentState.errorMessage)
    }
}
