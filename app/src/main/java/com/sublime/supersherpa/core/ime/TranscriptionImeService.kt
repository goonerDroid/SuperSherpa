package com.sublime.supersherpa.core.ime

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.LinearInterpolator
import androidx.annotation.Keep
import com.frogobox.libkeyboard.common.core.BaseKeyboardIME
import com.sublime.supersherpa.R
import com.sublime.supersherpa.app.SuperSherpaApp
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge
import com.sublime.supersherpa.databinding.ImeKeyboardLibraryBinding
import com.sublime.supersherpa.feature.transcription.domain.NativeTranscriptionMessage
import com.sublime.supersherpa.feature.transcription.domain.parseNativeTranscriptionMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TranscriptionImeService : BaseKeyboardIME<ImeKeyboardLibraryBinding>() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bridge by lazy { RustTranscriptionBridge() }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transcriptionRuntimeInitializer by lazy {
        (application as SuperSherpaApp).appContainer.transcriptionRuntimeInitializer
    }
    private val historyRepository by lazy {
        (application as SuperSherpaApp).appContainer.transcriptHistoryStore
    }

    private var voiceBarPhase = VoiceBarPhase.Idle
    private var latestTranscript = ""
    private var isAwaitingCommit = false
    private var waveformAnimator: ValueAnimator? = null
    private var latestAudioLevel = 0f
    private var smoothedAudioLevel = 0f
    private var previousAudioLevel = 0f
    private var spikeEnergy = 0f

    override fun onCreate() {
        super.onCreate()
        transcriptionRuntimeInitializer.initialize(this, bridge)
    }

    override fun onDestroy() {
        stopListeningWaveform()
        serviceScope.cancel()
        bridge.cleanupNative(this)
        super.onDestroy()
    }

    override fun setupViewBinding(): ImeKeyboardLibraryBinding {
        return ImeKeyboardLibraryBinding.inflate(LayoutInflater.from(this), null, false)
    }

    override fun initialSetupKeyboard() {
        val currentKeyboard = keyboard ?: return
        binding?.keyboardMain?.setKeyboard(currentKeyboard)
    }

    override fun setupBinding() {
        initialSetupKeyboard()
        binding?.keyboardMain?.mOnKeyboardActionListener = this
        binding?.keyboardEmoji?.mOnKeyboardActionListener = this
        binding?.voiceBarAction?.setOnClickListener {
            onVoiceBarActionPressed()
        }
        renderVoiceBar()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        transcriptionRuntimeInitializer.initialize(this, bridge)
        super.onStartInputView(info, restarting)
    }

    override fun initBackToMainKeyboard() {
        binding?.keyboardEmoji?.binding?.toolbarBack?.setOnClickListener {
            binding?.keyboardEmoji?.visibility = View.GONE
            showMainKeyboard()
        }
    }

    override fun invalidateAllKeys() {
        binding?.keyboardMain?.invalidateAllKeys()
    }

    override fun showMainKeyboard() {
        binding?.keyboardMain?.visibility = View.VISIBLE
        binding?.keyboardEmoji?.visibility = View.GONE
    }

    override fun hideMainKeyboard() {
        binding?.keyboardMain?.visibility = View.GONE
    }

    override fun showOnlyKeyboard() {
        showMainKeyboard()
    }

    override fun hideOnlyKeyboard() {
        hideMainKeyboard()
    }

    override fun runEmojiBoard() {
        binding?.keyboardEmoji?.visibility = View.VISIBLE
        hideMainKeyboard()
        binding?.keyboardEmoji?.openEmojiPalette()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (voiceBarPhase == VoiceBarPhase.Listening || voiceBarPhase == VoiceBarPhase.Processing) {
            bridge.cancelRecording()
        }
        voiceBarPhase = VoiceBarPhase.Idle
        isAwaitingCommit = false
        latestTranscript = ""
        renderVoiceBar()
        super.onFinishInputView(finishingInput)
    }

    @Keep
    fun onStatusUpdate(message: String) {
        mainHandler.post {
            when (val event = parseNativeTranscriptionMessage(message)) {
                NativeTranscriptionMessage.Listening -> {
                    voiceBarPhase = VoiceBarPhase.Listening
                    latestTranscript = ""
                }
                NativeTranscriptionMessage.Processing -> {
                    voiceBarPhase = VoiceBarPhase.Processing
                }
                NativeTranscriptionMessage.Ready -> {
                    if (voiceBarPhase == VoiceBarPhase.Processing) {
                        voiceBarPhase = if (latestTranscript.isBlank()) {
                            VoiceBarPhase.Idle
                        } else {
                            VoiceBarPhase.Result
                        }
                    }
                }
                NativeTranscriptionMessage.Canceled -> {
                    voiceBarPhase = VoiceBarPhase.Idle
                    latestTranscript = ""
                    isAwaitingCommit = false
                }
                is NativeTranscriptionMessage.Error -> {
                    voiceBarPhase = VoiceBarPhase.Error
                    latestTranscript = event.message
                    isAwaitingCommit = false
                }
                is NativeTranscriptionMessage.Transcript -> {
                    if (voiceBarPhase == VoiceBarPhase.Idle) {
                        latestTranscript = event.text
                    }
                }
            }
            renderVoiceBar()
        }
    }

    @Keep
    fun onAudioLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        mainHandler.post {
            latestAudioLevel = clamped
            val risingEdge = (clamped - previousAudioLevel).coerceAtLeast(0f)
            spikeEnergy = (spikeEnergy + (risingEdge * 1.6f)).coerceAtMost(1f)
            previousAudioLevel = clamped
        }
    }

    @Keep
    fun onTextTranscribed(text: String) {
        mainHandler.post {
            val cleaned = text.trim()
            latestTranscript = cleaned

            if (cleaned.isBlank()) {
                voiceBarPhase = VoiceBarPhase.Error
                isAwaitingCommit = false
                renderVoiceBar()
                return@post
            }

            serviceScope.launch {
                historyRepository.addTranscript(cleaned)
            }

            if (isAwaitingCommit) {
                currentInputConnection?.commitText("$cleaned ", 1)
            }
            latestTranscript = cleaned
            voiceBarPhase = VoiceBarPhase.Result
            isAwaitingCommit = false
            renderVoiceBar()
        }
    }

    private fun onVoiceBarActionPressed() {
        when (voiceBarPhase) {
            VoiceBarPhase.Idle,
            VoiceBarPhase.Result,
            VoiceBarPhase.Error,
            -> {
                latestTranscript = ""
                isAwaitingCommit = true
                voiceBarPhase = VoiceBarPhase.Listening
                renderVoiceBar()
                bridge.startRecording()
            }
            VoiceBarPhase.Listening -> {
                voiceBarPhase = VoiceBarPhase.Processing
                renderVoiceBar()
                bridge.stopRecording()
            }
            VoiceBarPhase.Processing -> Unit
        }
    }

    private fun renderVoiceBar() {
        val currentBinding = binding ?: return

        val statusText = when (voiceBarPhase) {
            VoiceBarPhase.Idle -> ""
            VoiceBarPhase.Listening -> getString(R.string.voice_bar_listening)
            VoiceBarPhase.Processing -> getString(R.string.voice_bar_processing)
            VoiceBarPhase.Result -> ""
            VoiceBarPhase.Error -> latestTranscript.ifBlank {
                getString(R.string.voice_bar_empty)
            }
        }
        val actionText = when (voiceBarPhase) {
            VoiceBarPhase.Listening -> getString(R.string.voice_bar_stop)
            else -> getString(R.string.voice_bar_start)
        }
        val actionIconRes = when (voiceBarPhase) {
            VoiceBarPhase.Listening -> R.drawable.ic_voice_bar_stop_circle_24
            else -> R.drawable.ic_voice_bar_mic_24
        }

        currentBinding.voiceBarStatus.text = statusText
        currentBinding.voiceBarAction.setImageResource(actionIconRes)
        currentBinding.voiceBarAction.isEnabled = voiceBarPhase != VoiceBarPhase.Processing
        currentBinding.voiceBarAction.contentDescription = actionText
        currentBinding.voiceBarAction.alpha = 1f
        updateListeningWaveformState()
    }

    private fun updateListeningWaveformState() {
        if (voiceBarPhase == VoiceBarPhase.Listening) {
            startListeningWaveform()
        } else {
            stopListeningWaveform()
        }
    }

    private fun startListeningWaveform() {
        if (waveformAnimator?.isRunning == true) return
        val currentBinding = binding ?: return
        currentBinding.voiceBarWaveOuter.visibility = View.VISIBLE
        currentBinding.voiceBarWaveInner.visibility = View.VISIBLE
        currentBinding.voiceBarWaveMid.visibility = View.VISIBLE

        waveformAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                renderWaveformFrame(progress)
            }
            start()
        }
    }

    private fun stopListeningWaveform() {
        waveformAnimator?.cancel()
        waveformAnimator = null
        latestAudioLevel = 0f
        smoothedAudioLevel = 0f
        previousAudioLevel = 0f
        spikeEnergy = 0f
        val currentBinding = binding ?: return
        currentBinding.voiceBarWaveOuter.visibility = View.GONE
        currentBinding.voiceBarWaveInner.visibility = View.GONE
        currentBinding.voiceBarWaveMid.visibility = View.GONE
        currentBinding.voiceBarWaveOuter.alpha = 0f
        currentBinding.voiceBarWaveInner.alpha = 0f
        currentBinding.voiceBarWaveMid.alpha = 0f
        currentBinding.voiceBarWaveOuter.scaleX = 1f
        currentBinding.voiceBarWaveOuter.scaleY = 1f
        currentBinding.voiceBarWaveInner.scaleX = 1f
        currentBinding.voiceBarWaveInner.scaleY = 1f
        currentBinding.voiceBarWaveMid.scaleX = 1f
        currentBinding.voiceBarWaveMid.scaleY = 1f
        currentBinding.voiceBarAction.scaleX = 1f
        currentBinding.voiceBarAction.scaleY = 1f
    }

    private fun renderWaveformFrame(progress: Float) {
        val currentBinding = binding ?: return

        smoothedAudioLevel += (latestAudioLevel - smoothedAudioLevel) * 0.38f
        spikeEnergy = (spikeEnergy * 0.9f).coerceAtLeast(0f)
        val intensity = (0.2f + (smoothedAudioLevel * 0.55f) + (spikeEnergy * 0.85f)).coerceIn(0f, 1.4f)

        val midProgress = (progress + 0.33f) % 1f
        val innerProgress = (progress + 0.66f) % 1f

        val outerScale = 1f + (0.45f + (0.9f * intensity)) * progress
        val midScale = 1f + (0.38f + (0.75f * intensity)) * midProgress
        val innerScale = 1f + (0.3f + (0.62f * intensity)) * innerProgress

        currentBinding.voiceBarWaveOuter.visibility = View.VISIBLE
        currentBinding.voiceBarWaveMid.visibility = View.VISIBLE
        currentBinding.voiceBarWaveInner.visibility = View.VISIBLE

        currentBinding.voiceBarWaveOuter.scaleX = outerScale
        currentBinding.voiceBarWaveOuter.scaleY = outerScale
        currentBinding.voiceBarWaveOuter.alpha = (1f - progress) * (0.45f + 0.65f * intensity).coerceAtMost(1f)

        currentBinding.voiceBarWaveMid.scaleX = midScale
        currentBinding.voiceBarWaveMid.scaleY = midScale
        currentBinding.voiceBarWaveMid.alpha = (1f - midProgress) * (0.38f + 0.58f * intensity).coerceAtMost(1f)

        currentBinding.voiceBarWaveInner.scaleX = innerScale
        currentBinding.voiceBarWaveInner.scaleY = innerScale
        currentBinding.voiceBarWaveInner.alpha = (1f - innerProgress) * (0.3f + 0.52f * intensity).coerceAtMost(1f)

        val buttonScale = 1f + 0.14f * intensity
        currentBinding.voiceBarAction.scaleX = buttonScale
        currentBinding.voiceBarAction.scaleY = buttonScale
    }

    private enum class VoiceBarPhase {
        Idle,
        Listening,
        Processing,
        Result,
        Error,
    }
}
