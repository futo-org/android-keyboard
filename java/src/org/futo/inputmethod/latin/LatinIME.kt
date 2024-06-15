package org.futo.inputmethod.latin

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.EmojiTracker.unuseEmoji
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.KeyboardBottomOffsetSetting
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.applyWindowColors
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.updates.scheduleUpdateCheckingJob

class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    LatinIMELegacy.SuggestionStripController, DynamicThemeProviderOwner {


    private lateinit var mLifecycleRegistry: LifecycleRegistry
    private lateinit var mViewModelStore: ViewModelStore
    private lateinit var mSavedStateRegistryController: SavedStateRegistryController



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

    lateinit var languageModelFacilitator: LanguageModelFacilitator

    val uixManager = UixManager(this)
    lateinit var suggestionBlacklist: SuggestionBlacklist

    private var activeThemeOption: ThemeOption? = null
    private var activeColorScheme = DarkColorScheme
    private var colorSchemeLoaderJob: Job? = null
    private var pendingRecreateKeyboard: Boolean = false

    val themeOption get() = activeThemeOption
    val colorScheme get() = activeColorScheme
    val keyboardColor get() = drawableProvider?.primaryKeyboardColor?.let { androidx.compose.ui.graphics.Color(it) } ?: colorScheme.surface

    private var drawableProvider: DynamicThemeProvider? = null

    private var lastEditorInfo: EditorInfo? = null

    private fun recreateKeyboard() {
        latinIMELegacy.updateTheme()
        latinIMELegacy.mKeyboardSwitcher.mState.onLoadKeyboard(latinIMELegacy.currentAutoCapsState, latinIMELegacy.currentRecapitalizeState);
        Log.w("LatinIME", "Recreating keyboard")
    }

    private var isNavigationBarVisible = false
    fun updateNavigationBarVisibility(visible: Boolean? = null) {
        if(visible != null) isNavigationBarVisible = visible

        val color = drawableProvider?.primaryKeyboardColor

        window.window?.let { window ->
            if(color == null || !isNavigationBarVisible) {
                applyWindowColors(window, Color.TRANSPARENT, statusBar = false)
            } else {
                applyWindowColors(window, color, statusBar = false)
            }
        }
    }

    private fun updateDrawableProvider(colorScheme: ColorScheme) {
        activeColorScheme = colorScheme
        drawableProvider = BasicThemeProvider(this, overrideColorScheme = colorScheme)

        updateNavigationBarVisibility()
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
        if(pendingRecreateKeyboard) {
            pendingRecreateKeyboard = false
            recreateKeyboard()
        }
    }

    private var currentSubtype = ""

    val jobs = mutableListOf<Job>()
    private fun launchJob(task: suspend CoroutineScope.() -> Unit) {
        jobs.add(lifecycleScope.launch(block = task))
    }

    private fun stopJobs() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    override fun onCreate() {
        super.onCreate()

        mLifecycleRegistry = LifecycleRegistry(this)
        mLifecycleRegistry.currentState = Lifecycle.State.INITIALIZED

        mViewModelStore = ViewModelStore()

        mSavedStateRegistryController = SavedStateRegistryController.create(this)
        mSavedStateRegistryController.performRestore(null)

        mLifecycleRegistry.currentState = Lifecycle.State.CREATED

        suggestionBlacklist = SuggestionBlacklist(latinIMELegacy.mSettings, this, lifecycleScope)

        Subtypes.addDefaultSubtypesIfNecessary(this)

        languageModelFacilitator = LanguageModelFacilitator(
            this,
            latinIMELegacy.mInputLogic,
            latinIMELegacy.mDictionaryFacilitator,
            latinIMELegacy.mSettings,
            latinIMELegacy.mKeyboardSwitcher,
            lifecycleScope,
            suggestionBlacklist
        )

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

        latinIMELegacy.onCreate()

        languageModelFacilitator.launchProcessor()
        languageModelFacilitator.loadHistoryLog()

        scheduleUpdateCheckingJob(this)
        launchJob { uixManager.showUpdateNoticeIfNeeded() }

        suggestionBlacklist.init()

        launchJob {
            dataStore.data.collect {
                drawableProvider?.let { provider ->
                    if(provider is BasicThemeProvider) {
                        if (provider.hasUpdated(it)) {
                            activeThemeOption?.obtainColors?.let { f ->
                                updateDrawableProvider(f(this@LatinIME))
                                if (!uixManager.isMainKeyboardHidden) {
                                    recreateKeyboard()
                                } else {
                                    pendingRecreateKeyboard = true
                                }
                            }
                        }
                    }
                }
            }
        }

        launchJob {
            val onNewSubtype: suspend (String) -> Unit = {
                val activeSubtype = it.ifEmpty {
                    getSettingBlocking(SubtypesSetting).firstOrNull()
                }

                if(activeSubtype != null && activeSubtype != currentSubtype) {
                    currentSubtype = activeSubtype

                    withContext(Dispatchers.Main) {
                        changeInputMethodSubtype(Subtypes.convertToSubtype(activeSubtype))
                    }
                }
            }

            onNewSubtype(getSetting(ActiveSubtype))

            dataStore.data.collect {
                onNewSubtype(it[ActiveSubtype.key] ?: ActiveSubtype.default)
            }
        }

        launchJob {
            dataStore.data.collect {
                CrashLoggingApplication.logPreferences(it)
            }
        }

        uixManager.onCreate()
    }

