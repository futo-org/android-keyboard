package org.futo.inputmethod.engine

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor

interface InputMethodConnectionProvider {
    fun getCurrentInputConnection(): InputConnection?
    fun getCurrentEditorInfo(): EditorInfo?
}

class IMEHelper(
    private val latinIME: LatinIME
) : InputMethodConnectionProvider, SuggestionStripViewAccessor {
    val context: Context
        get() = latinIME.applicationContext

    val lifecycleScope: LifecycleCoroutineScope
        get() = latinIME.lifecycleScope

    public val keyboardSwitcher: KeyboardSwitcher
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher

    public val keyboardShiftMode: Int
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher.keyboardShiftMode

    public val currentKeyboardScriptId: Int
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher.currentKeyboardScriptId

    // TODO: Maybe could just wrap this in something else...
    public fun onCodePointDeleted(textBeforeCursor: String) {
        latinIME.onEmojiDeleted(textBeforeCursor)
    }

    public fun updateBoostedCodePoints(codes: Set<Int>?) {
        latinIME.latinIMELegacy.mKeyboardSwitcher?.mainKeyboardView?.mKeyDetector?.let {
            it.updateBoostedCodePoints(codes)
        }
    }

    public fun updateUiInputState(textEmpty: Boolean) {
        latinIME.uixManager.onInputEvent(textEmpty)
    }

    public fun getCodepointCoordinates(codePoints: IntArray): IntArray {
        return latinIME.latinIMELegacy.getCoordinatesForCurrentKeyboard(codePoints)
    }

    public fun triggerAction(actionId: Int, alt: Boolean) {
        latinIME.uixManager.triggerAction(actionId, alt)
    }

    override fun getCurrentInputConnection(): InputConnection? {
        return latinIME.currentInputConnection
    }

    override fun getCurrentEditorInfo(): EditorInfo? {
        return latinIME.currentInputEditorInfo
    }

    override fun setNeutralSuggestionStrip() {
        latinIME.setSuggestions(
            suggestedWords = SuggestedWords.getEmptyInstance(),
            rtlSubtype = Settings.getInstance().current.mIsRTL
        )
    }

    override fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        latinIME.setSuggestions(
            suggestedWords = suggestedWords,
            rtlSubtype = Settings.getInstance().current.mIsRTL
        )
    }
}