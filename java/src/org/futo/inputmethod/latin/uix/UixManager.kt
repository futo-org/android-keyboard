package org.futo.inputmethod.latin.uix

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.latin.AudioAndHapticFeedbackManager
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.FoldingOptions
import org.futo.inputmethod.latin.LanguageSwitcherDialog
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.SupportsNavbarExtension
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewListener
import org.futo.inputmethod.latin.uix.actions.ActionEditor
import org.futo.inputmethod.latin.uix.actions.ActionRegistry
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.KeyboardModeAction
import org.futo.inputmethod.latin.uix.resizing.KeyboardResizers
import org.futo.inputmethod.latin.uix.settings.DataStoreCacheProvider
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.ActionBarDisplayedSetting
import org.futo.inputmethod.latin.uix.settings.pages.InlineAutofillSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.updates.DISABLE_UPDATE_REMINDER
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


private class LatinIMEActionInputTransaction(
    private val inputLogic: InputLogic,
    shouldApplySpace: Boolean
): ActionInputTransaction {
    private val isSpaceNecessary: Boolean
    private var isFinished = false

    init {
        val priorText = inputLogic.mConnection.getTextBeforeCursor(1, 0)
        isSpaceNecessary = shouldApplySpace && !priorText.isNullOrEmpty() && !priorText.last().isWhitespace()

        inputLogic.startSuppressingLogic()
    }

    private fun transformText(text: String): String {
        return if(isSpaceNecessary) { " $text" } else { text }
    }

    private var previousText = ""
    override fun updatePartial(text: String) {
        if(isFinished) return
        previousText = text
        inputLogic.mConnection.setComposingText(
            transformText(text),
            1
        )
    }

    override fun commit(text: String) {
        if(isFinished) return
        isFinished = true
        inputLogic.mConnection.commitText(
            transformText(text),
            1
        )
        inputLogic.endSuppressingLogic()
    }

    override fun cancel() {
        commit(previousText)
    }
}

class UixActionKeyboardManager(val uixManager: UixManager, val latinIME: LatinIME) : KeyboardManagerForAction {
    override fun getContext(): Context {
        return latinIME
    }

    override fun getLifecycleScope(): LifecycleCoroutineScope {
        return latinIME.lifecycleScope
    }

    override fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction {
        return LatinIMEActionInputTransaction(latinIME.inputLogic, applySpaceIfNeeded)
    }

    override fun typeText(v: String) {
        if(latinIME.isInputConnectionOverridden) {
            latinIME.getBaseInputConnection()?.commitText(v, 1)
        } else {
            latinIME.latinIMELegacy.onTextInput(v)
        }
    }

    override fun typeUri(uri: Uri, mimeTypes: List<String>): Boolean {
        if(mimeTypes.isEmpty()) {
            Log.w("UixManager", "mimeTypes is empty")
            return false
        }

        val description = ClipDescription("Pasted image", mimeTypes.toTypedArray())

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            InputContentInfo(uri, description, null)
        } else {
            return false
        }

