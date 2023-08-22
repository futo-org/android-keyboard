package org.futo.inputmethod.latin.uix

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import org.futo.inputmethod.latin.uix.theme.ThemeOption


interface KeyboardManagerForAction {
    fun triggerSystemVoiceInput()

    fun updateTheme(newTheme: ThemeOption)
}

interface ActionWindow {
    @Composable
    fun windowName(): String

    @Composable
    fun WindowContents(manager: KeyboardManagerForAction)
}

data class Action(
    @DrawableRes val icon: Int,
    val name: String, // TODO: @StringRes Int
    val windowImpl: ActionWindow?,
    val simplePressImpl: ((KeyboardManagerForAction) -> Unit)?
)
