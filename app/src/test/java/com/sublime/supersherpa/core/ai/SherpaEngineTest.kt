package com.sublime.supersherpa.core.ai

import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SherpaEngineTest {
    @Test
    fun initializeOnceAndReuseAcrossSessions() = runBlocking {
        val recognizer = FakeSherpaRecognizer(resultText = "transcribed text")
        val loader = FakeSherpaRecognizerLoader(recognizer)
        val engine = SherpaEngine(dispatcher = Dispatchers.Default)

        assertTrue(engine.initialize(loader).isSuccess)
        assertTrue(engine.initialize(loader).isSuccess)
        assertTrue(engine.isInitialized)
        assertEquals(1, loader.createCount)

        assertTrue(engine.startStreaming().isSuccess)
        assertTrue(engine.acceptAudio(shortArrayOf(1, 2, 3)).isSuccess)
        assertEquals("transcribed text", engine.getResult().getOrThrow())
        assertEquals(1, recognizer.createdStreamCount)
        assertEquals(1, recognizer.decodeCount)

        assertEquals("transcribed text", engine.stop().getOrThrow())
        assertFalse(engine.isStreaming)

        assertTrue(engine.startStreaming().isSuccess)
        assertTrue(engine.acceptAudio(byteArrayOf(0x01, 0x00, 0x02, 0x00)).isSuccess)
        assertEquals("transcribed text", engine.stop().getOrThrow())
        assertEquals(2, recognizer.createdStreamCount)
        assertEquals(2, recognizer.decodeCount)
    }

    @Test
    fun missingRecognizerIsSurfacedAsRecoverableError() = runBlocking {
        val engine = SherpaEngine(dispatcher = Dispatchers.Default)
        val failingLoader = object : SherpaRecognizerLoader {
            override fun createRecognizer(): SherpaRecognizerHandle {
                throw IOException("Missing model assets")
            }
        }

        val initializeResult = engine.initialize(failingLoader)

        assertTrue(initializeResult.isFailure)
        assertFalse(engine.isInitialized)
        assertTrue(engine.lastError != null)
        assertTrue(engine.startStreaming().isFailure)
        assertTrue(engine.getResult().isFailure)
    }
}

private class FakeSherpaRecognizerLoader(
    private val recognizer: FakeSherpaRecognizer,
) : SherpaRecognizerLoader {
    var createCount: Int = 0
        private set

    override fun createRecognizer(): SherpaRecognizerHandle {
        createCount += 1
        return recognizer
    }
}

private class FakeSherpaRecognizer(
    private val resultText: String,
) : SherpaRecognizerHandle {
    var createdStreamCount: Int = 0
        private set

    var decodeCount: Int = 0
        private set

    override fun createStream(): SherpaStreamHandle {
        createdStreamCount += 1
        return FakeSherpaStream()
    }

    override fun decode(stream: SherpaStreamHandle) {
        require(stream is FakeSherpaStream)
        decodeCount += 1
    }

    override fun isReady(stream: SherpaStreamHandle): Boolean {
        require(stream is FakeSherpaStream)
        return true
    }

    override fun getResult(stream: SherpaStreamHandle): OnlineRecognizerResult {
        require(stream is FakeSherpaStream)
        return OnlineRecognizerResult(
            resultText,
            emptyArray(),
            floatArrayOf(),
            floatArrayOf(),
        )
    }

    override fun close() = Unit
}

private class FakeSherpaStream : SherpaStreamHandle {
    var closeCount: Int = 0
        private set

    override fun acceptWaveform(samples: FloatArray, sampleRateHz: Int) = Unit

    override fun close() {
        closeCount += 1
    }
}
