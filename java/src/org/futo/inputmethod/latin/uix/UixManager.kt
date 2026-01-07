package org.futo.inputmethod.latin.uix

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.AudioAndHapticFeedbackManager
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.FoldingOptions
import org.futo.inputmethod.latin.LanguageSwitcherDialog
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SupportsNavbarExtension
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewListener
import org.futo.inputmethod.latin.uix.actions.ActionEditor
import org.futo.inputmethod.latin.uix.actions.ActionRegistry
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.BugViewerAction
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.actions.KeyboardModeAction
import org.futo.inputmethod.latin.uix.actions.PersistentEmojiState
import org.futo.inputmethod.latin.uix.actions.keyCode
import org.futo.inputmethod.latin.uix.actions.keyCodeAlt
import org.futo.inputmethod.latin.uix.resizing.KeyboardResizers
import org.futo.inputmethod.latin.uix.settings.DataStoreCacheProvider
import org.futo.inputmethod.latin.uix.settings.pages.ActionBarDisplayedSetting
import org.futo.inputmethod.latin.uix.settings.pages.InlineAutofillSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.KeyboardSurfaceShaderBackground
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.updates.autoDeferManualUpdateIfNeeded
import org.futo.inputmethod.updates.deferManualUpdate
import org.futo.inputmethod.updates.isManualUpdateTimeExpired
import org.futo.inputmethod.updates.openManualUpdateCheck
import org.futo.inputmethod.updates.retrieveSavedLastUpdateCheckResult
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize
import org.futo.inputmethod.v2keyboard.opposite
import java.util.Locale
import kotlin.math.roundToInt

val LocalManager = staticCompositionLocalOf<KeyboardManagerForAction> {
    error("No LocalManager provided")
}

val LocalThemeProvider = compositionLocalOf<DynamicThemeProvider> {
    error("No LocalThemeProvider provided")
}

val LocalFoldingState = compositionLocalOf<FoldingOptions> {
    FoldingOptions(null)
}

val LocalNavController = compositionLocalOf<NavHostController?> {
    null
}

private val UixLocaleFollowsSubtypeLocale = true

@Composable
fun navBarHeight(): Dp = with(LocalDensity.current) {
    if(SupportsNavbarExtension) {
        WindowInsets.systemBars.getBottom(this).toDp()
    } else {
        0.dp
    }
}


data class KeyboardPadding(
    val left: Dp,
    val right: Dp
)

val LocalKeyboardPadding = compositionLocalOf {
    KeyboardPadding(0.dp, 0.dp)
}

@Composable
fun Modifier.safeKeyboardPadding(): Modifier {
    val padding = LocalKeyboardPadding.current

    return this.absolutePadding(left = padding.left.coerceAtLeast(0.dp), right = padding.right.coerceAtLeast(0.dp))
}

@Composable
fun Modifier.keyboardBottomPadding(size: ComputedKeyboardSize): Modifier = with(LocalDensity.current) {
    this@keyboardBottomPadding.absolutePadding(bottom = size.padding.bottom.toDp())
}

@Composable
fun BoxScope.KeyboardBackground(
    colorScheme: KeyboardColorScheme,
    computedSize: ComputedKeyboardSize? = null,
    useThumbnail: Boolean = false,
) {
    val backgroundBrush = colorScheme.keyboardBackgroundGradient ?: SolidColor(colorScheme.keyboardSurface)
    val advanced = colorScheme.extended.advancedThemeOptions

    val shader = advanced.backgroundShader
    val image = if(useThumbnail) advanced.thumbnailImage else advanced.backgroundImage
    val rect = advanced.backgroundImageVisibleArea?.let {
        if(useThumbnail) {
            Rect(
                (it.left * advanced.thumbnailScale).roundToInt(),
                (it.top * advanced.thumbnailScale).roundToInt(),
                (it.right * advanced.thumbnailScale).roundToInt(),
                (it.bottom * advanced.thumbnailScale).roundToInt()
            )
        } else it
    }

    when {
        shader != null -> KeyboardSurfaceShaderBackground(shader, modifier = Modifier.matchParentSize())
        image != null && rect != null -> {
            val navbarHeight = navBarHeight()
            Canvas(Modifier.matchParentSize()) {
                drawRect(colorScheme.keyboardSurface)

                val fixedWidth = computedSize?.width?.toFloat() ?: size.width
                val fixedHeight = when {
                    (computedSize != null) -> computedSize.height + ActionBarHeight.toPx() + navbarHeight.toPx()
                    else -> size.height
                }

                val canvasScale = maxOf(fixedWidth / rect.width(), fixedHeight / rect.height())

                val offset = Offset(
                    -rect.left*1f - rect.width()/2f + fixedWidth/2f/canvasScale,
                    -rect.top*1f - rect.height()/2f + fixedHeight/2f/canvasScale
                )

                translate(0f, size.height - fixedHeight) {
                    scale(canvasScale, pivot = Offset.Zero) {
                        translate(offset.x, offset.y) {
                            drawImage(image)
                        }
                    }
                }
            }

            Box(Modifier.matchParentSize().background(backgroundBrush))
        }
        else -> Box(Modifier.background(backgroundBrush).matchParentSize())
    }
}

class UixActionKeyboardManager(val uixManager: UixManager, val latinIME: LatinIME) : KeyboardManagerForAction {
    override fun getContext(): Context {
        return latinIME
    }

    override fun getLifecycleScope(): LifecycleCoroutineScope {
        return latinIME.lifecycleScope
    }

    override fun createInputTransaction(): ActionInputTransaction {
        return latinIME.imeManager.createInputTransaction()
    }

    override fun typeText(v: String) {
        if(latinIME.isInputConnectionOverridden) {
            latinIME.getBaseInputConnection()?.commitText(v, 1)
        } else {
            latinIME.latinIMELegacy.onTextInput(v)
        }
    }

