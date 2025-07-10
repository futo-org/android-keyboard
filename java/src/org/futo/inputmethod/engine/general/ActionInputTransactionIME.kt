package org.futo.inputmethod.engine.general

import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.utils.TextContext

class ActionInputTransactionIME(val helper: IMEHelper) : IMEInterface, ActionInputTransaction {
    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDeviceUnlocked() {}
    override fun onStartInput(layout: String) {}
    override fun onOrientationChanged() {}
    override fun onFinishInput() {}
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
    }

    override fun isGestureHandlingAvailable(): Boolean = false
    override fun onEvent(event: Event) {}
    override fun onStartBatchInput() {}
    override fun onUpdateBatchInput(batchPointers: InputPointers?) {}
    override fun onEndBatchInput(batchPointers: InputPointers?) {}
    override fun onCancelBatchInput() {}
    override fun onCancelInput() {}
    override fun onFinishSlidingInput() {}
    override fun onCustomRequest(requestCode: Int): Boolean = false
    override fun onMovePointer(steps: Int, select: Boolean?) {}
    override fun onMoveDeletePointer(steps: Int) {}
    override fun onUpWithDeletePointerActive() {}
    override fun onUpWithPointerActive() {}
    override fun onSwipeLanguage(direction: Int) {}
    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {}
    override fun clearUserHistoryDictionaries() {}
    override fun requestSuggestionRefresh() {}

    override val textContext: TextContext = TextContext(
        beforeCursor = helper.getCurrentInputConnection()
            ?.getTextBeforeCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0),
        afterCursor = helper.getCurrentInputConnection()
            ?.getTextAfterCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0)
    )

    private var isFinished = false
    private var partialText = ""
    override fun updatePartial(text: String) {
        if (isFinished) return
        partialText = text
        helper.getCurrentInputConnection()?.setComposingText(
            partialText,
            1
        )
    }

    override fun commit(text: String) {
        if (isFinished) return
        isFinished = true
        helper.getCurrentInputConnection()?.commitText(
            text,
            1
        )
        helper.endInputTransaction(this)
    }

    override fun cancel() {
        commit(partialText)
    }

    fun ensureFinished() {
        isFinished = true
    }
}