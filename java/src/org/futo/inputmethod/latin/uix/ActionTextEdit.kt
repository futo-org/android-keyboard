package org.futo.inputmethod.latin.uix

import android.content.Context
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.HandwritingGesture
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.PreviewableHandwritingGesture
import android.view.inputmethod.SurroundingText
import android.view.inputmethod.TextAttribute
import android.view.inputmethod.TextBoundsInfoResult
import android.view.inputmethod.TextSnapshot
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.IntConsumer

/** Default InputConnection will not handle key events properly because the view technically is not
 * focused, so this wrapper will make sure KeyEvents get sent properly */
class ActionEditTextInputConnection(val ic: InputConnection, val view: ActionEditText) : InputConnection {
    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        return view.dispatchKeyEvent(event)
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = ic.getTextBeforeCursor(n, flags)
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = ic.getTextAfterCursor(n, flags)
    override fun getSelectedText(flags: Int): CharSequence? = ic.getSelectedText(flags)
    override fun getCursorCapsMode(reqModes: Int): Int = ic.getCursorCapsMode(reqModes)
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = ic.getExtractedText(request, flags)
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = ic.deleteSurroundingText(beforeLength, afterLength)
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = ic.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = ic.setComposingText(text, newCursorPosition)
    override fun setComposingRegion(start: Int, end: Int): Boolean = ic.setComposingRegion(start, end)
    override fun finishComposingText(): Boolean = ic.finishComposingText()
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = ic.commitText(text, newCursorPosition)
    override fun commitCompletion(text: CompletionInfo?): Boolean = ic.commitCompletion(text)
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = ic.commitCorrection(correctionInfo)
    override fun setSelection(start: Int, end: Int): Boolean = ic.setSelection(start, end)
    override fun performEditorAction(editorAction: Int): Boolean = ic.performEditorAction(editorAction)
    override fun performContextMenuAction(id: Int): Boolean = ic.performContextMenuAction(id)
    override fun beginBatchEdit(): Boolean = ic.beginBatchEdit()
    override fun endBatchEdit(): Boolean = ic.endBatchEdit()
    override fun clearMetaKeyStates(states: Int): Boolean = ic.clearMetaKeyStates(states)
    override fun reportFullscreenMode(enabled: Boolean): Boolean = ic.reportFullscreenMode(enabled)
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = ic.performPrivateCommand(action, data)
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = ic.requestCursorUpdates(cursorUpdateMode)
    override fun getHandler(): Handler? = ic.getHandler()
    override fun closeConnection() = ic.closeConnection()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? = ic.getSurroundingText(beforeLength, afterLength, flags)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun performHandwritingGesture(gesture: HandwritingGesture, executor: Executor?, consumer: IntConsumer?) = ic.performHandwritingGesture(gesture, executor, consumer)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun performSpellCheck(): Boolean = ic.performSpellCheck()
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun previewHandwritingGesture(gesture: PreviewableHandwritingGesture, cancellationSignal: CancellationSignal?): Boolean = ic.previewHandwritingGesture(gesture, cancellationSignal)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = ic.replaceText(start, end, text, newCursorPosition, textAttribute)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun requestCursorUpdates(cursorUpdateMode: Int, cursorUpdateFilter: Int): Boolean = ic.requestCursorUpdates(cursorUpdateMode, cursorUpdateFilter)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun requestTextBoundsInfo(bounds: RectF, executor: Executor, consumer: Consumer<TextBoundsInfoResult?>) = ic.requestTextBoundsInfo(bounds, executor, consumer)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean = ic.setComposingRegion(start, end, textAttribute)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = ic.setComposingText(text, newCursorPosition, textAttribute)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = ic.setImeConsumesInput(imeConsumesInput)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun takeSnapshot(): TextSnapshot? = ic.takeSnapshot()
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = ic.commitContent(inputContentInfo, flags, opts)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = ic.commitText(text, newCursorPosition, textAttribute)

    override fun equals(other: Any?): Boolean = ic.equals(other)
    override fun hashCode(): Int = ic.hashCode()
}

class ActionEditText(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
    val inspection: Boolean = false
) :
    androidx.appcompat.widget.AppCompatEditText(context, attrs, defStyleAttr) {
    var inputConnection: InputConnection? = null
        private set

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if(inspection) return null
        inputConnection = super.onCreateInputConnection(outAttrs)?.let {
            ActionEditTextInputConnection(it, this)
        }

        return inputConnection
    }

    private var textChanged: (String) -> Unit = { }
    fun setTextChangeCallback(
        textChanged: (String) -> Unit
    ) {
        this.textChanged = textChanged
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // For some strange reason this IS null sometimes, even though it
        // shouldn't be
        if(textChanged != null) {
            textChanged(text?.toString() ?: "")
        }
    }
}


