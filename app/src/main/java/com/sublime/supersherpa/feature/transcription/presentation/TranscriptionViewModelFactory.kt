package com.sublime.supersherpa.feature.transcription.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sublime.supersherpa.feature.transcription.domain.TranscriptHistoryStore

class TranscriptionViewModelFactory(
    private val historyStore: TranscriptHistoryStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TranscriptionViewModel(historyStore) as T
    }
}