    override fun typeTextSurroundedByWhitespace(v: String) {
        if(latinIME.isInputConnectionOverridden) {
            latinIME.getBaseInputConnection()?.commitText(v, 1)
        } else {
            latinIME.latinIMELegacy.onTextInputWithSpace(v)
        }
    }

    override fun typeUri(
        uri: Uri,
        mimeTypes: List<String>,
        ignoreConnectionOverride: Boolean
    ): Boolean {
        if(mimeTypes.isEmpty()) {
            Log.w("UixManager", "mimeTypes is empty")
            return false
        }

        val inputConnection = if(ignoreConnectionOverride) {
            latinIME.getBaseInputConnection()
        } else {
            latinIME.currentInputConnection
        }

        val editorInfo = if(ignoreConnectionOverride) {
            latinIME.getBaseInputEditorInfo()
        } else {
            latinIME.currentInputEditorInfo
        }

        if(inputConnection == null || editorInfo == null) return false

        latinIME.grantUriPermission(
            editorInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val description = ClipDescription("", mimeTypes.toTypedArray())

        return InputConnectionCompat.commitContent(
            inputConnection,
            editorInfo,
            InputContentInfoCompat(uri, description, null),
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null
        ).let { result ->
            if(!result) {
                val toast = Toast.makeText(
                    latinIME,
                    latinIME.getString(R.string.action_clipboard_manager_error_app_image_insertion_unsupported),
                    Toast.LENGTH_SHORT
                )
                toast.show()
            }

            result
        }
    }

    private fun mimeTypeMatches(mimeTypePattern: String, schema: String): Boolean {
        if (mimeTypePattern == schema) return true
        val patternParts = mimeTypePattern.split('/')
        val schemaParts = schema.split('/')
        if (patternParts.size != 2 || schemaParts.size != 2) return false
        return (patternParts[0] == schemaParts[0] &&
                (patternParts[1] == "*" || patternParts[1] == schemaParts[1]))
    }

    override fun appSupportsImageInsertion(
        schema: String,
        ignoreConnectionOverride: Boolean
    ): Boolean {
        val editorInfo = if(ignoreConnectionOverride) {
            latinIME.getBaseInputEditorInfo()
        } else {
            latinIME.currentInputEditorInfo
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            editorInfo?.contentMimeTypes?.any { mimeTypeMatches(it, schema) } == true
        } else {
            false
        }
    }

    override fun backspace(amount: Int) {
        for(i in 0 until amount) {
            latinIME.latinIMELegacy.onCodeInput(
                Constants.CODE_DELETE,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE, false
            )
        }
    }

    override fun closeActionWindow() {
        uixManager.closeActionWindow()
    }

    override fun forceActionWindowAboveKeyboard(to: Boolean) {
        uixManager.toggleExpandAction(to)
    }

    override fun triggerSystemVoiceInput() {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        )
    }

    override fun getThemeProvider(): DynamicThemeProvider {
        return latinIME.getDrawableProvider()
    }

    override fun sendCodePointEvent(codePoint: Int) {
        latinIME.latinIMELegacy.onCodeInput(codePoint,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun sendKeyEvent(keyCode: Int, metaState: Int) {
        val event = Event.createDownUpKeyEvent(keyCode, metaState)
        latinIME.imeManager.getActiveIME(
            Settings.getInstance().current
        ).onEvent(event)
        //latinIME.inputLogic.sendDownUpKeyEvent(keyCode, metaState)
    }

    override fun isShifted(): Boolean = latinIME.latinIMELegacy.mKeyboardSwitcher.mState.shifted

    override fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.imeManager.getActiveIME(
            Settings.getInstance().current
        ).onMovePointer(-1, stepOverWords, select)
        //latinIME.inputLogic.cursorLeft(steps, stepOverWords, select)
    }

    override fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.imeManager.getActiveIME(
            Settings.getInstance().current
        ).onMovePointer( 1, stepOverWords, select)
        //latinIME.inputLogic.cursorRight(steps, stepOverWords, select)
    }

    override fun performHapticAndAudioFeedback(code: Int, view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(code, view)
    }

    override fun getActiveLocales(): List<Locale> {
        return RichInputMethodManager.getInstance().currentSubtypeLocales
    }

    override fun overrideInputConnection(inputConnection: InputConnection, editorInfo: EditorInfo) {
        latinIME.overrideInputConnection(inputConnection, editorInfo)
        uixManager.toggleExpandAction(true)
        uixManager.isInputOverridden.value = true
    }

    override fun unsetInputConnection() {
        latinIME.overrideInputConnection(null, null)
        uixManager.isInputOverridden.value = false
    }

    override fun requestDialog(text: String, options: List<DialogRequestItem>, onCancel: () -> Unit) {
        uixManager.activeDialogRequest.value = ActiveDialogRequest(text, options, onCancel)
        uixManager.activeDialogRequestDismissed.value = false
    }

    override fun openInputMethodPicker() {
        uixManager.showLanguageSwitcher()
    }

    override fun announce(s: String) {
        AccessibilityUtils.init(getContext())
        if(AccessibilityUtils.getInstance().isAccessibilityEnabled && uixManager.composeView != null) {
            AccessibilityUtils.getInstance().announceForAccessibility(uixManager.composeView, s)
        }
    }

    override fun activateAction(action: Action) {
        uixManager.onActionActivated(action)
    }

    override fun showActionEditor() {
        uixManager.showActionEditor()
    }

    override fun showResizer() {
        uixManager.resizers.displayResizer()

        if(uixManager.currWindowAction != KeyboardModeAction) {
            uixManager.closeActionWindow()
        }
    }

    override fun getTutorialMode(): TutorialMode {
        return uixManager.tutorialMode
    }

