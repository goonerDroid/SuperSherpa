package com.sublime.supersherpa.feature.transcription

import com.sublime.supersherpa.core.audio.MicRecorder
import com.sublime.supersherpa.model.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneTestViewModelTest {
    @Test
    fun startStopCycleUpdatesStateAndAccumulatesStreamText() {
        val recorder = FakeMicRecorder()
        val viewModel = MicrophoneTestViewModel(recorder)

        viewModel.onPermissionResult(true)
        viewModel.onMicToggleClicked()
        recorder.emitFrame(shortArrayOf(1, 2, 3))
        recorder.emitFrame(shortArrayOf(4, 5))

        val runningState = viewModel.uiState.value
        assertTrue(runningState.permissionGranted)
        assertTrue(runningState.isRecording)
        assertEquals(2, runningState.frameCount)
        assertEquals(2, runningState.lastFrameSize)
        assertEquals(VoiceState.Listening, runningState.voiceState)
        assertTrue(runningState.streamText.contains("[1, 2, 3]"))
        assertTrue(runningState.streamText.contains("[4, 5]"))

        viewModel.onMicToggleClicked()

        val stoppedState = viewModel.uiState.value
        assertFalse(stoppedState.isRecording)
        assertEquals(VoiceState.Result("Captured 2 frame(s)."), stoppedState.voiceState)
        assertTrue(recorder.stopCalled)
    }

    @Test
    fun permissionDeniedResetsToIdle() {
        val recorder = FakeMicRecorder()
        val viewModel = MicrophoneTestViewModel(recorder)

        viewModel.onPermissionResult(true)
        viewModel.onMicToggleClicked()
        viewModel.onPermissionResult(false)

        val state = viewModel.uiState.value
        assertFalse(state.permissionGranted)
        assertFalse(state.isRecording)
        assertEquals(VoiceState.Idle, state.voiceState)
        assertEquals("Microphone permission is required.", state.lastError)
        assertTrue(recorder.stopCalled)
    }
}

private class FakeMicRecorder : MicRecorder {
    private var callback: ((ShortArray) -> Unit)? = null

    var stopCalled: Boolean = false
        private set

    override fun isRecording(): Boolean = callback != null

    override fun startRecording(onAudioFrame: (ShortArray) -> Unit) {
        check(callback == null) { "Recorder already started." }
        stopCalled = false
        callback = onAudioFrame
    }

    override fun stopRecording() {
        if (!stopRecordingIfActive()) {
            throw IllegalStateException("Recorder is not recording.")
        }
    }

    override fun stopRecordingIfActive(): Boolean {
        val active = callback != null
        callback = null
        stopCalled = active || stopCalled
        return active
    }

    override fun close() {
        callback = null
    }

    fun emitFrame(frame: ShortArray) {
        callback?.invoke(frame)
    }
}
