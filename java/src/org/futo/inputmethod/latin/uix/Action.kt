package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleCoroutineScope
import org.futo.inputmethod.latin.uix.theme.ThemeOption

interface ActionInputTransaction {
    fun updatePartial(text: String)
    fun commit(text: String)
    fun cancel()
}

interface KeyboardManagerForAction {
    fun getContext(): Context
    fun getLifecycleScope(): LifecycleCoroutineScope

    fun triggerContentUpdate()

    fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction

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
    /**
     * When called, the device may be on low memory and is requesting the action to clean up its
     * state. You can close any resources that may not be necessary anymore. This will never be
     * called when the action window is currently open. The PersistentActionState will stick around
     * after this.
     */
    suspend fun cleanUp()
}

data class Action(
    @DrawableRes val icon: Int,
    @StringRes val name: Int,
    val windowImpl: ((KeyboardManagerForAction, PersistentActionState?) -> ActionWindow)?,
    val simplePressImpl: ((KeyboardManagerForAction, PersistentActionState?) -> Unit)?,
    val persistentState: ((KeyboardManagerForAction) -> PersistentActionState)? = null,
)
