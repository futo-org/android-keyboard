package org.futo.inputmethod.latin.uix.settings.pages.buggyeditors

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.text.Editable
import android.text.NoCopySpan
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.MetaKeyKeyListener
import android.util.Log
import android.util.LogPrinter
import android.view.ContentInfo
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.SurroundingText
import android.view.inputmethod.TextAttribute
import android.view.inputmethod.TextSnapshot
import androidx.annotation.CallSuper
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min

private const val MEMORY_EFFICIENT_TEXT_LENGTH = 2048

open class BuggyBaseInputConnection : InputConnection {
    /** @hide
     */
    protected val mIMM: InputMethodManager

    /**
     * Target view for the input connection.
     *
     *
     * This could be null for a fallback input connection.
     */
    val mTargetView: View?

    val mFallbackMode: Boolean

    private var mDefaultComposingSpans: Array<Any> = emptyArray()

    var mEditable: Editable? = null
    var mKeyCharacterMap: KeyCharacterMap? = null
    var mConfig: BuggyEditorConfiguration = BuggyEditorConfiguration()

    open fun onInvalidate(origin: String) { }

    internal constructor(mgr: InputMethodManager, fullEditor: Boolean) {
        mIMM = mgr
        mTargetView = null
        mFallbackMode = !fullEditor
    }

    constructor(targetView: View, fullEditor: Boolean, config: BuggyEditorConfiguration) {
        mIMM = (targetView.context.getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager?)!!
        mTargetView = targetView
        mFallbackMode = !fullEditor
        mConfig = config
    }

    open val editable: Editable?
        /**
         * Return the target of edit operations. The default implementation returns its own fake
         * editable that is just used for composing text; subclasses that are real text editors should
         * override this and supply their own.
         *
         *
         * Subclasses could override this method to turn null.
         */
        get() {
            if (mEditable == null) {
                mEditable = Editable.Factory.getInstance().newEditable("")
                Selection.setSelection(mEditable, 0)
            }
            return mEditable
        }

    /** Default implementation does nothing.  */
    override fun beginBatchEdit(): Boolean {
        return false
    }

    /** Default implementation does nothing.  */
    override fun endBatchEdit(): Boolean {
        return false
    }

    /**
     * Called after only the composing region is modified (so it isn't called if the text also
     * changes).
     *
     *
     * Default implementation does nothing.
     *
     * @hide
     */
    fun endComposingRegionEditInternal() {}

