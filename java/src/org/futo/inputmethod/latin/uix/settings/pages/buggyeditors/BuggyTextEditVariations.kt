package org.futo.inputmethod.latin.uix.settings.pages.buggyeditors

import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.HandwritingGesture
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.PreviewableHandwritingGesture
import android.view.inputmethod.SurroundingText
import android.view.inputmethod.TextAttribute
import android.view.inputmethod.TextBoundsInfoResult
import android.view.inputmethod.TextSnapshot
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.IntConsumer


fun ExtractedTextRequest?.niceString() = buildString {
    if(this@niceString == null) {
        append("null")
        return@buildString
    }

    append("ExtractedTextRequest(flags=${this@niceString.flags}, token=${this@niceString.token}, maxChars=${this@niceString.hintMaxChars}, maxLines=${this@niceString.hintMaxLines})")
}

@Suppress("HardCodedStringLiteral")
class WrappedIC(val ic: InputConnection) : InputConnection {
    var i = 0
    private fun<T> log(name: String, fn: () -> T): T {
        var id = i++
        Log.i("WrappedIC", "< [$id] $name")
        val result = fn()
        if(result != "" && result != null && result != Unit) {
            Log.i("WrappedIC", "< [$id] -> $result")
        }
        return result
    }

    override fun sendKeyEvent(event: KeyEvent?) = log("sendKeyEvent($event)") { ic.sendKeyEvent(event) }
    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = log("getTextBeforeCursor($n, $flags)") { ic.getTextBeforeCursor(n, flags) }
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = log("getTextAfterCursor($n, $flags)") { ic.getTextAfterCursor(n, flags) }
    override fun getSelectedText(flags: Int): CharSequence? = log("getSelectedText($flags)") { ic.getSelectedText(flags) }
    override fun getCursorCapsMode(reqModes: Int): Int = log("getCursorCapsMode($reqModes)") { ic.getCursorCapsMode(reqModes) }
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = log("getExtractedText(${request.niceString()}, $flags)") { ic.getExtractedText(request, flags) }
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = log("deleteSurroundingText($beforeLength, $afterLength)") { ic.deleteSurroundingText(beforeLength, afterLength) }
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = log("deleteSurroundingTextInCodePoints($beforeLength, $afterLength)") { ic.deleteSurroundingTextInCodePoints(beforeLength, afterLength) }
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = log("setComposingText($text, $newCursorPosition)") { ic.setComposingText(text, newCursorPosition) }
    override fun setComposingRegion(start: Int, end: Int): Boolean = log("setComposingRegion($start, $end)") { ic.setComposingRegion(start, end) }
    override fun finishComposingText(): Boolean = log("finishComposingText()") { ic.finishComposingText() }
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = log("commitText($text, $newCursorPosition)") { ic.commitText(text, newCursorPosition) }
    override fun commitCompletion(text: CompletionInfo?): Boolean = log("commitCompletion($text)") { ic.commitCompletion(text) }
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = log("commitCorrection($correctionInfo)") { ic.commitCorrection(correctionInfo) }
    override fun setSelection(start: Int, end: Int): Boolean = log("setSelection($start, $end)") { ic.setSelection(start, end) }
    override fun performEditorAction(editorAction: Int): Boolean = log("performEditorAction($editorAction)") { ic.performEditorAction(editorAction) }
    override fun performContextMenuAction(id: Int): Boolean = log("performContextMenuAction($id)") { ic.performContextMenuAction(id) }
    override fun beginBatchEdit(): Boolean = log("beginBatchEdit()") { ic.beginBatchEdit() }
    override fun endBatchEdit(): Boolean = log("endBatchEdit()") { ic.endBatchEdit() }
    override fun clearMetaKeyStates(states: Int): Boolean = log("clearMetaKeyStates($states)") { ic.clearMetaKeyStates(states) }
    override fun reportFullscreenMode(enabled: Boolean): Boolean = log("reportFullscreenMode($enabled)") { ic.reportFullscreenMode(enabled) }
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = log("performPrivateCommand($action, $data)") { ic.performPrivateCommand(action, data) }
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = log("requestCursorUpdates($cursorUpdateMode)") { ic.requestCursorUpdates(cursorUpdateMode) }
    override fun getHandler(): Handler? = log("getHandler()") { ic.getHandler() }
    override fun closeConnection() = log("closeConnection()") { ic.closeConnection() }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? = log("getSurroundingText($beforeLength, $afterLength, $flags)") { ic.getSurroundingText(beforeLength, afterLength, flags) }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun performHandwritingGesture(gesture: HandwritingGesture, executor: Executor?, consumer: IntConsumer?) = log("performHandwritingGesture($gesture, $executor, $consumer)") { ic.performHandwritingGesture(gesture, executor, consumer) }
    @RequiresApi(Build.VERSION_CODES.S)
    override fun performSpellCheck(): Boolean = log("performSpellCheck()") { ic.performSpellCheck() }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun previewHandwritingGesture(gesture: PreviewableHandwritingGesture, cancellationSignal: CancellationSignal?): Boolean = log("previewHandwritingGesture($gesture, $cancellationSignal)") { ic.previewHandwritingGesture(gesture, cancellationSignal) }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = log("replaceText($start, $end, $text, $newCursorPosition, $textAttribute)") { ic.replaceText(start, end, text, newCursorPosition, textAttribute) }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun requestCursorUpdates(cursorUpdateMode: Int, cursorUpdateFilter: Int): Boolean = log("requestCursorUpdates($cursorUpdateMode, $cursorUpdateFilter)") { ic.requestCursorUpdates(cursorUpdateMode, cursorUpdateFilter) }
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun requestTextBoundsInfo(bounds: RectF, executor: Executor, consumer: Consumer<TextBoundsInfoResult?>) = log("requestTextBoundsInfo($bounds, $executor, $consumer)") { ic.requestTextBoundsInfo(bounds, executor, consumer) }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean = log("setComposingRegion($start, $end, $textAttribute)") { ic.setComposingRegion(start, end, textAttribute) }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = log("setComposingText($text, $newCursorPosition, $textAttribute)") { ic.setComposingText(text, newCursorPosition, textAttribute) }
    @RequiresApi(Build.VERSION_CODES.S)
    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = log("setImeConsumesInput($imeConsumesInput)") { ic.setImeConsumesInput(imeConsumesInput) }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun takeSnapshot(): TextSnapshot? = log("takeSnapshot()") { ic.takeSnapshot() }
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = log("commitContent($inputContentInfo, $flags, $opts)") { ic.commitContent(inputContentInfo, flags, opts) }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean = log("commitText($text, $newCursorPosition, $textAttribute)") { ic.commitText(text, newCursorPosition, textAttribute) }

