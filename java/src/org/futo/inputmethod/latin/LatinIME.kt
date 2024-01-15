package org.futo.inputmethod.latin

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
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
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.updates.scheduleUpdateCheckingJob

class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    LatinIMELegacy.SuggestionStripController, DynamicThemeProviderOwner {

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

    fun setOwners() {
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

    val latinIMELegacy = LatinIMELegacy(
        this as InputMethodService,
        this as LatinIMELegacy.SuggestionStripController
    )

    val inputLogic get() = latinIMELegacy.mInputLogic

    val languageModelFacilitator = LanguageModelFacilitator(
        this,
        latinIMELegacy.mInputLogic,
        latinIMELegacy.mDictionaryFacilitator,
        latinIMELegacy.mSettings,
        latinIMELegacy.mKeyboardSwitcher,
        lifecycleScope
    )

    val uixManager = UixManager(this)

    private var activeThemeOption: ThemeOption? = null
    private var activeColorScheme = DarkColorScheme
    private var colorSchemeLoaderJob: Job? = null
    private var pendingRecreateKeyboard: Boolean = false

    val themeOption get() = activeThemeOption
    val colorScheme get() = activeColorScheme

    private var drawableProvider: DynamicThemeProvider? = null

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
        uixManager.onColorSchemeChanged()
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

    fun updateTheme(newTheme: ThemeOption) {
        assert(newTheme.available(this))

        if (activeThemeOption != newTheme) {
            activeThemeOption = newTheme
            updateDrawableProvider(newTheme.obtainColors(this))
            deferSetSetting(THEME_KEY, newTheme.key)

            if(!uixManager.isMainKeyboardHidden) {
                recreateKeyboard()
            } else {
                pendingRecreateKeyboard = true
            }
        }
    }

    // Called by UixManager when the intention is to subsequently call LegacyKeyboardView with hidden=false
    // Maybe this can be changed to LaunchedEffect
    fun onKeyboardShown() {
        //if(pendingRecreateKeyboard) {
        //    pendingRecreateKeyboard = false
        //    recreateKeyboard()
        //}
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

        scheduleUpdateCheckingJob(this)
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

        val composeView = uixManager.createComposeView()
        latinIMELegacy.setComposeInputView(composeView)

        return composeView
    }

    private var inputViewHeight: Int = -1

    // Both called by UixManager
    fun updateTouchableHeight(to: Int) { touchableHeight = to }
    fun getInputViewHeight(): Int = inputViewHeight

    // The keyboard view really doesn't like being detached, so it's always
    // shown, but resized to 0 if an action window is open
    @Composable
    internal fun LegacyKeyboardView(hidden: Boolean) {
        LaunchedEffect(hidden) {
            if(hidden) {
                latinIMELegacy.mKeyboardSwitcher.saveKeyboardState()
            } else {
                if(pendingRecreateKeyboard) {
                    pendingRecreateKeyboard = false
                    recreateKeyboard()
                }
            }
        }

        val modifier = if(hidden) {
            Modifier
                .clipToBounds()
                .size(0.dp)
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

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        legacyInputView = newView

        uixManager.setContent()
        uixManager.getComposeView()?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)

        uixManager.getComposeView()?.let {
            latinIMELegacy.setComposeInputView(it)
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
        uixManager.onInputFinishing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()

        uixManager.onInputFinishing()
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

        uixManager.onInputFinishing()
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
        val composeView = uixManager.getComposeView()

        // This method may be called before {@link #setInputView(View)}.
        if (legacyInputView == null || composeView == null) {
            return
        }

        val inputHeight: Int = composeView.height
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
        val touchRight = composeView.width
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
        uixManager.updateVisibility(shouldShowSuggestionsStrip, fullscreenMode)
    }

    override fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        uixManager.setSuggestions(suggestedWords, rtlSubtype)
    }

    override fun maybeShowImportantNoticeTitle(): Boolean {
        return false
    }

    override fun onLowMemory() {
        super.onLowMemory()
        uixManager.cleanUpPersistentStates()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        uixManager.cleanUpPersistentStates()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest {
        return createInlineSuggestionsRequest(this, this.activeColorScheme)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        return uixManager.onInlineSuggestionsResponse(response)
    }

    fun postUpdateSuggestionStrip(inputStyle: Int) {
        languageModelFacilitator.updateSuggestionStripAsync(inputStyle);
    }
}