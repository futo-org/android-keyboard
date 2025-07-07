package org.futo.inputmethod.latin.uix.actions

import android.widget.Toast
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.services.QuickSwitchService
import org.futo.inputmethod.latin.uix.ENABLE_SWITCH_APPS
import org.futo.inputmethod.latin.uix.getSettingBlocking

val SwitchAppsAction = Action(
    icon = R.drawable.move,
    name = R.string.action_switch_apps_title,
    simplePressImpl = { manager: KeyboardManagerForAction, _ ->
        if(!manager.getContext().getSettingBlocking(ENABLE_SWITCH_APPS)) return@Action

        val service = QuickSwitchService.instance
        if (service != null) {
            service.switchToPreviousApp()
        } else {
            Toast.makeText(
                manager.getContext(),
                manager.getContext().getString(R.string.action_switch_apps_enable_service),
                Toast.LENGTH_SHORT
            ).show()
        }
    },
    windowImpl = null,
)
