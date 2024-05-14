package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import org.futo.inputmethod.latin.ActiveSubtype
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking

fun switchToNextLanguage(context: Context) {
    val enabledSubtypes = context.getSettingBlocking(SubtypesSetting).toList()
    val currentSubtype = context.getSettingBlocking(ActiveSubtype)

    val index = enabledSubtypes.indexOf(currentSubtype)
    val nextIndex = if(index == -1) {
        0
    } else {
        (index + 1) % enabledSubtypes.size
    }

    context.setSettingBlocking(ActiveSubtype.key, enabledSubtypes[nextIndex])
}

val SwitchLanguageAction = Action(
    icon = R.drawable.globe,
    name = R.string.show_language_switch_key,
    simplePressImpl = { manager, _ ->
        switchToNextLanguage(manager.getContext())
    },
    windowImpl = null,
)