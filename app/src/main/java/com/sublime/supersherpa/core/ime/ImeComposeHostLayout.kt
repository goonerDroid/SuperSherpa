package com.sublime.supersherpa.core.ime

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class ImeComposeHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var hostLifecycleOwner: LifecycleOwner? = null
    var hostSavedStateRegistryOwner: SavedStateRegistryOwner? = null
    var hostViewModelStoreOwner: ViewModelStoreOwner? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        hostLifecycleOwner?.let { lifecycleOwner ->
            setViewTreeLifecycleOwner(lifecycleOwner)
            rootView.setViewTreeLifecycleOwner(lifecycleOwner)
        }

        hostSavedStateRegistryOwner?.let { savedStateOwner ->
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            rootView.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        }

        hostViewModelStoreOwner?.let { viewModelOwner ->
            setViewTreeViewModelStoreOwner(viewModelOwner)
            rootView.setViewTreeViewModelStoreOwner(viewModelOwner)
        }
    }
}
