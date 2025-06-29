package org.futo.inputmethod.engine.general

import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.uix.japaneseSuggestionsList

// Dummy IME to help test UI
class JapaneseIME(val helper: IMEHelper) : IMEInterface {
    override fun onCreate() {
    }

    override fun onDestroy() {

    }

    override fun onDeviceUnlocked() {

    }

    override fun onStartInput(layout: String) {
        helper.setNeutralSuggestionStrip(true)
    }

    override fun onOrientationChanged() {

    }

    override fun onFinishInput() {

    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {

    }

    override fun isGestureHandlingAvailable(): Boolean {
        return false
    }

    override fun onEvent(event: Event) {
        val suggestionList = ArrayList(japaneseSuggestionsList.shuffled())
        val suggestedWords = SuggestedWords(
            suggestionList,
            suggestionList,
            suggestionList[0],
            true,
            true,
            false,
            0,
            0
        )

        if(event.eventType == Event.EVENT_TYPE_INPUT_KEYPRESS && event.mKeyCode == Constants.CODE_DELETE) {
            helper.setNeutralSuggestionStrip(true)
            helper.getCurrentInputConnection()?.deleteSurroundingText(100, 0)
        } else if(event.eventType == Event.EVENT_TYPE_SUGGESTION_PICKED && event.mSuggestedWordInfo != null && event.mSuggestedWordInfo.mWord != null) {
            helper.getCurrentInputConnection()?.commitText(
                event.mSuggestedWordInfo.mWord, 1
            )
        } else {
            helper.showSuggestionStrip(
                suggestedWords,
                true
            )
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
}