    override fun setTutorialArrowPosition(coordinates: LayoutCoordinates) {
        uixManager.tutorialArrowPosition.value = coordinates
    }

    override fun markTutorialCompleted() {
        uixManager.tutorialCompleted.value = true
    }

    override fun isDeviceLocked(): Boolean {
        return getContext().isDeviceLocked
    }

    override fun overrideKeyboardTypeface(typeface: Typeface?) {
        if(latinIME.getDrawableProvider().typefaceOverride == typeface) return
        latinIME.getDrawableProvider().typefaceOverride = typeface
        latinIME.invalidateKeyboard()
    }

    override fun copyToClipboard(cut: Boolean) {
        if(cut) {
            sendKeyEvent(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON)
        } else {
            sendKeyEvent(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)
        }
    }

    override fun pasteFromClipboard() {
        sendKeyEvent(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
        uixManager.dismissQuickClips()
    }

    override fun getSizingCalculator(): KeyboardSizingCalculator =
        latinIME.sizingCalculator

    override fun getLatinIMEForDebug(): LatinIME = latinIME

    override fun <T : IMEInterface> getIMEInterface(clazz: Class<T>): T? {
        if(clazz == IMEInterface::class.java) throw IllegalArgumentException("Please specify a specific IMEInterface")

        val ime = latinIME.imeManager.getActiveIME(Settings.getInstance().current)
        return if(clazz.isInstance(ime)) clazz.cast(ime) else null
    }
}

data class ActiveDialogRequest(
    val text: String,
    val options: List<DialogRequestItem>,
    val onCancel: () -> Unit
)

@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This is for debug purposes only.")
@Retention(AnnotationRetention.BINARY)
annotation class DebugOnly

@DebugOnly
var UixManagerInstanceForDebug: UixManager? = null

class UixManager(private val latinIME: LatinIME) {
    init {
        @OptIn(DebugOnly::class)
        UixManagerInstanceForDebug = this
    }

    internal val composeView: ComposeView?
        get() = latinIME.composeView

    private val shouldShowSuggestionStrip = mutableStateOf(true)
    private val suggestedWords: MutableState<SuggestedWords?> = mutableStateOf(null)
    private val useExpandableSuggestionsUi: MutableState<Boolean> = mutableStateOf(false)

    var currWindowAction: MutableState<Action?> = mutableStateOf(null)
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()

    private var inlineSuggestions: MutableState<List<MutableState<View?>>> = mutableStateOf(emptyList())
    private val keyboardManagerForAction = UixActionKeyboardManager(this, latinIME)

    private var mainKeyboardHidden = mutableStateOf(false)

    private var numSuggestionsSinceNotice = 0
    private var currentNotice: MutableState<ImportantNotice?> = mutableStateOf(null)

    private var inlineStuffHiddenByTyping = mutableStateOf(false)

    private var isActionsExpanded = mutableStateOf(false)
    private fun toggleActionsExpanded() {
        isActionsExpanded.value = !isActionsExpanded.value
        latinIME.deferSetSetting(latinIME, ActionBarExpanded, isActionsExpanded.value)
    }


    val actionsExpanded: Boolean
        get() = isActionsExpanded.value

    internal val resizers = KeyboardResizers(latinIME)

    private var isShowingActionEditor = mutableStateOf(false)
    fun showActionEditor() {
        isShowingActionEditor.value = true
    }

    val foldingOptions = mutableStateOf(FoldingOptions(null))

    var isInputOverridden = mutableStateOf(false)

    var currWindowActionWindow: MutableState<ActionWindow?> = mutableStateOf(null)

    private var measuredTouchableHeight = 0
    val touchableHeight: Int
        get() = measuredTouchableHeight

    val isMainKeyboardHidden get() = mainKeyboardHidden.value

    private fun onActionActivatedInternal(rawAction: Action) {
        resizers.hideResizer()

        val action = runBlocking {
            ActionRegistry.getActionOverride(latinIME, rawAction)
        }

        if (action.windowImpl != null) {
            enterActionWindowView(action)
        } else if (action.simplePressImpl != null) {
            action.simplePressImpl.invoke(keyboardManagerForAction, persistentStates[action])
        } else {
            throw IllegalStateException("An action must have either a window implementation or a simple press implementation")
        }
    }

    private fun onActionAltActivatedInternal(rawAction: Action) {
        val action = runBlocking {
            ActionRegistry.getActionOverride(latinIME, rawAction)
        }

        action.altPressImpl?.invoke(keyboardManagerForAction, persistentStates[action])
    }

    fun onActionActivated(rawAction: Action) {
        val event = Event.createSoftwareKeypressEvent(Constants.NOT_A_CODE, rawAction.keyCode, 0, 0, false)
        latinIME.imeManager.getActiveIME(
            Settings.getInstance().current
        ).onEvent(event)
    }

    fun onActionAltActivated(rawAction: Action) {
        val event = Event.createSoftwareKeypressEvent(Constants.NOT_A_CODE, rawAction.keyCodeAlt, 0, 0, false)
        latinIME.imeManager.getActiveIME(
            Settings.getInstance().current
        ).onEvent(event)
    }

