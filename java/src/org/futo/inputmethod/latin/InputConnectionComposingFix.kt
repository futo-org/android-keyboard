package org.futo.inputmethod.latin

import android.annotation.SuppressLint
import android.util.Log
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute

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
@Suppress("HardCodedStringLiteral")
@SuppressLint("LogConditional")
class InputConnectionPatched(target: InputConnection?) : InputConnectionWrapper(target, true) {
    var ic = target

    fun updateIc(to: InputConnection?) {
        if(ic != to) {
            ic = to
            setTarget(to)
            if(BuildConfig.DEBUG) Log.d(TAG, "InputConnection updated!! Kill everything")
            selStart = -1
            selEnd = -1
            composingStart = -1
            composingEnd = -1
            composingText = ""
        }
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
        super.finishComposingText()
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
        if(text.length < 5) {
            text.forEach {
                super.commitText(it.toString(), 1)
            }
        } else {
            super.commitText(text, 1)
        }

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
        super.deleteSurroundingText(amount, 0)

        // In case editor is not sending cursor updates, try to keep track of it ourselves
        selStart -= amount
        selEnd -= amount
    }

    private fun commitComposingTextInternal(text: CharSequence, setComposing: Boolean) {
        if(BuildConfig.DEBUG) Log.d(TAG, "commitComposingTextInternal [$text] $setComposing")

        super.finishComposingText()
        val cursor = selStart
        if(cursor == -1) {
            if(BuildConfig.DEBUG) Log.d(TAG, "exit due to no cursor pos")
            requestCursorUpdates(CURSOR_UPDATE_IMMEDIATE)
        }
        if(composingStart != -1) {
            val isAddition = text.startsWith(composingText)
            if(isAddition) {
                // Simple case: when the new text starts with the previous text, we can just append
                // the new characters.
                beginBatchEdit()
                typeChars(text.substring(composingText.length))
                if(setComposing) super.setComposingRegion(composingStart, selStart)
                endBatchEdit()
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

                beginBatchEdit()
                backspace(cursor - composingStart - commonLength)
                typeChars(text.substring(commonLength))
                if(setComposing) super.setComposingRegion(composingStart, selStart)
                endBatchEdit()
            } else {
                // User probably moved the cursor
                // Not sure what to do here...
            }
        } else {
            composingStart = cursor
            beginBatchEdit()
            typeChars(text)
            if(setComposing) super.setComposingRegion(composingStart, selStart)
            endBatchEdit()
        }
        composingText = text.toString()
        requestCursorUpdates(CURSOR_UPDATE_IMMEDIATE)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(newCursorPosition == 1) {
            commitComposingTextInternal(text, true)
        } else {
            // TODO("Unsupported")
            commitComposingTextInternal(text, true)
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

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "setComposingRegion($start, $end), was [$composingText] $composingStart-$composingEnd")
        composingText = "?"
        composingStart = start
        composingEnd = end
        //return super.setComposingRegion(start, end)
        return true
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if(BuildConfig.DEBUG) Log.d(TAG, "commitText [$text] $newCursorPosition")
        if(newCursorPosition == 1) {
            commitComposingTextInternal(text, false)
        } else {
            // TODO: Unsupported
            commitComposingTextInternal(text, false)
        }
        finishComposingText()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        super.finishComposingText()
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        super.finishComposingText()
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
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

}