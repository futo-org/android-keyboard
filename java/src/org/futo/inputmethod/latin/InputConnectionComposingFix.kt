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
import org.futo.inputmethod.latin.uix.getSetting

/*
 This is an experimental wrapper for InputConnection to deal with apps that don't compose text
 properly or have weird input logic that behaves in unexpected ways (e.g. doesnt allow certain
 characters to be typed)

 Standard input procedure

 setComposing h
 setComposing he
 setComposing hel
 setComposing helk
 setComposing helko
 commitText   hello (autocorrected)

 We need to translate this to
 send keyevent h
 send keyevent e
 send keyevent l
 send keyevent k
 send keyevent o
 send keyevent backspace
 send keyevent backspace
 send keyevent l
 send keyevent o

 setComposing h
 setComposing he
 setComposing hel
 setComposing helk
 setComposing hel (backspaced)
 commitText hello (selected autocomplete)

 Weird edge cases
    App is filtering non-alphanumeric characters

 setComposing Hello                     actual text committed: [Hello]
 setComposing Hello, my deer friend.    actual text committed: [Hellomydeerfriend]
 setComposing Hello, my dear friend.

 Procedure to resolve a deletion
 1. Get past text
 2. Identify where it starts (I guess we can rely on composingStart being stable, just not composingEnd)
 3. Get common denominator, in this case: [Hello]
 4. Backspace N characters. N = currentCursorPosition - composingStart - lenCommonDenominator
 5. Retype the subsequent characters

 Problem: If we're calling setComposing back to back, we may not be able to rely on currentCursorPosition.

 Core logic for setComposing(text):
 if(alreadyComposing) {
   isAddition = text.startsWith(prevText)
   if(isAddition) {
     for char in text.substring(prevText.length) {
        sendKeyUpDown(char)
     }
   } else {
     // Cant rely on prevText being accurate
     pastText = getPastTextUntil(composingStart)

     commonLength = getCommonLength(pastText, text)
     backspace(cursorPosition - composingStart - commonLength)
     for char in text.substring(commonLength) {
       sendKeyUpDown(char)
     }
   }
 } else {
   // start composing
   composingStart = cursorPosition
 }
 prevText = text
 */

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

interface IBufferedInputConnection {
    fun send()
}

