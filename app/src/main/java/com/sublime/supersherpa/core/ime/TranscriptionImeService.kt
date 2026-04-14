package com.sublime.supersherpa.core.ime

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
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
    private val historyRepository by lazy {
        (application as SuperSherpaApp).appContainer.transcriptHistoryStore
    }

    private var voiceBarPhase = VoiceBarPhase.Idle
    private var latestTranscript = ""
    private var isAwaitingCommit = false

    override fun onCreate() {
        super.onCreate()
        bridge.initNative(this)
    }

    override fun onDestroy() {
        serviceScope.cancel()
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
        bridge.initNative(this)
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
        Unit
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
            VoiceBarPhase.Idle -> getString(R.string.voice_bar_ready)
            VoiceBarPhase.Listening -> getString(R.string.voice_bar_listening)
            VoiceBarPhase.Processing -> getString(R.string.voice_bar_processing)
            VoiceBarPhase.Result -> getString(R.string.voice_bar_ready)
            VoiceBarPhase.Error -> latestTranscript.ifBlank {
                getString(R.string.voice_bar_empty)
            }
        }
        val actionText = when (voiceBarPhase) {
            VoiceBarPhase.Listening -> getString(R.string.voice_bar_stop)
            VoiceBarPhase.Processing -> getString(R.string.voice_bar_wait)
            else -> getString(R.string.voice_bar_start)
        }

        currentBinding.voiceBarStatus.text = statusText
        currentBinding.voiceBarAction.text = actionText
        currentBinding.voiceBarAction.isEnabled = voiceBarPhase != VoiceBarPhase.Processing
    }

    private enum class VoiceBarPhase {
        Idle,
        Listening,
        Processing,
        Result,
        Error,
    }
}