@Composable
private fun GenericEditTextCompose(
    text: MutableState<String>,
    multiline: Boolean,
    textSize: TextUnit,
    placeholder: String?,
    autocorrect: Boolean,
    typeface: Typeface? = null,
    modifier: Modifier = Modifier,
    onOverride: ((InputConnection, EditorInfo) -> Unit)? = null,
    onUnoverride: (() -> Unit)? = null,
    autofocus: Boolean = false,
    forceQwerty: Boolean = false,
) {
    val context = LocalContext.current

    val height = with(LocalDensity.current) {
        48.dp.toPx()
    }

    val inputType = if(multiline) {
        EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
    } else {
        EditorInfo.TYPE_CLASS_TEXT
    } or if(autocorrect) {
        EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
    } else {
        0
    }

    val color = LocalContentColor.current
    val textSizeToUse = with(LocalDensity.current) { textSize.toPx() }
    val typefaceToUse = typeface
    val inspection = LocalInspectionMode.current
    val editText = remember {
        ActionEditText(context, inspection = inspection).apply {
            this.inputType = inputType

            setTextChangeCallback { text.value = it }

            setText(text.value)
            setTextColor(color.toArgb())
            placeholder?.let { setHint(it) }

            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeToUse)
            typefaceToUse?.let { setTypeface(it) }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            privateImeOptions = StringBuilder().apply {
                if(!autocorrect) append("org.futo.inputmethod.latin.NoSuggestions=1,")
                if(forceQwerty) append("org.futo.inputmethod.latin.ForceLayout=qwerty,org.futo.inputmethod.latin.ForceLocale=zz,")
            }.toString()

            setHeight(height.toInt())

            val editorInfo = EditorInfo().apply {
                this.inputType = inputType
                this.packageName = context.packageName
            }
            onCreateInputConnection(editorInfo)

            onOverride?.invoke(inputConnection!!, editorInfo)

            // Remove underline and padding
            background = null
            setPadding(0, 0, 0, 0)

            if(autofocus) requestFocus()
        }
    }

    LaunchedEffect(text.value) {
        if(text.value != editText.getText().toString()) {
            editText.setText(text.value)
        }
    }

    AndroidView(
        factory = { editText },
        modifier = modifier,
        onRelease = {
            onUnoverride?.invoke()
        }
    )

    val fgColor = LocalContentColor.current
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(fgColor, primaryColor) {
        editText.setTextColor(fgColor.toArgb())
        editText.setHintTextColor(fgColor.copy(alpha = 0.7f).toArgb())
        editText.highlightColor = primaryColor.copy(alpha = 0.7f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable?.setTint(primaryColor.toArgb())
            editText.textSelectHandle?.setTint(primaryColor.toArgb())
            editText.textSelectHandleLeft?.setTint(primaryColor.toArgb())
            editText.textSelectHandleRight?.setTint(primaryColor.toArgb())
        }
    }

    val token = LocalView.current.windowToken
    DisposableEffect(autofocus, token) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if(autofocus) {
            imm.showSoftInput(editText, 0)
        }

        onDispose {
            imm.hideSoftInputFromWindow(token, 0)
        }
    }
}

@Composable
fun ActionTextEditor(
    text: MutableState<String>,
    multiline: Boolean = false,
    textSize: TextUnit = 16.sp,
    typeface: Typeface? = null,
    autocorrect: Boolean = false
) {
    val manager = if(!LocalInspectionMode.current) LocalManager.current else null
    GenericEditTextCompose(
        text = text,
        multiline = multiline,
        textSize = textSize,
        typeface = typeface,
        autocorrect = autocorrect,
        placeholder = null,
        modifier = Modifier.fillMaxSize(),
        onOverride = { ic, ed ->
            manager!!.overrideInputConnection(ic, ed)
        },
        onUnoverride = {
            manager!!.unsetInputConnection()
        }
    )
}

@Composable
fun SettingsTextEdit(
    text: MutableState<String>,
    multiline: Boolean = false,
    placeholder: String? = null,
    textSize: TextUnit = 16.sp,
    icon: (@Composable () -> Unit)? = null,
    autocorrect: Boolean = true,
    autofocus: Boolean = false,
    forceQwerty: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                it.invoke()
                Spacer(modifier = Modifier.width(8.dp))
            }

            GenericEditTextCompose(
                text = text,
                multiline = multiline,
                textSize = textSize,
                placeholder = placeholder,
                autocorrect = autocorrect,
                autofocus = autofocus,
                forceQwerty = forceQwerty
            )
        }
    }
}