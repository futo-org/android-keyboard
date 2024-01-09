package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InlineSuggestionsResponse
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.suggestions.SuggestionStripView
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper

private class LatinIMEActionInputTransaction(
    private val inputLogic: InputLogic,
    shouldApplySpace: Boolean
): ActionInputTransaction {
    private val isSpaceNecessary: Boolean
    init {
        val priorText = inputLogic.mConnection.getTextBeforeCursor(1, 0)
        isSpaceNecessary = shouldApplySpace && !priorText.isNullOrEmpty() && !priorText.last().isWhitespace()
    }

    private fun transformText(text: String): String {
        return if(isSpaceNecessary) { " $text" } else { text }
    }

    override fun updatePartial(text: String) {
        inputLogic.mConnection.setComposingText(
            transformText(text),
            1
        )
    }

    override fun commit(text: String) {
        inputLogic.mConnection.commitText(
            transformText(text),
            1
        )
    }

    override fun cancel() {
        inputLogic.mConnection.finishComposingText()
    }
}

class UixActionKeyboardManager(val uixManager: UixManager, val latinIME: LatinIME) : KeyboardManagerForAction {
    override fun getContext(): Context {
        return latinIME
    }

    override fun getLifecycleScope(): LifecycleCoroutineScope {
        return latinIME.lifecycleScope
    }

    override fun triggerContentUpdate() {
        uixManager.setContent()
    }

    override fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction {
        return LatinIMEActionInputTransaction(latinIME.inputLogic, applySpaceIfNeeded)
    }

    override fun typeText(v: String) {
        latinIME.latinIMELegacy.onTextInput(v)
    }

    override fun backspace(amount: Int) {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_DELETE,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun closeActionWindow() {
        if(uixManager.currWindowActionWindow == null) return
        uixManager.returnBackToMainKeyboardViewFromAction()
    }

    override fun triggerSystemVoiceInput() {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        );
    }

    override fun updateTheme(newTheme: ThemeOption) {
        latinIME.updateTheme(newTheme)
    }

    override fun sendCodePointEvent(codePoint: Int) {
        latinIME.latinIMELegacy.onCodeInput(codePoint,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun sendKeyEvent(keyCode: Int, metaState: Int) {
        latinIME.inputLogic.sendDownUpKeyEvent(keyCode, metaState)
    }
}

class UixManager(private val latinIME: LatinIME) {
    private var shouldShowSuggestionStrip: Boolean = true
    private var suggestedWords: SuggestedWords? = null

    private var composeView: ComposeView? = null

    private var currWindowAction: Action? = null
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()

    private var inlineSuggestions: List<MutableState<View?>> = listOf()
    private val keyboardManagerForAction = UixActionKeyboardManager(this, latinIME)

    private var mainKeyboardHidden = false

    var currWindowActionWindow: ActionWindow? = null

    val isMainKeyboardHidden get() = mainKeyboardHidden

    private fun onActionActivated(action: Action) {
        latinIME.inputLogic.finishInput()

        if (action.windowImpl != null) {
            enterActionWindowView(action)
        } else if (action.simplePressImpl != null) {
            action.simplePressImpl.invoke(keyboardManagerForAction, persistentStates[action])
        } else {
            throw IllegalStateException("An action must have either a window implementation or a simple press implementation")
        }
    }

    @Composable
    private fun MainKeyboardViewWithActionBar() {
        Column {
            // Don't show suggested words when it's not meant to be shown
            val suggestedWordsOrNull = if(shouldShowSuggestionStrip) {
                suggestedWords
            } else {
                null
            }

            ActionBar(
                suggestedWordsOrNull,
                latinIME.latinIMELegacy as SuggestionStripView.Listener,
                inlineSuggestions = inlineSuggestions,
                onActionActivated = { onActionActivated(it) }
            )
        }
    }

    private fun enterActionWindowView(action: Action) {
        assert(action.windowImpl != null)

        mainKeyboardHidden = true

        currWindowAction = action

        if (persistentStates[action] == null) {
            persistentStates[action] = action.persistentState?.let { it(keyboardManagerForAction) }
        }

        currWindowActionWindow = action.windowImpl?.let { it(keyboardManagerForAction, persistentStates[action]) }

        setContent()
    }

    fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow == null) return

        currWindowActionWindow!!.close()

        currWindowAction = null
        currWindowActionWindow = null

        mainKeyboardHidden = false

        latinIME.onKeyboardShown()

        setContent()
    }

