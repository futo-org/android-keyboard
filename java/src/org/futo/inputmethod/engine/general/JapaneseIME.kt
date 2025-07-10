package org.futo.inputmethod.engine.general

import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.google.common.base.Optional
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.common.StringUtils
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.actions.ArrowLeftAction
import org.futo.inputmethod.latin.uix.actions.ArrowRightAction
import org.futo.inputmethod.latin.uix.actions.UndoAction
import org.futo.inputmethod.latin.uix.actions.keyCode
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.utils.InputTypeUtils
import org.futo.inputmethod.nativelib.mozc.KeycodeConverter
import org.futo.inputmethod.nativelib.mozc.KeycodeConverter.KeyEventInterface
import org.futo.inputmethod.nativelib.mozc.KeycodeConverter.getMozcKeyEvent
import org.futo.inputmethod.nativelib.mozc.MozcLog
import org.futo.inputmethod.nativelib.mozc.MozcUtil
import org.futo.inputmethod.nativelib.mozc.keyboard.Keyboard
import org.futo.inputmethod.nativelib.mozc.model.SelectionTracker
import org.futo.inputmethod.nativelib.mozc.session.SessionExecutor
import org.futo.inputmethod.nativelib.mozc.session.SessionHandlerFactory
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Context.InputFieldType
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig
import java.io.File

// Focused segment's attribute.
private val SPAN_CONVERT_HIGHLIGHT = BackgroundColorSpan(0x66EF3566)

// Background color span for non-focused conversion segment.
// We don't create a static CharacterStyle instance since there are multiple segments at the
// same
// time. Otherwise, segments except for the last one cannot have style.
private const val CONVERT_NORMAL_COLOR = 0x19EF3566

// Cursor position.
// Note that InputConnection seems not to be able to show cursor. This is a workaround.
private val SPAN_BEFORE_CURSOR = BackgroundColorSpan(0x664DB6AC)

// Background color span for partial conversion.
private val SPAN_PARTIAL_SUGGESTION_COLOR = BackgroundColorSpan(0x194DB6AC)

// Underline.
private val SPAN_UNDERLINE = UnderlineSpan()


