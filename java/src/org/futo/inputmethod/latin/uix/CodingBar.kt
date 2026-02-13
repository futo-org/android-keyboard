package org.futo.inputmethod.latin.uix

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.actions.CodingAltAction
import org.futo.inputmethod.latin.uix.actions.CodingCtrlAction
import org.futo.inputmethod.latin.uix.actions.CodingDashAction
import org.futo.inputmethod.latin.uix.actions.CodingEscAction
import org.futo.inputmethod.latin.uix.actions.CodingModifierState
import org.futo.inputmethod.latin.uix.actions.CodingShiftAction
import org.futo.inputmethod.latin.uix.actions.CodingSlashAction
import org.futo.inputmethod.latin.uix.actions.CodingTabAction

/**
 * A coding-focused bar that replaces the action/suggestions bar when enabled.
 * Displays shortcut buttons for TAB, CTRL, ALT, SHIFT, /, -, ESC.
 *
 * Modifier keys (CTRL, ALT, SHIFT) support two modes:
 * - **Single press**: One-shot — consumed after the next key press.
 * - **Long press**: Locked — stays active across multiple key presses.
 *   A border around the button indicates the locked state.
 *   Press the button again (short press) to unlock.
 *
 * All states (active + locked) are fully reactive via Compose [mutableStateOf]
 * in [CodingModifierState], and are fully reset when the keyboard hides.
 */

private val codingKeyTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 0.3.sp,
    textAlign = TextAlign.Center
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodingKeyButton(
    label: String,
    action: Action,
    onActionActivated: (Action) -> Unit,
    isHighlighted: Boolean = false,
    isLocked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val fgCol = if (isHighlighted) {
        LocalKeyboardScheme.current.onPrimary
    } else {
        LocalKeyboardScheme.current.onBackground
    }

    val bgCol = if (isHighlighted) {
        LocalKeyboardScheme.current.primary
    } else {
        LocalKeyboardScheme.current.keyboardContainer
    }

    val shape = RoundedCornerShape(6.dp)

    // Locked modifiers get a visible border to distinguish from one-shot
    val borderMod = if (isLocked) {
        Modifier.border(
            width = 1.5.dp,
            color = LocalKeyboardScheme.current.onPrimary.copy(alpha = 0.8f),
            shape = shape
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 1.5.dp, vertical = 4.dp)
            .clip(shape)
            .then(borderMod)
            .background(bgCol)
            .combinedClickable(
                onClick = { onActionActivated(action) },
                onLongClick = if (onLongClick != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                } else null
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = codingKeyTextStyle,
            color = fgCol,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CodingBar(
    onActionActivated: (Action) -> Unit,
    onActionAltActivated: (Action) -> Unit,
    isActionsExpanded: Boolean,
    toggleActionsExpanded: () -> Unit,
    keyboardManagerForAction: KeyboardManagerForAction? = null,
) {
    val view = LocalView.current

    // Read modifier states directly from CodingModifierState (Compose reactive).
    val ctrlActive = CodingModifierState.ctrlActive
    val altActive = CodingModifierState.altActive
    val shiftActive = CodingModifierState.shiftActive
    val ctrlLocked = CodingModifierState.ctrlLocked
    val altLocked = CodingModifierState.altLocked
    val shiftLocked = CodingModifierState.shiftLocked

    val useDoubleHeight = isActionsExpanded

    Column(
        Modifier
            .height(ActionBarHeight * (if (useDoubleHeight) 2 else 1))
            .semantics {
                testTag = "CodingBar"
                testTagsAsResourceId = true
            }
    ) {
        // Expanded favorites row (same as ActionBar)
        if (isActionsExpanded) {
            ActionSep()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                color = LocalKeyboardScheme.current.keyboardSurfaceDim
            ) {
                ActionItems(onActionActivated, onActionAltActivated)
            }
        }

        ActionSep()

        // Coding bar row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            color = actionBarColor()
        ) {
            Row(Modifier.safeKeyboardPadding()) {
                ExpandActionsButton(isActionsExpanded) {
                    toggleActionsExpanded()
                    keyboardManagerForAction?.performHapticAndAudioFeedback(
                        Constants.CODE_TAB,
                        view
                    )
                }

                // TAB — not a modifier, no long press
                CodingKeyButton("TAB", CodingTabAction, onActionActivated,
                    modifier = Modifier.weight(1f))

                // CTRL — modifier with lock support
                CodingKeyButton("CTRL", CodingCtrlAction, onActionActivated,
                    isHighlighted = ctrlActive,
                    isLocked = ctrlLocked,
                    onLongClick = {
                        CodingModifierState.ctrlActive = true
                        CodingModifierState.ctrlLocked = true
                    },
                    modifier = Modifier.weight(1f))

                // ALT — modifier with lock support
                CodingKeyButton("ALT", CodingAltAction, onActionActivated,
                    isHighlighted = altActive,
                    isLocked = altLocked,
                    onLongClick = {
                        CodingModifierState.altActive = true
                        CodingModifierState.altLocked = true
                    },
                    modifier = Modifier.weight(1f))

                // SHIFT — modifier with lock support
                CodingKeyButton("SHIFT", CodingShiftAction, onActionActivated,
                    isHighlighted = shiftActive,
                    isLocked = shiftLocked,
                    onLongClick = {
                        CodingModifierState.shiftActive = true
                        CodingModifierState.shiftLocked = true
                    },
                    modifier = Modifier.weight(1.1f))

                // / — not a modifier
                CodingKeyButton("/", CodingSlashAction, onActionActivated,
                    modifier = Modifier.weight(0.6f))

                // - — not a modifier
                CodingKeyButton("-", CodingDashAction, onActionActivated,
                    modifier = Modifier.weight(0.6f))

                // ESC — not a modifier
                CodingKeyButton("ESC", CodingEscAction, onActionActivated,
                    modifier = Modifier.weight(1f))
            }
        }

        ActionSep(true)
    }
}
