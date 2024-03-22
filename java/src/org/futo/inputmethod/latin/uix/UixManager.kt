package org.futo.inputmethod.latin.uix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InlineSuggestionsResponse
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.AudioAndHapticFeedbackManager
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.suggestions.SuggestionStripView
import org.futo.inputmethod.latin.uix.actions.EmojiAction
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.updates.DEFER_MANUAL_UPDATE_UNTIL
import org.futo.inputmethod.updates.MANUAL_UPDATE_PERIOD_MS
import org.futo.inputmethod.updates.openManualUpdateCheck
import org.futo.inputmethod.updates.retrieveSavedLastUpdateCheckResult


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

    override fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorLeft(steps, stepOverWords, select)
    }

    override fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorRight(steps, stepOverWords, select)
    }

    override fun performHapticAndAudioFeedback(code: Int, view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(code, view)
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

    private var numSuggestionsSinceNotice = 0
    private var currentNotice: MutableState<ImportantNotice?> = mutableStateOf(null)

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
                onActionActivated = { onActionActivated(it) },
                importantNotice = currentNotice.value,
                keyboardManagerForAction = keyboardManagerForAction
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
    }

    fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow == null) return

        currWindowActionWindow!!.close()

        currWindowAction = null
        currWindowActionWindow = null

        mainKeyboardHidden = false

        latinIME.onKeyboardShown()

        latinIME.window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
                    suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripView.Listener,
                    inlineSuggestions = inlineSuggestions
                )
            }
        }
    }

    val wordBeingForgotten: MutableState<SuggestedWordInfo?> = mutableStateOf(null)
    val forgetWordDismissed: MutableState<Boolean> = mutableStateOf(true)

    @Composable
    fun BoxScope.ForgetWordDialog() {
        AnimatedVisibility(
            visible = forgetWordDismissed.value == false,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (wordBeingForgotten.value != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.66f),
                        modifier = Modifier.matchParentSize().pointerInput(Unit) {
                            this.detectTapGestures(onPress = {
                                forgetWordDismissed.value = true
                            })
                        }
                    ) { }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(
                                    R.string.blacklist_from_suggestions,
                                    wordBeingForgotten.value?.mWord!!
                                ))

                            Row {
                                TextButton(
                                    onClick = {
                                        forgetWordDismissed.value = true
                                    }
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }

                                TextButton(
                                    onClick = {
                                        latinIME.forceForgetWord(wordBeingForgotten.value!!)
                                        forgetWordDismissed.value = true
                                    }
                                ) {
                                    Text(stringResource(R.string.blacklist))
                                }

                                if(wordBeingForgotten.value!!.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
                                    TextButton(
                                        onClick = {
                                            runBlocking { latinIME.setSetting(SHOW_EMOJI_SUGGESTIONS, false) }
                                            forgetWordDismissed.value = true
                                            latinIME.refreshSuggestions()
                                        }
                                    ) {
                                        Text(stringResource(R.string.disable_emoji))
                                    }
                                }
                            }
                        }
                    }
                }
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

    suspend fun showUpdateNoticeIfNeeded() {
        val updateInfo = retrieveSavedLastUpdateCheckResult(latinIME)
        if(updateInfo != null && updateInfo.isNewer()) {
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

                    if(context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    context.startActivity(intent)
                }
            }
        } else {
            val defermentTime = latinIME.getSetting(DEFER_MANUAL_UPDATE_UNTIL, Long.MAX_VALUE)
            if(System.currentTimeMillis() > defermentTime) {
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
                            latinIME.setSetting(
                                DEFER_MANUAL_UPDATE_UNTIL,
                                System.currentTimeMillis() + MANUAL_UPDATE_PERIOD_MS
                            )
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

    fun requestForgetWord(suggestedWordInfo: SuggestedWords.SuggestedWordInfo) {
        wordBeingForgotten.value = suggestedWordInfo
        forgetWordDismissed.value = false

        val v = latinIME.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v!!.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v!!.vibrate(50)
        }
    }
}