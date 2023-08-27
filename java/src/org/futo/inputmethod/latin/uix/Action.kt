package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleCoroutineScope
import org.futo.inputmethod.latin.uix.theme.ThemeOption


interface KeyboardManagerForAction {
    fun getContext(): Context
    fun getLifecycleScope(): LifecycleCoroutineScope

    fun triggerContentUpdate()

    fun typePartialText(v: String)

    fun typeText(v: String)

    fun closeActionWindow()

    fun triggerSystemVoiceInput()

    fun updateTheme(newTheme: ThemeOption)
}

interface ActionWindow {
    @Composable
    fun windowName(): String

    @Composable
    fun WindowContents()

    fun close()
}

interface PersistentActionState {
    fun cleanUp()
}

data class Action(
    @DrawableRes val icon: Int,
    val name: String, // TODO: @StringRes Int
    val windowImpl: ((KeyboardManagerForAction, PersistentActionState?) -> ActionWindow)?,
    val simplePressImpl: ((KeyboardManagerForAction, PersistentActionState?) -> Unit)?,
    val persistentState: ((KeyboardManagerForAction) -> PersistentActionState)? = null,
)
