package org.futo.inputmethod.latin

import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBar
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import kotlin.math.roundToInt

interface DynamicThemeProvider {
    val primaryKeyboardColor: Int

    val keyboardBackground: Drawable
    val keyBackground: Drawable
    val spaceBarBackground: Drawable

    val keyFeedback: Drawable

    val moreKeysKeyboardBackground: Drawable
    val popupKey: Drawable

    @ColorInt
    fun getColor(i: Int): Int?

    fun getDrawable(i: Int): Drawable?

    companion object {
        @ColorInt
        fun getColorOrDefault(i: Int, @ColorInt default: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Int {
            return (provider?.getColor(i)) ?: keyAttr.getColor(i, default)
        }

        fun getDrawableOrDefault(i: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Drawable? {
            return (provider?.getDrawable(i)) ?: keyAttr.getDrawable(i)
        }
    }
}

// TODO: Expand the number of drawables this provides so it covers the full theme, and
// build some system to dynamically change these colors
class BasicThemeProvider(val context: Context, val overrideColorScheme: ColorScheme? = null) : DynamicThemeProvider {
    override val primaryKeyboardColor: Int

    override val keyboardBackground: Drawable
    override val keyBackground: Drawable
    override val spaceBarBackground: Drawable

    override val keyFeedback: Drawable

    override val moreKeysKeyboardBackground: Drawable
    override val popupKey: Drawable

    private val colors: HashMap<Int, Int> = HashMap()
    override fun getColor(i: Int): Int? {
        return colors[i]
    }


    private val drawables: HashMap<Int, Drawable> = HashMap()
    override fun getDrawable(i: Int): Drawable? {
        return drawables[i]
    }

    private fun dp(dp: Dp): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.value,
            context.resources.displayMetrics
        );
    }

    private fun coloredRectangle(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
        }
    }

    private fun coloredRoundedRectangle(@ColorInt color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun coloredOval(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = Float.MAX_VALUE
            setColor(color)
        }
    }

    private fun StateListDrawable.addStateWithHighlightLayerOnPressed(@ColorInt highlight: Int, stateSet: IntArray, drawable: Drawable) {
        addState(intArrayOf(android.R.attr.state_pressed) + stateSet, LayerDrawable(arrayOf(
            drawable,
            coloredRoundedRectangle(highlight, dp(8.dp))
        )))
        addState(stateSet, drawable)
    }

    init {
        val colorScheme = if(overrideColorScheme != null) {
            overrideColorScheme
        }else if(!DynamicColors.isDynamicColorAvailable()) {
            DarkColorScheme
        } else {
            val dCtx = DynamicColors.wrapContextIfAvailable(context)

            dynamicLightColorScheme(dCtx)
        }


        val primary = colorScheme.primary.toArgb()
        val secondary = colorScheme.secondary.toArgb()
        val highlight = colorScheme.outline.copy(alpha = 0.33f).toArgb()

        val background = colorScheme.surface.toArgb()
        val surface = colorScheme.background.toArgb()
        val outline = colorScheme.outline.toArgb()

        val onSecondary = colorScheme.onSecondary.toArgb()
        val onBackground = colorScheme.onBackground.toArgb()
        val onBackgroundHalf = colorScheme.onBackground.copy(alpha = 0.5f).toArgb()

        val transparent = Color.TRANSPARENT

        colors[R.styleable.Keyboard_Key_keyTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyTextShadowColor] = 0
        colors[R.styleable.Keyboard_Key_functionalTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyHintLetterColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyHintLabelColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyPreviewTextColor] = onSecondary
        colors[R.styleable.MainKeyboardView_languageOnSpacebarTextColor] = onBackgroundHalf

        drawables[R.styleable.Keyboard_iconDeleteKey] = AppCompatResources.getDrawable(context, R.drawable.delete)!!.apply {
            setTint(onBackground)
        }
        drawables[R.styleable.Keyboard_iconLanguageSwitchKey] = AppCompatResources.getDrawable(context, R.drawable.globe)!!.apply {
            setTint(onBackground)
        }

        drawables[R.styleable.Keyboard_iconShiftKey] = AppCompatResources.getDrawable(context, R.drawable.shift)!!.apply {
            setTint(onBackground)
        }

        drawables[R.styleable.Keyboard_iconShiftKeyShifted] = AppCompatResources.getDrawable(context, R.drawable.shiftshifted)!!.apply {
            setTint(onBackground)
        }

        primaryKeyboardColor = background

        keyboardBackground = coloredRectangle(background)

        keyBackground = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_active),
                coloredRoundedRectangle(primary, dp(8.dp)).apply {
                    setSize(dp(64.dp).toInt(), dp(48.dp).toInt())
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable, android.R.attr.state_checked),
                coloredRoundedRectangle(colorScheme.secondaryContainer.toArgb(), dp(8.dp))
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable),
                coloredRectangle(transparent)
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_empty),
                coloredRectangle(transparent)
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                coloredRectangle(transparent)
            )
        }

        spaceBarBackground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                LayerDrawable(arrayOf(
                    coloredRoundedRectangle(highlight, dp(32.dp)),
                    coloredRoundedRectangle(highlight, dp(32.dp))
                ))
            )
            addState(intArrayOf(),
                coloredRoundedRectangle(highlight, dp(32.dp))
            )
        }

        keyFeedback = ShapeDrawable().apply {
            paint.color = secondary
            shape = RoundRectShape(floatArrayOf(
                dp(8.dp),dp(8.dp),dp(8.dp),dp(8.dp),
                dp(8.dp),dp(8.dp),dp(8.dp),dp(8.dp),
            ), null, null)

            intrinsicWidth = dp(48.dp).roundToInt()
            intrinsicHeight = dp(24.dp).roundToInt()

            setPadding(0, 0, 0, dp(50.dp).roundToInt())
        }

        moreKeysKeyboardBackground = coloredRoundedRectangle(surface, dp(8.dp))
        popupKey = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                coloredRoundedRectangle(surface, dp(8.dp))
            )
        }
    }

}