    private fun toggleExpandAction() {
        mainKeyboardHidden = !mainKeyboardHidden
        if(!mainKeyboardHidden) {
            latinIME.onKeyboardShown()
        }

        setContent()
    }

    @Composable
    private fun ActionViewWithHeader(windowImpl: ActionWindow) {
        val heightDiv = if(mainKeyboardHidden) {
            1
        } else {
            1.5
        }
        Column {
            if(mainKeyboardHidden) {
                ActionWindowBar(
                    onBack = { returnBackToMainKeyboardViewFromAction() },
                    canExpand = currWindowAction!!.canShowKeyboard,
                    onExpand = { toggleExpandAction() },
                    windowName = windowImpl.windowName()
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) {
                    (latinIME.getInputViewHeight().toFloat() / heightDiv.toFloat()).toDp()
                })
            ) {
                windowImpl.WindowContents(keyboardShown = !isMainKeyboardHidden)
            }

            if(!mainKeyboardHidden) {
                val suggestedWordsOrNull = if (shouldShowSuggestionStrip) {
                    suggestedWords
                } else {
                    null
                }

                CollapsibleSuggestionsBar(
                    onCollapse = { toggleExpandAction() },
                    onClose = { returnBackToMainKeyboardViewFromAction() },
                    words = suggestedWordsOrNull,
                    suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripView.Listener,
                    inlineSuggestions = inlineSuggestions
                )
            }
        }
    }

    fun setContent() {
        composeView?.setContent {
            UixThemeWrapper(latinIME.colorScheme) {
                Column {
                    Spacer(modifier = Modifier.weight(1.0f))
                    Surface(modifier = Modifier.onSizeChanged {
                        latinIME.updateTouchableHeight(it.height)
                    }) {
                        Column {
                            when {
                                currWindowActionWindow != null -> ActionViewWithHeader(
                                    currWindowActionWindow!!
                                )

                                else -> MainKeyboardViewWithActionBar()
                            }

                            latinIME.LegacyKeyboardView(hidden = isMainKeyboardHidden)
                        }
                    }
                }
            }
        }
    }

    fun createComposeView(): View {
        if(composeView != null) {
            composeView = null
            //throw IllegalStateException("Attempted to create compose view, when one is already created!")
        }

        composeView = ComposeView(latinIME).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            latinIME.setOwners()
        }

        setContent()

        return composeView!!
    }

    fun getComposeView(): View? {
        return composeView
    }

    fun onColorSchemeChanged() {
        setContent()
    }

    fun onInputFinishing() {
        closeActionWindow()
    }

    fun cleanUpPersistentStates() {
        println("Cleaning up persistent states")
        for((key, value) in persistentStates.entries) {
            if(currWindowAction != key) {
                latinIME.lifecycleScope.launch { value?.cleanUp() }
            }
        }
    }

    fun closeActionWindow() {
        if(currWindowActionWindow == null) return
        returnBackToMainKeyboardViewFromAction()
    }


    fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        this.shouldShowSuggestionStrip = shouldShowSuggestionsStrip
        setContent()
    }

    fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        this.suggestedWords = suggestedWords
        setContent()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        inlineSuggestions = response.inlineSuggestions.map {
            latinIME.inflateInlineSuggestion(it)
        }
        setContent()

        return true
    }
}