package org.futo.inputmethod.latin.uix

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.latin.AudioAndHapticFeedbackManager
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.LanguageSwitcherDialog
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.suggestions.SuggestionStripView
import org.futo.inputmethod.latin.uix.actions.ActionRegistry
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.EmojiAction
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
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
import java.util.Locale

val LocalManager = staticCompositionLocalOf<KeyboardManagerForAction> {
    error("No LocalManager provided")
}

private class LatinIMEActionInputTransaction(
    private val inputLogic: InputLogic,
    shouldApplySpace: Boolean,
    private val context: Context
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

    override fun triggerContentUpdate() {
        uixManager.setContent()
    }

    override fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction {
        return LatinIMEActionInputTransaction(latinIME.inputLogic, applySpaceIfNeeded, latinIME)
    }

    override fun typeText(v: String) {
        latinIME.latinIMELegacy.onTextInput(v)
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

    override fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorLeft(steps, stepOverWords, select)
    }

    override fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorRight(steps, stepOverWords, select)
    }

    override fun performHapticAndAudioFeedback(code: Int, view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(code, view)
    }

    override fun getActiveLocale(): Locale {
        return latinIME.latinIMELegacy.locale
    }

    override fun overrideInputConnection(inputConnection: InputConnection) {
        latinIME.overrideInputConnection = inputConnection
        latinIME.inputLogic.startInput(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype,
            latinIME.latinIMELegacy.mSettings.current)
    }

    override fun unsetInputConnection() {
        latinIME.overrideInputConnection = null
        latinIME.inputLogic.startInput(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype,
            latinIME.latinIMELegacy.mSettings.current)
    }

    override fun requestDialog(text: String, options: List<DialogRequestItem>, onCancel: () -> Unit) {
        uixManager.activeDialogRequest.value = ActiveDialogRequest(text, options, onCancel)
        uixManager.activeDialogRequestDismissed.value = false
    }

    override fun announce(s: String) {
        AccessibilityUtils.init(getContext())
        if(AccessibilityUtils.getInstance().isAccessibilityEnabled) {
            AccessibilityUtils.getInstance().announceForAccessibility(uixManager.getComposeView(), s)
        }
    }
}

data class ActiveDialogRequest(
    val text: String,
    val options: List<DialogRequestItem>,
    val onCancel: () -> Unit
)

class UixManager(private val latinIME: LatinIME) {
    private var shouldShowSuggestionStrip: Boolean = true
    private var suggestedWords: SuggestedWords? = null

    private var composeView: ComposeView? = null

    private var currWindowAction: Action? = null
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()

    private var inlineSuggestions: List<MutableState<View?>> = listOf()
    private val keyboardManagerForAction = UixActionKeyboardManager(this, latinIME)

    private var mainKeyboardHidden = false

    private var numSuggestionsSinceNotice = 0
    private var currentNotice: MutableState<ImportantNotice?> = mutableStateOf(null)

    private val actionsForcedOpenByUser = mutableStateOf(false)

    var currWindowActionWindow: ActionWindow? = null

    val isMainKeyboardHidden get() = mainKeyboardHidden

    private fun onActionActivated(rawAction: Action) {
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
                onActionActivated = { onActionActivated(it) },
                importantNotice = currentNotice.value,
                keyboardManagerForAction = keyboardManagerForAction,
                actionsForcedOpenByUser = actionsForcedOpenByUser
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

        if(action.keepScreenAwake) {
            latinIME.window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent()

        actionsForcedOpenByUser.value = false
        keyboardManagerForAction.announce("${latinIME.resources.getString(action.name)} mode")
    }

    fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow == null) return

        val name = latinIME.resources.getString(currWindowAction!!.name)

        currWindowActionWindow!!.close()

        currWindowAction = null
        currWindowActionWindow = null

        mainKeyboardHidden = false

        latinIME.onKeyboardShown()

        latinIME.window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent()

        actionsForcedOpenByUser.value = false
        keyboardManagerForAction.announce("$name closed")
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
                    windowTitleBar = { windowImpl.WindowTitleBar(this) }
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) {
                    (latinIME
                        .getInputViewHeight()
                        .toFloat() / heightDiv.toFloat()).toDp()
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
                    suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripView.Listener
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

                    Box(modifier = Modifier.matchParentSize().padding(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    activeDialogRequest.value?.text ?: "",
                                    style = Typography.bodyMedium
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
            UixThemeAuto {
                LanguageSwitcherDialog(
                    onDismiss = { it.dismiss() }
                )
            }
        }

        languageSwitcherDialog?.show()
    }

    fun setContent() {
        composeView?.setContent {
            UixThemeWrapper(latinIME.colorScheme) {
                CompositionLocalProvider(LocalManager provides keyboardManagerForAction) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr ) {
                        Column {
                            Spacer(modifier = Modifier.weight(1.0f))
                            Surface(modifier = Modifier.onSizeChanged {
                                latinIME.updateTouchableHeight(it.height)
                            }, color = latinIME.keyboardColor) {
                                Box {
                                    Column {
                                        when {
                                            currWindowActionWindow != null -> ActionViewWithHeader(
                                                currWindowActionWindow!!
                                            )

                                            else -> MainKeyboardViewWithActionBar()
                                        }

                                        latinIME.LegacyKeyboardView(hidden = isMainKeyboardHidden)
                                    }

                                    ForgetWordDialog()
                                }
                            }
                        }
                    }
                }
            }
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
                        return "Please tap to check for updates"
                    }

                    override fun onDismiss(context: Context) {
                        currentNotice.value = null
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
        actionsForcedOpenByUser.value = false
        languageSwitcherDialog?.dismiss()
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

        if(currentNotice.value != null) {
            numSuggestionsSinceNotice += 1
            if(numSuggestionsSinceNotice > 4) {
                currentNotice.value?.onDismiss(latinIME)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        currentNotice.value?.onDismiss(latinIME)

        inlineSuggestions = response.inlineSuggestions.map {
            latinIME.inflateInlineSuggestion(it)
        }
        setContent()

        return true
    }

    fun openEmojiKeyboard() {
        if(currWindowAction == null) {
            onActionActivated(EmojiAction)
        }
    }

    fun triggerAction(id: Int) {
        val action = AllActions.getOrNull(id) ?: throw IllegalArgumentException("No such action with ID $id")

        if(currWindowAction != null && action.windowImpl != null) {
            closeActionWindow()
        }

        onActionActivated(action)
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
            },
            { }
        )

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
    }
}