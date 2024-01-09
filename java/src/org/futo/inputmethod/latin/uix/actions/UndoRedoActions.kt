package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action

val UndoAction = Action(
    icon = R.drawable.undo,
    name = R.string.undo_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)
val RedoAction = Action(
    icon = R.drawable.redo,
    name = R.string.redo_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)