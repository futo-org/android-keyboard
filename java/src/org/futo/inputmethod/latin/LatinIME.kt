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
import android.widget.Toast
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.futo.inputmethod.engine.ExpandableSuggestionBarConfiguration
import org.futo.inputmethod.engine.IMEManager
import org.futo.inputmethod.engine.general.WordLearner
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.ActiveSubtype
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
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.Subtypes
import org.futo.inputmethod.latin.uix.SubtypesSetting
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

val HideKeyboardWhenHardKeyboardConnected = SettingsKey(
    booleanPreferencesKey("hideKeyboardWhenHardKeyboardConnected"),
    false
)

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
                latinIMELegacy.mKeyboardSwitcher.keyboard?.mId?.mKeyboardLayoutSetName,
                null
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

    private fun onSizeMaybeUpdated() {
        if(sizingCalculator.didMaybeChange(size.value)) {
            onSizeUpdated()
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

        UserBinaryDictionary.resetUserDictionariesRequiringRecreation()

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
                    withContext(Dispatchers.Main) {
                        changeSubtype(activeSubtype)
                    }
                }
            }

            onNewSubtype(getSetting(ActiveSubtype))

            dataStore.data.collect {
                onNewSubtype(it[ActiveSubtype.key] ?: ActiveSubtype.default)
            }
        }

        launchJob {
            getSettingFlow(SubtypesSetting).collect {
                Subtypes.updateLanguageOnSpaceBarVisibility(this@LatinIME)
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
                prev.keys