interface DynamicThemeProviderOwner {
    fun getDrawableProvider(): DynamicThemeProvider
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val THEME_KEY = stringPreferencesKey("activeThemeOption")

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}


class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, LatinIMELegacy.SuggestionStripController, DynamicThemeProviderOwner,
    KeyboardManagerForAction {
    private var activeColorScheme = DarkColorScheme
    private var colorSchemeLoaderJob: Job? = null

    private var drawableProvider: DynamicThemeProvider? = null
    override fun getDrawableProvider(): DynamicThemeProvider {
        if(drawableProvider == null) {
            if(colorSchemeLoaderJob != null && !colorSchemeLoaderJob!!.isCompleted) {
                runBlocking {
                    colorSchemeLoaderJob!!.join()
                }
            }
            drawableProvider = BasicThemeProvider(this, activeColorScheme)
        }

        return drawableProvider!!
    }

    private fun recreateKeyboard() {
        legacyInputView = latinIMELegacy.onCreateInputView()
        latinIMELegacy.loadKeyboard()
    }

    private fun updateDrawableProvider(colorScheme: ColorScheme) {
        activeColorScheme = colorScheme
        drawableProvider = BasicThemeProvider(this, overrideColorScheme = colorScheme)

        // recreate the keyboard if not in action window, if we are in action window then
        // it'll be recreated when we exit
        if(currWindowAction == null) recreateKeyboard()

        window.window?.navigationBarColor = drawableProvider!!.primaryKeyboardColor
        setContent()
    }

    private val latinIMELegacy = LatinIMELegacy(
        this as InputMethodService,
        this as LatinIMELegacy.SuggestionStripController
    )

    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    private val mLifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle
        get() = mLifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore
        get() = store

    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        mLifecycleRegistry.handleLifecycleEvent(event)

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate() {
        super.onCreate()

        colorSchemeLoaderJob = lifecycleScope.launch {
            var themeKey = this@LatinIME.getSetting(THEME_KEY, DynamicSystemTheme.key)
            var themeOption = ThemeOptions[themeKey]
            if(themeOption == null || !themeOption.available(this@LatinIME)) {
                themeKey = VoiceInputTheme.key
                themeOption = ThemeOptions[themeKey]!!
            }

            activeColorScheme = themeOption.obtainColors(this@LatinIME)
        }

        mSavedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        latinIMELegacy.onCreate()
    }

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

    override fun onDestroy() {
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

    private var currWindowAction: Action? = null
    private fun onActionActivated(action: Action) {
        if(action.windowImpl != null) {
            enterActionWindowView(action)
        } else if(action.simplePressImpl != null) {
            action.simplePressImpl.invoke(this)
        } else {
            throw IllegalStateException("An action must have either a window implementation or a simple press implementation")
        }
    }

    private var inputViewHeight: Int = -1
    private var shouldShowSuggestionStrip: Boolean = true
    private var suggestedWords: SuggestedWords? = null

    @Composable
    private fun LegacyKeyboardView() {
        key(legacyInputView) {
            AndroidView(factory = {
                legacyInputView!!
            }, update = { }, modifier = Modifier.onSizeChanged {
                inputViewHeight = it.height
            })
        }
    }

    @Composable
    private fun MainKeyboardViewWithActionBar() {
        Column {
            if (shouldShowSuggestionStrip) {
                ActionBar(
                    suggestedWords,
                    latinIMELegacy,
                    onActionActivated = { onActionActivated(it) }
                )
            }
            LegacyKeyboardView()
        }
    }

    private fun enterActionWindowView(action: Action) {
        assert(action.windowImpl != null)
        currWindowAction = action

        setContent()
    }

    private fun returnBackToMainKeyboardViewFromAction() {
        assert(currWindowAction != null)
        currWindowAction = null

        // Keyboard acts buggy in many ways after being detached from window then attached again,
        // so let's recreate it
        recreateKeyboard()

        setContent()
    }

    @Composable
    private fun ActionViewWithHeader(action: Action) {
        val windowImpl = action.windowImpl!!
        Column {
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(40.dp), color = MaterialTheme.colorScheme.background)
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

                    Text(windowImpl.windowName(), style = Typography.titleMedium, modifier = Modifier.align(CenterVertically))
                }
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { inputViewHeight.toDp() })) {
                windowImpl.WindowContents(manager = this@LatinIME)
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
                        when {
                            currWindowAction != null -> ActionViewWithHeader(currWindowAction!!)
                            else -> MainKeyboardViewWithActionBar()
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

        if(composeView != null) {
            latinIMELegacy.setComposeInputView(composeView)
        }

        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)

        if(composeView != null) {
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
        super.onStartInputView(info, restarting)
        latinIMELegacy.onStartInputView(info, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        latinIMELegacy.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        latinIMELegacy.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        latinIMELegacy.onWindowShown()

        // TODO: Check here if the dynamic color scheme has changed, reset and rebuild if so
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        latinIMELegacy.onWindowHidden()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        latinIMELegacy.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
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
        if (legacyInputView == null) {
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
        return latinIMELegacy.onShowInputRequested(flags, configChange) || super.onShowInputRequested(flags, configChange)
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

    override fun triggerSystemVoiceInput() {
        latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        );
    }

    override fun updateTheme(newTheme: ThemeOption) {
        assert(newTheme.available(this))
        updateDrawableProvider(newTheme.obtainColors(this))

        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                setSetting(THEME_KEY, newTheme.key)
            }
        }
    }
}