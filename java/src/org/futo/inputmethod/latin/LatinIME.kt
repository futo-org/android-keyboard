package org.futo.inputmethod.latin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.engine.IMEManager
import org.futo.inputmethod.engine.general.WordLearner
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.HiddenKeysSetting
import org.futo.inputmethod.latin.uix.KeyBordersSetting
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.actions.CanThrowIfDebug
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.forceUnlockDatastore
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.safeKeyboardPadding
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.applyWindowColors
import org.futo.inputmethod.latin.uix.theme.getThemeOption
import org.futo.inputmethod.latin.uix.theme.orDefault
import org.futo.inputmethod.latin.uix.theme.presets.DefaultDarkScheme
import org.futo.inputmethod.latin.utils.JniUtils
import org.futo.inputmethod.updates.scheduleUpdateCheckingJob
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardSettings
import org.futo.inputmethod.v2keyboard.KeyboardSizeSettingKind
import org.futo.inputmethod.v2keyboard.KeyboardSizeStateProvider
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import org.futo.inputmethod.v2keyboard.LayoutManager
import org.futo.inputmethod.v2keyboard.dimensionsSameAs
import org.futo.inputmethod.v2keyboard.getPrimaryLayoutOverride
import org.futo.inputmethod.v2keyboard.isFoldableInnerDisplayAllowed
import kotlin.math.roundToInt

/** Whether or not we can render into the navbar */
val SupportsNavbarExtension = Build.VERSION.SDK_INT >= 28

val SupportsNonComposing = Build.VERSION.SDK_INT >= 31

val UseTransparentNavbar =
    // https://github.com/futo-org/android-keyboard/issues/772
    !Build.MANUFACTURER.lowercase().contains("motorola")

private class UnlockedBroadcastReceiver(val onDeviceUnlocked: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
            onDeviceUnlocked()
        }
    }
}

