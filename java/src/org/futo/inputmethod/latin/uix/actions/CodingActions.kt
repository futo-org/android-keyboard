package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.pages.CodingBarDisplayedSetting

/**
 * Coding bar actions for sending special key events commonly used during coding:
 * TAB, CTRL (sticky modifier), ALT (sticky modifier), SHIFT (sticky modifier), /, -, ESC.
 *
 * Modifier keys have two modes:
 * - **Single press (one-shot)**: Activates the modifier for the next key press only,
 *   then automatically deactivates.
 * - **Long press (locked)**: Locks the modifier so it stays active across multiple
 *   key presses. Press the button again (short press) to unlock.
 *
 * The coding bar UI highlights active modifiers and shows a border when locked.
 */

/**
 * Global sticky modifier state shared between modifier actions and the coding bar UI.
 *
 * Uses Compose [mutableStateOf] so that any write (from action handlers, from the
 * LatinIMELegacy onCodeInput intercept, or from onWindowHidden reset) automatically
 * triggers recomposition of the CodingBar composable.
 *
 * From Java code, access via `CodingModifierState.INSTANCE.getCtrlActive()` /
 * `CodingModifierState.INSTANCE.setCtrlActive(true)` etc.
 */
object CodingModifierState {
    // Active state (one-shot or locked)
    var ctrlActive by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var shiftActive by mutableStateOf(false)

    // Locked state (long-press sticky — persists across key presses)
    var ctrlLocked by mutableStateOf(false)
    var altLocked by mutableStateOf(false)
    var shiftLocked by mutableStateOf(false)

    fun getMetaState(): Int {
        var meta = 0
        if (ctrlActive) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altActive) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (shiftActive) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return meta
    }

    /**
     * Consume one-shot modifiers after a key press.
     * Locked modifiers are NOT consumed — they stay active until explicitly unlocked.
     */
    fun consumeModifiers() {
        if (!ctrlLocked) ctrlActive = false
        if (!altLocked) altActive = false
        if (!shiftLocked) shiftActive = false
    }

    /**
     * Full reset: clears all active AND locked states.
     * Called when the keyboard is hidden to ensure a clean slate on reopen.
     */
    fun resetAll() {
        ctrlActive = false
        altActive = false
        shiftActive = false
        ctrlLocked = false
        altLocked = false
        shiftLocked = false
    }

    /**
     * Map common ASCII code points to Android KEYCODE_* constants.
     */
    fun codePointToKeyCode(codePoint: Int): Int {
        return when {
            codePoint in 'a'.code..'z'.code -> KeyEvent.KEYCODE_A + (codePoint - 'a'.code)
            codePoint in 'A'.code..'Z'.code -> KeyEvent.KEYCODE_A + (codePoint - 'A'.code)
            codePoint in '0'.code..'9'.code -> KeyEvent.KEYCODE_0 + (codePoint - '0'.code)
            codePoint == ' '.code -> KeyEvent.KEYCODE_SPACE
            codePoint == '.'.code -> KeyEvent.KEYCODE_PERIOD
            codePoint == ','.code -> KeyEvent.KEYCODE_COMMA
            codePoint == '/'.code -> KeyEvent.KEYCODE_SLASH
            codePoint == '\\'.code -> KeyEvent.KEYCODE_BACKSLASH
            codePoint == '-'.code -> KeyEvent.KEYCODE_MINUS
            codePoint == '='.code -> KeyEvent.KEYCODE_EQUALS
            codePoint == '['.code -> KeyEvent.KEYCODE_LEFT_BRACKET
            codePoint == ']'.code -> KeyEvent.KEYCODE_RIGHT_BRACKET
            codePoint == ';'.code -> KeyEvent.KEYCODE_SEMICOLON
            codePoint == '\''.code -> KeyEvent.KEYCODE_APOSTROPHE
            codePoint == '`'.code -> KeyEvent.KEYCODE_GRAVE
            codePoint == '\t'.code -> KeyEvent.KEYCODE_TAB
            codePoint == '\n'.code -> KeyEvent.KEYCODE_ENTER
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}

val CodingTabAction = Action(
    icon = R.drawable.ic_coding_tab,
    name = R.string.action_coding_tab_title,
    simplePressImpl = { manager, _ ->
        val meta = CodingModifierState.getMetaState()
        manager.sendKeyEvent(KeyEvent.KEYCODE_TAB, meta)
        CodingModifierState.consumeModifiers()
    },
    windowImpl = null,
)

val CodingCtrlAction = Action(
    icon = R.drawable.ic_coding_ctrl,
    name = R.string.action_coding_ctrl_title,
    simplePressImpl = { _, _ ->
        if (CodingModifierState.ctrlLocked) {
            // Unlock and deactivate
            CodingModifierState.ctrlLocked = false
            CodingModifierState.ctrlActive = false
        } else {
            // Toggle one-shot
            CodingModifierState.ctrlActive = !CodingModifierState.ctrlActive
        }
    },
    windowImpl = null,
)

val CodingAltAction = Action(
    icon = R.drawable.ic_coding_alt,
    name = R.string.action_coding_alt_title,
    simplePressImpl = { _, _ ->
        if (CodingModifierState.altLocked) {
            // Unlock and deactivate
            CodingModifierState.altLocked = false
            CodingModifierState.altActive = false
        } else {
            // Toggle one-shot
            CodingModifierState.altActive = !CodingModifierState.altActive
        }
    },
    windowImpl = null,
)

val CodingSlashAction = Action(
    icon = R.drawable.ic_coding_slash,
    name = R.string.action_coding_slash_title,
    simplePressImpl = { manager, _ ->
        val meta = CodingModifierState.getMetaState()
        if (meta != 0) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_SLASH, meta)
            CodingModifierState.consumeModifiers()
        } else {
            manager.typeText("/")
        }
    },
    windowImpl = null,
)

val CodingDashAction = Action(
    icon = R.drawable.ic_coding_dash,
    name = R.string.action_coding_dash_title,
    simplePressImpl = { manager, _ ->
        val meta = CodingModifierState.getMetaState()
        if (meta != 0) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_MINUS, meta)
            CodingModifierState.consumeModifiers()
        } else {
            manager.typeText("-")
        }
    },
    windowImpl = null,
)

val CodingEscAction = Action(
    icon = R.drawable.ic_coding_esc,
    name = R.string.action_coding_esc_title,
    simplePressImpl = { manager, _ ->
        val meta = CodingModifierState.getMetaState()
        manager.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, meta)
        CodingModifierState.consumeModifiers()
    },
    windowImpl = null,
)

val CodingShiftAction = Action(
    icon = R.drawable.ic_coding_shift,
    name = R.string.action_coding_shift_title,
    simplePressImpl = { _, _ ->
        if (CodingModifierState.shiftLocked) {
            // Unlock and deactivate
            CodingModifierState.shiftLocked = false
            CodingModifierState.shiftActive = false
        } else {
            // Toggle one-shot
            CodingModifierState.shiftActive = !CodingModifierState.shiftActive
        }
    },
    windowImpl = null,
)

/**
 * Toggle action for the coding bar. Can be assigned to favorites or the action key
 * left of the spacebar. Pressing it toggles the coding bar on/off.
 */
val CodingBarAction = Action(
    icon = R.drawable.ic_coding_bar,
    name = R.string.action_coding_bar_title,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        val current = context.getSettingBlocking(CodingBarDisplayedSetting)
        context.setSettingBlocking(CodingBarDisplayedSetting.key, !current)
    },
    windowImpl = null,
    shownInEditor = true,
)