    override fun equals(other: Any?): Boolean = ic.equals(other)
    override fun hashCode(): Int = ic.hashCode()
}

@Composable
private fun TextEdit(config: BuggyEditorConfiguration, name: String, type: Int, imeOptions: Int? = null, imeActionLabel: String? = null, imeActionId: Int? = null) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground
    val cursorColor = MaterialTheme.colorScheme.primary

    if(!LocalInspectionMode.current) {
        val editText = remember(config) {
            val ctx =  context
            BuggyEditText(ctx, config, null, android.R.attr.editTextStyle).apply {

            }.apply {
                this.inputType = type

                if(imeOptions != null) {
                    this.imeOptions = imeOptions
                }

                hint = name

                this.textColor = fgColor.toArgb()
                this.hintTextColor = fgColor.copy(alpha = 0.7f).toArgb()
                this.cursorColor = cursorColor.toArgb()
            }
        }

        LaunchedEffect(editText, bgColor, fgColor) {
            editText.setBackgroundColor(bgColor.toArgb())
        }

        AndroidView({ editText }, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun BuggyTextEditVariations(navController: NavHostController = rememberNavController()) {
    val config = remember { mutableStateOf(BuggyEditorConfiguration()) }

    ScrollableList {
        ScreenTitle("Text Edit Variations", showBack = true, navController)

        SettingToggleRaw("setComposingTextDoesNotCompose",
            config.value.setComposingTextDoesNotCompose,
            { config.value = config.value.copy(setComposingTextDoesNotCompose = it) },
            compact = true)

        SettingToggleRaw("composingTextResetsAfter",
            config.value.composingTextResetsAfter != null,
            { config.value = config.value.copy(composingTextResetsAfter = if(it == false) null else 100) },
            compact = true)

        SettingToggleRaw("noComposing",
            config.value.noComposing,
            { config.value = config.value.copy(noComposing = it) },
            compact = true)

        SettingToggleRaw("cursorMovesToEndAndBack",
            config.value.cursorMovesToEndAndBack,
            { config.value = config.value.copy(cursorMovesToEndAndBack = it) },
            compact = true)

        SettingToggleRaw("doesNotSendCursorUpdates",
            config.value.doesNotSendCursorUpdates,
            { config.value = config.value.copy(doesNotSendCursorUpdates = it) },
            compact = true)

        SettingToggleRaw("alwaysSendsNoComposing",
            config.value.alwaysSendsNoComposing,
            { config.value = config.value.copy(alwaysSendsNoComposing = it) },
            compact = true)

        SettingToggleRaw("cursor 20ms delay",
            config.value.delayCursorUpdates == 20L,
            { config.value = config.value.copy(delayCursorUpdates = if(it) 20 else null) },
            compact = true)

        SettingToggleRaw("cursor 200ms delay",
            config.value.delayCursorUpdates == 200L,
            { config.value = config.value.copy(delayCursorUpdates = if(it) 200 else null) },
            compact = true)

        SettingToggleRaw("cursor 2000ms delay",
            config.value.delayCursorUpdates == 2000L,
            { config.value = config.value.copy(delayCursorUpdates = if(it) 2000 else null) },
            compact = true)

        ScreenTitle("Editors")

        key(config.value) {
            TextEdit(
                config.value, "multi lines none",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
                EditorInfo.IME_ACTION_UNSPECIFIED
            )

            TextEdit(
                config.value, "multi lines send",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "short message send multi lines",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "multi lines search",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                EditorInfo.IME_ACTION_SEARCH
            )

            TextEdit(
                config.value, "short message send",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "autocap none",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                EditorInfo.IME_ACTION_NONE
            )

            TextEdit(
                config.value, "autocap send",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "uri go",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI,
                EditorInfo.IME_ACTION_GO
            )

            TextEdit(
                config.value, "email address done",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                EditorInfo.IME_ACTION_DONE
            )

            TextEdit(
                config.value, "auto correct search",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
                EditorInfo.IME_ACTION_SEARCH
            )

            TextEdit(
                config.value, "auto correct previous",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
            )

            TextEdit(
                config.value, "auto correct custom",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT,
                imeActionLabel = "custom action label",
                imeActionId = 100
            )

            TextEdit(
                config.value, "phone",
                EditorInfo.TYPE_CLASS_PHONE
            )

            TextEdit(
                config.value, "phone no action",
                EditorInfo.TYPE_CLASS_PHONE,
                EditorInfo.IME_ACTION_NONE
            )

            TextEdit(
                config.value, "number send",
                EditorInfo.TYPE_CLASS_NUMBER,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "number no action",
                EditorInfo.TYPE_CLASS_NUMBER,
                EditorInfo.IME_ACTION_NONE
            )

            TextEdit(
                config.value, "password next",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
                EditorInfo.IME_ACTION_NEXT
            )

            TextEdit(
                config.value, "visible password done",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                EditorInfo.IME_ACTION_DONE
            )

            TextEdit(
                config.value, "number password send",
                EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD,
                EditorInfo.IME_ACTION_SEND
            )

            TextEdit(
                config.value, "text no suggestion",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_ACTION_GO
            )

            TextEdit(
                config.value, "text no autocorrection",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                EditorInfo.IME_ACTION_DONE
            )


            TextEdit(
                config.value, "cap characters with autocorrect",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
            )

            TextEdit(
                config.value, "cap words with autocorrect",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
            )

            TextEdit(
                config.value, "cap sentences with autocorrect",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
            )

            TextEdit(
                config.value, "cap characters",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS
            )

            TextEdit(
                config.value, "cap words",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
            )

            TextEdit(
                config.value, "cap sentences",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
            )

            TextEdit(
                config.value, "email subject",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
            )

            TextEdit(
                config.value, "person name",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
            )

            TextEdit(
                config.value, "postal address",
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            )

            TextEdit(
                config.value, "signed number",
                EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
            )

            TextEdit(
                config.value, "decimal number",
                EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
            )

            TextEdit(
                config.value, "signed decimal number",
                EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
            )

            TextEdit(
                config.value, "datetime",
                EditorInfo.TYPE_CLASS_DATETIME or EditorInfo.TYPE_DATETIME_VARIATION_NORMAL
            )

            TextEdit(
                config.value, "date",
                EditorInfo.TYPE_CLASS_DATETIME or EditorInfo.TYPE_DATETIME_VARIATION_DATE
            )

            TextEdit(
                config.value, "time",
                EditorInfo.TYPE_CLASS_DATETIME or EditorInfo.TYPE_DATETIME_VARIATION_TIME
            )
        }

    }
}