    @Composable
    private fun MainKeyboardViewWithActionBar(
        needToUseExpandableSuggestionUi: Boolean
    ) {
        val view = LocalView.current

        val actionBarShown = useDataStore(ActionBarDisplayedSetting)

        Column {
            // Don't show suggested words when it's not meant to be shown
            val suggestedWordsOrNull = if(shouldShowSuggestionStrip.value) {
                suggestedWords.value
            } else {
                null
            }

            val inlineSuggestions = run {
                if(!inlineStuffHiddenByTyping.value) inlineSuggestions.value else emptyList()
            }

            if(actionBarShown.value || inlineSuggestions.isNotEmpty()) {
                ActionBar(
                    suggestedWordsOrNull,
                    latinIME.latinIMELegacy as SuggestionStripViewListener,
                    inlineSuggestions = inlineSuggestions,
                    onActionActivated = {
                        keyboardManagerForAction.performHapticAndAudioFeedback(
                            Constants.CODE_TAB,
                            view
                        )
                        onActionActivated(it)
                    },
                    onActionAltActivated = {
                        if (it.altPressImpl != null) {
                            keyboardManagerForAction.performHapticAndAudioFeedback(
                                Constants.CODE_TAB,
                                view
                            )
                        }
                        onActionAltActivated(it)
                    },
                    importantNotice = currentNotice.value,
                    keyboardManagerForAction = keyboardManagerForAction,
                    isActionsExpanded = isActionsExpanded.value,
                    toggleActionsExpanded = { toggleActionsExpanded() },
                    quickClipState = run {
                        if(!inlineStuffHiddenByTyping.value) quickClipState.value else null
                    },
                    onQuickClipDismiss = { quickClipState.value = null },
                    needToUseExpandableSuggestionUi = needToUseExpandableSuggestionUi
                )
            }
        }
    }

    private fun enterActionWindowView(action: Action) {
        assert(action.windowImpl != null)

        currWindowAction.value = action

        if (persistentStates[action] == null) {
            persistentStates[action] = action.persistentState?.let { it(keyboardManagerForAction) }
        }

        currWindowActionWindow.value = (action.windowImpl!!)(keyboardManagerForAction, persistentStates[action])

        mainKeyboardHidden.value = currWindowActionWindow.value?.onlyShowAboveKeyboard == false

        if(action.keepScreenAwake) {
            latinIME.window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        keyboardManagerForAction.announce(
            latinIME.getString(
                R.string.action_menu_opened,
                latinIME.resources.getString(action.name)
            ))
    }

    private fun returnBackToMainKeyboardViewFromAction(allowSkipClosing: Boolean): Boolean {
        if(currWindowActionWindow.value == null) return true

        val name = latinIME.resources.getString(currWindowAction.value!!.name)

        if(currWindowActionWindow.value!!.close() == CloseResult.PreventClosing
            && allowSkipClosing
        ) {
            return false
        }

        currWindowAction.value = null
        currWindowActionWindow.value = null

        mainKeyboardHidden.value = false

        latinIME.onKeyboardShown()

        latinIME.window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        keyboardManagerForAction.announce(latinIME.getString(R.string.action_menu_closed, name))
        return true
    }

    fun toggleExpandAction(to: Boolean? = null) {
        mainKeyboardHidden.value = !(to ?: mainKeyboardHidden.value)
        if(!mainKeyboardHidden.value) {
            latinIME.onKeyboardShown()
        }
    }

    @Composable
    private fun ActionViewWithHeader(windowImpl: ActionWindow,
                                     needToUseExpandableSuggestionUi: Boolean) {
        val heightDiv = if(mainKeyboardHidden.value) {
            1
        } else {
            1.5
        }

        val showingAboveKeyboard = !mainKeyboardHidden.value
        Column {
            Column(
                Modifier.background(
                    if (showingAboveKeyboard) {
                        LocalKeyboardScheme.current.keyboardSurfaceDim
                    } else {
                        Color.Transparent
                    }
                )
            ) {
                if (mainKeyboardHidden.value || isInputOverridden.value) {
                    ActionWindowBar(
                        onBack = { closeActionWindow(true) },
                        canExpand = currWindowAction.value!!.canShowKeyboard,
                        onExpand = { toggleExpandAction() },
                        windowTitleBar = { windowImpl.WindowTitleBar(this) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) {
                            currWindowActionWindow.value?.fixedWindowHeight ?: ((latinIME
                                .getInputViewHeight()
                                .toFloat() / heightDiv.toFloat()).toDp() +
                                    if (actionsExpanded) ActionBarHeight else 0.dp)
                        })
                        .safeKeyboardPadding()
                ) {
                    windowImpl.WindowContents(keyboardShown = !mainKeyboardHidden.value)
                }
            }

            if((!mainKeyboardHidden.value && !latinIME.isInputConnectionOverridden)
                || (latinIME.inputConnectionOverridenWithSuggestions)) {
                val suggestedWordsOrNull = if (shouldShowSuggestionStrip.value) {
                    suggestedWords.value
                } else {
                    null
                }

                if(!needToUseExpandableSuggestionUi) {
                    CollapsibleSuggestionsBar(
                        onCollapse = { toggleExpandAction() },
                        onClose = { closeActionWindow() },
                        words = suggestedWordsOrNull,
                        showClose = currWindowActionWindow.value?.showCloseButton == true,
                        showCollapse = currWindowActionWindow.value?.positionIsUserManagable == true,
                        suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripViewListener
                    )
                }
            } else if(showingAboveKeyboard && !needToUseExpandableSuggestionUi) {
                ActionSep()
                Spacer(Modifier.height(1.dp))
            }
        }
    }

    val activeDialogRequest: MutableState<ActiveDialogRequest?> = mutableStateOf(null)
    val activeDialogRequestDismissed: MutableState<Boolean> = mutableStateOf(true)

    @Composable
    fun BoxScope.ForgetWordDialog() {
        AnimatedVisibility(
            visible = !activeDialogRequestDismissed.value,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (activeDialogRequest.value != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.66f),
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                this.detectTapGestures(onPress = {
                                    activeDialogRequestDismissed.value = true
                                    activeDialogRequest.value?.onCancel?.invoke()
                                })
                            }
                    ) { }

                    Box(modifier = Modifier
                        .matchParentSize()
                        .padding(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    activeDialogRequest.value?.text ?: "",
                                    style = Typography.Body.Medium
                                )

                                Row {
                                    activeDialogRequest.value?.options?.forEach {
                                        TextButton(
                                            onClick = {
                                                it.onClick()
                                                activeDialogRequestDismissed.value = true
                                            }
                                        ) {
                                            Text(it.option)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var languageSwitcherDialog: DialogComposeView? = null
    fun showLanguageSwitcher() {
        // Dismiss old dialog
        languageSwitcherDialog?.dismiss()

        // Create new dialog
        languageSwitcherDialog = createDialogComposeView(latinIME) { dialog ->
            DataStoreCacheProvider {
                UixThemeAuto {
                    LanguageSwitcherDialog(
                        onDismiss = { dialog.dismiss() },
                        switchToIme = {
                            latinIME.lifecycleScope.launch(Dispatchers.Main) {
                                latinIME.switchInputMethod(it.id)
                                dialog.dismiss()
                            }
                        }
                    )
                }
            }
        }

        languageSwitcherDialog?.show()
    }

    @Composable
    fun ActionEditorHost() {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = navBarHeight()), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = isShowingActionEditor.value,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                content = {
                    ActionEditor()
                },
            )
        }
    }

    @Composable
    fun InputDarkener(darken: Boolean, onClose: () -> Unit) {
        val color by animateColorAsState(
            if (darken) Color.Black.copy(alpha = 0.25f) else Color.Transparent
        )

        LaunchedEffect(darken) {
            latinIME.setInputModal(darken)
        }

        Box(Modifier
            .background(color)
            .fillMaxWidth()
            .fillMaxHeight()
            .then(
                if (darken) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            onClose()
                        }
                    }
                } else {
                    Modifier
                })
        )
    }

