package com.sublime.supersherpa.core.audio

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Streams microphone audio as 16 kHz mono PCM 16-bit frames.
 *
 * The recorder owns a dedicated IO coroutine scope so callers do not need to
 * manage background threading for microphone reads.
 */
class AudioRecorder(
    private val sessionFactory: AudioRecordingSessionFactory = AndroidAudioRecordingSessionFactory(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MicRecorder {
    private val lock = Any()
    private var recordingJob: Job? = null
    private var activeSession: AudioRecordingSession? = null

    override fun isRecording(): Boolean = synchronized(lock) {
        recordingJob != null
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(onAudioFrame: (ShortArray) -> Unit) {
        val job = synchronized(lock) {
            check(recordingJob == null) { "AudioRecorder is already recording." }

            scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                streamAudio(onAudioFrame)
            }.also { launchedJob ->
                recordingJob = launchedJob
                launchedJob.invokeOnCompletion {
                    synchronized(lock) {
                        if (recordingJob === launchedJob) {
                            recordingJob = null
                        }
                    }
                }
            }
        }

        job.start()
    }

    override fun stopRecording() {
        check(stopRecordingIfActive()) { "AudioRecorder is not recording." }
    }

    override fun stopRecordingIfActive(): Boolean {
        val (job, session) = synchronized(lock) {
            val currentJob = recordingJob ?: return false
            currentJob to activeSession
        }

        session?.stop()
        job.cancel()
        return true
    }

    override fun close() {
        val (job, session) = synchronized(lock) {
            recordingJob to activeSession
        }

        session?.stop()
        job?.cancel()
        synchronized(lock) {
            recordingJob = null
            activeSession = null
        }
        scope.cancel()
    }

    private suspend fun streamAudio(onAudioFrame: (ShortArray) -> Unit) {
        val session = sessionFactory.create()
        synchronized(lock) {
            activeSession = session
        }
        try {
            if (!currentCoroutineContext().isActive) {
                return
            }
            session.start()
            val frame = ShortArray(session.bufferSizeInShorts)
            while (currentCoroutineContext().isActive) {
                val read = session.read(frame, 0, frame.size)
                when {
                    read > 0 -> onAudioFrame(frame.copyOf(read))
                    read == 0 -> continue
                    else -> break
                }
            }
        } finally {
            synchronized(lock) {
                if (activeSession === session) {
                    activeSession = null
                }
            }
            session.stop()
            session.release()
        }
    }
}