open class InputMethodServiceCompose : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var mLifecycleRegistry: LifecycleRegistry
    private lateinit var mViewModelStore: ViewModelStore
    private lateinit var mSavedStateRegistryController: SavedStateRegistryController

    fun setOwners() {
        val decorView = window.window?.decorView
        decorView?.setViewTreeLifecycleOwner(this)
        decorView?.setViewTreeViewModelStoreOwner(this)
        decorView?.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onCreate() {
        super.onCreate()

        mLifecycleRegistry = LifecycleRegistry(this)
        mLifecycleRegistry.currentState = Lifecycle.State.INITIALIZED

        mViewModelStore = ViewModelStore()

        mSavedStateRegistryController = SavedStateRegistryController.create(this)
        mSavedStateRegistryController.performRestore(null)

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onDestroy() {
        super.onDestroy()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = mViewModelStore

    internal var composeView: ComposeView? = null

    override fun onCreateInputView(): View =
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setParentCompositionContext(null)

            setOwners()

            composeView = this
        }
}

class LatinIME : InputMethodServiceCompose(), LatinIMELegacy.SuggestionStripController,
        DynamicThemeProviderOwner, FoldStateProvider, KeyboardSizeStateProvider {

    val imeManager = IMEManager(this)

    val latinIMELegacy = LatinIMELegacy(
        this as InputMethodService,
        this as LatinIMELegacy.SuggestionStripController,
    )

    val uixManager = UixManager(this)

    val sizingCalculator = KeyboardSizingCalculator(this, uixManager)

    private var activeThemeOption: ThemeOption? = null
    private val activeColorScheme = mutableStateOf(DefaultDarkScheme.obtainColors(this))
    private var pendingRecreateKeyboard: Boolean = false

    val colorScheme get() = activeColorScheme.value
    val keyboardColor get() = colorScheme.keyboardSurface.let { fallback ->
        drawableProvider?.keyboardColor?.let { androidx.compose.ui.graphics.Color(it) } ?: fallback
    }

    val size: MutableState<ComputedKeyboardSize?> = mutableStateOf(null)
    private fun calculateSize(): ComputedKeyboardSize? = sizingCalculator.calculate(
        getPrimaryLayoutOverride(currentInputEditorInfo) ?: latinIMELegacy.mKeyboardSwitcher.keyboard?.mId?.mKeyboardLayoutSetName ?: "qwerty",
        Settings.getInstance().current
    )

    private var drawableProvider: DynamicThemeProvider? = null

    private var settingsRefreshRequired = false
    private fun recreateKeyboard() {
        latinIMELegacy.updateTheme()

        if(settingsRefreshRequired) {
            latinIMELegacy.mKeyboardSwitcher.loadKeyboard(
                currentInputEditorInfo ?: return,
                latinIMELegacy.mSettings.current,
                latinIMELegacy.currentAutoCapsState
            )
        } else {
            latinIMELegacy.mKeyboardSwitcher.mState.onLoadKeyboard(
                currentInputEditorInfo ?: return,
                latinIMELegacy.currentAutoCapsState,
                latinIMELegacy.mKeyboardSwitcher.keyboard?.mId?.mKeyboardLayoutSetName
            )
        }

        settingsRefreshRequired = false
    }

    private var isNavigationBarVisible = false
    fun updateNavigationBarVisibility(visible: Boolean? = null) {
        if(visible != null) isNavigationBarVisible = visible

        if(SupportsNavbarExtension) {
            val isFloating = size.value is FloatingKeyboardSize

            val color = (colorScheme.navigationBarColor ?: colorScheme.keyboardSurface)

            val colorToUse = when {
                isFloating -> Color.BLACK
                else -> (colorScheme.navigationBarColorForTransparency ?: color).toArgb()
            } and 0x00FFFFFF

            window.window?.let { window ->
                if(UseTransparentNavbar) {
                    applyWindowColors(window, colorToUse, statusBar = false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.setNavigationBarContrastEnforced(isFloating)
                    }

                    WindowCompat.setDecorFitsSystemWindows(window, false)
                } else {
                    applyWindowColors(window, color.toArgb(), statusBar = false)
                }
            }
        } else {
            val color = colorScheme.navigationBarColor?.toArgb() ?: drawableProvider?.keyboardColor

            window.window?.let { window ->
                if(color == null || !isNavigationBarVisible) {
                    applyWindowColors(window, Color.TRANSPARENT, statusBar = false)
                } else {
                    applyWindowColors(window, color, statusBar = false)
                }
            }
        }
    }

    private fun updateDrawableProvider(colorScheme: KeyboardColorScheme) {
        activeColorScheme.value = colorScheme
        drawableProvider = BasicThemeProvider(this, colorScheme)

        updateNavigationBarVisibility()
        uixManager.onColorSchemeChanged()
    }

    override fun getDrawableProvider(): DynamicThemeProvider {
        return drawableProvider ?: BasicThemeProvider(this, colorScheme).let {
            drawableProvider = it
            it
        }
    }

    private fun updateColorsIfDynamicChanged() {
        if(activeThemeOption?.dynamic == true) {
            val currColors = colorScheme
            val nextColors = activeThemeOption!!.obtainColors(this)

            if(currColors.differsFrom(nextColors)) {
                updateDrawableProvider(nextColors)
                recreateKeyboard()
                return
            }
        }

        // TODO: Verify this actually fixes anything
        if(drawableProvider?.displayDpi != resources.displayMetrics.densityDpi) {
            updateDrawableProvider(colorScheme)
            recreateKeyboard()
            return
        }
    }

    fun onSizeUpdated() {
        val newSize = calculateSize() ?: return
        val shouldInvalidateKeyboard = size.value?.let { oldSize ->
            when {
                oldSize is FloatingKeyboardSize && newSize is FloatingKeyboardSize -> {
                    oldSize.width != newSize.width || oldSize.height != newSize.height
                }
                else -> !newSize.dimensionsSameAs(oldSize)
            }
        } ?: true

        size.value = newSize

        if(shouldInvalidateKeyboard) {
            invalidateKeyboard(true)
            updateNavigationBarVisibility()
        }
    }

    fun invalidateKeyboard(refreshSettings: Boolean = false) {
        if(destroying) return
        size.value = calculateSize()
        updateNavigationBarVisibility()
        settingsRefreshRequired = settingsRefreshRequired || refreshSettings

        if(!uixManager.isMainKeyboardHidden) {
            recreateKeyboard()
        } else {
            pendingRecreateKeyboard = true
        }
    }

    // Called by UixManager when the intention is to subsequently call LegacyKeyboardView with hidden=false
    // Maybe this can be changed to LaunchedEffect
    fun onKeyboardShown() {
        if(pendingRecreateKeyboard) {
            pendingRecreateKeyboard = false
            recreateKeyboard()
        } else {
            latinIMELegacy.mKeyboardSwitcher?.mainKeyboardView?.invalidateAllKeys()
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

    private var unlockReceiver = UnlockedBroadcastReceiver { onDeviceUnlocked() }

    override fun onCreate() {
        super.onCreate()

        CanThrowIfDebug = isDirectBootUnlocked

        JniUtils.loadNativeLibrary()

        LayoutManager.init(this)

        DataStoreHelper.init(this)

        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        registerReceiver(unlockReceiver, filter)

        Subtypes.addDefaultSubtypesIfNecessary(this)

        getSettingBlocking(THEME_KEY).let {
            val themeOption = getThemeOption(this, it).orDefault(this@LatinIME)

            activeThemeOption = themeOption
            activeColorScheme.value = themeOption.obtainColors(this@LatinIME)
        }

        imeManager.onCreate()
        latinIMELegacy.onCreate()

        scheduleUpdateCheckingJob(this)
        launchJob { uixManager.showUpdateNoticeIfNeeded() }

        launchJob {
            getSettingFlow(THEME_KEY).collect {
                val themeOption = getThemeOption(this@LatinIME, it).orDefault(this@LatinIME)

                activeThemeOption = themeOption
                activeColorScheme.value = themeOption.obtainColors(this@LatinIME)

                updateDrawableProvider(activeColorScheme.value)
                invalidateKeyboard()
            }
        }

        launchJob {
            combine(
                getSettingFlow(HiddenKeysSetting),
                getSettingFlow(KeyBordersSetting),
                getSettingFlow(KeyHintsSetting)
            ) { a, b, c -> Triple(a, b, c) }.collect {
                    drawableProvider?.let { provider ->
                        if(provider is BasicThemeProvider) {
                            activeThemeOption?.obtainColors?.let { f ->
                                updateDrawableProvider(f(this@LatinIME))
                                invalidateKeyboard()
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
                        val subtype = Subtypes.convertToSubtype(activeSubtype)
                        changeInputMethodSubtype(subtype)
                        uixManager.updateLocale(Subtypes.getLocale(subtype))
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

        // Listen to size changes
        launchJob {
            val prev: MutableMap<KeyboardSizeSettingKind, String?> =
                KeyboardSizeSettingKind.entries.associateWith { null }.toMutableMap()

            dataStore.data.collect { data ->
                prev.keys.toList().forEach {
                    if(data[KeyboardSettings[it]!!.key] != prev[it]) {
                        prev[it] = data[KeyboardSettings[it]!!.key]
                        onSizeUpdated()
                    }
                }
            }
        }

        uixManager.onCreate()

        Settings.getInstance().settingsChangedListeners.add { oldSettings, newSettings ->
            val differs = (oldSettings.mActionKeyId != newSettings.mActionKeyId)
                    || (oldSettings.mShowsActionKey != newSettings.mShowsActionKey)

            if (differs) {
                invalidateKeyboard(refreshSettings = true)
            }
        }
    }

    private var destroying = false
    override fun onDestroy() {
        destroying = true
        unregisterReceiver(unlockReceiver)
        stopJobs()
        viewModelStore.clear()
        uixManager.onDestroy()
        imeManager.onDestroy()
        Settings.getInstance().settingsChangedListeners.clear()
        latinIMELegacy.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.w("LatinIME", "Configuration changed")
        size.value = calculateSize()
        updateNavigationBarVisibility()
        latinIMELegacy.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
        uixManager.updateLocaleOnCfgChanged()
    }

    override fun onInitializeInterface() {
        latinIMELegacy.onInitializeInterface()
    }

    private var legacyInputView: MutableState<View?> = mutableStateOf(null)
    override fun onCreateInputView(): View {
        val composeView = super.onCreateInputView()

        legacyInputView.value = latinIMELegacy.onCreateInputView()
        latinIMELegacy.setComposeInputView(composeView)

        uixManager.setContent()

        return composeView
    }

    private var inputViewHeight: Int = -1

    fun getInputViewHeight(): Int = inputViewHeight
    fun getViewHeight(): Int = composeView?.height ?: resources.displayMetrics.heightPixels
    fun getViewWidth(): Int = composeView?.width ?: resources.displayMetrics.widthPixels

    private var isInputModal = false
    fun setInputModal(to: Boolean) {
        isInputModal = to
    }

    // The keyboard view really doesn't like being detached, so it's always
    // shown, but resized to 0 if an action window is open
    @Composable
    internal fun LegacyKeyboardView(modifier: Modifier, hidden: Boolean) {
        val modifier = if(hidden) {
            modifier
                .clipToBounds()
                .size(0.dp)
        } else {
            modifier.onSizeChanged {
                inputViewHeight = it.height
            }
        }.safeKeyboardPadding()

        val legacyInputView = legacyInputView.value
        key(legacyInputView) {
            AndroidView(factory = {
                legacyInputView!!.also {
                    if(it.parent != null) (it.parent as ViewGroup).removeView(it)
                }
            }, modifier = modifier, onRelease = {
                val view = it as InputView
                view.deallocateMemory()
                view.removeAllViews()
            })
        }
    }

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        legacyInputView.value = newView
        composeView?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(newView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)

        composeView?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(legacyInputView.value)
    }

    override fun setCandidatesView(view: View?) {
        return latinIMELegacy.setCandidatesView(view)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        latinIMELegacy.onStartInput(attribute, restarting)
        uixManager.inputStarted(attribute)
        //imeManager.onStartInput() // TODO: Is this call needed or not?
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        imeManager.onStartInput()
        latinIMELegacy.onStartInputView(info, restarting)
        lifecycleScope.launch { uixManager.showUpdateNoticeIfNeeded() }
        updateColorsIfDynamicChanged()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        latinIMELegacy.onFinishInputView(finishingInput)
        uixManager.onInputFinishing()
        imeManager.onFinishInput()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()
        uixManager.onInputFinishing()
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

    }

    override fun onComputeInsets(outInsets: Insets?) {
        // This method may be called before {@link #setInputView(View)}.
        if (legacyInputView.value == null || composeView == null) {
            return
        }

        val viewHeight = composeView!!.height
        val size = size.value ?: return
        latinIMELegacy.setInsets(outInsets!!.apply {
            when(size) {
                is FloatingKeyboardSize -> {
                    val height = uixManager.touchableHeight

                    val left   = uixManager.floatingPosition.x.toInt()
                    val right  = (uixManager.floatingPosition.x + size.width).roundToInt()
                    val top    = uixManager.floatingPosition.y.toInt()
                    val bottom = (uixManager.floatingPosition.y + height).roundToInt()

                    touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                    touchableRegion.set(left, top, right, bottom)
                    contentTopInsets = viewHeight
                    visibleTopInsets = viewHeight
                }
                else -> {
                    touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT

                    val touchableHeight = uixManager.touchableHeight
                    val topInset = if(touchableHeight < 1 || touchableHeight >= viewHeight - 1) {
                        val actionBarHeight = sizingCalculator.calculateTotalActionBarHeightPx()

                        viewHeight - size.height - actionBarHeight
                    } else {
                        viewHeight - touchableHeight
                    }

                    contentTopInsets = topInset
                    visibleTopInsets = topInset
                }
            }

            if(isInputModal || latinIMELegacy.mKeyboardSwitcher?.isShowingMoreKeysPanel == true) {
                touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                touchableRegion.set(0, 0, composeView!!.width, composeView!!.height)
            }
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

    override fun setSuggestions(
        suggestedWords: SuggestedWords,
        rtlSubtype: Boolean,
        useExpandableUi: Boolean
    ) {
        uixManager.setSuggestions(suggestedWords, rtlSubtype, useExpandableUi)

        // Cache the auto-correction in accessibility code so we can speak it if the user
        // touches a key that will insert it.
        AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords)
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
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        return createInlineSuggestionsRequest(this, colorScheme)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        return uixManager.onInlineSuggestionsResponse(response)
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWordInfo) {
        uixManager.requestForgetWord(suggestedWordInfo)
    }

    fun blacklistWord(suggestedWordInfo: SuggestedWordInfo?) = lifecycleScope.launch {
        if(suggestedWordInfo != null) {
            val existingWords = getSetting(SUGGESTION_BLACKLIST).toMutableSet()
            existingWords.add(suggestedWordInfo.mWord)
            setSetting(SUGGESTION_BLACKLIST, existingWords)
        }

        imeManager.getActiveIME(Settings.getInstance().current).let {
            if(it is WordLearner && suggestedWordInfo != null) {
                it.removeFromHistory(
                    suggestedWordInfo.mWord,
                    NgramContext.EMPTY_PREV_WORDS_INFO,
                    -1,
                    Constants.NOT_A_CODE
                )
            }

            withContext(Dispatchers.Main) {
                it.requestSuggestionRefresh()
            }
        }
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

    private var overrideInputConnection: InputConnection? = null
    private var overrideEditorInfo: EditorInfo? = null
    fun overrideInputConnection(to: InputConnection?, editorInfo: EditorInfo?) {
        imeManager.onFinishInput()
        this.overrideInputConnection = to
        this.overrideEditorInfo = editorInfo

        latinIMELegacy.loadSettings()

        imeManager.onStartInput()

        val currentIC = currentInputConnection
        currentIC?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.getCurrentInputConnection()?.setImeConsumesInput(to != null)
        }
    }

    val isInputConnectionOverridden
        get() = overrideInputConnection != null

    val inputConnectionOverridenWithSuggestions
        get() = isInputConnectionOverridden && overrideEditorInfo?.let {
            !it.privateImeOptions.contains("org.futo.inputmethod.latin.NoSuggestions=1")
        } ?: false

    override fun getCurrentInputConnection(): InputConnection? {
        return overrideInputConnection ?: super.getCurrentInputConnection()
    }

    override fun getCurrentInputEditorInfo(): EditorInfo? {
        return overrideEditorInfo ?: super.getCurrentInputEditorInfo()
    }

    fun getBaseInputConnection(): InputConnection? {
        return super.getCurrentInputConnection()
    }

    fun getBaseInputEditorInfo(): EditorInfo? {
        return super.getCurrentInputEditorInfo()
    }

    private fun onDeviceUnlocked() {
        forceUnlockDatastore(this)

        Log.i("LatinIME", "Device has been unlocked, reloading settings")

        // Every place that called getDefaultSharedPreferences now needs to be refreshed or call it again

        // Mainly Settings singleton needs to be refreshed
        Settings.init(applicationContext)
        Settings.getInstance().onSharedPreferenceChanged(null /* unused */, "")
        latinIMELegacy.loadSettings()
        recreateKeyboard()

        imeManager.onDeviceUnlocked()

        uixManager.onPersistentStatesUnlocked()

        CanThrowIfDebug = true

        // TODO: Spell checker service
    }

    override val foldState: FoldingOptions
        get() = uixManager.foldingOptions.value

    override val currentSizeState: KeyboardSizeSettingKind
        get() = when {
            (foldState.feature != null && isFoldableInnerDisplayAllowed()) ->
                KeyboardSizeSettingKind.FoldableInnerDisplay

            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ->
                KeyboardSizeSettingKind.Landscape

            else ->
                KeyboardSizeSettingKind.Portrait
        }
}