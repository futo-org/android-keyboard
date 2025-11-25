package org.futo.inputmethod.latin.uix.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker

val ThemeAction = Action(
    icon = R.drawable.themes,
    name = R.string.action_theme_switcher_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            override val onlyShowAboveKeyboard: Boolean = true

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_theme_switcher_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                ThemePicker({ manager.updateTheme(it) }, {})
            }
        }
    }
)