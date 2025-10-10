package org.futo.inputmethod.keyboard.internal

import android.util.Log
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.v2keyboard.getKeyboardMode

enum class KeyboardLayoutKind {
    Alphabet0,
    Alphabet1,
    Alphabet2,
    Alphabet3,
    Symbols,
    Phone,
    Number,
    NumberBasic,
}

val KeyboardLayoutKind.isAlphabet get() = when(this) {
    KeyboardLayoutKind.Alphabet0 -> true
    KeyboardLayoutKind.Alphabet1 -> true
    KeyboardLayoutKind.Alphabet2 -> true
    KeyboardLayoutKind.Alphabet3 -> true
    else -> false
}

fun KeyboardLayoutKind.toAlphaKind(): Int? = when(this) {
    KeyboardLayoutKind.Alphabet0 -> 0
    KeyboardLayoutKind.Alphabet1 -> 1
    KeyboardLayoutKind.Alphabet2 -> 2
    KeyboardLayoutKind.Alphabet3 -> 3
    else -> null
}

fun fromAlphaKind(int: Int): KeyboardLayoutKind? = when(int) {
    0 -> KeyboardLayoutKind.Alphabet0
    1 -> KeyboardLayoutKind.Alphabet1
    2 -> KeyboardLayoutKind.Alphabet2
    3 -> KeyboardLayoutKind.Alphabet3
    else -> null
}

fun KeyboardLayoutKind.normalize() = when(this) {
    KeyboardLayoutKind.Alphabet1, KeyboardLayoutKind.Alphabet2, KeyboardLayoutKind.Alphabet3 ->
        KeyboardLayoutKind.Alphabet0
    else -> this
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
        this.copy(kind = kind.normalize(), page = page.normalize())

    val elementId: Int
        get() = when(kind) {
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

            KeyboardLayoutKind.Alphabet0,
            KeyboardLayoutKind.Alphabet1,
            KeyboardLayoutKind.Alphabet2,
            KeyboardLayoutKind.Alphabet3 -> when(page) {
                KeyboardLayoutPage.Base -> KeyboardId.ELEMENT_ALPHABET
                KeyboardLayoutPage.Shifted -> KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                KeyboardLayoutPage.ManuallyShifted -> KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                KeyboardLayoutPage.ShiftLocked -> KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
                else -> KeyboardId.ELEMENT_ALPHABET
            }
        }

    companion object {
        @JvmStatic
        @VisibleForTesting
        fun fromElementId(value: Int): KeyboardLayoutElement =
            when(value) {
                KeyboardId.ELEMENT_ALPHABET                    -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet0, page = KeyboardLayoutPage.Base)
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED     -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet0, page = KeyboardLayoutPage.ManuallyShifted)
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED  -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet0, page = KeyboardLayoutPage.Shifted)
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED       -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet0, page = KeyboardLayoutPage.ShiftLocked)
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> KeyboardLayoutElement(kind = KeyboardLayoutKind.Alphabet0, page = KeyboardLayoutPage.ShiftLocked)
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
    fun requestUpdatingShiftState(autoCapsFlags: Int)
}

