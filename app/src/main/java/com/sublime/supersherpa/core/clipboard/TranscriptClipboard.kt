package com.sublime.supersherpa.core.clipboard

interface TranscriptClipboard {
    fun copy(text: String): Result<Unit>
}
