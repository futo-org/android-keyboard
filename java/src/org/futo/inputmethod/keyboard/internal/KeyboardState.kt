// TODO: Save numpad preference?
package org.futo.inputmethod.keyboard.internal

import android.util.Log
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.v2keyboard.getKeyboardMode

enum class KeyboardLayoutKind {
    Alphabet,
    Symbols,
    Phone,
    Number,
    NumberBasic
}

enum class KeyboardLayoutPage(val locked: Boolean, val altIdx: Int? = null) {
    Base(true),
    Shifted(false),
    ManuallyShifted(false),
    ShiftLocked(true),
    Alt0(false, 0),
    Alt1(false, 1),
    Alt2(false, 2),
    Alt3(false, 3),
}

/** Normalizes to the base page (for shifted variationsu) */
fun KeyboardLayoutPage.normalize(): KeyboardLayoutPage =
    when(this) {
        KeyboardLayoutPage.ManuallyShifted -> KeyboardLayoutPage.Shifted
        KeyboardLayoutPage.ShiftLocked -> KeyboardLayoutPage.Shifted
        else -> this
    }

data class KeyboardLayoutElement(
    val kind: KeyboardLayoutKind,
    val page: KeyboardLayoutPage
) {
    fun normalize(): KeyboardLayoutElement =
        this.copy(kind = kind, page = page.normalize())

    val elementId: Int
        get() = when(kind) {
            KeyboardLayoutKind.Alphabet -> when(page) {
                KeyboardLayoutPage.Base -> KeyboardId.ELEMENT_ALPHABET
                KeyboardLayoutPage.Shifted -> KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                KeyboardLayoutPage.ManuallyShifted -> KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                KeyboardLayoutPage.ShiftLocked -> KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
                else -> KeyboardId.ELEMENT_ALPHABET
            }

            KeyboardLayoutKind.Symbols -> when(page) {
                KeyboardLayoutPage.Base -> KeyboardId.ELEMENT_SYMBOLS
                KeyboardLayoutPage.Shifted,
                KeyboardLayoutPage.ManuallyShifted,
                KeyboardLayoutPage.ShiftLocked -> KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                else -> KeyboardId.ELEMENT_SYMBOLS
            }

            KeyboardLayoutKind.Phone -> when(page) {
                KeyboardLayoutPage.Base -> KeyboardId.ELEMENT_PHONE
                KeyboardLayoutPage.Shifted,
                KeyboardLayoutPage.ManuallyShifted,
                KeyboardLayoutPage.ShiftLocked -> KeyboardId.ELEMENT_PHONE_SYMBOLS
                else -> KeyboardId.ELEMENT_PHONE
            }

            KeyboardLayoutKind.Number, KeyboardLayoutKind.NumberBasic -> KeyboardId.ELEMENT_NUMBER
        }

    companion object {
        @JvmStatic
        fun fromElementId(value: Int): KeyboardLayoutElement =
            when(value) {
                KeyboardId.ELEMENT_ALPHABET                    -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet, page = KeyboardLayoutPage.Base)
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED     -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet, page = KeyboardLayoutPage.ManuallyShifted)
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED  -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet, page = KeyboardLayoutPage.Shifted)
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED       -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet, page = KeyboardLayoutPage.ShiftLocked)
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet, page = KeyboardLayoutPage.ShiftLocked)
                KeyboardId.ELEMENT_SYMBOLS                     -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Symbols, page = KeyboardLayoutPage.Base)
                KeyboardId.ELEMENT_SYMBOLS_SHIFTED             -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Symbols, page = KeyboardLayoutPage.Shifted)
                KeyboardId.ELEMENT_PHONE                       -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Phone, page = KeyboardLayoutPage.Base)
                KeyboardId.ELEMENT_PHONE_SYMBOLS               -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Phone, page = KeyboardLayoutPage.Shifted)
                KeyboardId.ELEMENT_NUMBER                      -> KeyboardLayoutElement(kind = KeyboardLayoutKind.NumberBasic, page = KeyboardLayoutPage.Base)
                else -> throw IllegalArgumentException("Invalid elementId $value")
            }
    }
}

interface SwitchActions {
    fun setKeyboard(element: KeyboardLayoutElement)
    fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: Int)
}