    /**
     * Default implementation calls [.finishComposingText] and `setImeConsumesInput(false)`.
     */
    @CallSuper
    override fun closeConnection() {
        finishComposingText()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setImeConsumesInput(false)
        }
    }

    /**
     * Default implementation uses [ MetaKeyKeyListener.clearMetaKeyState(long, int)][MetaKeyKeyListener.clearMetaKeyState] to clear the state.
     */
    override fun clearMetaKeyStates(states: Int): Boolean {
        val content = this.editable
        if (content == null) return false
        MetaKeyKeyListener.clearMetaKeyState(content, states)
        return true
    }

    /** Default implementation does nothing and returns false.  */
    override fun commitCompletion(text: CompletionInfo?): Boolean {
        return false
    }

    /** Default implementation does nothing and returns false.  */
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
        return false
    }

    /**
     * Default implementation replaces any existing composing text with the given text. In addition,
     * only if fallback mode, a key event is sent for the new text and the current editable buffer
     * cleared.
     */
    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (DEBUG) Log.v(TAG, "commitText(" + text + ", " + newCursorPosition + ")")
        replaceText(text, newCursorPosition, false)
        onInvalidate("commitText")
        sendCurrentText()
        return true
    }

    /**
     * The default implementation performs the deletion around the current selection position of the
     * editable text.
     *
     * @param beforeLength The number of characters before the cursor to be deleted, in code unit.
     * If this is greater than the number of existing characters between the beginning of the
     * text and the cursor, then this method does not fail but deletes all the characters in
     * that range.
     * @param afterLength The number of characters after the cursor to be deleted, in code unit. If
     * this is greater than the number of existing characters between the cursor and the end of
     * the text, then this method does not fail but deletes all the characters in that range.
     * @return `true` when selected text is deleted, `false` when either the selection
     * is invalid or not yet attached (i.e. selection start or end is -1), or the editable text
     * is `null`.
     */
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (DEBUG) Log.v(TAG, "deleteSurroundingText(" + beforeLength + ", " + afterLength + ")")
        val content = this.editable
        if (content == null) return false

        beginBatchEdit()

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        // Skip when the selection is not yet attached.
        if (a == -1 || b == -1) {
            endBatchEdit()
            return false
        }

        // Ignore the composing text.
        var ca = getComposingSpanStart(content)
        var cb = getComposingSpanEnd(content)
        if (cb < ca) {
            val tmp = ca
            ca = cb
            cb = tmp
        }
        if (ca != -1 && cb != -1) {
            if (ca < a) a = ca
            if (cb > b) b = cb
        }

        var deleted = 0

        if (beforeLength > 0) {
            var start = a - beforeLength
            if (start < 0) start = 0

            val numDeleteBefore = a - start
            if (a >= 0 && numDeleteBefore > 0) {
                content.delete(start, a)
                deleted = numDeleteBefore
            }
        }

        if (afterLength > 0) {
            b = b - deleted

            var end = b + afterLength
            if (end > content.length) end = content.length

            val numDeleteAfter = end - b
            if (b >= 0 && numDeleteAfter > 0) {
                content.delete(b, end)
            }
        }

        endBatchEdit()

        onInvalidate("deleteSurroundingText")

        return true
    }

    /**
     * The default implementation performs the deletion around the current selection position of the
     * editable text.
     *
     * @param beforeLength The number of characters before the cursor to be deleted, in code points.
     * If this is greater than the number of existing characters between the beginning of the
     * text and the cursor, then this method does not fail but deletes all the characters in
     * that range.
     * @param afterLength The number of characters after the cursor to be deleted, in code points.
     * If this is greater than the number of existing characters between the cursor and the end
     * of the text, then this method does not fail but deletes all the characters in that range.
     */
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        if (DEBUG) Log.v(TAG, "deleteSurroundingText " + beforeLength + " / " + afterLength)
        val content = this.editable
        if (content == null) return false

        beginBatchEdit()

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        // Ignore the composing text.
        var ca = getComposingSpanStart(content)
        var cb = getComposingSpanEnd(content)
        if (cb < ca) {
            val tmp = ca
            ca = cb
            cb = tmp
        }
        if (ca != -1 && cb != -1) {
            if (ca < a) a = ca
            if (cb > b) b = cb
        }

        if (a >= 0 && b >= 0) {
            val start = findIndexBackward(content, a, max(beforeLength.toDouble(), 0.0).toInt())
            if (start != INVALID_INDEX) {
                val end = findIndexForward(content, b, max(afterLength.toDouble(), 0.0).toInt())
                if (end != INVALID_INDEX) {
                    val numDeleteBefore = a - start
                    if (numDeleteBefore > 0) {
                        content.delete(start, a)
                    }
                    val numDeleteAfter = end - b
                    if (numDeleteAfter > 0) {
                        content.delete(b - numDeleteBefore, end - numDeleteBefore)
                    }
                }
            }
            // NOTE: You may think we should return false here if start and/or end is INVALID_INDEX,
            // but the truth is that IInputConnectionWrapper running in the middle of IPC calls
            // always returns true to the IME without waiting for the completion of this method as
            // IInputConnectionWrapper#isAtive() returns true.  This is actually why some methods
            // including this method look like asynchronous calls from the IME.
        }

        endBatchEdit()

        onInvalidate("deleteSurroundingTextInCodePoints")

        return true
    }

    /**
     * The default implementation removes the composing state from the current editable text. In
     * addition, only if fallback mode, a key event is sent for the new text and the current
     * editable buffer cleared.
     */
    override fun finishComposingText(): Boolean {
        if (DEBUG) Log.v(TAG, "finishComposingText")
        val content = this.editable
        if (content != null) {
            beginBatchEdit()
            removeComposingSpans(content)
            // Note: sendCurrentText does nothing unless mFallbackMode is set
            onInvalidate("finishComposingText")
            sendCurrentText()
            endBatchEdit()
            endComposingRegionEditInternal()
        }
        return true
    }

    /**
     * The default implementation uses TextUtils.getCapsMode to get the cursor caps mode for the
     * current selection position in the editable text, unless in fallback mode in which case 0 is
     * always returned.
     */
    override fun getCursorCapsMode(reqModes: Int): Int {
        if (mFallbackMode) return 0

        val content = this.editable
        if (content == null) return 0

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        return TextUtils.getCapsMode(content, a, reqModes)
    }

    /** The default implementation always returns null.  */
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        return null
    }

    /**
     * The default implementation returns the given amount of text from the current cursor position
     * in the buffer.
     */
    override fun getTextBeforeCursor(@IntRange(from = 0) length: Int, flags: Int): CharSequence? {
        var length = length

        val content = this.editable
        if (content == null) return null

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        if (a <= 0) {
            return ""
        }

        if (length > a) {
            length = a
        }

        if ((flags and InputConnection.GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(a - length, a)
        }
        return TextUtils.substring(content, a - length, a)
    }

    /**
     * The default implementation returns the text currently selected, or null if none is selected.
     */
    override fun getSelectedText(flags: Int): CharSequence? {
        val content = this.editable
        if (content == null) return null

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        if (a == b || a < 0) return null

        if ((flags and InputConnection.GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(a, b)
        }
        return TextUtils.substring(content, a, b)
    }

    /**
     * The default implementation returns the given amount of text from the current cursor position
     * in the buffer.
     */
    override fun getTextAfterCursor(@IntRange(from = 0) length: Int, flags: Int): CharSequence? {
        val content = this.editable
        if (content == null) return null

        var a = Selection.getSelectionStart(content)
        var b = Selection.getSelectionEnd(content)

        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }

        // Guard against the case where the cursor has not been positioned yet.
        if (b < 0) {
            b = 0
        }
        val end = min((b.toLong() + length).toDouble(), content.length.toDouble()).toInt()
        if ((flags and InputConnection.GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(b, end)
        }
        return TextUtils.substring(content, b, end)
    }

    /**
     * The default implementation returns the given amount of text around the current cursor
     * position in the buffer.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun getSurroundingText(
        @IntRange(from = 0) beforeLength: Int, @IntRange(from = 0) afterLength: Int, flags: Int
    ): SurroundingText? {
        val content = this.editable
        // If {@link #getEditable()} is null or {@code mEditable} is equal to {@link #getEditable()}
        // (a.k.a, a fake editable), it means we cannot get valid content from the editable, so
        // fallback to retrieve surrounding text from other APIs.
        if (content == null || mEditable === content) {
            return super.getSurroundingText(beforeLength, afterLength, flags)
        }

        var selStart = Selection.getSelectionStart(content)
        var selEnd = Selection.getSelectionEnd(content)

        // Guard against the case where the cursor has not been positioned yet.
        if (selStart < 0 || selEnd < 0) {
            return null
        }

        if (selStart > selEnd) {
            val tmp = selStart
            selStart = selEnd
            selEnd = tmp
        }

        // Guards the start and end pos within range [0, contentLength].
        val startPos = max(0.0, (selStart - beforeLength).toDouble()).toInt()
        val endPos =
            min((selEnd.toLong() + afterLength).toDouble(), content.length.toDouble()).toInt()

        val surroundingText: CharSequence
        if ((flags and InputConnection.GET_TEXT_WITH_STYLES) != 0) {
            surroundingText = content.subSequence(startPos, endPos)
        } else {
            surroundingText = TextUtils.substring(content, startPos, endPos)
        }
        return SurroundingText(
            surroundingText, selStart - startPos, selEnd - startPos, startPos
        )
    }

    /** The default implementation turns this into the enter key.  */
    override fun performEditorAction(actionCode: Int): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                (KeyEvent.FLAG_SOFT_KEYBOARD
                        or KeyEvent.FLAG_KEEP_TOUCH_MODE
                        or KeyEvent.FLAG_EDITOR_ACTION)
            )
        )
        sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(),
                eventTime,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                (KeyEvent.FLAG_SOFT_KEYBOARD
                        or KeyEvent.FLAG_KEEP_TOUCH_MODE
                        or KeyEvent.FLAG_EDITOR_ACTION)
            )
        )
        return true
    }

    override fun performContextMenuAction(id: Int): Boolean {
        when(id) {
            android.R.id.selectAll -> {
                setSelection(0, editable!!.length)
                return true
            }
        }
        return false
    }

    /** The default implementation does nothing.  */
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
        return false
    }

    /** The default implementation does nothing.  */
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
        return false
    }

    override fun getHandler(): Handler? {
        return null
    }

    /**
     * The default implementation places the given text into the editable, replacing any existing
     * composing text. The new text is marked as in a composing state with the composing style.
     */
    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (DEBUG) Log.v(TAG, "setComposingText(" + text + ", " + newCursorPosition + ")")
        replaceText(text, newCursorPosition, true)
        onInvalidate("setComposingText")
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        if (DEBUG) Log.v(TAG, "setComposingRegion(" + start + ", " + end + ")")
        val content = this.editable
        if (content != null) {
            beginBatchEdit()
            removeComposingSpans(content)
            var a = start
            var b = end
            if (a > b) {
                val tmp = a
                a = b
                b = tmp
            }
            // Clip the end points to be within the content bounds.
            val length = content.length
            if (a < 0) a = 0
            if (b < 0) b = 0
            if (a > length) a = length
            if (b > length) b = length

            ensureDefaultComposingSpans()
            if (mDefaultComposingSpans.isNotEmpty()) {
                for (i in mDefaultComposingSpans.indices) {
                    content.setSpan(
                        mDefaultComposingSpans[i],
                        a,
                        b,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
                    )
                }
            }

            if(!mConfig.noComposing) {
                content.setSpan(
                    COMPOSING, a, b,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
                )
            }

            // Note: sendCurrentText does nothing unless mFallbackMode is set
            sendCurrentText()
            onInvalidate("setComposingRegion")
            endBatchEdit()
            endComposingRegionEditInternal()
        }
        return true
    }

    /** The default implementation changes the selection position in the current editable text.  */
    override fun setSelection(start: Int, end: Int): Boolean {
        if (DEBUG) Log.v(TAG, "setSelection(" + start + ", " + end + ")")
        val content = this.editable
        if (content == null) return false
        val len = content.length
        if (start > len || end > len || start < 0 || end < 0) {
            // If the given selection is out of bounds, just ignore it.
            // Most likely the text was changed out from under the IME,
            // and the IME is going to have to update all of its state
            // anyway.
            return true
        }
        if (start == end && MetaKeyKeyListener.getMetaState(
                content,
                MetaKeyKeyListener.META_SHIFT_ON
            ) != 0
        ) {
            // If we are in selection mode, then we want to extend the
            // selection instead of replacing it.
            Selection.extendSelection(content, start)
        } else {
            Selection.setSelection(content, start, end)
        }
        onInvalidate("setSelection")
        return true
    }

    /**
     * Provides standard implementation for sending a key event to the window attached to the input
     * connection's view.
     */
    override fun sendKeyEvent(event: KeyEvent): Boolean {
        mIMM.dispatchKeyEventFromInputMethod(mTargetView, event)
        return false
    }

    /** Updates InputMethodManager with the current fullscreen mode.  */
    override fun reportFullscreenMode(enabled: Boolean): Boolean {
        return true
    }

    private fun sendCurrentText() {
        if (!mFallbackMode) {
            return
        }

        val content = this.editable
        if (content != null) {
            val N = content.length
            if (N == 0) {
                return
            }
            if (N == 1) {
                // If it's 1 character, we have a chance of being
                // able to generate normal key events...
                if (mKeyCharacterMap == null) {
                    mKeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                }
                val chars = CharArray(1)
                content.getChars(0, 1, chars, 0)
                val events = mKeyCharacterMap!!.getEvents(chars)
                if (events != null) {
                    for (i in events.indices) {
                        if (DEBUG) Log.v(TAG, "Sending: " + events[i])
                        sendKeyEvent(events[i]!!)
                    }
                    content.clear()
                    return
                }
            }

            // Otherwise, revert to the special key event containing
            // the actual characters.
            val event = KeyEvent(
                SystemClock.uptimeMillis(),
                content.toString(), KeyCharacterMap.VIRTUAL_KEYBOARD, 0
            )
            sendKeyEvent(event)
            content.clear()
        }
    }

    private fun ensureDefaultComposingSpans() {
        if (mDefaultComposingSpans.isEmpty()) {
            val context = mTargetView!!.context
            if (context != null) {
                val ta = context.getTheme()
                    .obtainStyledAttributes(
                        intArrayOf(
                            //com.android.internal.R.attr.candidatesTextStyleSpans
                        )
                    )
                val style = ta.getText(0)
                ta.recycle()
                if (style != null && style is Spanned) {
                    mDefaultComposingSpans = style.getSpans<Any?>(
                        0, style.length, Any::class.java
                    )
                }
            }
        }
    }

    override fun replaceText(
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        var start = start
        var end = end

        if (DEBUG) {
            Log.v(
                TAG,
                "replaceText " + start + ", " + end + ", " + text + ", " + newCursorPosition
            )
        }

        val content = this.editable
        if (content == null) {
            return false
        }
        beginBatchEdit()
        removeComposingSpans(content)

        val len = content.length
        start = min(start.toDouble(), len.toDouble()).toInt()
        end = min(end.toDouble(), len.toDouble()).toInt()
        if (end < start) {
            val tmp = start
            start = end
            end = tmp
        }
        replaceTextInternal(start, end, text, newCursorPosition,  /*composing=*/false)
        endBatchEdit()
        return true
    }

    private fun replaceText(text: CharSequence, newCursorPosition: Int, composing: Boolean) {
        val content = this.editable
        if (content == null) {
            return
        }

        beginBatchEdit()

        // delete composing text set previously.
        var a = getComposingSpanStart(content)
        var b = getComposingSpanEnd(content)

        if (DEBUG) Log.v(TAG, "Composing span: " + a + " to " + b)

        if (b < a) {
            val tmp = a
            a = b
            b = tmp
        }

        if (a != -1 && b != -1) {
            removeComposingSpans(content)
        } else {
            a = Selection.getSelectionStart(content)
            b = Selection.getSelectionEnd(content)
            if (a < 0) a = 0
            if (b < 0) b = 0
            if (b < a) {
                val tmp = a
                a = b
                b = tmp
            }
        }
        replaceTextInternal(a, b, text, newCursorPosition, composing)
        endBatchEdit()
    }

    private fun replaceTextInternal(
        a: Int, b: Int, text: CharSequence, newCursorPosition: Int, composing: Boolean
    ) {
        var text = text
        var newCursorPosition = newCursorPosition
        val content = this.editable
        if (content == null) {
            return
        }

        if (composing && !mConfig.noComposing && !mConfig.setComposingTextDoesNotCompose) {
            var sp: Spannable? = null
            if (text !is Spannable) {
                sp = SpannableStringBuilder(text)
                text = sp
                ensureDefaultComposingSpans()
                if (mDefaultComposingSpans.isNotEmpty()) {
                    for (i in mDefaultComposingSpans.indices) {
                        sp.setSpan(
                            mDefaultComposingSpans[i], 0, sp.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
                        )
                    }
                }
            } else {
                sp = text
            }
            setComposingSpans(sp)
        }

        if (DEBUG) {
            Log.v(
                TAG,
                ("Replacing from "
                        + a
                        + " to "
                        + b
                        + " with \""
                        + text
                        + "\", composing="
                        + composing
                        + ", newCursorPosition="
                        + newCursorPosition
                        + ", type="
                        + text.javaClass.getCanonicalName())
            )

            val lp = LogPrinter(Log.VERBOSE, TAG)
            lp.println("Current text:")
            TextUtils.dumpSpans(content, lp, "  ")
            lp.println("Composing text:")
            TextUtils.dumpSpans(text, lp, "  ")
        }

        // Position the cursor appropriately, so that after replacing the desired range of text it
        // will be located in the correct spot.
        // This allows us to deal with filters performing edits on the text we are providing here.
        val requestedNewCursorPosition = newCursorPosition
        if (newCursorPosition > 0) {
            newCursorPosition += b - 1
        } else {
            newCursorPosition += a
        }
        if (newCursorPosition < 0) newCursorPosition = 0
        if (newCursorPosition > content.length) newCursorPosition = content.length
        Selection.setSelection(content, newCursorPosition)
        content.replace(a, b, text)

        // Replace (or insert) to the cursor (a==b==newCursorPosition) will position the cursor to
        // the end of the new replaced/inserted text, we need to re-position the cursor to the start
        // according the API definition: "if <= 0, this is relative to the start of the text".
        if (requestedNewCursorPosition == 0 && a == b) {
            Selection.setSelection(content, newCursorPosition)
        }

        if (DEBUG) {
            val lp = LogPrinter(Log.VERBOSE, TAG)
            lp.println("Final text:")
            TextUtils.dumpSpans(content, lp, "  ")
        }
    }

    /**
     * Default implementation which invokes [View.performReceiveContent] on the target view if
     * the view [allows][View.getReceiveContentMimeTypes] content insertion; otherwise returns
     * false without any side effects.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun commitContent(
        inputContentInfo: InputContentInfo,
        flags: Int,
        opts: Bundle?
    ): Boolean {
        if (mTargetView == null) {
            return false
        }

        val description = inputContentInfo.getDescription()
        if (mTargetView.getReceiveContentMimeTypes() == null) {
            if (DEBUG) {
                Log.d(TAG, "Can't insert content from IME: content=" + description)
            }
            return false
        }
        if ((flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                Log.w(TAG, "Can't insert content from IME; requestPermission() failed", e)
                return false
            }
        }
        val clip = ClipData(
            inputContentInfo.getDescription(),
            ClipData.Item(inputContentInfo.getContentUri())
        )
        val payload: ContentInfo = ContentInfo.Builder(clip, ContentInfo.SOURCE_INPUT_METHOD)
            .setLinkUri(inputContentInfo.getLinkUri())
            .setExtras(opts)
            //.setInputContentInfo(inputContentInfo)
            .build()
        return mTargetView.performReceiveContent(payload) == null
    }

    /**
     * Default implementation that constructs [TextSnapshot] with information extracted from
     * [].
     *
     * @return `null` when [TextSnapshot] cannot be fully taken.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun takeSnapshot(): TextSnapshot? {
        val content = this.editable
        if (content == null) {
            return null
        }
        var composingStart = getComposingSpanStart(content)
        var composingEnd = getComposingSpanEnd(content)
        if (composingEnd < composingStart) {
            val tmp = composingStart
            composingStart = composingEnd
            composingEnd = tmp
        }

        val surroundingText = getSurroundingText(
            MEMORY_EFFICIENT_TEXT_LENGTH / 2,
            MEMORY_EFFICIENT_TEXT_LENGTH / 2, InputConnection.GET_TEXT_WITH_STYLES
        )
        if (surroundingText == null) {
            return null
        }

        val cursorCapsMode = getCursorCapsMode(
            (TextUtils.CAP_MODE_CHARACTERS
                    or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES)
        )

        return TextSnapshot(surroundingText, composingStart, composingEnd, cursorCapsMode)
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "DInputConnection"
        val COMPOSING: Any = object : NoCopySpan { }

        /**
         * Removes the composing spans from the given text if any.
         *
         * @param text the spannable text to remove composing spans
         */
        fun removeComposingSpans(text: Spannable) {
            text.removeSpan(COMPOSING)
            val sps = text.getSpans<Any?>(0, text.length, Any::class.java)
            if (sps != null) {
                for (i in sps.indices.reversed()) {
                    val o = sps[i]
                    if ((text.getSpanFlags(o) and Spanned.SPAN_COMPOSING) != 0) {
                        text.removeSpan(o)
                    }
                }
            }
        }

        /**
         * Removes the composing spans from the given text if any.
         *
         * @param text the spannable text to remove composing spans
         */
        fun setComposingSpans(text: Spannable) {
            setComposingSpans(text, 0, text.length)
        }

        /** @hide
         */
        fun setComposingSpans(text: Spannable, start: Int, end: Int) {
            val sps = text.getSpans<Any?>(start, end, Any::class.java)
            if (sps != null) {
                for (i in sps.indices.reversed()) {
                    val o = sps[i]
                    if (o === COMPOSING) {
                        text.removeSpan(o)
                        continue
                    }

                    val fl = text.getSpanFlags(o)
                    if ((fl and (Spanned.SPAN_COMPOSING or Spanned.SPAN_POINT_MARK_MASK))
                        != (Spanned.SPAN_COMPOSING or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ) {
                        text.setSpan(
                            o,
                            text.getSpanStart(o),
                            text.getSpanEnd(o),
                            ((fl and Spanned.SPAN_POINT_MARK_MASK.inv())
                                    or Spanned.SPAN_COMPOSING
                                    or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        )
                    }
                }
            }

            text.setSpan(
                COMPOSING, start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
            )
        }

        /** Return the beginning of the range of composing text, or -1 if there's no composing text.  */
        fun getComposingSpanStart(text: Spannable): Int {
            return text.getSpanStart(COMPOSING)
        }

        /** Return the end of the range of composing text, or -1 if there's no composing text.  */
        fun getComposingSpanEnd(text: Spannable): Int {
            return text.getSpanEnd(COMPOSING)
        }

        private val INVALID_INDEX = -1
        private fun findIndexBackward(
            cs: CharSequence, from: Int,
            numCodePoints: Int
        ): Int {
            var currentIndex = from
            var waitingHighSurrogate = false
            val N = cs.length
            if (currentIndex < 0 || N < currentIndex) {
                return INVALID_INDEX // The starting point is out of range.
            }
            if (numCodePoints < 0) {
                return INVALID_INDEX // Basically this should not happen.
            }
            var remainingCodePoints = numCodePoints
            while (true) {
                if (remainingCodePoints == 0) {
                    return currentIndex // Reached to the requested length in code points.
                }

                --currentIndex
                if (currentIndex < 0) {
                    if (waitingHighSurrogate) {
                        return INVALID_INDEX // An invalid surrogate pair is found.
                    }
                    return 0 // Reached to the beginning of the text w/o any invalid surrogate pair.
                }
                val c = cs.get(currentIndex)
                if (waitingHighSurrogate) {
                    if (!Character.isHighSurrogate(c)) {
                        return INVALID_INDEX // An invalid surrogate pair is found.
                    }
                    waitingHighSurrogate = false
                    --remainingCodePoints
                    continue
                }
                if (!Character.isSurrogate(c)) {
                    --remainingCodePoints
                    continue
                }
                if (Character.isHighSurrogate(c)) {
                    return INVALID_INDEX // A invalid surrogate pair is found.
                }
                waitingHighSurrogate = true
            }
        }

        private fun findIndexForward(
            cs: CharSequence, from: Int,
            numCodePoints: Int
        ): Int {
            var currentIndex = from
            var waitingLowSurrogate = false
            val N = cs.length
            if (currentIndex < 0 || N < currentIndex) {
                return INVALID_INDEX // The starting point is out of range.
            }
            if (numCodePoints < 0) {
                return INVALID_INDEX // Basically this should not happen.
            }
            var remainingCodePoints = numCodePoints

            while (true) {
                if (remainingCodePoints == 0) {
                    return currentIndex // Reached to the requested length in code points.
                }

                if (currentIndex >= N) {
                    if (waitingLowSurrogate) {
                        return INVALID_INDEX // An invalid surrogate pair is found.
                    }
                    return N // Reached to the end of the text w/o any invalid surrogate pair.
                }
                val c = cs.get(currentIndex)
                if (waitingLowSurrogate) {
                    if (!Character.isLowSurrogate(c)) {
                        return INVALID_INDEX // An invalid surrogate pair is found.
                    }
                    --remainingCodePoints
                    waitingLowSurrogate = false
                    ++currentIndex
                    continue
                }
                if (!Character.isSurrogate(c)) {
                    --remainingCodePoints
                    ++currentIndex
                    continue
                }
                if (Character.isLowSurrogate(c)) {
                    return INVALID_INDEX // A invalid surrogate pair is found.
                }
                waitingLowSurrogate = true
                ++currentIndex
            }
        }
    }
}
