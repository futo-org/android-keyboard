package org.futo.inputmethod.latin.uix.actions

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.uix.Action


val SwitchLanguageAction = Action(
    icon = R.drawable.globe,
    name = R.string.show_language_switch_key,
    simplePressImpl = { manager, _ ->
        Subtypes.switchToNextLanguage(manager.getContext(), 1)
    },
    altPressImpl = { manager, _ ->
        manager.openInputMethodPicker()
    },
    windowImpl = null,
)