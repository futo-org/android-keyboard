package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action

val SelectAllAction = Action(
    icon = R.drawable.maximize,
    name = R.string.select_all_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)

val CutAction = Action(
    icon = R.drawable.scissors,
    name = R.string.cut_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)

val CopyAction = Action(
    icon = R.drawable.copy,
    name = R.string.copy_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)

val PasteAction = Action(
    icon = R.drawable.clipboard,
    name = R.string.clipboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)