    @Composable
    private fun OffsetPositioner(offset: Offset, content: @Composable () -> Unit) {
        Column(modifier = Modifier
            .fillMaxHeight()
            .absoluteOffset { IntOffset(offset.x.toInt(), 0) }) {
            Spacer(Modifier.weight(1.0f))
            content()
            Spacer(Modifier.height(with(LocalDensity.current) { offset.y.toDp() }))
        }
    }

    @Composable
    private fun KeyboardSurface(
        requiredWidthPx: Int,
        backgroundColor: Color,
        modifier: Modifier = Modifier,
        shape: Shape = RectangleShape,
        padding: Rect = Rect(),
        content: @Composable BoxScope.() -> Unit
    ) = with(LocalDensity.current) {

        Box(modifier
            .onSizeChanged { measuredTouchableHeight = it.height }
            .requiredWidth(requiredWidthPx.toDp())
            .absolutePadding(
                //top = padding.top.toDp().coerceAtLeast(0.dp),
                //bottom = padding.bottom.toDp().coerceAtLeast(0.dp),
            )
            .clip(shape)
            .clipToBounds()
            // Blocks any input to inputDarkener within the keyboard
            .pointerInput(Unit) {}
        ) {
            KeyboardBackground(LocalKeyboardScheme.current, latinIME.size.value)

            CompositionLocalProvider(LocalKeyboardPadding provides KeyboardPadding(
                    left = padding.left.toDp().coerceAtLeast(0.dp),
                    right = padding.right.toDp().coerceAtLeast(0.dp),
                )
            ) {
                CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onBackground) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun FloatingKeyboardContents(
        pointerInputKey: Any?,
        onDragged: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) {
        // Content
        Box(Modifier.fillMaxWidth()) {
            content(4.dp)
        }

        // Bottom drag bar
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(pointerInputKey) {
                detectDragGestures(
                    onDrag = { _, dragAmount -> onDragged(dragAmount) },
                    onDragEnd = { onDragEnd() })
            }
        ) {
            IconButton(onClick = {
                onActionActivated(KeyboardModeAction)
            }, Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    painterResource(R.drawable.keyboard_gear),
                    contentDescription = stringResource(R.string.action_keyboard_modes_title),
                    tint = LocalKeyboardScheme.current.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .align(Alignment.Center)
                    .background(
                        LocalKeyboardScheme.current.onSurfaceVariant,
                        RoundedCornerShape(100)
                    )
            )
        }
    }

    var floatingPosition = Offset.Zero

    @Composable
    private fun FloatingKeyboardWindow(
        size: FloatingKeyboardSize,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) = with(LocalDensity.current) {
        val offset = remember(size) { mutableStateOf(Offset(size.bottomOrigin.first.toFloat(), size.bottomOrigin.second.toFloat())) }
        val shape = RoundedCornerShape(16.dp)

        OffsetPositioner(offset.value + Offset(0.0f, navBarHeight().toPx())) {
            KeyboardSurface(
                requiredWidthPx = size.width,
                backgroundColor = latinIME.keyboardColor,
                shape = shape,
                padding = size.padding,
                modifier = Modifier.onGloballyPositioned {
                    floatingPosition = it.positionInWindow()
                }
            ) {
                Column {
                    FloatingKeyboardContents(
                        pointerInputKey = size,
                        onDragged = { dragAmount ->
                            var newOffset = offset.value.copy(
                                x = offset.value.x + dragAmount.x,
                                y = offset.value.y - dragAmount.y
                            )

                            // Ensure we are not out of bounds
                            newOffset = newOffset.copy(
                                newOffset.x.coerceAtLeast(0.0f),
                                newOffset.y.coerceAtLeast(0.0f)
                            )
                            newOffset = newOffset.copy(
                                newOffset.x.coerceAtMost(
                                    latinIME.getViewWidth().toFloat() - size.width
                                ),
                                newOffset.y.coerceAtMost(
                                    latinIME.getViewHeight().toFloat() - measuredTouchableHeight
                                )
                            )

                            offset.value = newOffset
                        },
                        onDragEnd = {
                            latinIME.sizingCalculator.editSavedSettings { settings ->
                                settings.copy(
                                    floatingBottomOriginDp = Pair(
                                        offset.value.x.toDp().value,
                                        offset.value.y.toDp().value
                                    )
                                )
                            }
                        },
                        content = content
                    )
                }

                resizers.Resizer(this, size, shape)
            }
        }
    }

    @Composable
    private fun BoxScope.OneHandedOptions(size: OneHandedKeyboardSize) = with(LocalDensity.current) {
        Box(Modifier.matchParentSize()) {
            Column(modifier = Modifier
                .matchParentSize()
                .absolutePadding(
                    top = if (isActionsExpanded.value) ActionBarHeight else 0.dp
                ), horizontalAlignment = when(size.direction) {
                // Aligned opposite of the keyboard
                OneHandedDirection.Left -> Alignment.End
                OneHandedDirection.Right -> Alignment.Start
            }) {
                IconButton(onClick = {
                    latinIME.sizingCalculator.editSavedSettings {
                        it.copy(oneHandedDirection = it.oneHandedDirection.opposite)
                    }
                }) {
                    Icon(painterResource(when(size.direction) {
                        // Show opposite icon
                        OneHandedDirection.Left -> R.drawable.chevron_right
                        OneHandedDirection.Right -> R.drawable.chevron_left
                    }), contentDescription = stringResource(R.string.one_handed_mode_switch_hand)
                    )
                }

                Spacer(Modifier.weight(1.0f))

                IconButton(onClick = {
                    latinIME.sizingCalculator.exitOneHandedMode()
                }) {
                    Icon(painterResource(R.drawable.maximize), contentDescription = stringResource(R.string.one_handed_mode_exit))
                }

                Spacer(Modifier.height(navBarHeight()))
            }
        }
    }

    @Composable
    private fun NonFloatingKeyboardWindow(
        size: ComputedKeyboardSize,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) = with(LocalDensity.current) {
        OffsetPositioner(Offset.Zero) {
            KeyboardSurface(
                requiredWidthPx = size.width,
                backgroundColor = latinIME.keyboardColor,
                padding = size.padding
            ) {
                val paddingOverride = when(size) {
                    is OneHandedKeyboardSize -> {
                        val pad = LocalKeyboardPadding.current
                        val sidePad = (size.width - size.layoutWidth).toDp()
                        when(size.direction) {
                            OneHandedDirection.Left -> pad.copy(right = sidePad - pad.left)
                            OneHandedDirection.Right -> pad.copy(left = sidePad - pad.right)
                        }
                    }
                    else -> LocalKeyboardPadding.current
                }

                CompositionLocalProvider(LocalKeyboardPadding provides paddingOverride) {
                    content(size.padding.top.toDp().coerceAtLeast(4.dp))
                    resizers.Resizer(this, size)
                }

                if(size is OneHandedKeyboardSize) {
                    OneHandedOptions(size)
                }
            }
        }
    }

    @Composable
    fun KeyboardWindowSelector(content: @Composable BoxScope.(actionBarGap: Dp) -> Unit) {
        val size = latinIME.size.value
        when(size) {
            is FloatingKeyboardSize -> FloatingKeyboardWindow(size, content)

            is OneHandedKeyboardSize,
            is RegularKeyboardSize,
            is SplitKeyboardSize -> NonFloatingKeyboardWindow(size, content)

            null -> return
        }
    }

    var prevSize: IntSize = IntSize.Zero
    @Composable
    private fun ProvidersAndWrapper(content: @Composable () -> Unit) {
        UixThemeWrapper(latinIME.colorScheme) {
            DataStoreCacheProvider {
                CompositionLocalProvider(
                    LocalManager provides keyboardManagerForAction,
                    LocalThemeProvider provides latinIME.getDrawableProvider(),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalFoldingState provides foldingOptions.value
                ) {
                    Box(Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            // If the size changes, call the service to check if the size needs
                            // to be recalculated and keyboard recreated
                            if (it != prevSize) {
                                prevSize = it
                                latinIME.onSizeUpdated()
                            }
                        }) {
                        content()
                    }
                }
            }
        }
    }

