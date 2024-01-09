package org.futo.inputmethod.latin.uix.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker

val ThemeAction = Action(
    icon = R.drawable.eye,
    name = R.string.theme_switcher_action_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.theme_switcher_action_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                ThemePicker { manager.updateTheme(it) }
            }

            override fun close() {

            }
        }
    }
)