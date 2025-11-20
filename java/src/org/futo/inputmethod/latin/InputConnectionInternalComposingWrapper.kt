package org.futo.inputmethod.latin

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import kotlin.text.iterator

val TextInputAlternativeIC = SettingsKey(
    key = booleanPreferencesKey("text_input_experimental_ic_fix"),
    default = true
)

val TextInputAlternativeICComposing = SettingsKey(
    key = booleanPreferencesKey("text_input_experimental_ic_fix_use_composing"),
    default = false
)

val TextInputBufferedIC = SettingsKey(
    key = booleanPreferencesKey("text_input_buffered_ic"),
    default = true
)

val VoiceInputAlternativeIC = SettingsKey(
    booleanPreferencesKey("voice_input_experimental_ic_fix11"),
    true
)

val VoiceInputAlternativeICComposing = SettingsKey(
    booleanPreferencesKey("voice_input_experimental_ic_fix_composing"),
    false
)

/*
 * This is a wrapper around InputConnection that works around wonky app behavior by avoiding use of
 * the actual composing API. When apps don't implement it correctly or do weird things around it,
 * letters and entire words can become duplicated, causing a poor user experience.
 * Related issue: https://github.com/futo-org/android-keyboard/issues/1519
 *
 * This wrapper implements setComposingText, setComposingRegion by keeping track of the state
 * internally and it ultimately issues commitText and deleteSurroundingText calls to the underlying
 * input connection. It can also use the buffering wrapper to further work around bugs. It makes
 * some assumptions about the way the IME uses the API.
 *
 * The downside of this is that it's not possible to use visual spans for indicating composition
 * state (e.g. autocorrect, dead keys)
 *
 * The `send()` method should be called if buffering is enabled to actually send the commands.
 */