@Suppress("HardCodedStringLiteral")
@SuppressLint("LogConditional")
class InputConnectionPatched(val useComposing: Boolean, val useBuffering: Boolean, target: InputConnection?) : InputConnectionWrapper(null, true), IBufferedInputConnection {
    companion object {
        @JvmStatic
        fun createWithSettingsFromContext(context: Context, target: InputConnection): InputConnection {
            if(context.getSetting(TextInputAlternativeIC) == false) return target
            return InputConnectionPatched(
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
            if(useBuffering) BufferedInputConnection(it) else it
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
        if(BuildConfig.DEBUG) Log.d(TAG, " * Cursor updated: $oldSelStart-$oldSelEnd : $newSelStart-$newSelEnd * ")
        selStart = newSelStart
        selEnd = newSelEnd
        //super.finishComposingText()
    }

    /*private fun sendDownUpKeyEvent(keyCode: Int, metaState: Int) {
        val eventTime = SystemClock.uptimeMillis()
        super.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        super.sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }*/

    private fun typeChars(text: CharSequence) {
        super.commitText(text, 1)

        // In case editor is not sending cursor updates, try to keep track of it ourselves
        selStart += text.length
        selEnd += text.length
    }

    private fun getCommonLength(a: CharSequence, b: CharSequence): Int {
        var i = 0
        while(i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun backspace(amount: Int) {
        //if(BuildConfig.DEBUG) Log.d(TAG, "deleteSurroundingText($amount, 0) step 1 - textBeforeCursor = ${super.getTextBeforeCursor(60, 0)}")
        super.deleteSurroundingText(amount, 0)
        //if(BuildConfig.DEBUG) Log.d(TAG, "                                  step 2 - textBeforeCursor = ${super.getTextBeforeCursor(60, 0)}")

        // In case editor is not sending cursor updates, try to keep track of it ourselves
        selStart -= amount
        selEnd -= amount
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

    private fun commitComposingTextInternal(text: CharSequence, setComposing: Boolean) {
        val extracted: ExtractedText? = null// super.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 512 }, 0)
        if(BuildConfig.DEBUG) Log.d(TAG, "commitComposingTextInternal text=[$text] composingText=[$composingText] extracted=[${extracted?.text}][${extracted?.selectionStart} vs $selStart] composingStart=$composingStart setComposing=$setComposing ")

        if(setComposing) super.finishComposingText()
        var cursor = selStart
        var isAddition = true
        if(cursor == -1) {
            val extracted = super.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 512 }, 0)
            if(extracted == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "exit due to no cursor pos")
                //requestCursorUpdates(CURSOR_UPDATE_IMMEDIATE)
            } else {
                cursor = extracted.selectionStart
                selStart = cursor
            }
        }
        // :c
        /*if(extracted != null && extracted.selectionStart != selStart) {
            Log.e(TAG, "Expected selStart $selStart does not match reported selStart ${extracted.selectionStart}. Updating it...")
            cursor = extracted.selectionStart
            selStart = cursor
            isAddition = false
        }*/
        if(composingStart != -1) {
            if(cursor >= composingStart && cursor < composingEnd) {
                // Locate ourselves, may need to move cursor
                val textBefore = getTextBeforeCursor(composingText.length, 0)?.toString() ?: ""
                val textAfter = getTextAfterCursor(composingText.length, 0)?.toString() ?: ""
                val offs = locateWordEndOffset(composingText, textBefore, textAfter)
                if(offs != null && offs > 0) {
                    if(BuildConfig.DEBUG) Log.d(TAG, "Moving cursor by $offs to be at end of word")
                    super.setSelection(cursor + offs, cursor + offs)
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

                // Try to get past text. Some frameworks might filter out characters or do other
                // wonky stuff, so we can't trust that pastText is actually previous composingText.
                // If we did, we might accidentally delete too many characters trying to recover.
                // When editor doesn't keep track of text (e.g. vnc client), just fallback to
                // composingText.
                val pastText = super.getTextBeforeCursor(cursor - composingStart, 1)?.toString()
                    ?: composingText

                // Backtrack by getting the common starting length between the two strings, then
                // backspacing until there, and re-typing everything from there.
                val commonLength = getCommonLength(pastText, text)
                //Log.d(TAG, "pastText [$pastText], text [$text], common = $commonLength")
                //Log.d(TAG, "cursor is at $cursor, composingStart $composingStart. Deleting ${cursor - composingStart - commonLength}")

                if(BuildConfig.DEBUG) Log.d(TAG, " Case Complex: pastText=[$pastText] commonLength[$text]=$commonLength, therefore backspace $cursor - $composingStart - $commonLength = ${cursor - composingStart - commonLength} and type ${text.substring(commonLength)}.")

                backspace(cursor - composingStart - commonLength)
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
            composingStart = cursor
            typeChars(text)
            if(setComposing) super.setComposingRegion(composingStart, selStart)
        }
        composingText = text.toString()
        //requestCursorUpdates(CURSOR_UPDATE_IMMEDIATE)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(newCursorPosition == 1) {
            commitComposingTextInternal(text, useComposing)
        } else {
            // TODO("Unsupported")
            commitComposingTextInternal(text, useComposing)
        }
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

    fun setComposingRegionWithText(start: Int, end: Int, text: String): Boolean {
        val v = setComposingRegion(start, end)
        if(BuildConfig.DEBUG && composingText != text) {
            Log.e(TAG, "Expected composingText [$text] does not match actual [$composingText]")
        }
        return v
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "setComposingRegion($start, $end), was [$composingText] $composingStart-$composingEnd")
        if(end < start) throw IllegalArgumentException()

        val text = super.getExtractedText(ExtractedTextRequest(), 0)
        if(text == null) TODO()
        if(text.startOffset > start) TODO()

        if(text.text.length < end) {
            Log.e(TAG, "text length [${text.text}] is shorter than the requested end $start to $end")
            composingText = ""
            composingStart = -1
            composingEnd = -1
            return true
        }

        composingText = text.text.substring(
            start - text.startOffset, end - text.startOffset
        )

        composingStart = start
        composingEnd = end

        if(BuildConfig.DEBUG) Log.d(TAG, "setComposingRegion acquired text=[$composingText] from ${text.text} ${text.startOffset}")

        if(useComposing) return super.setComposingRegion(start, end)
        return true
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "commitText [$text] $newCursorPosition")
        if(newCursorPosition == 1) {
            commitComposingTextInternal(text, false)
        } else {
            // TODO("Unsupported [$text] [$newCursorPosition] [$composingText]")
            commitComposingTextInternal(text, false)
        }
        finishComposingText()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        super.finishComposingText()
        selStart -= beforeLength
        selEnd -= beforeLength
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        super.finishComposingText()
        selStart -= beforeLength
        selEnd -= beforeLength
        // TODO: Codepoints........
        //  (note: BufferedInputConnection treats it as codepoints anyway, but this is wrong for selStart/selEnd changing)
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    // ignore below
    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        // TODO
        return commitText(text, newCursorPosition)
    }

    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean {
        // TODO
        return setComposingRegion(start, end)
    }

    override fun setComposingText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        // TODO
        return setComposingText(text, newCursorPosition)
    }

    override fun send() {
        (ic as? IBufferedInputConnection)?.send()
    }
}


/*
 * Some apps misbehave when they receive multiple calls back to back
 * e.g. delete(4), commit("ello"), commit(" ")
 *
 * This fixes it by queueing all operations and sending them only at the end. A downside is that the
 * commands will not have immediate effect (e.g. getSurroundingText will not show updates until
 * send() is called), so send() should be called at the end to ensure consistent state. A workaround
 * is offered for getText[Before/After]Cursor
 */
class BufferedInputConnection(target: InputConnection) : InputConnectionWrapper(target, true), IBufferedInputConnection {
    sealed class InputCommand {
        data class Commit(val text: String) : InputCommand()
        data class Delete(val before: Int, val after: Int) : InputCommand()
    }