    override fun onDestroy() {
        stopJobs()
        mLifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()

        languageModelFacilitator.saveHistoryLog()

        runBlocking {
            languageModelFacilitator.destroyModel()
        }

        latinIMELegacy.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.w("LatinIME", "Configuration changed")
        latinIMELegacy.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onInitializeInterface() {
        latinIMELegacy.onInitializeInterface()
    }

    private var legacyInputView: View? = null
    private var touchableHeight: Int = 0
    override fun onCreateInputView(): View {
        Log.w("LatinIME", "Create input view")
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

        val padding = getSettingFlow(KeyboardBottomOffsetSetting).collectAsState(initial = 0.0f)

        key(legacyInputView) {
            AndroidView(factory = {
                legacyInputView!!
            }, modifier = modifier.padding(0.dp, 0.dp, 0.dp, padding.value.dp), onRelease = {
                val view = it as InputView
                view.deallocateMemory()
                view.removeAllViews()
            })
        }
    }

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        Log.w("LatinIME", "Updating legacy view")
        legacyInputView = newView

        uixManager.setContent()
        uixManager.getComposeView()?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(newView)
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
        languageModelFacilitator.onStartInput()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        mLifecycleRegistry.currentState = Lifecycle.State.STARTED

        lastEditorInfo = info

        super.onStartInputView(info, restarting)
        latinIMELegacy.onStartInputView(info, restarting)
        lifecycleScope.launch { uixManager.showUpdateNoticeIfNeeded() }
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

    private fun changeInputMethodSubtype(newSubtype: InputMethodSubtype?) {
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
        // TODO: Revisit fullscreen mode
        return false //latinIMELegacy.onEvaluateFullscreenMode(super.onEvaluateFullscreenMode())
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

    fun postUpdateSuggestionStrip(inputStyle: Int): Boolean {
        if(languageModelFacilitator.shouldPassThroughToLegacy()) return false

        languageModelFacilitator.updateSuggestionStripAsync(inputStyle);
        return true
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWordInfo) {
        uixManager.requestForgetWord(suggestedWordInfo)
    }

    fun refreshSuggestions() {
        latinIMELegacy.mInputLogic.performUpdateSuggestionStripSync(latinIMELegacy.mSettings.current, SuggestedWords.INPUT_STYLE_TYPING)
    }

    fun forceForgetWord(suggestedWordInfo: SuggestedWordInfo) {
        lifecycleScope.launch {
            val existingWords = getSetting(SUGGESTION_BLACKLIST).toMutableSet()
            existingWords.add(suggestedWordInfo.mWord)
            setSetting(SUGGESTION_BLACKLIST, existingWords)
        }

        latinIMELegacy.mDictionaryFacilitator.unlearnFromUserHistory(
            suggestedWordInfo.mWord, NgramContext.EMPTY_PREV_WORDS_INFO,
            -1, Constants.NOT_A_CODE
        )

        refreshSuggestions()
    }

    fun rememberEmojiSuggestion(suggestion: SuggestedWordInfo) {
        if(suggestion.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    useEmoji(suggestion.mWord)
                }
            }
        }
    }

    fun onEmojiDeleted(emoji: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                unuseEmoji(emoji)
            }
        }
    }

    var overrideInputConnection: InputConnection? = null
    override fun getCurrentInputConnection(): InputConnection? {
        return overrideInputConnection ?: super.getCurrentInputConnection()
    }

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = mViewModelStore
}