package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action

val SelectAllAction = Action(
    icon = R.drawable.maximize,
    name = R.string.action_select_all_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)

val CutAction = Action(
    icon = R.drawable.scissors,
    name = R.string.action_cut_title,
    simplePressImpl = { manager, _ ->
        manager.copyToClipboard(cut = true)
    },
    windowImpl = null,
)

val CopyAction = Action(
    icon = R.drawable.copy,
    name = R.string.action_copy_title,
    simplePressImpl = { manager, _ ->
        manager.copyToClipboard(cut = false)
    },
    windowImpl = null,
)

val PasteAction = Action(
    icon = R.drawable.clipboard,
    name = R.string.action_paste_title,
    simplePressImpl = { manager, _ ->
        manager.pasteFromClipboard()
    },
    windowImpl = null,
)