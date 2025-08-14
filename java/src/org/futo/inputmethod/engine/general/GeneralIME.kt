package org.futo.inputmethod.engine.general

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.event.InputTransaction
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.DictionaryFacilitatorProvider
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.Subtypes.switchToNextLanguage
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.WordComposer
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.v2keyboard.CombinerKind

interface WordLearner {
    fun addToHistory(
        word: String,
        wasCapitalized: Boolean,
        ngramContext: NgramContext,
        timestamp: Long,
        blockOffensive: Boolean,
        importance: Int
    )

    fun removeFromHistory(
        word: String,
        ngramContext: NgramContext,
        timestamp: Long,
        eventType: Int
    )
}

interface OnGetSuggestedWordsCallbackWithInputStyle {
    fun onGetSuggestedWords(suggestedWords: SuggestedWords, inputStyle: Int, sequenceNumber: Int)
}

/**
 * General IME implementation that works for Latin-based languages and some
 * more: Cyrillic, Hangul (with combiner), etc.
 *
 * Uses BinaryDictionary, etc
 */
class GeneralIME(val helper: IMEHelper) : IMEInterface, WordLearner, SuggestionStripViewAccessor, OnGetSuggestedWordsCallbackWithInputStyle {
    private val TAG = "GeneralIME"
    private val context = helper.context

    private val availabilityListener =
        object : DictionaryFacilitator.DictionaryInitializationListener {
            override fun onUpdateMainDictionaryAvailability(isMainDictionaryAvailable: Boolean) {
                helper.updateGestureAvailability(isGestureHandlingAvailable())
                // TODO: Original logic here would refresh suggestions
            }
        }

    private val dictionaryFacilitator: DictionaryFacilitator =
        DictionaryFacilitatorProvider.getDictionaryFacilitator(
            false /* isNeededForSpellChecking */
        )

    private val inputLogic: InputLogic = InputLogic(
        helper, this, dictionaryFacilitator,
        this
    )

    private val settings = Settings.getInstance()

    private val suggestionBlacklist = SuggestionBlacklist(
        settings,
        context,
        helper.lifecycleScope
    )

    private val languageModelFacilitator = LanguageModelFacilitator(
        context = context,
        inputLogic = inputLogic,
        dictionaryFacilitator = dictionaryFacilitator,
        settings = settings,
        keyboardSwitcher = KeyboardSwitcher.getInstance(),
        lifecycleScope = helper.lifecycleScope,
        suggestionBlacklist = suggestionBlacklist,
        suggestedWordsCallback = this
    )

    override fun addToHistory(
        word: String,
        wasCapitalized: Boolean,
        ngramContext: NgramContext,
        timestamp: Long,
        blockOffensive: Boolean,
        importance: Int
    ) {
        dictionaryFacilitator.addToUserHistory(
            word, wasCapitalized,
            ngramContext, timestamp,
            blockOffensive
        )

        if (settings.current.mTransformerPredictionEnabled) {
            languageModelFacilitator.addToHistory(
                word, wasCapitalized,
                ngramContext, timestamp,
                blockOffensive, importance
            )
        }
    }

    override fun removeFromHistory(
        word: String,
        ngramContext: NgramContext,
        timestamp: Long,
        eventType: Int
    ) {
        dictionaryFacilitator.unlearnFromUserHistory(
            word, ngramContext, timestamp, eventType
        )

        if (settings.current.mTransformerPredictionEnabled) {
            languageModelFacilitator.unlearnFromHistory(
                word, ngramContext, timestamp, eventType
            )
        }
    }


    fun resetDictionaryFacilitator(force: Boolean = false) {
        val settings = settings.current

        val locales = RichInputMethodManager.getInstance().currentSubtypeLocales
        if ((!force)
            && dictionaryFacilitator.isForLocales(locales)
            && dictionaryFacilitator.isForAccount(settings.mAccount)
        ) {
            return
        }

        dictionaryFacilitator.resetDictionaries(
            context,
            locales, settings.mUseContactsDict,
            settings.mUsePersonalizedDicts,
            force && true,  /* forceReloadMainDictionary */ // TODO: Not sure
            settings.mAccount, "",  /* dictNamePrefix */
            availabilityListener /* DictionaryInitializationListener */
        )

        if (settings.mAutoCorrectionEnabledPerUserSettings) {
            inputLogic.mSuggest.setAutoCorrectionThreshold(
                settings.mAutoCorrectionThreshold
            )
        }
        inputLogic.mSuggest.setPlausibilityThreshold(
            settings.mPlausibilityThreshold
        )
    }

