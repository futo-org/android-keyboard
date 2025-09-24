package org.futo.inputmethod.latin.uix.settings.pages.buggyeditors

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal class EditableSpannable(
    text: CharSequence = ""
) : SpannableStringBuilder(text), Editable {
    fun setSelection(start: Int, end: Int = start) {
        if (start == end) {
            removeSpan(SELECTION_START)
            removeSpan(SELECTION_END)
            setSpan(SELECTION_START, start, start, SPAN_POINT_POINT)
            setSpan(SELECTION_END, start, start, SPAN_POINT_POINT)
        } else {
            setSpan(SELECTION_START, start, start, SPAN_POINT_POINT)
            setSpan(SELECTION_END, end, end, SPAN_POINT_POINT)
        }
    }

    companion object {
        private val SELECTION_START = ForegroundColorSpan(0)
        private val SELECTION_END   = ForegroundColorSpan(0)
    }
}

private val TAG = "BEditText"

class BuggyEditText @JvmOverloads constructor(
    context: Context,
    var config: BuggyEditorConfiguration,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {


    private val imm: InputMethodManager
        get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun showIme() {
        Log.d(TAG, "> imm.showSoftInput")
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideIme() {
        Log.d(TAG, "> imm.hideSoftInputFromWindow")
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    internal fun notifyImeSelection() {
        var ss = selectionStart
        var se = selectionEnd
        var cs = BuggyBaseInputConnection.getComposingSpanStart(buffer)
        var ce = BuggyBaseInputConnection.getComposingSpanEnd(buffer)

        if(config.alwaysSendsNoComposing) {
            cs = -1
            ce = -1
        }

        if(config.delayCursorUpdates == null) {
            Log.i(TAG, "> imm.updateSelection($ss, $se, $cs, $ce)")
            imm.updateSelection(
                this,
                ss,
                se,
                cs,
                ce
            )
        } else {
            GlobalScope.launch(Dispatchers.Default) {
                delay(config.delayCursorUpdates ?: 0)
                Log.i(TAG, "> imm.updateSelection($ss, $se, $cs, $ce)")
                imm.updateSelection(
                    this@BuggyEditText,
                    ss,
                    se,
                    cs,
                    ce
                )
            }
        }
    }

    var inputType: Int = 0
    var imeOptions: Int = 0
    var hint: String = ""
    var textColor: Int = Color.BLACK
    var hintTextColor: Int = Color.GRAY
    var cursorColor: Int = Color.BLUE

    var text: CharSequence
        get() = buffer
        set(value) {
            buffer.replace(0, buffer.length, value)
            setSelection(0, 0)
        }

    var selectionStart: Int
        get() = Selection.getSelectionStart(buffer)
        set(value) = setSelection(value, selectionEnd)

    var selectionEnd: Int
        get() = Selection.getSelectionEnd(buffer)
        set(value) = setSelection(selectionStart, value)

    fun setSelection(start: Int, end: Int = start) {
        val len = buffer.length
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(0, len)
        Selection.setSelection(buffer, s, e)
        notifyImeSelection()
        invalidate()
    }

    private val buffer = EditableSpannable()
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        color = textColor
    }
    private val hintPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        color = hintTextColor
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = cursorColor
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(200, widthMeasureSpec)
        val height = resolveSize((paint.textSize * 1.2f).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if(paint.color != textColor || hintPaint.color != hintTextColor || cursorColor != cursorPaint.color) {
            paint.color = textColor
            hintPaint.color = hintTextColor
            cursorPaint.color = cursorColor
        }
        // text
        canvas.drawText(buffer, 0, buffer.length, 0f, -paint.ascent(), paint)
        if(buffer.isEmpty() && hint.isNotEmpty()) {
            canvas.drawText(hint, 0, hint.length, 0f, -hintPaint.ascent(), hintPaint)
        }

        // cursor
        if (hasFocus()) {
            val pos = selectionStart
            val x = paint.measureText(buffer, 0, pos)
            canvas.drawLine(x, 0f, x, paint.textSize, cursorPaint)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = inputType
        outAttrs.imeOptions = imeOptions
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0

        return WrappedIC(object : BuggyBaseInputConnection(this@BuggyEditText as View, true, config) {
            override val editable = buffer

            override fun beginBatchEdit(): Boolean = true

            override fun endBatchEdit(): Boolean {
                invalidate()
                return true
            }

            override fun getExtractedText(
                request: ExtractedTextRequest?,
                flags: Int
            ): ExtractedText? {
                if(request == null) return null

                return ExtractedText().apply {
                    text = buffer
                    startOffset = 0
                    partialStartOffset = -1
                    partialEndOffset = -1
                    this.selectionStart = this@BuggyEditText.selectionStart
                    this.selectionEnd = this@BuggyEditText.selectionEnd
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        this.hint = this@BuggyEditText.hint
                    }

                    Log.d(TAG, "Outputting ExtractedText(text=$text, startOffset=$startOffset, selectionStart=$selectionStart, selectionEnd=$selectionEnd)")
                }.also {
                    imm.updateExtractedText(this@BuggyEditText, request.token, it)
                }
            }

            override fun onInvalidate(origin: String) {
                //Log.d(TAG, ". . invalidated by $origin")
                invalidate()
                if(!config.doesNotSendCursorUpdates) {
                    if(config.cursorMovesToEndAndBack) {
                        var oldStart = selectionStart
                        var oldEnd = selectionEnd

                        selectionStart = buffer.length
                        selectionEnd = buffer.length
                        notifyImeSelection()

                        selectionStart = oldStart
                        selectionEnd = oldEnd
                    }
                    notifyImeSelection()
                }

                config.composingTextResetsAfter?.let { delayMs ->
                    if(getComposingSpanStart(buffer) != -1) {
                        GlobalScope.launch(Dispatchers.Default) {
                            delay(delayMs)
                            finishComposingText()
                        }
                    }
                }
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            val start = selectionStart
                            val end = selectionEnd
                            if (start == end && start > 0) {
                                buffer.delete(start - 1, start)
                            } else {
                                buffer.delete(start, end)
                            }
                        }
                        KeyEvent.KEYCODE_ENTER -> {
                            buffer.insert(selectionStart, "\n")
                            setSelection(selectionStart + 1)
                        }
                        else -> if (event.unicodeChar > 0) {
                            val c = event.unicodeChar.toChar().toString()
                            buffer.replace(selectionStart, selectionEnd, c)
                            setSelection(selectionStart + 1)
                        }
                    }
                }
                invalidate()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            val pos = getOffsetForPosition(event.x, event.y)
            setSelection(pos)
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun getOffsetForPosition(x: Float, y: Float): Int {
        val layout = StaticLayout.Builder
            .obtain(buffer, 0, buffer.length, paint, width)
            .build()
        return layout.getOffsetForHorizontal(0, x)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            showIme()
        } else {
            hideIme()
        }
    }
}