internal data class SavedKeyboardState(
    val isValid: Boolean = false,
    val layout: KeyboardLayoutElement = KeyboardLayoutElement(
        KeyboardLayoutKind.Alphabet0,
        KeyboardLayoutPage.Base
    ),
    val prefersNumberLayout: Boolean = false,
    val preferredAlphabetKind: Pair<String, Int> = "" to 0
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
        private val DEBUG_EVENT = BuildConfig.DEBUG
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
        kind = KeyboardLayoutKind.Alphabet0,
        page = KeyboardLayoutPage.Base
    )

    private val isAlphabet: Boolean
        get() = currentLayout.kind.isAlphabet

    private var prefersNumberLayout = false
    private var currentLayoutSet = ""
    private var preferredAlphabetKind: Pair<String, Int> = "" to 0

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

    private fun setAlphabetLayout(autoCapsFlags: Int) {
        val page = when {
            alphabetShiftState.isShiftLocked -> KeyboardLayoutPage.ShiftLocked
            alphabetShiftState.isManualShifted -> KeyboardLayoutPage.ManuallyShifted
            else -> KeyboardLayoutPage.Base
        }

        val alphabetKind = if(preferredAlphabetKind.first == currentLayoutSet) {
            preferredAlphabetKind.second
        } else {
            preferredAlphabetKind = currentLayoutSet to 0
            0
        }

        setLayout(KeyboardLayoutElement(
            kind = fromAlphaKind(alphabetKind) ?: KeyboardLayoutKind.Alphabet0,
            page = page
        ))

        switchActions.requestUpdatingShiftState(autoCapsFlags)
    }

    fun onLoadKeyboard(editorInfo: EditorInfo?, autoCapsFlags: Int, layoutSetName: String?) {
        // Reset alphabet shift state.
        alphabetShiftState.isShiftLocked = false

        if(layoutSetName != null) currentLayoutSet = layoutSetName

        shiftKeyState.onRelease()
        symbolKeyState.onRelease()

        when(getKeyboardMode(editorInfo ?: EditorInfo())) {
            KeyboardId.MODE_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME ->
                setNumberBasicLayout()

            KeyboardId.MODE_PHONE ->
                setPhoneLayout()

            else ->
                if (savedKeyboardState.isValid) {
                    onRestoreKeyboardState(autoCapsFlags)
                } else {
                    setAlphabetLayout(autoCapsFlags)
                }
        }

        savedKeyboardState = SavedKeyboardState(isValid = false)
    }

    fun onSaveKeyboardState() {
        savedKeyboardState = SavedKeyboardState(
            isValid = true,
            layout = currentLayout,
            prefersNumberLayout = prefersNumberLayout,
            preferredAlphabetKind = preferredAlphabetKind
        )

        if (DEBUG_EVENT) {
            Log.d(TAG, "onSaveKeyboardState: saved=$savedKeyboardState $debugState")
        }
    }

    private fun onRestoreKeyboardState(autoCapsFlags: Int) {
        val state = savedKeyboardState

        setLayout(state.layout)
        prefersNumberLayout = state.prefersNumberLayout
        switchActions.requestUpdatingShiftState(autoCapsFlags)
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

    fun onPressKey(code: Int, isSinglePointer: Boolean, autoCapsFlags: Int) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onPressKey: code=${Constants.printableCode(code)} flags($autoCapsFlags) single=$isSinglePointer state=$debugState"
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
                    setAlphabetLayout(autoCapsFlags)
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

    private fun finalizeChord(autoCapsFlags: Int) {
        when {
            shiftKeyState.isChording && shifted -> toggleShift(false)
            symbolKeyState.isChording && !isAlphabet -> setAlphabetLayout(autoCapsFlags)
            symbolKeyState.isChording && isAlphabet -> setSymbolLayout()
        }
    }

    fun onReleaseKey(code: Int, withSliding: Boolean, autoCapsFlags: Int) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onReleaseKey: code=${Constants.printableCode(code)} flags($autoCapsFlags) sliding=$withSliding state=$debugState"
            )
        }
        when (code) {
            Constants.CODE_SHIFT -> {
                finalizeChord(autoCapsFlags)
                if(!withSliding)
                    shiftKeyState.onRelease()
            }
            Constants.CODE_CAPSLOCK -> {
                lockShift()
            }
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
                finalizeChord(autoCapsFlags)
                if(!withSliding)
                    symbolKeyState.onRelease()
            }

            // Return back to main layout if on symbols, and space or enter was pressed
            Constants.CODE_SPACE, Constants.CODE_ENTER -> {
                if(symbolKeyState.isReleasing
                    && currentLayout.page == KeyboardLayoutPage.Base
                    && currentLayout.kind == KeyboardLayoutKind.Symbols
                ) {
                    setAlphabetLayout(autoCapsFlags)
                }
            }
            else -> {

            }
        }
    }

    fun onUpdateShiftState(autoCapsFlags: Int) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState($autoCapsFlags): $debugState")
        }
        updateAlphabetShiftState(autoCapsFlags)
    }

    fun onResetKeyboardStateToAlphabet(
        editorInfo: EditorInfo?,
        autoCapsFlags: Int
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onResetKeyboardStateToAlphabet: $debugState"
            )
        }
        when(getKeyboardMode(editorInfo ?: EditorInfo())) {
            KeyboardId.MODE_NUMBER, KeyboardId.MODE_PHONE,
            KeyboardId.MODE_DATE, KeyboardId.MODE_TIME,
            KeyboardId.MODE_DATETIME -> onLoadKeyboard(editorInfo, autoCapsFlags, currentLayoutSet)

            else -> setAlphabetLayout(autoCapsFlags)
        }
    }

    fun onFinishSlidingInput(autoCapsFlags: Int) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onFinishSlidingInput: $debugState")
        }

        finalizeChord(autoCapsFlags)
        shiftKeyState.onRelease()
        symbolKeyState.onRelease()
    }

    private fun updateAlphabetShiftState(autoCapsFlags: Int) {
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

    private fun switchToAlpha(alphaIndex: Int, autoCapsFlags: Int) {
        preferredAlphabetKind = currentLayoutSet to alphaIndex
        setAlphabetLayout(autoCapsFlags)
    }

    private fun switchToAltLayout(altLayoutIdx: Int, autoCapsFlags: Int) {
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
            updateAlphabetShiftState(autoCapsFlags)
        } else {
            alphabetShiftState.setShifted(false)
            setLayout(currentLayout.copy(page = altPage))
        }
    }

    fun onEvent(event: Event, autoCapsFlags: Int) {
        val code = if (event.isFunctionalKeyEvent) event.mKeyCode else event.mCodePoint
        if (DEBUG_EVENT) {
            Log.d(
                TAG, "onEvent: code=" + Constants.printableCode(code)
                        + " flags($autoCapsFlags)"
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
                switchToAltLayout(Constants.CODE_TO_ALT_0_LAYOUT - code, autoCapsFlags)
            }

            Constants.CODE_TO_ALPHA_0_LAYOUT,
            Constants.CODE_TO_ALPHA_1_LAYOUT,
            Constants.CODE_TO_ALPHA_2_LAYOUT,
            Constants.CODE_TO_ALPHA_3_LAYOUT -> {
                switchToAlpha(Constants.CODE_TO_ALPHA_0_LAYOUT - code, autoCapsFlags)
            }
        }

        if (Constants.isLetterCode(code)) {
            updateAlphabetShiftState(autoCapsFlags)
        }
    }
}
