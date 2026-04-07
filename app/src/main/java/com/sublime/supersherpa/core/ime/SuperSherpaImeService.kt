package com.sublime.supersherpa.core.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sublime.supersherpa.R
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme

class SuperSherpaImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()
    private var isRecording by mutableStateOf(false)

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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
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
            SuperSherpaTheme {
                SuperSherpaImeKeyboard(
                    isRecording = isRecording,
                    onToggleRecording = {
                        isRecording = !isRecording
                        Toast.makeText(
                            this@SuperSherpaImeService,
                            if (isRecording) "Listening" else "Recording stopped",
                            Toast.LENGTH_SHORT,
                        ).show()
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

    private fun deleteBackward() {
        currentInputConnection?.let { connection ->
            if (!connection.deleteSurroundingText(1, 0)) {
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
    }
}
