package com.sublime.supersherpa.app

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.sublime.supersherpa.core.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

class SuperSherpaApp : Application() {
    val appContainer by lazy(LazyThreadSafetyMode.NONE) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this, Dispatchers.IO.asExecutor()))
    }
}
