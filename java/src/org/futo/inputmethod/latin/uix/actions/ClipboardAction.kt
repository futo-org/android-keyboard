package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action

val ClipboardAction = Action(
    icon = R.drawable.clipboard,
    name = R.string.clipboard_action_title,
    simplePressImpl = { manager, _ ->
        manager.sendKeyEvent(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
    },
    windowImpl = null,
)