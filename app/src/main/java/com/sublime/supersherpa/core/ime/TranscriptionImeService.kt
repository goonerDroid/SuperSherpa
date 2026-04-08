package com.sublime.supersherpa.core.ime

import android.view.LayoutInflater
import android.view.View
import com.frogobox.libkeyboard.common.core.BaseKeyboardIME
import com.sublime.supersherpa.databinding.ImeKeyboardLibraryBinding

class TranscriptionImeService : BaseKeyboardIME<ImeKeyboardLibraryBinding>() {

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
}