    override fun onCreate() {
        languageModelFacilitator.launchProcessor()
        if (context.isDirectBootUnlocked) onDeviceUnlocked()

        suggestionBlacklist.init()

        helper.lifecycleScope.launch {
            GlobalIMEMessage.collect { message ->
                when(message) {
                    IMEMessage.ReloadResources -> withContext(Dispatchers.Main) {
                        resetDictionaryFacilitator(force = true)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        dictionaryFacilitator.closeDictionaries()
        languageModelFacilitator.saveHistoryLog()

        runBlocking {
            languageModelFacilitator.destroyModel()
        }
    }

    override fun onDeviceUnlocked() {
        languageModelFacilitator.loadHistoryLog()
    }

    override fun onStartInput(
        layout: String
    ) {
        resetDictionaryFacilitator(force = false)
        setNeutralSuggestionStrip()
        dictionaryFacilitator.onStartInput()
        languageModelFacilitator.onStartInput()
        inputLogic.startInput(
            RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype,
            settings.current
        )

        cancelSync()
    }

    var dictSyncJob: Job? = null
    private fun delayedSync() {
        if (!context.isDirectBootUnlocked) return

        dictSyncJob?.cancel()
        dictSyncJob = helper.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                delay(5000L)
                withContext(Dispatchers.Main) {
                    dictionaryFacilitator.flushUserHistoryDictionaries()
                    languageModelFacilitator.saveHistoryLog()
                }
            }
        }
    }

    private fun cancelSync() {
        dictSyncJob?.cancel()
    }

    override fun onOrientationChanged() {
        inputLogic.onOrientationChange(settings.current)
    }

    override fun onFinishInput() {
        inputLogic.finishInput()
        dictionaryFacilitator.onFinishInput(context)
        delayedSync()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        inputLogic.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd,
            Settings.getInstance().current
        )
    }

    override fun isGestureHandlingAvailable(): Boolean =
        dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()

    override fun onEvent(event: Event) {
        Log.d(TAG, "Event: ${event.eventType}")
        val inputTransaction = when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS -> {
                inputLogic.onCodeInput(
                    settings.current,
                    event,
                    helper.keyboardShiftMode,
                    helper.currentKeyboardScriptId
                )
            }

            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> {
                inputLogic.onTextInput(
                    settings.current,
                    event,
                    helper.keyboardShiftMode
                )
            }

            Event.EVENT_TYPE_SUGGESTION_PICKED -> {
                inputLogic.onPickSuggestionManually(
                    settings.current,
                    event.mSuggestedWordInfo!!,
                    helper.keyboardShiftMode,
                    helper.currentKeyboardScriptId
                )
            }

            Event.EVENT_TYPE_DOWN_UP_KEYEVENT -> {
                inputLogic.sendDownUpKeyEvent(
                    event.mKeyCode,
                    event.mX
                )
                InputTransaction(
                    settings.current,
                    event,
                    System.currentTimeMillis(),
                    0,
                    0
                ).apply { setRequiresUpdateSuggestions() }
            }

            Event.EVENT_TYPE_NOT_HANDLED -> { null }

            Event.EVENT_TYPE_TOGGLE -> { null }

            Event.EVENT_TYPE_MODE_KEY -> { null }

            Event.EVENT_TYPE_GESTURE -> { null }

            Event.EVENT_TYPE_CURSOR_MOVE -> { null }
            else -> { null }
        }

        // TODO: Shift update

        if(inputTransaction?.requiresUpdateSuggestions() != null) { // TODO
            val inputStyle = if(inputTransaction.mEvent.isSuggestionStripPress) {
                SuggestedWords.INPUT_STYLE_NONE
            } else if(inputTransaction.mEvent.isGesture) {
                SuggestedWords.INPUT_STYLE_TAIL_BATCH
            } else {
                SuggestedWords.INPUT_STYLE_TYPING
            }

            updateSuggestions(inputStyle)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dictionaryScope = Dispatchers.Default.limitedParallelism(1)


    override fun onGetSuggestedWords(
        suggestedWords: SuggestedWords,
        inputStyle: Int,
        sequenceNumber: Int
    ) {
        val words = when {
            suggestedWords.isEmpty && (inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH ||
                    inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH
                    ) -> inputLogic.mSuggestedWords

            else -> suggestedWords
        }

        showSuggestionStrip(words)
        updateSuggestionJob = null
        when(inputStyle) {
            SuggestedWords.INPUT_STYLE_TAIL_BATCH ->
                inputLogic.onUpdateTailBatchInputCompleted(
                    settings.current,
                    words,
                    helper.keyboardSwitcher
                )
        }
    }

    var updateSuggestionJob: Job? = null
    private fun updateSuggestionsDictionaryInternal(inputStyle: Int) {
        val sequenceNumber = SuggestedWords.NOT_A_SEQUENCE_NUMBER
        inputLogic.getSuggestedWords(
            settings.current,
            helper.keyboardSwitcher.keyboard ?: return,
            helper.keyboardShiftMode,
            inputStyle,
            sequenceNumber
        ) { suggestedWords ->
            onGetSuggestedWords(suggestedWords, inputStyle, sequenceNumber)
        }
    }

    fun updateSuggestions(inputStyle: Int) {
        if(!languageModelFacilitator.shouldPassThroughToLegacy()) {
            languageModelFacilitator.updateSuggestionStripAsync(inputStyle)
        } else {
            updateSuggestionJob?.cancel()
            updateSuggestionJob = helper.lifecycleScope.launch {
                when(inputStyle) {
                    SuggestedWords.INPUT_STYLE_TYPING -> delay(40L)
                }
                withContext(dictionaryScope) {
                    updateSuggestionsDictionaryInternal(inputStyle)
                }
            }
            // TODO: The below method is broken?
            /*inputLogic.performUpdateSuggestionStripSync(
                settings.current,
                inputStyle
            )*/
        }
    }

    fun ensureSuggestionsCompleted(): Boolean {
        if(!languageModelFacilitator.shouldPassThroughToLegacy()) {
            if(languageModelFacilitator.hasPendingUpdate()) {
                return languageModelFacilitator.blockUntilComplete()
            } else {
                // no pending updates
                return true
            }
        } else {
            // TODO: ASYNC!!!!!
            if(updateSuggestionJob?.isActive == true) {
                //runBlocking { updateSuggestionJob?.join() }
                updateSuggestionJob?.cancel()
                runBlocking(dictionaryScope) {
                    updateSuggestionsDictionaryInternal(SuggestedWords.INPUT_STYLE_TYPING)
                }
            }


            /*
                        if (handler.hasPendingUpdateSuggestions()) {
                handler.cancelUpdateSuggestionStrip();
                // To know the input style here, we should retrieve the in-flight "update suggestions"
                // message and read its arg1 member here. However, the Handler class does not let
                // us retrieve this message, so we can't do that. But in fact, we notice that
                // we only ever come here when the input style was typing. In the case of batch
                // input, we update the suggestions synchronously when the tail batch comes. Likewise
                // for application-specified completions. As for recorrections, we never auto-correct,
                // so we don't come here either. Hence, the input style is necessarily
                // INPUT_STYLE_TYPING.
                performUpdateSuggestionStripSync(settingsValues, SuggestedWords.INPUT_STYLE_TYPING);
            }
             */
            return true
        }
    }
    /*
    override fun onTextInput(text: String?) {
        val event = Event.createSoftwareTextEvent(text, Constants.CODE_OUTPUT_TEXT)
        val completeInputTransaction: InputTransaction =
            inputLogic.onTextInput(
                settings.current,
                event,

            )

        TODO("updateStateAfterInputTransaction")
    }*/

    override fun onStartBatchInput() {
        inputLogic.onStartBatchInput(
            settings.current,
            helper.keyboardSwitcher,
        )
    }

    override fun onUpdateBatchInput(batchPointers: InputPointers?) {
        inputLogic.onUpdateBatchInput(batchPointers)
    }

    override fun onEndBatchInput(batchPointers: InputPointers?) {
        inputLogic.onEndBatchInput(batchPointers)
    }

    override fun onCancelBatchInput() {
        inputLogic.onCancelBatchInput()
    }

    override fun onCancelInput() {
        // GeneralIME does nothing
    }

    override fun onFinishSlidingInput() {
        // GeneralIME does nothing
    }

    override fun onCustomRequest(requestCode: Int): Boolean {
        return false // GeneralIME does nothing
    }

    override fun onMovePointer(steps: Int, select: Boolean?) {
        setNeutralSuggestionStrip()

        val shiftMode: Int = helper.keyboardShiftMode
        val select = select
            ?: ((shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFTED) || (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED))

        if (select) {
            inputLogic.disableRecapitalization()
        }

        if (steps < 0) {
            inputLogic.cursorLeft(steps, false, select)
        } else {
            inputLogic.cursorRight(steps, false, select)
        }
    }

    override fun onMoveDeletePointer(steps: Int) {
        setNeutralSuggestionStrip()
        if (inputLogic.mConnection.hasCursorPosition()) {
            val stepOverWords =
                settings.current.mBackspaceMode == Settings.BACKSPACE_MODE_WORDS
            if (steps < 0) {
                inputLogic.cursorLeft(steps, stepOverWords, true)
            } else {
                inputLogic.cursorRight(steps, stepOverWords, true)
            }
        } else {
            var steps = steps
            while (steps < 0) {
                // TODO: Verify this is right
                onEvent(
                    Event.createSoftwareKeypressEvent(
                        Constants.CODE_DELETE,
                        Event.NOT_A_CODE_POINT,
                        Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE,
                        false
                    )
                )
                steps++
            }
        }
    }

    override fun onUpWithDeletePointerActive() {
        if (inputLogic.mConnection.hasSelection()) {
            val selection: CharSequence? = inputLogic.mConnection.getSelectedText(0)
            if (selection != null) {
                val info = ArrayList<SuggestedWordInfo?>()
                info.add(
                    SuggestedWordInfo(
                        selection.toString(),
                        "",
                        0,
                        SuggestedWordInfo.KIND_UNDO,
                        null,
                        0,
                        0
                    )
                )
                showSuggestionStrip(
                    SuggestedWords(
                        info,
                        null,
                        null,
                        false,
                        false,
                        false,
                        0,
                        0
                    )
                )
                languageModelFacilitator.ignoreNextUpdate()
            }

            onEvent(
                Event.createSoftwareKeypressEvent(
                    Event.NOT_A_CODE_POINT,
                    Constants.CODE_DELETE,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE,
                    false
                )
            )

            inputLogic.resetComposingWord(
                Settings.getInstance().current,
                false
            )
        } else {
            onUpWithPointerActive()
        }
    }

    override fun onUpWithPointerActive() {
        inputLogic.restartSuggestionsOnWordTouchedByCursor(
            settings.current,
            false,
            helper.currentKeyboardScriptId
        )
    }

    override fun onSwipeLanguage(direction: Int) {
        switchToNextLanguage(context, direction)
    }

    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {
        // GeneralIME does nothing
    }

    override fun clearUserHistoryDictionaries() {
        dictionaryFacilitator.clearUserHistoryDictionary(context)
        resetDictionaryFacilitator(force = true) // do we need force?
    }

    override fun requestSuggestionRefresh() {
        updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING)
    }

    override fun getCurrentAutoCapsState(): Int =
        inputLogic.getCurrentAutoCapsState(settings.current)

    override fun getCurrentRecapitalizeState(): Int =
        inputLogic.currentRecapitalizeState

    override fun setCombiners(kinds: MutableList<CombinerKind>) {
        inputLogic.mWordComposer.setCombiners(kinds)
    }

    private val useExpandableUi = false
    override fun setNeutralSuggestionStrip() {
        inputLogic.setSuggestedWords(SuggestedWords.getEmptyInstance())
        helper.setNeutralSuggestionStrip(useExpandableUi)
    }

    override fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        inputLogic.setSuggestedWords(suggestedWords ?: SuggestedWords.getEmptyInstance())
        helper.showSuggestionStrip(suggestedWords, useExpandableUi)
    }

    fun debugInfo(): List<String> = listOf(
        "composingText = ${inputLogic.mConnection.composingTextForDebug}",
        "committedTextBeforeComposingText = ${inputLogic.mConnection.committedTextBeforeComposingTextForDebug}",
        "LM.shouldPassThroughToLegacy = ${languageModelFacilitator.shouldPassThroughToLegacy()}",
        "LM.isTransformerDisabledDueToTimeout = ${languageModelFacilitator.isTransformerDisabled()}"
    )
}