internal fun placeMozcData(context: Context) {
    context.resources.openRawResource(R.raw.mozc).use { input ->
        File(context.cacheDir, "mozc.data").also {
            try { it.delete() }catch(e: Exception) { }
        }.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

internal fun getInputFieldType(attribute: EditorInfo): InputFieldType {
    val inputType = attribute.inputType
    if (MozcUtil.isPasswordField(inputType)) {
        return InputFieldType.PASSWORD
    }
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    if (inputClass == InputType.TYPE_CLASS_PHONE) {
        return InputFieldType.TEL
    }
    return if (inputClass == InputType.TYPE_CLASS_NUMBER) {
        InputFieldType.NUMBER
    } else InputFieldType.NORMAL
}

private const val SUGGESTION_ID_INVERSION = 10000
private const val TAG = "JapaneseIME"
class JapaneseIME(val helper: IMEHelper) : IMEInterface {
    companion object { init { MozcLog.forceLoggable = false }}

    init {
        if(!helper.context.isDirectBootUnlocked) TODO("Only supported in unlocked state right now")
    }

    val selectionTracker = SelectionTracker()
    lateinit var executor: SessionExecutor

    private fun updateConfig() {
        Settings.getInstance().current
        executor.config = ProtoConfig.Config.newBuilder().apply {
            sessionKeymap = ProtoConfig.Config.SessionKeymap.MOBILE
            selectionShortcut = ProtoConfig.Config.SelectionShortcut.NO_SHORTCUT // TODO?
            useEmojiConversion = true

            // TODO: Settings
            spaceCharacterForm = ProtoConfig.Config.FundamentalCharacterForm.FUNDAMENTAL_INPUT_MODE
            useKanaModifierInsensitiveConversion = true
            useTypingCorrection = true
            historyLearningLevel = ProtoConfig.Config.HistoryLearningLevel.DEFAULT_HISTORY
            incognitoMode = BuildConfig.DEBUG//settings.mInputAttributes.mNoLearning
            generalConfig = ProtoConfig.GeneralConfig.newBuilder().apply {
                uploadUsageStats = false
            }.build()
        }.build()

        // TODO: Other keyboards
        val keyboardSpecification = Keyboard.KeyboardSpecification.TWELVE_KEY_TOGGLE_FLICK_KANA
        val keyboardRequest = MozcUtil.getRequestBuilder(keyboardSpecification, helper.context.resources.configuration, 3).build()
        executor.updateRequest(
            keyboardRequest,
            emptyList()
        )
        executor.switchInputMode(
            Optional.of(KeycodeConverter.getKeyEventInterface(0)),
            keyboardSpecification.compositionMode,
            evaluationCallback
        )

        selectionTracker.onConfigurationChanged()
    }

    override fun onCreate() {
        placeMozcData(helper.context)

        executor = SessionExecutor.getInstanceInitializedIfNecessary(
            SessionHandlerFactory(helper.context),
            helper.context
        )

        updateConfig()
        executor.syncData()
    }

    override fun onDestroy() {
        executor.syncData()
    }

    override fun onDeviceUnlocked() {
        throw Exception("Should never be called!")
    }

    override fun onStartInput(layout: String) {
        setNeutralSuggestionStrip()
        updateConfig()
        executor.resetContext()
        selectionTracker.onStartInput(
            helper.getCurrentEditorInfo()?.initialSelStart ?: -1,
            helper.getCurrentEditorInfo()?.initialSelEnd ?: -1,
            false
        )

        helper.getCurrentEditorInfo()?.let { executor.switchInputFieldType(getInputFieldType(it)) }
    }

    override fun onOrientationChanged() {
        updateConfig()
    }

    override fun onFinishInput() {
        executor.syncData()
        selectionTracker.onFinishInput()
        executor.resetContext()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        val updateStatus = selectionTracker.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd,
            false
        )
        when(updateStatus) {
            SelectionTracker.DO_NOTHING -> {}
            SelectionTracker.RESET_CONTEXT -> {
                executor.resetContext()
                helper.getCurrentInputConnection()?.finishComposingText()
                setNeutralSuggestionStrip()
            }

            else -> {
                if(updateStatus < 0) throw IllegalStateException("Invalid updateStatus $updateStatus")
                executor.moveCursor(updateStatus, evaluationCallback)
            }
        }
    }

    override fun isGestureHandlingAvailable(): Boolean {
        return false
    }

    private fun createKeyEvent(
        original: KeyEvent,
        eventTime: Long,
        action: Int,
        repeatCount: Int,
    ): KeyEvent {
        return KeyEvent(
            original.downTime,
            eventTime,
            action,
            original.keyCode,
            repeatCount,
            original.metaState,
            original.deviceId,
            original.scanCode,
            original.flags,
        )
    }

    private fun maybeProcessDelete(keyCode: Int): Boolean {
        if(keyCode != Constants.CODE_DELETE) return false
        helper.getCurrentInputConnection()?.deleteSurroundingText(1, 0)
        return true
    }


    private fun sendCodePoint(codePoint: Int): Unit = when(codePoint) {
        Constants.CODE_ENTER -> {
            // TODO: Code duplication between here and InputLogic.handleNonFunctionalEvent
            val ei = helper.getCurrentEditorInfo() ?: return
            val ic = helper.getCurrentInputConnection() ?: return
            val imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(ei)

            val isCustomAction =
                InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId
            val isEditorAction =
                EditorInfo.IME_ACTION_NONE != imeOptionsActionId

            if(isCustomAction) {
                ic.performEditorAction(ei.actionId)
            } else if(isEditorAction) {
                ic.performEditorAction(imeOptionsActionId)
            } else {
                helper.getCurrentInputConnection()?.commitText(
                    StringUtils.newSingleCodePointString(Constants.CODE_ENTER),
                    1
                )
            }

            Unit
        }
        else -> {
            // TODO: Are we not overwriting composing text here?
            helper.getCurrentInputConnection()?.commitText(
                StringUtils.newSingleCodePointString(codePoint),
                1
            )

            Unit
        }
    }

    /** Sends the `KeyEvent`, which is not consumed by the mozc server. */
    private fun sendKeyEvent(keyEvent: KeyEventInterface?) {
        if (keyEvent == null) {
            return
        }
        val keyCode = keyEvent.keyCode
        // Some keys have a potential to be consumed from mozc client.
        if (maybeProcessDelete(keyCode)) {
            // The key event is consumed.
            return
        }

        // Following code is to fallback to target activity.
        val nativeKeyEvent = keyEvent.nativeEvent
        val inputConnection = helper.getCurrentInputConnection()
        if (nativeKeyEvent.isPresent && inputConnection != null) {
            // Meta keys are from this.onKeyDown/Up so fallback each time.
            if (KeycodeConverter.isMetaKey(nativeKeyEvent.get())) {
                inputConnection.sendKeyEvent(
                    createKeyEvent(
                        nativeKeyEvent.get(),
                        SystemClock.uptimeMillis(),
                        nativeKeyEvent.get().action,
                        nativeKeyEvent.get().repeatCount,
                    )
                )
                return
            }

            // Other keys are from this.onKeyDown so create dummy Down/Up events.
            inputConnection.sendKeyEvent(
                createKeyEvent(nativeKeyEvent.get(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, 0)
            )
            inputConnection.sendKeyEvent(
                createKeyEvent(nativeKeyEvent.get(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, 0)
            )
            return
        }

        // Otherwise, just delegates the key event to the connected application.
        // However space key needs special treatment because it is expected to produce space character
        // instead of sending ACTION_DOWN/UP pair.
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            inputConnection?.commitText(" ", 0)
        } else {
            sendDownUpKeyEvent(keyCode, 0)
        }
    }
    fun sendDownUpKeyEvent(keyCode: Int, metaState: Int) {
        val eventTime = SystemClock.uptimeMillis()
        helper.getCurrentInputConnection()?.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        helper.getCurrentInputConnection()?.sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }


    private fun maybeDeleteSurroundingText(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasDeletionRange()) {
            return
        }
        val range = output.deletionRange
        val leftRange = -range.offset
        val rightRange = range.length - leftRange
        if (leftRange < 0 || rightRange < 0) {
            // If the range does not include the current position, do nothing
            // because Android's API does not expect such situation.
            Log.w(TAG, "Deletion range has unsupported parameters: $range")
            return
        }
        if (!inputConnection.deleteSurroundingText(leftRange, rightRange)) {
            Log.e(TAG, "Failed to delete surrounding text.")
        }
    }

    private fun maybeCommitText(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasResult()) {
            return
        }
        val outputText = output.result.value
        if (outputText == "") {
            // Do nothing for an empty result string.
            return
        }
        var position = MozcUtil.CURSOR_POSITION_TAIL
        if (output.result.hasCursorOffset()) {
            if (output.result.cursorOffset == -outputText.codePointCount(0, outputText.length)) {
                position = MozcUtil.CURSOR_POSITION_HEAD
            } else {
                Log.e(TAG, "Unsupported position: " + output.result.toString())
            }
        }
        if (!inputConnection.commitText(outputText, position)) {
            Log.e(TAG, "Failed to commit text.")
        }
    }

    internal fun renderInputConnection(command: ProtoCommands.Command, keyEvent: KeyEventInterface?) {
        val inputConnection = helper.getCurrentInputConnection() ?: return
        val output = command.output
        if(!output.hasConsumed() || !output.consumed) {
            maybeCommitText(output, inputConnection)

            if(keyEvent?.nativeEvent?.isPresent == true) {
                sendKeyEvent(keyEvent)
            } else if(keyEvent?.keyCode != null) {
                sendCodePoint(keyEvent.keyCode)
            }
            return
        }

        // Meta key may invoke a command for Mozc server like SWITCH_INPUT_MODE session command. In this
        // case, the command is consumed by Mozc server and the application cannot get the key event.
        // To avoid such situation, we should send the key event back to application. b/13238551
        // The command itself is consumed by Mozc server, so we should NOT put a return statement here.
        if (
            keyEvent != null &&
            keyEvent.nativeEvent.isPresent &&
            KeycodeConverter.isMetaKey(keyEvent.nativeEvent.get())
        ) {
            sendKeyEvent(keyEvent)
        }

        // Here the key is consumed by the Mozc server.
        inputConnection.beginBatchEdit()
        try {
            maybeDeleteSurroundingText(output, inputConnection)
            maybeCommitText(output, inputConnection)
            setComposingText(command, inputConnection)
            maybeSetSelection(output, inputConnection)
            selectionTracker.onRender(
                if (output.hasDeletionRange()) output.deletionRange else null,
                if (output.hasResult()) output.result.value else null,
                if (output.hasPreedit()) output.preedit else null,
            )
        } finally {
            inputConnection.endBatchEdit()
        }
    }

    private fun setComposingText(command: ProtoCommands.Command, inputConnection: InputConnection) {
        val output = command.output

        helper.updateUiInputState(!output.hasPreedit())

        if (!output.hasPreedit()) {
            // If preedit field is empty, we should clear composing text in the InputConnection
            // because Mozc server asks us to do so.
            // But there is special situation in Android.
            // On onWindowShown, SWITCH_INPUT_MODE command is sent as a step of initialization.
            // In this case we reach here with empty preedit.
            // As described above we should clear the composing text but if we do so
            // texts in selection range (e.g., URL in OmniBox) is always cleared.
            // To avoid from this issue, we don't clear the composing text if the input
            // is SWITCH_INPUT_MODE.
            val input = command.input
            if (
                input.type != ProtoCommands.Input.CommandType.SEND_COMMAND ||
                input.command.type != SessionCommand.CommandType.SWITCH_INPUT_MODE
            ) {
                if (!inputConnection.setComposingText("", 0)) {
                    Log.e(TAG, "Failed to set composing text.")
                }
            }
            return
        }

        // Builds preedit expression.
        val preedit = output.preedit
        val builder = SpannableStringBuilder()
        for (segment in preedit.segmentList) {
            builder.append(segment.value)
        }

        // Set underline for all the preedit text.
        builder.setSpan(SPAN_UNDERLINE, 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Draw cursor if in composition mode.
        val cursor = preedit.cursor
        val spanFlags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
        if (
            output.hasAllCandidateWords() &&
            output.allCandidateWords.hasCategory() &&
            output.allCandidateWords.category == ProtoCandidateWindow.Category.CONVERSION
        ) {
            var offsetInString = 0
            for (segment in preedit.segmentList) {
                val length = segment.value.length
                builder.setSpan(
                    if (segment.hasAnnotation() && segment.annotation == Preedit.Segment.Annotation.HIGHLIGHT)
                        SPAN_CONVERT_HIGHLIGHT
                    else BackgroundColorSpan(CONVERT_NORMAL_COLOR),
                    offsetInString,
                    offsetInString + length,
                    spanFlags,
                )
                offsetInString += length
            }
        } else {
            // We cannot show system cursor inside preedit here.
            // Instead we change text style before the preedit's cursor.
            val cursorOffsetInString = builder.toString().offsetByCodePoints(0, cursor)
            if (cursor != builder.length) {
                builder.setSpan(
                    SPAN_PARTIAL_SUGGESTION_COLOR,
                    cursorOffsetInString,
                    builder.length,
                    spanFlags,
                )
            }
            if (cursor > 0) {
                builder.setSpan(SPAN_BEFORE_CURSOR, 0, cursorOffsetInString, spanFlags)
            }
        }

        // System cursor will be moved to the tail of preedit.
        // It triggers onUpdateSelection again.
        val cursorPosition = if (cursor > 0) MozcUtil.CURSOR_POSITION_TAIL else 0
        if (!inputConnection.setComposingText(builder, cursorPosition)) {
            Log.e(TAG, "Failed to set composing text.")
        }
    }

    private fun getPreeditLength(preedit: Preedit): Int {
        var result = 0
        for (i in 0 until preedit.segmentCount) {
            result += preedit.getSegment(i).valueLength
        }
        return result
    }

    private fun maybeSetSelection(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasPreedit()) {
            return
        }
        val preedit = output.preedit
        val cursor = preedit.cursor
        if (cursor == 0 || cursor == getPreeditLength(preedit)) {
            // The cursor is at the beginning/ending of the preedit. So we don't anything about the
            // caret setting.
            return
        }
        var caretPosition = selectionTracker.preeditStartPosition
        if (output.hasDeletionRange()) {
            caretPosition += output.deletionRange.offset
        }
        if (output.hasResult()) {
            caretPosition += output.result.value.length
        }
        if (output.hasPreedit()) {
            caretPosition += output.preedit.cursor
        }
        if (!inputConnection.setSelection(caretPosition, caretPosition)) {
            Log.e(TAG, "Failed to set selection.")
        }
    }

    private val evaluationCallback = object : SessionExecutor.EvaluationCallback {
        override fun onCompleted(
            command: Optional<ProtoCommands.Command>,
            triggeringKeyEvent: Optional<KeyEventInterface>
        ) {
            if(!command.isPresent) return

            renderInputConnection(command.get(), triggeringKeyEvent.orNull())

            val output = command.get().output

            if(output.allCandidateWords.candidatesCount == 0) {
                setNeutralSuggestionStrip()
            } else {
                val candidateList = output.allCandidateWords.candidatesList
                val suggestedWordList = candidateList.map {
                    SuggestedWords.SuggestedWordInfo(
                        it.value,
                        "",
                        SUGGESTION_ID_INVERSION - it.index,
                        1,
                        null,
                        it.id,
                        0
                    )
                }.let { ArrayList(it) }

                showSuggestionStrip(SuggestedWords(
                        suggestedWordList,
                        suggestedWordList,
                        null,
                        true,
                        true,
                        false,
                        0,
                        0
                    )
                )
            }
        }

    }

    private fun touchEvent(x: Int, y: Int, down: Boolean) = ProtoCommands.Input.TouchEvent.newBuilder().apply {
            sourceId = 2 // TODO: Supposed to correspond to keyboard keys, not be a constant
            addStroke(ProtoCommands.Input.TouchPosition.newBuilder().apply {
                action = if(down) ProtoCommands.Input.TouchAction.TOUCH_DOWN
                else ProtoCommands.Input.TouchAction.TOUCH_UP

                this.x = x.toFloat() / helper.keyboardRect.width().toFloat()
                this.y = y.toFloat() / helper.keyboardRect.height().toFloat()
            }.build())
        }.build()

    private fun maybeHandleAction(keyCode: Int) {
        if (keyCode <= Constants.CODE_ACTION_MAX && keyCode >= Constants.CODE_ACTION_0) {
            val actionId: Int = keyCode - Constants.CODE_ACTION_0
            helper.triggerAction(actionId, false)
            return
        }

        if (keyCode <= Constants.CODE_ALT_ACTION_MAX && keyCode >= Constants.CODE_ALT_ACTION_0) {
            val actionId: Int = keyCode - Constants.CODE_ALT_ACTION_0
            helper.triggerAction(actionId, true)
            return
        }
    }

    override fun onEvent(event: Event) = when (event.eventType) {
        Event.EVENT_TYPE_INPUT_KEYPRESS -> {
            val mozcEvent = when (event.mCodePoint) {
                Constants.CODE_SPACE -> KeycodeConverter.SPECIALKEY_SPACE
                Constants.CODE_ENTER -> KeycodeConverter.SPECIALKEY_VIRTUAL_ENTER
                Constants.NOT_A_CODE -> when (event.mKeyCode) {
                    Constants.CODE_DELETE -> KeycodeConverter.SPECIALKEY_BACKSPACE
                    ArrowLeftAction.keyCode -> KeycodeConverter.SPECIALKEY_VIRTUAL_LEFT
                    ArrowRightAction.keyCode -> KeycodeConverter.SPECIALKEY_VIRTUAL_RIGHT
                    UndoAction.keyCode -> {
                        executor.undoOrRewind(emptyList(), evaluationCallback)
                        return
                    }

                    else -> {
                        maybeHandleAction(event.mKeyCode)
                        null
                    }
                }

                else -> getMozcKeyEvent(event.mCodePoint)
            } ?: return run {
                Log.e(TAG, "Unknown keycode for that event (${ArrowLeftAction.keyCode})")
                Unit
            }

            val triggeringKeyEvent = if (event.mKeyCode != Event.NOT_A_KEY_CODE) {
                KeycodeConverter.getKeyEventInterface(
                    KeyEvent(
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        KeyEvent.ACTION_DOWN,
                        event.mKeyCode,
                        0,
                        0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD,
                        0,
                        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                    )
                )
            } else {
                KeycodeConverter.getKeyEventInterface(event.mCodePoint)
            }

            val touchEvents = emptyList<ProtoCommands.Input.TouchEvent>() /* listOf(
                touchEvent(event.mX, event.mY, true),
                touchEvent(event.mX, event.mY, false),
            ) */

            executor.sendKey(
                mozcEvent,
                triggeringKeyEvent,
                touchEvents,
                evaluationCallback
            )
        }

        Event.EVENT_TYPE_SUGGESTION_PICKED -> {
            val suggestion = event.mSuggestedWordInfo ?: return
            val mozcId = suggestion.mIndexOfTouchPointOfSecondWord // Re-using this field for now
            val rowIdx = SUGGESTION_ID_INVERSION - suggestion.mScore
            executor.submitCandidate(mozcId, Optional.of(rowIdx), evaluationCallback)
        }

        else -> {
            Log.e(TAG, "Unhandled event type ${event.eventType}: $event")

            Unit
        }
    }

    override fun onStartBatchInput() {

    }

    override fun onUpdateBatchInput(batchPointers: InputPointers?) {

    }

    override fun onEndBatchInput(batchPointers: InputPointers?) {

    }

    override fun onCancelBatchInput() {

    }

    override fun onCancelInput() {

    }

    override fun onFinishSlidingInput() {

    }

    override fun onCustomRequest(requestCode: Int): Boolean {
        return false
    }

    override fun onMovePointer(steps: Int, select: Boolean?) {

    }

    override fun onMoveDeletePointer(steps: Int) {

    }

    override fun onUpWithDeletePointerActive() {

    }

    override fun onUpWithPointerActive() {

    }

    override fun onSwipeLanguage(direction: Int) {

    }

    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {

    }

    override fun clearUserHistoryDictionaries() {

    }

    private var prevSuggestions: SuggestedWords? = null
    override fun requestSuggestionRefresh() {
        if(prevSuggestions != null) showSuggestionStrip(prevSuggestions)
    }

    private val useExpandableUi = true
    fun setNeutralSuggestionStrip() {
        prevSuggestions = null
        helper.setNeutralSuggestionStrip(useExpandableUi)
    }

    fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        prevSuggestions = suggestedWords
        helper.showSuggestionStrip(suggestedWords, useExpandableUi)
    }
}