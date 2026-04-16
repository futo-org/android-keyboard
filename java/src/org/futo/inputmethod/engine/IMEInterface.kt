package org.futo.inputmethod.engine

import androidx.compose.runtime.MutableState
import org.futo.inputmethod.annotations.UsedForTesting
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2

data class StateHint(
    /** Makes KeyboardState instantly unshift when any key with code > 0 is pressed on the alphabet layout */
    @JvmField val unshiftOnPressed: Boolean = false,

    /** Makes PointerTracker use looser matching rules for keyboard layout changes, to be used in combination with the above */
    @JvmField val useLooseMatching: Boolean = false,
)
val DefaultStateHint = StateHint()

interface IMEInterface {
    // Basic lifecycle
    fun onCreate()
    fun onDestroy()
    fun onDeviceUnlocked()

    // State
    fun onStartInput()
    fun onLayoutUpdated(layout: KeyboardLayoutSetV2)
    fun onOrientationChanged()
    fun onFinishInput()
    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    )

    fun isGestureHandlingAvailable(): Boolean

    /** Optionally return a state to indicate the IME is loading and no input can be processed */
    fun getLoadingState(): MutableState<Boolean>? = null

    /** Optional hint for how the keyboard state should behave */
    fun getStateHint(imeHint: String?) = DefaultStateHint

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

    fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?)
    fun onMoveDeletePointer(steps: Int)
    fun onUpWithDeletePointerActive()
    fun onUpWithPointerActive()
    fun onSwipeLanguage(direction: Int)
    fun onMovingCursorLockEvent(canMoveCursor: Boolean)
    fun clearUserHistoryDictionaries()

    /** Refresh as a result of blacklist update */
    fun requestSuggestionRefresh()

    /**
     * Hints the keyboard switcher whether to auto-shift (capitalize) the layout or not.
     * For immediate changes, call imeHelper.keyboardSwitcher.requestUpdatingShiftState(mode)
     * Value of CAP_MODE_OFF will unshift it, any other value will shift it.
     */
    fun getCurrentAutoCapsState(): Int = Constants.TextUtils.CAP_MODE_OFF

    @UsedForTesting
    fun recycle() { }
}
