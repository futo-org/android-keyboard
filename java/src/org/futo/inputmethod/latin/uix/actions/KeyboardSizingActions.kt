package org.futo.inputmethod.latin.uix.actions

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.OneHandedDirection

val LeftHandedKeyboardAction = Action(
    icon = R.drawable.arrow_left,
    name = R.string.left_handed_keyboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.getSizingCalculator().editSavedSettings {
            if(it.currentMode == KeyboardMode.OneHanded
                && it.oneHandedDirection == OneHandedDirection.Left) {
                it.copy(
                    currentMode = KeyboardMode.Regular
                )
            } else {
                it.copy(
                    oneHandedDirection = OneHandedDirection.Left,
                    currentMode = KeyboardMode.OneHanded
                )
            }
        }
    },
    windowImpl = null,
)

val RightHandedKeyboardAction = Action(
    icon = R.drawable.arrow_right,
    name = R.string.right_handed_keyboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.getSizingCalculator().editSavedSettings {
            if(it.currentMode == KeyboardMode.OneHanded
                && it.oneHandedDirection == OneHandedDirection.Right) {
                it.copy(
                    currentMode = KeyboardMode.Regular
                )
            } else {
                it.copy(
                    oneHandedDirection = OneHandedDirection.Right,
                    currentMode = KeyboardMode.OneHanded
                )
            }
        }
    },
    windowImpl = null,
)

val SplitKeyboardAction = Action(
    icon = R.drawable.arrow_down,
    name = R.string.split_keyboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.getSizingCalculator().editSavedSettings {
            if(it.currentMode == KeyboardMode.Split) {
                it.copy(
                    currentMode = KeyboardMode.Regular
                )
            } else {
                it.copy(
                    currentMode = KeyboardMode.Split
                )
            }
        }
    },
    windowImpl = null,
)

val FloatingKeyboardAction = Action(
    icon = R.drawable.arrow_up,
    name = R.string.floating_keyboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.getSizingCalculator().editSavedSettings {
            if(it.currentMode == KeyboardMode.Floating) {
                it.copy(
                    currentMode = KeyboardMode.Regular
                )
            } else {
                it.copy(
                    currentMode = KeyboardMode.Floating
                )
            }
        }
    },
    windowImpl = null,
)

val ResizeKeyboardAction = Action(
    icon = R.drawable.maximize,
    name = R.string.resize_keyboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.showResizer()
    },
    windowImpl = null,
)