@Suppress("HardCodedStringLiteral")
@SuppressLint("LogConditional")
class InputConnectionInternalComposingWrapper(
    val useSetComposingRegion: Boolean,
    val useBufferingWrapper: Boolean,
    target: InputConnection?
) : InputConnectionWrapper(null, true), IBufferedInputConnection {
    companion object {
        @JvmStatic
        fun createWithSettingsFromContext(context: Context, target: InputConnection): InputConnection {
            if(context.getSetting(TextInputAlternativeIC) == false) return target
            if(!SupportsNonComposing) return target

            return InputConnectionInternalComposingWrapper(
                context.getSetting(TextInputAlternativeICComposing),
                context.getSetting(TextInputBufferedIC),
                target
            )
        }
    }
    var mTarget: InputConnection? = null
    var ic: InputConnection? = null
    init {
        updateIc(target)
    }

    fun updateIc(to: InputConnection?) {
        if(to == mTarget) return
        mTarget = to
        ic = to?.let {
            if(useBufferingWrapper) InputConnectionWithBufferingWrapper(it) else it
        }
        setTarget(ic)

        if (BuildConfig.DEBUG) Log.d(TAG, "InputConnection updated!! Kill everything")
        selStart = -1
        selEnd = -1
        composingStart = -1
        composingEnd = -1
        composingText = ""
    }

    var composingText = ""
    val TAG = "ICPatched"

    var selStart = -1
    var selEnd = -1
    var composingStart = -1
    var composingEnd = -1

    fun cursorUpdated(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ) {
        if(BuildConfig.DEBUG) Log.d(TAG, " [${System.identityHashCode(this)}] Cursor updated: $oldSelStart-$oldSelEnd : $newSelStart-$newSelEnd * ")
        selStart = newSelStart
        selEnd = newSelEnd
    }

    var previousUpdateWasBelated = false
    fun mightBeBelated(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        return when {
            selStart == newSelStart && selEnd == newSelEnd -> false // In this case it's not belated, it's up to date, we want to skip logic where we extract cursor position
            (selStart == oldSelStart && selEnd == oldSelEnd)
                    && (oldSelStart != newSelStart || oldSelEnd != newSelEnd) -> false
            else -> (newSelStart == newSelEnd)
                    && (newSelStart - oldSelStart) * (selStart - newSelStart) >= 0
                    && (newSelEnd - oldSelEnd) * (selEnd - newSelEnd) >= 0
        }.also {
            if(BuildConfig.DEBUG) Log.d(TAG, " $newSelStart:$newSelEnd belated = $it (current $selStart:$selEnd)")
            previousUpdateWasBelated = it
        }
    }

    private fun typeChars(text: CharSequence) {
        if(BuildConfig.DEBUG) Log.d(TAG, "    commitText($text)")
        super.commitText(text, 1)

        // In case editor is not sending cursor updates, try to keep track of it ourselves
        if(selStart != -1) selStart += text.length
        if(selEnd != -1) selEnd += text.length
    }

    private fun getCommonLength(a: CharSequence, b: CharSequence): Int {
        var i = 0
        while(i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun getCommonLengthOnlyLetters(aS: CharSequence, bS: CharSequence): Int {
        val a = aS.filter { it.isLetterOrDigit() }
        val b = bS.filter { it.isLetterOrDigit() }
        var i = 0
        while(i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun backspace(amount: Int) {
        if(BuildConfig.DEBUG) Log.d(TAG, "    backspace($amount)")
        super.deleteSurroundingText(amount, 0)

        // In case editor is not sending cursor updates, try to keep track of it ourselves
        if(selStart != -1) selStart = (selStart - amount).coerceAtLeast(0)
        if(selEnd != -1) selEnd = (selEnd - amount).coerceAtLeast(0)
    }

    private fun locateWordEndOffset(word: String, textBefore: String, textAfter: String): Int? {
        val combined = textBefore + textAfter
        var wordStart = -1000
        var wordOffset = 0
        var currCoordinate = -textBefore.length
        for(c in combined) {
            if(c != word[wordOffset]) {
                wordOffset = 0
                wordStart = -1000
            }

            if(c == word[wordOffset]) {
                if(wordStart == -1000) wordStart = currCoordinate
                wordOffset++
            }

            if(wordOffset == word.length) return wordStart + wordOffset

            currCoordinate += 1
        }

        return null
    }

    private fun extractPosition(): Int? {
        val selection = InputConnectionUtil.extractSelection(this)
        if(selection.first == -1) return null

        if(BuildConfig.DEBUG) Log.d(TAG, "Extracted cursor position for backtracking: $selection")
        selStart = selection.first
        selEnd = selection.second
        return selection.first
    }

    private fun commitComposingTextInternal(text: CharSequence, setComposing: Boolean) {
        val extracted: ExtractedText? = null// super.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 512 }, 0)
        if(BuildConfig.DEBUG) Log.d(TAG, "commitComposingTextInternal text=[$text] composingText=[$composingText] extracted=[${extracted?.text}][${extracted?.selectionStart} vs $selStart] composingStart=$composingStart setComposing=$setComposing ")
        if(useSetComposingRegion || setComposing) super.finishComposingText()
        var cursor = selStart
        var isAddition = true
        if(cursor < 0) {
            val extracted = extractPosition()
            if(extracted != null) {
                cursor = extracted
            } else {
                // In case we have absolutely no idea where the cursor is, our best hope is to just decide it's at 0
                Log.e(TAG, "Could not extract cursor position! Falling back to 0")
                super.setSelection(0, 0)
                cursor = 0
                return
            }
        }
        if(composingStart != -1) {
            var located = false
            if(cursor >= composingStart && cursor < composingEnd) {
                // Locate ourselves, may need to move cursor
                val textBefore = getTextBeforeCursor(composingText.length, 0)?.toString() ?: ""
                val textAfter = getTextAfterCursor(composingText.length, 0)?.toString() ?: ""
                val offs = locateWordEndOffset(composingText, textBefore, textAfter)
                if(offs != null && offs > 0) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "Moving cursor by $offs to be at end of word")
                    super.setSelection(cursor + offs, cursor + offs)
                    selStart += offs
                    selEnd += offs
                    cursor += offs
                    located = true
                }
            }

            isAddition = isAddition && text.startsWith(composingText)
            if(isAddition) {
                // Simple case: when the new text starts with the previous text, we can just append
                // the new characters.
                val textToAdd = text.substring(composingText.length)
                if(BuildConfig.DEBUG) Log.d(TAG, " Case Addition: simply append $text from len($composingText)=${composingText.length} -> $textToAdd")

                typeChars(textToAdd)
                if(setComposing) super.setComposingRegion(composingStart, selStart)
            } else if(cursor >= composingStart) {
                // When it's not a simple addition, we'll have to backtrack by deleting a certain
                // number of characters.
                if((previousUpdateWasBelated && !located) || (cursor - composingStart != composingText.length)) {
                    // Our cursor position may be incorrect for this situation, try to extract it
                    extractPosition()?.let { cursor = it }
                }

                // Try to get past text. Some frameworks might filter out characters or do other
                // wonky stuff, so we can't trust that pastText is actually previous composingText.
                // If we did, we might accidentally delete too many characters trying to recover.
                val lengthAccordingToCursor = (cursor - composingStart).coerceAtLeast(0) // Cursor position may have changed due to extractPosition, leading to a negative invalid value
                val lengthAccordingToHistory = (composingText.length)
                val lengthToFetch = maxOf(lengthAccordingToHistory, lengthAccordingToCursor)
                var pastText = super.getTextBeforeCursor(lengthToFetch, 1)
                if(pastText != null) {
                    // There are multiple cases we need to account for:
                    // 1. The framework is filtering out specific characters (e.g. spaces), in which case we must trust cursor position instead of composingText which contains spaces
                    // 2. The framework has shifted our cursor where the word is located, in which case we cannot trust cursor position and have to rely on composingText
                    // This tries to deduce which pastText we should trust based on which one best matches our composingText.
                    val pastTextAccordingToCursor = pastText.takeLast(lengthAccordingToCursor)
                    val pastTextAccordingToHistory = pastText.takeLast(lengthAccordingToHistory)

                    // Ignores spaces and symbols for this check
                    if(getCommonLengthOnlyLetters(pastTextAccordingToCursor, composingText) >= getCommonLengthOnlyLetters(pastTextAccordingToHistory, composingText)) {
                        pastText = pastTextAccordingToCursor
                    } else {
                        pastText = pastTextAccordingToHistory
                    }
                } else {
                    // When editor doesn't keep track of text (e.g. vnc client), just fallback to
                    // composingText.
                    pastText = composingText
                }

                // Backtrack by getting the common starting length between the two strings, then
                // backspacing until there, and re-typing everything from there.
                val commonLength = getCommonLength(pastText, text)

                if(BuildConfig.DEBUG) Log.d(TAG, " Case Complex: pastText=[$pastText] commonLength[$text]=$commonLength, therefore backspace $cursor - $composingStart - $commonLength = ${cursor - composingStart - commonLength} and type ${text.substring(commonLength)}.")

                backspace(pastText.length - commonLength)
                typeChars(text.substring(commonLength))
                if(setComposing) super.setComposingRegion(composingStart, selStart)
            } else {
                if(BuildConfig.DEBUG) Log.d(TAG, " Case Unknown: user moved cursor? cursor=$cursor composingStart=$composingStart")
                // User probably moved the cursor
                // Not sure what to do here...
                // Probably commit the new letters and tell inputlogic to reset composing?
            }
        } else {
            if(BuildConfig.DEBUG) Log.d(TAG, " Case Begin: type [$text]")
            if(previousUpdateWasBelated) {
                // Our cursor position may be incorrect for this situation, try to extract it
                extractPosition()?.let { cursor = it }
            }
            composingStart = cursor
            typeChars(text)
            if(setComposing) super.setComposingRegion(composingStart, selStart)
        }
        composingText = text.toString()
        //requestCursorUpdates(CURSOR_UPDATE_IMMEDIATE)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(newCursorPosition != 1) throwIfDebug(UnsupportedOperationException("newCursorPosition must be 1"))
        commitComposingTextInternal(text, useSetComposingRegion)
        return true
    }

    override fun finishComposingText(): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "finishComposingText, was [$composingText]")
        composingText = ""
        composingStart = -1
        composingEnd = -1
        //return super.finishComposingText()
        return true
    }

    fun setComposingRegionWithText(start: Int, end: Int, text: String?): Boolean {
        composingStart = start
        composingEnd = end
        composingText = text ?: ""

        if(text == null || BuildConfig.DEBUG) {
            val extracted = super.getExtractedText(ExtractedTextRequest(), 0)
            if(extracted == null || extracted.startOffset > start || extracted.text.length < end) {
                // Maybe our best bet here is to set cursor position to end, then get text before cursor (end-start)
                //  and then we could restore the position back, but that would be quite a mess...
                if(BuildConfig.DEBUG) Log.e(TAG, "setComposingRegion was out of bounds for extracted text")

                if(text == null) {
                    composingStart = -1
                    composingEnd = -1
                    composingText = ""
                    return false
                }
            } else {
                composingText = extracted.text.substring(
                    start - extracted.startOffset, end - extracted.startOffset
                )

                if(text != null && BuildConfig.DEBUG && composingText != text)
                    Log.e(TAG, "Expected composingText [$text], but actually got [$composingText]")
            }
        }

        if(BuildConfig.DEBUG) Log.d(TAG, "setComposingRegion acquired text=[$composingText]")

        if(useSetComposingRegion) return super.setComposingRegion(start, end)
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        return setComposingRegionWithText(start, end, null)
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "commitText [$text] $newCursorPosition")
        if(newCursorPosition != 1) throwIfDebug(UnsupportedOperationException("newCursorPosition must be 1"))

        if(composingStart != -1) {
            commitComposingTextInternal(text, false)
            finishComposingText()
        } else {
            typeChars(text)
        }
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "    deleteSurroundingText($beforeLength, $afterLength) $selStart:$selEnd")
        super.finishComposingText()
        if(selStart != -1) selStart = (selStart - beforeLength).coerceAtLeast(0)
        if(selEnd != -1) selEnd = (selEnd - beforeLength).coerceAtLeast(0)
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "    manual setSelection($start, $end) from $selStart:$selEnd")
        selStart = start
        selEnd = end
        return super.setSelection(start, end)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        throwIfDebug(UnsupportedOperationException("Please use deleteSurroundingText instead"))
        return false
    }

    override fun send() {
        (ic as? IBufferedInputConnection)?.send()
    }

    // TextAttributes are unsupported by this wrapper
    override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?) =
        commitText(text, newCursorPosition)

    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?) =
        setComposingRegion(start, end)

    override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?) =
        setComposingText(text, newCursorPosition)
}