package org.futo.inputmethod.engine

import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.utils.RecapitalizeStatus
import org.futo.inputmethod.v2keyboard.CombinerKind

interface IMEInterface {
    // Basic lifecycle
    fun onCreate()
    fun onDestroy()
    fun onDeviceUnlocked()

    // State
    fun onStartInput(layout: String)
    fun onOrientationChanged()
    fun onFinishInput()
    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    )

    fun isGestureHandlingAvailable(): Boolean

    // Input
    fun onEvent(event: Event)


    /**
     * Called when user started batch input.
     */
    fun onStartBatchInput()

    /**
     * Sends the ongoing batch input points data.
     * @param batchPointers the batch input points representing the user input
     */
    fun onUpdateBatchInput(batchPointers: InputPointers?)

    /**
     * Sends the final batch input points data.
     *
     * @param batchPointers the batch input points representing the user input
     */
    fun onEndBatchInput(batchPointers: InputPointers?)

    fun onCancelBatchInput()

    /**
     * Called when user released a finger outside any key.
     */
    fun onCancelInput()

    /**
     * Called when user finished sliding key input.
     */
    fun onFinishSlidingInput()

    /**
     * Send a non-"code input" custom request to the listener.
     * @return true if the request has been consumed, false otherwise.
     */
    fun onCustomRequest(requestCode: Int): Boolean

    fun onMovePointer(steps: Int, select: Boolean?)
    fun onMoveDeletePointer(steps: Int)
    fun onUpWithDeletePointerActive()
    fun onUpWithPointerActive()
    fun onSwipeLanguage(direction: Int)
    fun onMovingCursorLockEvent(canMoveCursor: Boolean)
    fun clearUserHistoryDictionaries()

    /** Refresh as a result of blacklist update */
    fun requestSuggestionRefresh()

    // TODO: Not sure how to do this properly
    fun getCurrentAutoCapsState(): Int = Constants.TextUtils.CAP_MODE_OFF
    fun getCurrentRecapitalizeState(): Int = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE

    // TODO: onStartInput contains layout String, maybe it would be better to pass this information
    //  there (e.g. pass the full KeyboardLayoutSetV2)
    fun setCombiners(kinds: MutableList<CombinerKind>)
}
