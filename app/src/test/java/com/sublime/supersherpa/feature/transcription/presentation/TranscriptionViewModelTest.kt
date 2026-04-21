package com.sublime.supersherpa.feature.transcription.presentation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        viewModel.applyPartialTranscript("live partial")
        viewModel.setAudioLevel(1.4f)
        assertEquals(1f, viewModel.currentState.audioLevel, 0f)
        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)
        assertEquals("live partial", viewModel.currentState.transcript)

        viewModel.setAudioLevel(-0.5f)
        assertEquals(0f, viewModel.currentState.audioLevel, 0f)
        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)
        assertEquals("live partial", viewModel.currentState.transcript)
    }

    @Test
    fun partialTranscriptUpdatesListeningState() {
        val viewModel = TranscriptionViewModel()

        viewModel.setListening()
        viewModel.applyPartialTranscript(" hello wor ")

        assertEquals(VoicePhase.Listening, viewModel.currentState.phase)
        assertEquals("hello wor", viewModel.currentState.transcript)
    }

    @Test
    fun finalTranscriptReplacesPartialTranscript() = runBlocking {
        val viewModel = TranscriptionViewModel()

        viewModel.setListening()
        viewModel.applyPartialTranscript("hello wor")

        assertTrue(viewModel.applyTranscribedText(" hello world "))
        assertEquals(VoicePhase.Result, viewModel.currentState.phase)
        assertEquals("hello world", viewModel.currentState.transcript)
    }

    @Test
    fun resetClearsPartialTranscript() {
        val viewModel = TranscriptionViewModel()

        viewModel.setListening()
        viewModel.applyPartialTranscript("draft")

        viewModel.reset()

        assertEquals(VoicePhase.Idle, viewModel.currentState.phase)
        assertEquals("", viewModel.currentState.transcript)
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
