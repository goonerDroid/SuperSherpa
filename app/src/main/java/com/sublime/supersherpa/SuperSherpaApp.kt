package com.sublime.supersherpa

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

class SuperSherpaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this, Dispatchers.IO.asExecutor()))
    }
}
