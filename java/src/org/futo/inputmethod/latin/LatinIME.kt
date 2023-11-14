package org.futo.inputmethod.latin

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBar
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.actions.VoiceInputAction
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.inflateInlineSuggestion
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.xlm.LanguageModel;
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator;
import org.futo.inputmethod.latin.utils.SuggestionResults

class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    LatinIMELegacy.SuggestionStripController, DynamicThemeProviderOwner, KeyboardManagerForAction {

    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    private val mLifecycleRegistry = LifecycleRegistry(this)
    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        mLifecycleRegistry.handleLifecycleEvent(event)

    override val lifecycle
        get() = mLifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore
        get() = store

    private fun setOwners() {
        val decorView = window.window?.decorView
        if (decorView?.findViewTreeLifecycleOwner() == null) {
            decorView?.setViewTreeLifecycleOwner(this)
        }
        if (decorView?.findViewTreeViewModelStoreOwner() == null) {
            decorView?.setViewTreeViewModelStoreOwner(this)
        }
        if (decorView?.findViewTreeSavedStateRegistryOwner() == null) {
            decorView?.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    private var composeView: ComposeView? = null

    private val latinIMELegacy = LatinIMELegacy(
        this as InputMethodService,
        this as LatinIMELegacy.SuggestionStripController
    )

    public val languageModelFacilitator = LanguageModelFacilitator(
        this,
        latinIMELegacy.mInputLogic,
        latinIMELegacy.mDictionaryFacilitator,
        latinIMELegacy.mSettings,
        latinIMELegacy.mKeyboardSwitcher,
        lifecycleScope
    )

    private var activeThemeOption: ThemeOption? = null
    private var activeColorScheme = DarkColorScheme
    private var colorSchemeLoaderJob: Job? = null

    private var drawableProvider: DynamicThemeProvider? = null

    private var currWindowAction: Action? = null
    private var currWindowActionWindow: ActionWindow? = null
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()
    private fun isActionWindowOpen(): Boolean {
        return currWindowActionWindow != null
    }

    private var inlineSuggestions: List<MutableState<View?>> = listOf()

    private var lastEditorInfo: EditorInfo? = null

    // TODO: Calling this repeatedly as the theme changes tends to slow everything to a crawl
    private fun recreateKeyboard() {
        latinIMELegacy.updateTheme()
        latinIMELegacy.mKeyboardSwitcher.mState.onLoadKeyboard(latinIMELegacy.currentAutoCapsState, latinIMELegacy.currentRecapitalizeState);
    }

    private fun updateDrawableProvider(colorScheme: ColorScheme) {
        activeColorScheme = colorScheme
        drawableProvider = BasicThemeProvider(this, overrideColorScheme = colorScheme)

        window.window?.navigationBarColor = drawableProvider!!.primaryKeyboardColor
        setContent()
    }

    override fun getDrawableProvider(): DynamicThemeProvider {
        if (drawableProvider == null) {
            if (colorSchemeLoaderJob != null && !colorSchemeLoaderJob!!.isCompleted) {
                // Must have completed by now!
                runBlocking {
                    colorSchemeLoaderJob!!.join()
                }
            }
            drawableProvider = BasicThemeProvider(this, activeColorScheme)
        }

        return drawableProvider!!
    }

    private fun updateColorsIfDynamicChanged() {
        if(activeThemeOption?.dynamic == true) {
            val currColors = activeColorScheme
            val nextColors = activeThemeOption!!.obtainColors(this)

            if(currColors.differsFrom(nextColors)) {
                updateDrawableProvider(nextColors)
                recreateKeyboard()
            }
        }

        deferGetSetting(THEME_KEY) { key ->
            if(key != activeThemeOption?.key) {
                ThemeOptions[key]?.let { if(it.available(this)) updateTheme(it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        colorSchemeLoaderJob = deferGetSetting(THEME_KEY) {
            val themeOptionFromSettings = ThemeOptions[it]
            val themeOption = when {
                themeOptionFromSettings == null -> VoiceInputTheme
                !themeOptionFromSettings.available(this@LatinIME) -> VoiceInputTheme
                else -> themeOptionFromSettings
            }

            activeThemeOption = themeOption
            activeColorScheme = themeOption.obtainColors(this@LatinIME)
        }

        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        latinIMELegacy.onCreate()

        languageModelFacilitator.launchProcessor()
        languageModelFacilitator.loadHistoryLog()
    }

    override fun onDestroy() {
        languageModelFacilitator.saveHistoryLog()

        runBlocking {
            languageModelFacilitator.destroyModel()
        }

        latinIMELegacy.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        latinIMELegacy.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onInitializeInterface() {
        latinIMELegacy.onInitializeInterface()
    }

    private var legacyInputView: View? = null
    private var touchableHeight: Int = 0
    override fun onCreateInputView(): View {
        legacyInputView = latinIMELegacy.onCreateInputView()
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            this@LatinIME.setOwners()
        }

        setContent()

        latinIMELegacy.setComposeInputView(composeView)

        return composeView!!
    }

    private fun onActionActivated(action: Action) {
        // Finish what we are typing so far
        latinIMELegacy.onFinishInputViewInternal(false)

        if (action.windowImpl != null) {
            enterActionWindowView(action)
        } else if (action.simplePressImpl != null) {
            action.simplePressImpl.invoke(this, persistentStates[action])
        } else {
            throw IllegalStateException("An action must have either a window implementation or a simple press implementation")
        }
    }

    private var inputViewHeight: Int = -1
    private var shouldShowSuggestionStrip: Boolean = true
    private var suggestedWords: SuggestedWords? = null

    @Composable
    private fun LegacyKeyboardView(hidden: Boolean) {
        val modifier = if(hidden) {
            Modifier.clipToBounds().size(0.dp)
        } else {
            Modifier.onSizeChanged {
                inputViewHeight = it.height
            }
        }
        key(legacyInputView) {
            AndroidView(factory = {
                legacyInputView!!
            }, update = { }, modifier = modifier)
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
                latinIMELegacy,
                inlineSuggestions = inlineSuggestions,
                onActionActivated = { onActionActivated(it) }
            )
        }
    }

    private fun enterActionWindowView(action: Action) {
        assert(action.windowImpl != null)

        latinIMELegacy.mKeyboardSwitcher.saveKeyboardState()

        currWindowAction = action

        if (persistentStates[action] == null) {
            persistentStates[action] = action.persistentState?.let { it(this) }
        }

        currWindowActionWindow = action.windowImpl?.let { it(this, persistentStates[action]) }

        setContent()
    }

    private fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow == null) return

        currWindowActionWindow!!.close()

        currWindowAction = null
        currWindowActionWindow = null

        if(hasThemeChanged) {
            hasThemeChanged = false
            recreateKeyboard()
        }

        setContent()
    }

    @Composable
    private fun ActionViewWithHeader(windowImpl: ActionWindow) {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp), color = MaterialTheme.colorScheme.background
            )
            {
                Row {
                    IconButton(onClick = {
                        returnBackToMainKeyboardViewFromAction()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Back"
                        )
                    }

                    Text(
                        windowImpl.windowName(),
                        style = Typography.titleMedium,
                        modifier = Modifier.align(CenterVertically)
                    )
                }
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { inputViewHeight.toDp() })
            ) {
                windowImpl.WindowContents()
            }
        }
    }

    private fun setContent() {
        composeView?.setContent {
            UixThemeWrapper(activeColorScheme) {
                Column {
                    Spacer(modifier = Modifier.weight(1.0f))
                    Surface(modifier = Modifier.onSizeChanged {
                        touchableHeight = it.height
                    }) {
                        Column {
                            when {
                                isActionWindowOpen() -> ActionViewWithHeader(
                                    currWindowActionWindow!!
                                )

                                else -> MainKeyboardViewWithActionBar()
                            }

                            // The keyboard view really doesn't like being detached, so it's always
                            // shown, but resized to 0 if an action window is open
                            LegacyKeyboardView(hidden = isActionWindowOpen())
                        }
                    }
                }
            }
        }
    }

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        legacyInputView = newView
        setContent()

        if (composeView != null) {
            latinIMELegacy.setComposeInputView(composeView)
        }

        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)

        if (composeView != null) {
            latinIMELegacy.setComposeInputView(composeView)
        }

        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setCandidatesView(view: View?) {
        return latinIMELegacy.setCandidatesView(view)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        latinIMELegacy.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        lastEditorInfo = info

        super.onStartInputView(info, restarting)
        latinIMELegacy.onStartInputView(info, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        latinIMELegacy.onFinishInputView(finishingInput)

        closeActionWindow()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()

        closeActionWindow()
        languageModelFacilitator.saveHistoryLog()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        latinIMELegacy.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        latinIMELegacy.onWindowShown()

        updateColorsIfDynamicChanged()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        latinIMELegacy.onWindowHidden()

        closeActionWindow()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        latinIMELegacy.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
    }

    override fun onExtractedTextClicked() {
        latinIMELegacy.onExtractedTextClicked()
        super.onExtractedTextClicked()
    }

    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        latinIMELegacy.onExtractedCursorMovement(dx, dy)
        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        latinIMELegacy.hideWindow()
        super.hideWindow()
    }

    override fun onDisplayCompletions(completions: Array<out CompletionInfo>?) {
        latinIMELegacy.onDisplayCompletions(completions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        // This method may be called before {@link #setInputView(View)}.
        if (legacyInputView == null || composeView == null) {
            return
        }

        val inputHeight: Int = composeView!!.height
        if (latinIMELegacy.isImeSuppressedByHardwareKeyboard && !legacyInputView!!.isShown) {
            // If there is a hardware keyboard and a visible software keyboard view has been hidden,
            // no visual element will be shown on the screen.
            latinIMELegacy.setInsets(outInsets!!.apply {
                contentTopInsets = inputHeight
                visibleTopInsets = inputHeight
            })
            return
        }

        val visibleTopY = inputHeight - touchableHeight

        val touchLeft = 0
        val touchTop = visibleTopY
        val touchRight = composeView!!.width
        val touchBottom = inputHeight

        latinIMELegacy.setInsets(outInsets!!.apply {
            touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
            touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom);
            contentTopInsets = visibleTopY
            visibleTopInsets = visibleTopY
        })
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return latinIMELegacy.onShowInputRequested(
            flags,
            configChange
        ) || super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return latinIMELegacy.onEvaluateInputViewShown() || super.onEvaluateInputViewShown()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return latinIMELegacy.onEvaluateFullscreenMode(super.onEvaluateFullscreenMode())
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        latinIMELegacy.updateFullscreenMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        this.shouldShowSuggestionStrip = shouldShowSuggestionsStrip
        setContent()
    }

    override fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        this.suggestedWords = suggestedWords
        setContent()
    }

    override fun maybeShowImportantNoticeTitle(): Boolean {
        return false
    }

    private fun cleanUpPersistentStates() {
        println("Cleaning up persistent states")
        for((key, value) in persistentStates.entries) {
            if(currWindowAction != key) {
                lifecycleScope.launch { value?.cleanUp() }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        cleanUpPersistentStates()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        cleanUpPersistentStates()
    }

    override fun getContext(): Context {
        return this
    }

    override fun getLifecycleScope(): LifecycleCoroutineScope {
        return lifecycleScope
    }

    override fun triggerContentUpdate() {
        setContent()
    }

    private class LatinIMEActionInputTransaction(
        private val latinIME: LatinIME,
        shouldApplySpace: Boolean
    ): ActionInputTransaction {
        private val isSpaceNecessary: Boolean
        init {
            val priorText = latinIME.latinIMELegacy.mInputLogic.mConnection.getTextBeforeCursor(1, 0)
            isSpaceNecessary = shouldApplySpace && !priorText.isNullOrEmpty() && !priorText.last().isWhitespace()
        }

        private fun transformText(text: String): String {
            return if(isSpaceNecessary) { " $text" } else { text }
        }

        override fun updatePartial(text: String) {
            latinIME.latinIMELegacy.mInputLogic.mConnection.setComposingText(
                transformText(text),
                1
            )
        }

        override fun commit(text: String) {
            latinIME.latinIMELegacy.mInputLogic.mConnection.commitText(
                transformText(text),
                1
            )
        }

        override fun cancel() {
            // TODO: Do we want to leave the composing text as-is, or delete it?
            latinIME.latinIMELegacy.mInputLogic.mConnection.finishComposingText()
        }
    }

    override fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction {
        return LatinIMEActionInputTransaction(this, applySpaceIfNeeded)
    }

    override fun typeText(v: String) {
        latinIMELegacy.mInputLogic.mConnection.commitText(v, 1)
    }

    override fun closeActionWindow() {
        if(currWindowActionWindow == null) return
        returnBackToMainKeyboardViewFromAction()
    }

    override fun triggerSystemVoiceInput() {
        latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        );
    }

    private var hasThemeChanged: Boolean = false
    override fun updateTheme(newTheme: ThemeOption) {
        assert(newTheme.available(this))

        if (activeThemeOption != newTheme) {
            activeThemeOption = newTheme
            updateDrawableProvider(newTheme.obtainColors(this))
            deferSetSetting(THEME_KEY, newTheme.key)

            hasThemeChanged = true
            if(!isActionWindowOpen()) {
                recreateKeyboard()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest {
        return createInlineSuggestionsRequest(this, this.activeColorScheme)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        inlineSuggestions = response.inlineSuggestions.map {
            inflateInlineSuggestion(it)
        }
        setContent()

        return true
    }

    fun postUpdateSuggestionStrip(inputStyle: Int) {
        languageModelFacilitator.updateSuggestionStripAsync(inputStyle);
    }
}