internal data class SavedKeyboardState(
    val isValid: Boolean = false,
    val layout: KeyboardLayoutElement = KeyboardLayoutElement(
        KeyboardLayoutKind.Alphabet,
        KeyboardLayoutPage.Base
    ),
    val prefersNumberLayout: Boolean = false
) {
    override fun toString(): String {
        if (!isValid) {
            return "INVALID"
        }
        return "${layout.kind.name}${layout.page.name}"
    }
}

/**
 * Keyboard state machine.
 *
 * This class contains all keyboard state transition logic.
 *
 * The input events are [.onLoadKeyboard], [.onSaveKeyboardState],
 * [.onPressKey], [.onReleaseKey],
 * [.onEvent], [.onFinishSlidingInput],
 * [.onUpdateShiftState], [.onResetKeyboardStateToAlphabet].
 *
 * The actions are [SwitchActions]'s methods.
 */
class KeyboardState(private val switchActions: SwitchActions) {
    companion object {
        private const val TAG = "KeyboardState"
        private const val DEBUG_EVENT = false
        private const val DEBUG_INTERNAL_ACTION = false

        @JvmStatic
        fun shouldReleaseAllPointers(keyboard: Keyboard?, key: Key?): Boolean = when {
            keyboard == null || key == null -> false
            key.isModifier -> true
            Constants.isLetterCode(key.code)
                    && !keyboard.mId.mElement.page.locked
                    && !keyboard.mShiftKeys.any { it.pressed } -> true
            else -> false
        }
    }

    private val shiftKeyState = ShiftKeyState("Shift")
    private val symbolKeyState = ModifierKeyState("Symbol")

    private val alphabetShiftState = AlphabetShiftState()
    private var savedKeyboardState = SavedKeyboardState()

    private var currentLayout = KeyboardLayoutElement(
        kind = KeyboardLayoutKind.Alphabet,
        page = KeyboardLayoutPage.Base
    )

    private val isAlphabet: Boolean
        get() = currentLayout.kind == KeyboardLayoutKind.Alphabet

    private var prefersNumberLayout = false

    private val debugState: String
        get() = "Layout $currentLayout, alphabetShiftState: $alphabetShiftState, shiftKeyState $shiftKeyState, symbolKeyState $symbolKeyState"

    private fun setLayout(layout: KeyboardLayoutElement) {
        currentLayout = layout
        switchActions.setKeyboard(layout)
    }