    val commandQueue = mutableListOf<InputCommand>()

    private fun merge(commands: List<InputCommand>): List<InputCommand> {
        var text = ""
        var deletedAmount = 0
        var deletedAfterAmount = 0

        for (cmd in commands) {
            when (cmd) {
                is InputCommand.Commit -> {
                    text += cmd.text
                }

                is InputCommand.Delete -> {
                    val len = text.codePointCount(0, text.length)
                    val keep = len - cmd.before
                    if(keep > 0) {
                        val end = text.offsetByCodePoints(0, keep)
                        text = text.substring(0, end)
                    } else {
                        text = ""
                        deletedAmount -= keep
                    }

                    deletedAfterAmount += cmd.after
                }
            }
        }

        return buildList {
            if(deletedAmount > 0 && deletedAfterAmount > 0 && text.isEmpty()) {
                add(InputCommand.Delete(deletedAmount, deletedAfterAmount))
            } else {
                if (deletedAmount > 0) add(InputCommand.Delete(deletedAmount, 0))
                if (text.isNotEmpty()) add(InputCommand.Commit(text))
                if (deletedAfterAmount > 0) add(InputCommand.Delete(0, deletedAfterAmount))
            }
        }
    }

    private fun applyBefore(beforeTxt: String): String {
        var result = beforeTxt
        commandQueue.forEach { cmd ->
            when(cmd) {
                is InputCommand.Commit -> result += cmd.text
                is InputCommand.Delete -> {
                    result = result.substring(0,
                        try {
                            result.offsetByCodePoints(result.length, -cmd.before)
                        } catch(e: IndexOutOfBoundsException) {
                            0
                        }
                    )
                }
            }
        }
        return result
    }

    private fun applyAfter(afterTxt: String): String {
        var result = afterTxt
        commandQueue.forEach { cmd ->
            when(cmd) {
                is InputCommand.Commit -> { }
                is InputCommand.Delete -> {
                    result = result.substring(
                        try {
                            result.offsetByCodePoints(0, cmd.after)
                        } catch(e: IndexOutOfBoundsException) {
                            result.length
                        }
                    )
                }
            }
        }
        return result
    }

    private fun extraHeadroom() = 16

    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "commit (" + text + ")")
        assert(newCursorPosition == 1)
        commandQueue.add(InputCommand.Commit(text.toString()))
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        assert(newCursorPosition == 1)
        if(text == null) return true
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "commit (" + text + ")")
        commandQueue.add(InputCommand.Commit(text.toString()))
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        commandQueue.add(InputCommand.Delete(beforeLength, afterLength))
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        if(afterLength > 0) super.deleteSurroundingTextInCodePoints(0, afterLength)
        commandQueue.add(InputCommand.Delete(beforeLength, afterLength))
        return true
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        return applyBefore(super.getTextBeforeCursor(n + extraHeadroom(), flags)?.toString() ?: return null).takeLast(n)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        return applyAfter(super.getTextAfterCursor(n + extraHeadroom(), flags)?.toString() ?: return null).take(n)
    }

    override fun send() {
        val mergedList = merge(commandQueue)
        if(BuildConfig.DEBUG) Log.d("BufferedInputConnection", "Command queue: $commandQueue, merged: $mergedList")
        commandQueue.clear()

        mergedList.forEach { when(it) {
            is InputCommand.Commit -> { super.commitText(it.text, 1) }
            is InputCommand.Delete -> { super.deleteSurroundingTextInCodePoints(it.before, it.after) }
        } }
    }
}