    @Composable
    fun Content() {
        ProvidersAndWrapper {
            InputDarkener(isInputOverridden.value || isShowingActionEditor.value) {
                closeActionWindow()
                isShowingActionEditor.value = false
            }

            TutorialArrow()

            KeyboardWindowSelector { gap ->
                Column {
                    // TODO: Refactor how we handle expandable suggestions here to not be a mess
                    val needToUseExpandableSuggestionUi =
                        useExpandableSuggestionsUi.value && suggestedWords.value?.size()?.equals(0) != true
                                && mainKeyboardHidden.value == false
                                && (quickClipState.value == null || inlineStuffHiddenByTyping.value)
                                && currentNotice.value == null
                                && (inlineSuggestions.value.isEmpty() || inlineStuffHiddenByTyping.value)
                    when {
                        currWindowActionWindow.value != null -> ActionViewWithHeader(
                            currWindowActionWindow.value!!,
                            needToUseExpandableSuggestionUi
                        )

                        else -> MainKeyboardViewWithActionBar(
                            needToUseExpandableSuggestionUi
                        )
                    }

                    if(!needToUseExpandableSuggestionUi) {
                        Spacer(modifier = Modifier.height(gap))
                    }

                    val kbHeight = remember { mutableIntStateOf(latinIME.size.value!!.height) }
                    val keyboardViewOffset = remember(needToUseExpandableSuggestionUi) { mutableIntStateOf(0) }
                    Box(Modifier.let {
                        if(needToUseExpandableSuggestionUi) {
                            it.height(
                                with(LocalDensity.current) { kbHeight.intValue.toDp() }
                                        + latinIME.sizingCalculator.calculateSuggestionBarHeightDp().dp
                                        + gap
                            )
                        } else {
                            it
                        }
                    }) {
                        if(needToUseExpandableSuggestionUi) {
                            ActionBarWithExpandableCandidates(
                                suggestedWords.value,
                                latinIME.latinIMELegacy as SuggestionStripViewListener,
                                isActionsExpanded = isActionsExpanded.value,
                                toggleActionsExpanded = { toggleActionsExpanded() },
                                closeActionWindow = currWindowActionWindow.value?.let {{ closeActionWindow() }},
                                keyboardOffset = keyboardViewOffset,
                                keyboardHeight = (latinIME.size.value?.height ?: kbHeight.intValue) + with(LocalDensity.current) { navBarHeight().toPx().toInt() }
                            )
                        }
                        latinIME.LegacyKeyboardView(modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged {
                                // TODO: Is there a better way?
                                kbHeight.intValue = it.height.let {
                                    if(it > 0) {
                                        it
                                    } else {
                                        latinIME.size.value!!.height
                                    }
                                }
                            }.absoluteOffset { IntOffset(0, keyboardViewOffset.intValue) },
                            hidden = mainKeyboardHidden.value)
                    }

                    if(latinIME.size.value !is FloatingKeyboardSize) {
                        Spacer(Modifier.height(navBarHeight()))
                    }
                }

                ForgetWordDialog()
            }

            ActionEditorHost()
        }
    }

