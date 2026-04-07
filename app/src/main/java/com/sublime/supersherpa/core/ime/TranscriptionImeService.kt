package com.sublime.supersherpa.core.ime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sublime.supersherpa.R
import com.sublime.supersherpa.core.rust.RustTranscriptionBridge
import com.sublime.supersherpa.feature.transcription.TranscriptionViewModel
import com.sublime.supersherpa.feature.transcription.VoicePhase
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class TranscriptionImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bridge = RustTranscriptionBridge()
    private val transcriptionViewModel by lazy {
        ViewModelProvider(this)[TranscriptionViewModel::class.java]
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreHolder

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        runCatching {
            bridge.initNative(this)
        }.onFailure { throwable ->
            transcriptionViewModel.setError(
                throwable.message ?: "Failed to initialize transcription.",
            )
            Toast.makeText(
                this,
                "Error: ${throwable.message ?: "Failed to initialize transcription."}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            super.onFinishInputView(finishingInput)
            return
        }
        if (transcriptionViewModel.currentState.isActive) {
            runCatching { bridge.cancelRecording() }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { bridge.cleanupNative() }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreHolder.clear()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.ime_keyboard_compose, null) as ImeComposeHostLayout
        root.hostLifecycleOwner = this
        root.hostSavedStateRegistryOwner = this
        root.hostViewModelStoreOwner = this
        val composeView = root.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.ime_compose_view)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setContent {
            val voiceState = transcriptionViewModel.voiceState.collectAsStateWithLifecycle().value
            SuperSherpaTheme {
                TranscriptionKeyboard(
                    voiceState = voiceState,
                    onToggleRecording = {
                        toggleRecording()
                    },
                    onShowKeyboardPicker = {
                        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showInputMethodPicker()
                    },
                    onCommitText = ::commitText,
                    onDelete = ::deleteBackward,
                )
            }
        }

        return root
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun toggleRecording() {
        when (transcriptionViewModel.currentState.phase) {
            VoicePhase.Processing -> {
                Toast.makeText(
                    this,
                    "Transcription is still running",
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            VoicePhase.Listening -> {
                transcriptionViewModel.setProcessing()
                runCatching { bridge.stopRecording() }
                    .onFailure { throwable ->
                        handleNativeFailure(
                            throwable.message ?: "Unable to stop recording.",
                        )
                    }
                return
            }
            else -> Unit
        }

        if (!hasMicPermission()) {
            transcriptionViewModel.setError("Microphone permission is required")
            Toast.makeText(
                this,
                "Microphone permission is required",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        transcriptionViewModel.reset()
        transcriptionViewModel.setListening()
        runCatching { bridge.startRecording() }
            .onFailure { throwable ->
                handleNativeFailure(
                    throwable.message ?: "Unable to start recording.",
                )
            }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleNativeFailure(message: String) {
        transcriptionViewModel.setError(message)
        Toast.makeText(
            this,
            "Error: $message",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun deleteBackward() {
        currentInputConnection?.let { connection ->
            if (!connection.deleteSurroundingText(1, 0)) {
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
    }

    @Suppress("unused")
    fun onStatusUpdate(message: String) {
        postToMain {
            transcriptionViewModel.applyNativeStatus(message)
        }
    }

    @Suppress("unused")
    fun onAudioLevel(level: Float) {
        // Reserved for future visualizations such as a listening indicator.
        if (level.isNaN()) return
    }

    @Suppress("unused")
    fun onTextTranscribed(text: String) {
        postToMain {
            if (transcriptionViewModel.applyTranscribedText(text)) {
                currentInputConnection?.commitText(text, 1)
            }
        }
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
