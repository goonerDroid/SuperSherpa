package com.sublime.supersherpa.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class AndroidTranscriptClipboard(
    context: Context,
) : TranscriptClipboard {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(text: String): Result<Unit> {
        return runCatching {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Transcript", text))
        }
    }
}
