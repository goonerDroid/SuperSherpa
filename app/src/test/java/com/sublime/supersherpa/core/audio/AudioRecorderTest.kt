package com.sublime.supersherpa.core.audio

import android.media.AudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioRecorderTest {
    @Test
    fun startStopCycleStreamsFramesAndCanRestart() {
        val firstSession = FakeAudioRecordingSession(
            frames = arrayOf(
                shortArrayOf(1, 2),
                shortArrayOf(3, 4, 5),
            ),
        )
        val secondSession = FakeAudioRecordingSession(
            frames = arrayOf(
                shortArrayOf(9, 8, 7),
            ),
        )
        val factory = SequentialFakeAudioRecordingSessionFactory(
            initialSessions = arrayOf(firstSession, secondSession),
        )
        val recorder = AudioRecorder(
            sessionFactory = factory,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        recorder.use { recorder ->
            val firstFrames = CopyOnWriteArrayList<ShortArray>()
            val firstLatch = CountDownLatch(2)

            recorder.startRecording {
                firstFrames += it
                firstLatch.countDown()
            }

            assertTrue(firstLatch.await(2, TimeUnit.SECONDS))
            recorder.stopRecording()
            awaitCondition { firstSession.released && !recorder.isRecording() }
            assertEquals(1, factory.createCount)
            assertArrayEquals(shortArrayOf(1, 2), firstFrames[0])
            assertArrayEquals(shortArrayOf(3, 4, 5), firstFrames[1])

            val secondFrames = CopyOnWriteArrayList<ShortArray>()
            val secondLatch = CountDownLatch(1)

            recorder.startRecording {
                secondFrames += it
                secondLatch.countDown()
            }

            assertTrue(secondLatch.await(2, TimeUnit.SECONDS))
            recorder.stopRecording()
            awaitCondition { secondSession.released && !recorder.isRecording() }
            assertEquals(2, factory.createCount)
            assertArrayEquals(shortArrayOf(9, 8, 7), secondFrames[0])
        }
    }

    @Test
    fun doubleStartIsRejected() {
        val session = FakeAudioRecordingSession(frames = emptyArray())
        val recorder = AudioRecorder(
            sessionFactory = SequentialFakeAudioRecordingSessionFactory(
                initialSessions = arrayOf(session),
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        recorder.use { recorder ->
            recorder.startRecording { }

            var thrown = false
            try {
                recorder.startRecording { }
            } catch (_: IllegalStateException) {
                thrown = true
            }

            assertTrue(thrown)
        }
    }

    @Test
    fun stopWithoutStartIsRejected() {
        val recorder = AudioRecorder(
            sessionFactory = SequentialFakeAudioRecordingSessionFactory(emptyArray()),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        recorder.use { recorder ->
            var thrown = false
            try {
                recorder.stopRecording()
            } catch (_: IllegalStateException) {
                thrown = true
            }

            assertTrue(thrown)
        }
    }

    private fun awaitCondition(
        timeoutMs: Long = 2_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }

        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }
}

private class SequentialFakeAudioRecordingSessionFactory(
    initialSessions: Array<FakeAudioRecordingSession>,
) : AudioRecordingSessionFactory {
    private val sessions = initialSessions.toMutableList()

    var createCount: Int = 0
        private set

    override fun create(): AudioRecordingSession {
        if (sessions.isEmpty()) {
            throw IllegalStateException("No more fake sessions configured.")
        }

        createCount += 1
        return sessions.removeAt(0)
    }
}

private class FakeAudioRecordingSession(
    frames: Array<ShortArray>,
) : AudioRecordingSession {
    private val lock = Object()
    private val queue = frames.toMutableList()

    @Volatile
    var released: Boolean = false
        private set

    @Volatile
    private var started: Boolean = false

    @Volatile
    private var stopped: Boolean = false

    override val bufferSizeInShorts: Int = 16

    override fun start() {
        started = true
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        check(started) { "Session must be started before reading." }

        synchronized(lock) {
            while (queue.isEmpty() && !stopped) {
                lock.wait(10)
            }

            if (queue.isEmpty()) {
                return AudioRecord.ERROR_INVALID_OPERATION
            }

            val nextFrame = queue.removeAt(0)
            val length = minOf(size, nextFrame.size)
            System.arraycopy(nextFrame, 0, buffer, offset, length)
            return length
        }
    }

    override fun stop() {
        stopped = true
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    override fun release() {
        stop()
        released = true
    }
}
