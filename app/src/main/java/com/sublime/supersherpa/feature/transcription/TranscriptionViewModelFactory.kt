package com.sublime.supersherpa.feature.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TranscriptionViewModelFactory(
    private val historyStore: TranscriptHistoryStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TranscriptionViewModel(historyStore) as T
    }
}