    fun setContent() {
        composeView?.setContent {
            Content()
        }
    }

    suspend fun showUpdateNoticeIfNeeded() {
        if(!BuildConfig.UPDATE_CHECKING) return

        autoDeferManualUpdateIfNeeded(latinIME)

        val updateInfo = retrieveSavedLastUpdateCheckResult(latinIME)
        if(updateInfo != null && updateInfo.isNewer()) {
            /*if(!latinIME.getSetting(DISABLE_UPDATE_REMINDER)) {
                numSuggestionsSinceNotice = 0
                currentNotice.value = object : ImportantNotice {
                    @Composable
                    override fun getText(): String {
                        return "Update available: ${updateInfo.nextVersionString}"
                    }

                    override fun onDismiss(context: Context) {
                        currentNotice.value = null
                    }

                    override fun onOpen(context: Context) {
                        currentNotice.value = null

                        val intent = Intent(context, SettingsActivity::class.java)
                        intent.putExtra("navDest", "update")

                        if (context !is Activity) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        context.startActivity(intent)
                    }
                }
            }*/
        } else {
            if(isManualUpdateTimeExpired(latinIME)) {
                numSuggestionsSinceNotice = 0
                currentNotice.value = object : ImportantNotice {
                    @Composable
                    override fun getText(): String {
                        return stringResource(R.string.keyboard_actionbar_manual_update_check_notice)
                    }

                    override fun onDismiss(context: Context, auto: Boolean) {
                        currentNotice.value = null

                        runBlocking {
                            deferManualUpdate(latinIME)
                        }
                    }

                    override fun onOpen(context: Context) {
                        currentNotice.value = null
                        context.openManualUpdateCheck()

                        runBlocking {
                            deferManualUpdate(latinIME)
                        }
                    }
                }

            }
        }
    }

    fun onColorSchemeChanged() {
        setContent()
    }

    fun cleanUpPersistentStates() {
        for((key, value) in persistentStates.entries) {
            if(currWindowAction != key) {
                latinIME.lifecycleScope.launch { value?.cleanUp() }
            }
        }
    }

    fun closeActionWindow(allowSkipClosing: Boolean = false) {
        if(returnBackToMainKeyboardViewFromAction(allowSkipClosing) == false) return

        // Reset any typeface override as they're not supposed to persist outside of an active
        // action window
        keyboardManagerForAction.overrideKeyboardTypeface(null)
    }


    fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        this.shouldShowSuggestionStrip.value = shouldShowSuggestionsStrip
    }

    fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean, useExpandableUi: Boolean) {
        this.suggestedWords.value = suggestedWords
        this.useExpandableSuggestionsUi.value = useExpandableUi

        if(currentNotice.value != null && suggestedWords?.isEmpty != true) {
            if(numSuggestionsSinceNotice > 0) {
                currentNotice.value?.onDismiss(latinIME, true)
            }
            numSuggestionsSinceNotice += 1
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if(latinIME.getSetting(ActionBarDisplayedSetting) == false) return false
        if(latinIME.getSetting(InlineAutofillSetting) == false) return false

        if(response.inlineSuggestions.isNotEmpty() == true){
            currentNotice.value?.onDismiss(latinIME, true)
        }

        inlineSuggestions.value = response.inlineSuggestions.map {
            latinIME.inflateInlineSuggestion(it)
        }

        return true
    }

    fun triggerActionInternalFromIme(id: Int, alt: Boolean) {
        val action = AllActions.getOrNull(id) ?: throw IllegalArgumentException("No such action with ID $id")

        if(alt) {
            onActionAltActivatedInternal(action)
        } else {
            if (currWindowAction.value != null && action.windowImpl != null) {
                closeActionWindow()
            }

            onActionActivatedInternal(action)
        }
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWords.SuggestedWordInfo) {
        keyboardManagerForAction.requestDialog(
            latinIME.getString(R.string.keyboard_suggest_blacklist_body, suggestedWordInfo.mWord),
            listOf(
                DialogRequestItem(latinIME.getString(R.string.cancel)) { },
                DialogRequestItem(latinIME.getString(R.string.keyboard_suggest_add_word_to_blacklist)) {
                    latinIME.blacklistWord(suggestedWordInfo)
                },
            ) + if(suggestedWordInfo.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
                listOf(
                    DialogRequestItem(latinIME.getString(R.string.keyboard_suggest_disable_emojis)) {
                        runBlocking { latinIME.setSetting(SHOW_EMOJI_SUGGESTIONS, false) }
                        latinIME.blacklistWord(null)
                    }
                )
            } else {
                listOf()
            }
        ) { }

        val v = latinIME.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v!!.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v!!.vibrate(50)
        }
    }

    private fun initKeyboardLoadActions() {
        if(!latinIME.isDirectBootUnlocked) return
        AllActions.forEach { action ->
            if (action.persistentStateInitialization == PersistentStateInitialization.OnKeyboardLoad) {
                persistentStates[action] = persistentStates[action] ?:
                    action.persistentState?.let { it(keyboardManagerForAction) }
            }
        }
    }

    fun onCreate() {
        initKeyboardLoadActions()

        isActionsExpanded.value = latinIME.getSettingBlocking(ActionBarExpanded)

        latinIME.lifecycleScope.launch(Dispatchers.Main) {
            WindowInfoTracker.getOrCreate(latinIME).windowLayoutInfo(latinIME).collect {
                foldingOptions.value = FoldingOptions(it.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull())
                latinIME.invalidateKeyboard(true)
            }
        }

        setContent()
    }

    fun onPersistentStatesUnlocked() {
        initKeyboardLoadActions()

        persistentStates.forEach {
            latinIME.lifecycleScope.launch {
                it.value?.onDeviceUnlocked()
            }
        }
    }

    var editorInfo: EditorInfo? = null


    // TODO: Move tutorial stuff to a separate class

    val tutorialMode: TutorialMode
        get() = when {
            editorInfo?.privateImeOptions?.contains("org.futo.inputmethod.latin.ResizeMode=1") == true ->
                TutorialMode.ResizerTutorial

            else ->
                TutorialMode.None
        }

    val currTutorialMode = mutableStateOf(TutorialMode.None)
    val tutorialArrowPosition: MutableState<LayoutCoordinates?> = mutableStateOf(null)
    val tutorialCompleted = mutableStateOf(false)

    @Composable
    private fun TutorialArrow() = with(LocalDensity.current) {
        if(currTutorialMode.value == TutorialMode.ResizerTutorial && !tutorialCompleted.value) {
            tutorialArrowPosition.value?.let { position ->
                val pos = position.positionInWindow()
                Icon(
                    painterResource(R.drawable.pointy_arrow),
                    contentDescription = null,
                    tint = LocalKeyboardScheme.current.primary,
                    modifier = Modifier
                        .size(128.dp)
                        .offset {
                            IntOffset(
                                pos.x.roundToInt(),
                                (pos.y - 128.dp.toPx()).roundToInt()
                            )
                        }
                )
            }
        }
    }

    private val quickClipState: MutableState<QuickClipState?> = mutableStateOf(null)
    fun dismissQuickClips() { quickClipState.value = null }
    fun inputStarted(editorInfo: EditorInfo?) {
        try {
            checkIfDictInstalled()
        } catch(e: Exception) {
            e.printStackTrace()
        }
        inlineStuffHiddenByTyping.value = false
        this.editorInfo = editorInfo

        currTutorialMode.value = tutorialMode
        tutorialCompleted.value = false
        tutorialArrowPosition.value = null

        if(tutorialMode == TutorialMode.ResizerTutorial) {
            onActionActivated(KeyboardModeAction)
        }else if(BugViewerState.isBugViewerPendingOpen) {
            BugViewerState.clearPendingOpen()
            onActionActivated(BugViewerAction)
        }

        quickClipState.value = QuickClip.getCurrentState(latinIME)
    }

    fun onInputFinishing() {
        closeActionWindow()
        languageSwitcherDialog?.dismiss()
        isShowingActionEditor.value = false
        resizers.hideResizer()
        inlineSuggestions.value = emptyList()
    }

    // Called by InputLogic on any event
    fun onInputEvent(textBlank: Boolean) {
        inlineStuffHiddenByTyping.value = textBlank == false
    }

    private var prevLocale: Locale? = null
    fun updateLocale(locale: Locale): Configuration? {
        var result: Configuration? = null
        prevLocale = locale
        if(UixLocaleFollowsSubtypeLocale) {
            latinIME.resources.apply {
                val config = Configuration(configuration)
                config.setLocale(locale)
                result = config
                updateConfiguration(config, displayMetrics)
            }
            setContent()
        }

        PersistentEmojiState.loadTranslationsForLanguage(latinIME, locale)
        checkIfDictInstalled()
        return result
    }

    fun updateLocaleOnCfgChanged(): Configuration? {
        return prevLocale?.let { updateLocale(it) }
    }

    fun onDestroy() {
        closeActionWindow()
        persistentStates.values.filterNotNull().forEach {
            it.close()
        }
        persistentStates.clear()
    }

    private fun checkIfDictInstalled() {
        val check = MissingDictionaryHelper.checkIfDictInstalled(latinIME)
        when(check) {
            MissingDictionaryHelper.DictCheckResult.CheckFailed -> { }
            MissingDictionaryHelper.DictCheckResult.DontShowDictNotice -> {
                // Hide any existing one, e.g. if user just installed a dict or switched to a language
                // with a dict.
                if(currentNotice.value is MissingDictionaryHelper.NoDictionaryNotice) {
                    currentNotice.value = null
                }
            }
            is MissingDictionaryHelper.DictCheckResult.ShowDictNotice -> {
                numSuggestionsSinceNotice = 0
                currentNotice.value = MissingDictionaryHelper.NoDictionaryNotice(
                    check.dismissalSetting,
                    check.locale,
                    latinIME.getString(R.string.keyboard_actionbar_no_dictionary_installed_notice)
                ) { currentNotice.value = null }
            }
        }
    }
}