        return latinIME.currentInputConnection?.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) ?: run {
            Log.w("UixManager", "Current input connection is null")
            return false
        }
    }

    override fun backspace(amount: Int) {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_DELETE,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun closeActionWindow() {
        uixManager.returnBackToMainKeyboardViewFromAction()
    }

    override fun triggerSystemVoiceInput() {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        )
    }

    override fun updateTheme(newTheme: ThemeOption) {
        latinIME.updateTheme(newTheme)
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
        latinIME.inputLogic.sendDownUpKeyEvent(keyCode, metaState)
    }

    override fun isShifted(): Boolean = latinIME.latinIMELegacy.mKeyboardSwitcher.mState.shifted

    override fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorLeft(steps, stepOverWords, select)
    }

    override fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorRight(steps, stepOverWords, select)
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

    override fun getSizingCalculator(): KeyboardSizingCalculator =
        latinIME.sizingCalculator

    override fun getLatinIMEForDebug(): LatinIME = latinIME

    override fun getSuggestionBlacklist(): SuggestionBlacklist = latinIME.suggestionBlacklist
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

    private var shouldShowSuggestionStrip = mutableStateOf(true)
    private var suggestedWords: MutableState<SuggestedWords?> = mutableStateOf(null)

    var currWindowAction: MutableState<Action?> = mutableStateOf(null)
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()

    private var inlineSuggestions: MutableState<List<MutableState<View?>>> = mutableStateOf(emptyList())
    private val keyboardManagerForAction = UixActionKeyboardManager(this, latinIME)

    private var mainKeyboardHidden = mutableStateOf(false)

    private var numSuggestionsSinceNotice = 0
    private var currentNotice: MutableState<ImportantNotice?> = mutableStateOf(null)

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

    val isMainKeyboardHidden get() = mainKeyboardHidden

    fun onActionActivated(rawAction: Action) {
        resizers.hideResizer()
        latinIME.inputLogic.finishInput()

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

    fun onActionAltActivated(rawAction: Action) {
        latinIME.inputLogic.finishInput()

        val action = runBlocking {
            ActionRegistry.getActionOverride(latinIME, rawAction)
        }

        action.altPressImpl?.invoke(keyboardManagerForAction, persistentStates[action])
    }

    @Composable
    private fun MainKeyboardViewWithActionBar() {
        val view = LocalView.current

        val actionBarShown = useDataStore(ActionBarDisplayedSetting)

        Column {
            // Don't show suggested words when it's not meant to be shown
            val suggestedWordsOrNull = if(shouldShowSuggestionStrip.value) {
                suggestedWords.value
            } else {
                null
            }

            if(actionBarShown.value) {
                ActionBar(
                    suggestedWordsOrNull,
                    latinIME.latinIMELegacy as SuggestionStripViewListener,
                    inlineSuggestions = inlineSuggestions.value,
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

        keyboardManagerForAction.announce("${latinIME.resources.getString(action.name)} mode")
    }

    fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow.value == null) return

        val name = latinIME.resources.getString(currWindowAction.value!!.name)

        currWindowActionWindow.value!!.close()

        currWindowAction.value = null
        currWindowActionWindow.value = null

        mainKeyboardHidden.value = false

        latinIME.onKeyboardShown()

        latinIME.window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        keyboardManagerForAction.announce("$name closed")
    }

    fun toggleExpandAction(to: Boolean? = null) {
        mainKeyboardHidden.value = !(to ?: mainKeyboardHidden.value)
        if(!mainKeyboardHidden.value) {
            latinIME.onKeyboardShown()
        }
    }

    @Composable
    private fun ActionViewWithHeader(windowImpl: ActionWindow) {
        val heightDiv = if(mainKeyboardHidden.value) {
            1
        } else {
            1.5
        }
        Column {
            if(mainKeyboardHidden.value || latinIME.isInputConnectionOverridden) {
                ActionWindowBar(
                    onBack = { returnBackToMainKeyboardViewFromAction() },
                    canExpand = currWindowAction.value!!.canShowKeyboard,
                    onExpand = { toggleExpandAction() },
                    windowTitleBar = { windowImpl.WindowTitleBar(this) }
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) {
                    currWindowActionWindow.value?.fixedWindowHeight ?: ((latinIME
                        .getInputViewHeight()
                        .toFloat() / heightDiv.toFloat()).toDp() +
                            if (actionsExpanded) ActionBarHeight else 0.dp)
                })
                .safeKeyboardPadding()
            ) {
                windowImpl.WindowContents(keyboardShown = !isMainKeyboardHidden.value)
            }

            if(!mainKeyboardHidden.value && !latinIME.isInputConnectionOverridden) {
                val suggestedWordsOrNull = if (shouldShowSuggestionStrip.value) {
                    suggestedWords.value
                } else {
                    null
                }

                CollapsibleSuggestionsBar(
                    onCollapse = { toggleExpandAction() },
                    onClose = { returnBackToMainKeyboardViewFromAction() },
                    words = suggestedWordsOrNull,
                    showClose = currWindowActionWindow.value?.showCloseButton == true,
                    showCollapse = currWindowActionWindow.value?.onlyShowAboveKeyboard == false,
                    suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripViewListener
                )
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
        languageSwitcherDialog = createDialogComposeView(latinIME) {
            DataStoreCacheProvider {
                UixThemeAuto {
                    LanguageSwitcherDialog(
                        onDismiss = { it.dismiss() }
                    )
                }
            }
        }

        languageSwitcherDialog?.show()
    }

    @Composable
    fun ActionEditorHost() {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = navBarHeight()), contentAlignment = Alignment.BottomCenter) {
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
        Column(modifier = Modifier.fillMaxHeight().absoluteOffset { IntOffset(offset.x.toInt(), 0) }) {
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

        val backgroundBrush = LocalKeyboardScheme.current.keyboardBackgroundGradient ?: SolidColor(backgroundColor)

        Box(modifier
            .onSizeChanged { measuredTouchableHeight = it.height }
            .background(backgroundBrush, shape)
            .requiredWidth(requiredWidthPx.toDp())
            .absolutePadding(
                //top = padding.top.toDp().coerceAtLeast(0.dp),
                //bottom = padding.bottom.toDp().coerceAtLeast(0.dp),
            )
            .clipToBounds()
        ) {
            CompositionLocalProvider(LocalKeyboardPadding provides KeyboardPadding(
                left = padding.left.toDp().coerceAtLeast(0.dp),
                right = padding.right.toDp().coerceAtLeast(0.dp),
            )) {
                CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onKeyboardContainer) {
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
        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(pointerInputKey) {
                detectDragGestures(
                    onDrag = { _, dragAmount -> onDragged(dragAmount)},
                    onDragEnd = { onDragEnd() })
            }) {

            IconButton(onClick = {
                onActionActivated(KeyboardModeAction)
            }, Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    painterResource(R.drawable.keyboard_gear),
                    contentDescription = "Keyboard modes",
                    tint = LocalKeyboardScheme.current.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(0.6f).height(4.dp)
                    .align(Alignment.Center).background(
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

        OffsetPositioner(offset.value + Offset(0.0f, navBarHeight().toPx())) {
            KeyboardSurface(
                requiredWidthPx = size.width,
                backgroundColor = latinIME.keyboardColor,
                shape = RoundedCornerShape(16.dp),
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

                resizers.Resizer(this, size)
            }
        }
    }

    @Composable
    private fun BoxScope.OneHandedOptions(size: OneHandedKeyboardSize) = with(LocalDensity.current) {
        Box(Modifier.matchParentSize()) {
            Column(modifier = Modifier.matchParentSize().absolutePadding(
                top = if(isActionsExpanded.value) ActionBarHeight else 0.dp
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
                    }), contentDescription = "Switch handedness")
                }

                Spacer(Modifier.weight(1.0f))

                IconButton(onClick = {
                    latinIME.sizingCalculator.exitOneHandedMode()
                }) {
                    Icon(painterResource(R.drawable.maximize), contentDescription = "Exit one-handed mode")
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
                CompositionLocalProvider(LocalManager provides keyboardManagerForAction) {
                    CompositionLocalProvider(LocalThemeProvider provides latinIME.getDrawableProvider()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            CompositionLocalProvider(LocalFoldingState provides foldingOptions.value) {
                                Box(Modifier.fillMaxSize().onSizeChanged {
                                    // If the size changes, call the service to check if the size needs
                                    // to be recalculated and keyboard recreated
                                    if(it != prevSize) {
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
                    when {
                        currWindowActionWindow.value != null -> ActionViewWithHeader(
                            currWindowActionWindow.value!!
                        )

                        else -> MainKeyboardViewWithActionBar()
                    }

                    Spacer(modifier = Modifier.height(gap))

                    latinIME.LegacyKeyboardView(hidden = isMainKeyboardHidden.value)

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
            if(!latinIME.getSetting(DISABLE_UPDATE_REMINDER)) {
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
            }
        } else {
            if(isManualUpdateTimeExpired(latinIME)) {
                numSuggestionsSinceNotice = 0
                currentNotice.value = object : ImportantNotice {
                    @Composable
                    override fun getText(): String {
                        return "Please tap to check for updates, or check in settings"
                    }

                    override fun onDismiss(context: Context) {
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

    fun onInputFinishing() {
        closeActionWindow()
        languageSwitcherDialog?.dismiss()
        isShowingActionEditor.value = false
        resizers.hideResizer()
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
        returnBackToMainKeyboardViewFromAction()
    }


    fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        this.shouldShowSuggestionStrip.value = shouldShowSuggestionsStrip
    }

    fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        this.suggestedWords.value = suggestedWords

        if(currentNotice.value != null) {
            if(numSuggestionsSinceNotice > 1) {
                currentNotice.value?.onDismiss(latinIME)
            }
            numSuggestionsSinceNotice += 1
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if(latinIME.getSetting(ActionBarDisplayedSetting) == false) return false
        if(latinIME.getSetting(InlineAutofillSetting) == false) return false

        currentNotice.value?.onDismiss(latinIME)

        inlineSuggestions.value = response.inlineSuggestions.map {
            latinIME.inflateInlineSuggestion(it)
        }

        return true
    }

    fun triggerAction(id: Int, alt: Boolean) {
        val action = AllActions.getOrNull(id) ?: throw IllegalArgumentException("No such action with ID $id")

        if(alt) {
            onActionAltActivated(action)
        } else {
            if (currWindowAction.value != null && action.windowImpl != null) {
                closeActionWindow()
            }

            onActionActivated(action)
        }
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWords.SuggestedWordInfo) {
        keyboardManagerForAction.requestDialog(
            latinIME.getString(R.string.blacklist_from_suggestions, suggestedWordInfo.mWord),
            listOf(
                DialogRequestItem(latinIME.getString(R.string.cancel)) { },
                DialogRequestItem(latinIME.getString(R.string.blacklist)) {
                    latinIME.forceForgetWord(suggestedWordInfo)
                },
            ) + if(suggestedWordInfo.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
                listOf(
                    DialogRequestItem(latinIME.getString(R.string.disable_emoji)) {
                        runBlocking { latinIME.setSetting(SHOW_EMOJI_SUGGESTIONS, false) }
                        latinIME.refreshSuggestions()
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

    fun onCreate() {
        AllActions.forEach { action ->
            if(action.persistentStateInitialization == PersistentStateInitialization.OnKeyboardLoad) {
                persistentStates[action] = action.persistentState?.let { it(keyboardManagerForAction) }
            }
        }

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
                    modifier = Modifier.size(128.dp).offset {
                        IntOffset(
                            pos.x.roundToInt(),
                            (pos.y - 128.dp.toPx()).roundToInt()
                        )
                    }
                )
            }
        }
    }

    fun inputStarted(editorInfo: EditorInfo?) {
        inlineSuggestions.value = emptyList()
        this.editorInfo = editorInfo

        currTutorialMode.value = tutorialMode
        tutorialCompleted.value = false
        tutorialArrowPosition.value = null

        if(tutorialMode == TutorialMode.ResizerTutorial) {
            onActionActivated(KeyboardModeAction)
        }
    }
}