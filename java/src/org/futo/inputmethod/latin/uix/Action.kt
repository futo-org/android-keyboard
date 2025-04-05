package org.futo.inputmethod.latin.uix

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.LifecycleCoroutineScope
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import java.util.Locale

interface ActionInputTransaction {
    fun updatePartial(text: String)
    fun commit(text: String)
    fun cancel()
}

data class DialogRequestItem(
    val option: String,
    val onClick: () -> Unit
)

enum class TutorialMode {
    None,
    ResizerTutorial
}

interface KeyboardManagerForAction {
    fun getContext(): Context
    fun getLifecycleScope(): LifecycleCoroutineScope

    fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction

    fun typeText(v: String)
    fun typeUri(uri: Uri, mimeTypes: List<String>): Boolean
    fun backspace(amount: Int)

    fun closeActionWindow()

    fun triggerSystemVoiceInput()

    fun updateTheme(newTheme: ThemeOption)
    fun getThemeProvider(): DynamicThemeProvider

    fun sendCodePointEvent(codePoint: Int)
    fun sendKeyEvent(keyCode: Int, metaState: Int)

    fun isShifted(): Boolean

    fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean)
    fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean)

    fun performHapticAndAudioFeedback(code: Int, view: View)
    fun announce(s: String)
    fun getActiveLocales(): List<Locale>

    fun getInputConnection(): InputConnection?
    fun overrideInputConnection(inputConnection: InputConnection, editorInfo: EditorInfo)
    fun unsetInputConnection()

    fun requestDialog(text: String, options: List<DialogRequestItem>, onCancel: () -> Unit)
    fun openInputMethodPicker()
    fun activateAction(action: Action)
    fun showActionEditor()

    fun getSuggestionBlacklist(): SuggestionBlacklist
    fun getLatinIMEForDebug(): LatinIME
    fun isDeviceLocked(): Boolean

    fun getSizingCalculator(): KeyboardSizingCalculator
    fun showResizer()

    fun getTutorialMode(): TutorialMode
    fun setTutorialArrowPosition(coordinates: LayoutCoordinates)
    fun markTutorialCompleted()
}

interface ActionWindow {
    val onlyShowAboveKeyboard: Boolean
        get() = false

    val showCloseButton: Boolean
        get() = true

    val fixedWindowHeight: Dp?
        get() = null

    @Composable
    fun windowName(): String

    @Composable
    fun WindowContents(keyboardShown: Boolean)

    @Composable
    fun WindowTitleBar(rowScope: RowScope) {
        with(rowScope) {
            Text(windowName(), modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(modifier = Modifier.weight(1.0f))
        }
    }

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


    suspend fun onDeviceUnlocked() { }
}

enum class PersistentStateInitialization {
    OnActionTrigger,
    OnKeyboardLoad
}

data class Action(
    @DrawableRes val icon: Int,
    @StringRes val name: Int,
    val canShowKeyboard: Boolean = false,
    val keepScreenAwake: Boolean = false,

    val windowImpl: ((KeyboardManagerForAction, PersistentActionState?) -> ActionWindow)?,
    val simplePressImpl: ((KeyboardManagerForAction, PersistentActionState?) -> Unit)?,
    val persistentState: ((KeyboardManagerForAction) -> PersistentActionState)? = null,
    val persistentStateInitialization: PersistentStateInitialization = PersistentStateInitialization.OnActionTrigger,
    val altPressImpl: ((KeyboardManagerForAction, PersistentActionState?) -> Unit)? = null,

    val shownInEditor: Boolean = true
)