    private fun setNumberLayout(prefer: Boolean = true) {
        if(prefer) prefersNumberLayout = true

        setLayout(KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Number,
            page = KeyboardLayoutPage.Base
        ))
    }

    private fun setSymbolLayout() {
        if(currentLayout.kind == KeyboardLayoutKind.Number || !prefersNumberLayout) {
            prefersNumberLayout = false

            setLayout(
                KeyboardLayoutElement(
                    kind = KeyboardLayoutKind.Symbols,
                    page = KeyboardLayoutPage.Base
                )
            )
        } else {
            setLayout(KeyboardLayoutElement(
                kind = KeyboardLayoutKind.Number,
                page = KeyboardLayoutPage.Base
            ))
        }
    }

    private fun setPhoneLayout() {
        setLayout(KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Phone,
            page = KeyboardLayoutPage.Base
        ))
    }

    private fun setNumberBasicLayout() {
        setLayout(KeyboardLayoutElement(
            kind = KeyboardLayoutKind.NumberBasic,
            page = KeyboardLayoutPage.Base
        ))
    }

    private fun setAlphabetLayout(autoCapsFlags: Int, recapitalizeMode: Int) {
        val page = when {
            alphabetShiftState.isShiftLocked -> KeyboardLayoutPage.ShiftLocked
            alphabetShiftState.isManualShifted -> KeyboardLayoutPage.ManuallyShifted
            else -> KeyboardLayoutPage.Base
        }

        setLayout(KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Alphabet,
            page = page
        ))

        switchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode)
    }

    fun onLoadKeyboard(editorInfo: EditorInfo?, autoCapsFlags: Int, recapitalizeMode: Int) {
        // Reset alphabet shift state.
        alphabetShiftState.isShiftLocked = false

        shiftKeyState.onRelease()
        symbolKeyState.onRelease()

        when(getKeyboardMode(editorInfo ?: EditorInfo())) {
            KeyboardId.MODE_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME ->
                setNumberBasicLayout()

            KeyboardId.MODE_PHONE ->
                setPhoneLayout()

            else ->
                if (savedKeyboardState.isValid) {
                    onRestoreKeyboardState(autoCapsFlags, recapitalizeMode)
                } else {
                    setAlphabetLayout(autoCapsFlags, recapitalizeMode)
                }
        }

        savedKeyboardState = SavedKeyboardState(isValid = false)
    }

    fun onSaveKeyboardState() {
        savedKeyboardState = SavedKeyboardState(
            isValid = true,
            layout = currentLayout,
            prefersNumberLayout = prefersNumberLayout
        )

        if (DEBUG_EVENT) {
            Log.d(TAG, "onSaveKeyboardState: saved=$savedKeyboardState $debugState")
        }
    }

    private fun onRestoreKeyboardState(autoCapsFlags: Int, recapitalizeMode: Int) {
        val state = savedKeyboardState

        setLayout(state.layout)
        prefersNumberLayout = state.prefersNumberLayout
        switchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode)
    }

    val shifted: Boolean
        get() = currentLayout.page == KeyboardLayoutPage.Shifted ||
                currentLayout.page == KeyboardLayoutPage.ManuallyShifted ||
                currentLayout.page == KeyboardLayoutPage.ShiftLocked
    private fun toggleShift(to: Boolean = !shifted, manually: Boolean = false) {
        setLayout(currentLayout.copy(page = when {
            to -> {
                if(isAlphabet) alphabetShiftState.setShifted(true)

                if(manually) {
                    KeyboardLayoutPage.ManuallyShifted
                } else {
                    KeyboardLayoutPage.Shifted
                }
            }

            else -> {
                if(isAlphabet) alphabetShiftState.setShifted(false)
                KeyboardLayoutPage.Base
            }
        }))

        if(isAlphabet) {
            alphabetShiftState.isShiftLocked = currentLayout.page == KeyboardLayoutPage.ShiftLocked
        }
    }

    private fun lockShift() {
        if(!isAlphabet) return

        alphabetShiftState.isShiftLocked = !alphabetShiftState.isShiftLocked
        if(alphabetShiftState.isShiftLocked) {
            setLayout(currentLayout.copy(page = KeyboardLayoutPage.ShiftLocked))
        }
    }

    private var shiftTime: Long? = null
    private fun onShiftTapForShiftLockTimer() {
        if(!isAlphabet) return onCancelShiftTimer()

        val shiftTimeout = ViewConfiguration.getDoubleTapTimeout()

        val currentTime = System.currentTimeMillis()
        if(shiftTime == null || (currentTime > shiftTime!! + shiftTimeout)) {
            shiftTime = System.currentTimeMillis()
        } else {
            lockShift()
            onCancelShiftTimer()
        }
    }
    private fun onCancelShiftTimer() {
        shiftTime = null
    }

    fun onPressKey(
        code: Int, isSinglePointer: Boolean, autoCapsFlags: Int,
        recapitalizeMode: Int
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onPressKey: code=${Constants.printableCode(code)} flags($autoCapsFlags, $recapitalizeMode) single=$isSinglePointer state=$debugState"
            )
        }

        if (code != Constants.CODE_SHIFT) {
            // cancel shift double tap timer
            onCancelShiftTimer()
        }

        when (code) {
            Constants.CODE_SHIFT -> {
                shiftKeyState.onPress()
                toggleShift(manually = true)
                onShiftTapForShiftLockTimer()
            }
            Constants.CODE_CAPSLOCK -> {
                // only in onReleaseKey?
            }
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
                symbolKeyState.onPress()
                if(currentLayout.kind == KeyboardLayoutKind.Symbols
                    || currentLayout.kind == KeyboardLayoutKind.Number) {
                    setAlphabetLayout(autoCapsFlags, recapitalizeMode)
                } else {
                    setSymbolLayout()
                }
            }
            else -> {
                shiftKeyState.onOtherKeyPressed()
                symbolKeyState.onOtherKeyPressed()
            }
        }
    }

    private fun finalizeChord(autoCapsFlags: Int,
                              recapitalizeMode: Int) {
        when {
            shiftKeyState.isChording && shifted -> toggleShift(false)
            symbolKeyState.isChording && !isAlphabet -> setAlphabetLayout(autoCapsFlags, recapitalizeMode)
            symbolKeyState.isChording && isAlphabet -> setSymbolLayout()
        }
    }

    fun onReleaseKey(
        code: Int, withSliding: Boolean, autoCapsFlags: Int,
        recapitalizeMode: Int
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onReleaseKey: code=${Constants.printableCode(code)} flags($autoCapsFlags, $recapitalizeMode) sliding=$withSliding state=$debugState"
            )
        }
        when (code) {
            Constants.CODE_SHIFT -> {
                finalizeChord(autoCapsFlags, recapitalizeMode)
                if(!withSliding)
                    shiftKeyState.onRelease()
            }
            Constants.CODE_CAPSLOCK -> {
                lockShift()
            }
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
                finalizeChord(autoCapsFlags, recapitalizeMode)
                if(!withSliding)
                    symbolKeyState.onRelease()
            }

            // Return back to main layout if on symbols, and space or enter was pressed
            Constants.CODE_SPACE, Constants.CODE_ENTER -> {
                if(symbolKeyState.isReleasing
                    && currentLayout.page == KeyboardLayoutPage.Base
                    && currentLayout.kind == KeyboardLayoutKind.Symbols
                ) {
                    setAlphabetLayout(autoCapsFlags, recapitalizeMode)
                }
            }
            else -> {

            }
        }
    }

    fun onUpdateShiftState(autoCapsFlags: Int, recapitalizeMode: Int) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState($autoCapsFlags, $recapitalizeMode): $debugState")
        }
        updateAlphabetShiftState(autoCapsFlags, recapitalizeMode)
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    fun onResetKeyboardStateToAlphabet(
        autoCapsFlags: Int,
        recapitalizeMode: Int
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onResetKeyboardStateToAlphabet: $debugState"
            )
        }
        //resetKeyboardStateToAlphabet(autoCapsFlags, recapitalizeMode)
    }

    fun onFinishSlidingInput(autoCapsFlags: Int, recapitalizeMode: Int) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onFinishSlidingInput: $debugState")
        }

        finalizeChord(autoCapsFlags, recapitalizeMode)
        shiftKeyState.onRelease()
        symbolKeyState.onRelease()
    }

    private fun updateAlphabetShiftState(autoCapsFlags: Int, recapitalizeMode: Int) {
        // Only return to main layout in alphabet layout
        if(!isAlphabet) return

        // Do not update shift state while shift key is being pressed or chorded
        if(!shiftKeyState.isReleasing || shiftKeyState.isChording) return

        // Shift the layout from base if autocaps is enabled
        if(autoCapsFlags != Constants.TextUtils.CAP_MODE_OFF) {
            // Only shift from base layout. If we are in an alt layout, do nothing.
            if(currentLayout.page == KeyboardLayoutPage.Base) {
                toggleShift(true, manually = false)
            }

            return
        }

        // Otherwise, unshift the layout if it's not locked
        if(!currentLayout.page.locked) {
            toggleShift(false)
            return
        }
    }

    private fun switchToAltLayout(altLayoutIdx: Int, autoCapsFlags: Int, recapitalizeMode: Int) {
        val altPage = when(altLayoutIdx) {
            0 -> KeyboardLayoutPage.Alt0
            1 -> KeyboardLayoutPage.Alt1
            2 -> KeyboardLayoutPage.Alt2
            3 -> KeyboardLayoutPage.Alt3
            else -> KeyboardLayoutPage.Alt0
        }

        if(currentLayout.page == altPage) {
            // Switch back to base
            toggleShift(false)
            updateAlphabetShiftState(autoCapsFlags, recapitalizeMode)
        } else {
            alphabetShiftState.setShifted(false)
            setLayout(currentLayout.copy(page = altPage))
        }
    }

    fun onEvent(event: Event, autoCapsFlags: Int, recapitalizeMode: Int) {
        val code = if (event.isFunctionalKeyEvent) event.mKeyCode else event.mCodePoint
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onEvent: code=" + Constants.printableCode(code)
                        + " flags($autoCapsFlags, $recapitalizeMode)"
                        + " " + debugState
            )
        }

        when(code) {
            Constants.CODE_TO_NUMBER_LAYOUT -> {
                if(currentLayout.kind == KeyboardLayoutKind.Number) {
                    // Return back to symbol layout
                    prefersNumberLayout = false
                    setSymbolLayout()
                } else {
                    // Set number layout
                    setNumberLayout()
                }
            }

            Constants.CODE_TO_ALT_0_LAYOUT,
            Constants.CODE_TO_ALT_1_LAYOUT,
            Constants.CODE_TO_ALT_2_LAYOUT -> {
                switchToAltLayout(Constants.CODE_TO_ALT_0_LAYOUT - code,
                    autoCapsFlags, recapitalizeMode)
            }
        }

        if (Constants.isLetterCode(code)) {
            updateAlphabetShiftState(autoCapsFlags, recapitalizeMode)
        }
    }
}
