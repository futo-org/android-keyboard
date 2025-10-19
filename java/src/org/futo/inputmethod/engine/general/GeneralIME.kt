package org.futo.inputmethod.engine.general

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.annotations.UsedForTesting
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
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2

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

val UseExpandableSuggestionsForGeneralIME = SettingsKey(
    booleanPreferencesKey("use_expandable_suggestions_for_generalime"),
    false
)

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
                updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING)
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


    fun resetDictionaryFacilitator(force: Boolean = false, reloadMainDictionary: Boolean = false) {
        val settings = settings.current

        val locales = settings.mInputAttributes.mLocaleOverride?.let {
            listOf(it)
        } ?: RichInputMethodManager.getInstance().currentSubtypeLocales
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
            reloadMainDictionary,
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
                        resetDictionaryFacilitator(force = true, reloadMainDictionary = true)
                    }
                    else -> {}
                }
            }
        }

        blacklist.init()
    }

    override fun onDestroy() {
        dictionaryFacilitator.closeDictionaries()
        languageModelFacilitator.saveHistoryLog()

        runBlocking {
            languageModelFacilitator.destroyModel()
            languageModelFacilitator.close()
        }
    }

    override fun onDeviceUnlocked() {
        languageModelFacilitator.loadHistoryLog()
    }

    override fun onStartInput() {
        useExpandableUi = helper.context.getSetting(UseExpandableSuggestionsForGeneralIME)

        resetDictionaryFacilitator()
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
        updateSuggestionJob?.cancel()
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

    private fun onEventInternal(event: Event, ignoreSuggestionUpdate: Boolean = false) {
        helper.requestCursorUpdate()

        val inputTransaction = when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> {
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

        inputLogic.mConnection.send()

        when(inputTransaction?.requiredShiftUpdate) {
            InputTransaction.SHIFT_UPDATE_LATER,
            InputTransaction.SHIFT_UPDATE_NOW ->
                helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
        }

        if(inputTransaction?.requiresUpdateSuggestions() == true && !ignoreSuggestionUpdate) {
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

    override fun onEvent(event: Event) = onEventInternal(event)

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
        if(!settings.current.needsToLookupSuggestions()) {
            updateSuggestionJob?.cancel()
            setNeutralSuggestionStrip()
            return
        }

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
            if(updateSuggestionJob?.isActive == true) {
                updateSuggestionJob?.cancel()
                runBlocking(dictionaryScope) {
                    updateSuggestionsDictionaryInternal(SuggestedWords.INPUT_STYLE_TYPING)
                }
            }
            return true
        }
    }

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

    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {
        setNeutralSuggestionStrip()

        val shiftMode: Int = helper.keyboardShiftMode
        val select = select
            ?: ((shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFTED) || (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED))

        if (select) {
            inputLogic.disableRecapitalization()
        }

        if (steps < 0) {
            inputLogic.cursorLeft(steps, stepOverWords, select)
        } else {
            inputLogic.cursorRight(steps, stepOverWords, select)
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
                onEvent(
                    Event.createSoftwareKeypressEvent(
                        Event.NOT_A_CODE_POINT,
                        Constants.CODE_DELETE,
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

            onEventInternal(
                Event.createSoftwareKeypressEvent(
                    Event.NOT_A_CODE_POINT,
                    Constants.CODE_DELETE,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE,
                    false
                ),
                ignoreSuggestionUpdate = true
            )

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
            }
        } else {
            onUpWithPointerActive()
        }
    }

    override fun onUpWithPointerActive() {
        inputLogic.restartSuggestionsOnWordTouchedByCursor(
            settings.current, null,
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
        resetDictionaryFacilitator(force = true)
    }

    override fun requestSuggestionRefresh() {
        updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING)
    }

    override fun getCurrentAutoCapsState(): Int =
        inputLogic.getCurrentAutoCapsState(settings.current)

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {
        inputLogic.mWordComposer.setCombiners(layout.mainLayout.combiners)
    }

    private var useExpandableUi = false
    override fun setNeutralSuggestionStrip() {
        inputLogic.setSuggestedWords(SuggestedWords.getEmptyInstance())
        helper.setNeutralSuggestionStrip(useExpandableUi)
    }

    val blacklist = SuggestionBlacklist(Settings.getInstance(), helper.context, helper.lifecycleScope)
    override fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        val words = suggestedWords?.let { blacklist.filterBlacklistedSuggestions(it) } ?: SuggestedWords.getEmptyInstance()
        inputLogic.setSuggestedWords(words)

        if(settings.current.isSuggestionsEnabledPerUserSettings) {
            helper.showSuggestionStrip(words, useExpandableUi)
        } else {
            helper.setNeutralSuggestionStrip(useExpandableUi)
        }
    }

    fun debugInfo(): List<String> = buildList {
        if(!settings.current.mInputAttributes.mIsPasswordField) {
            add("composingText = ${inputLogic.mConnection.composingTextForDebug}")
            add("committedTextBeforeComposingText = ${inputLogic.mConnection.committedTextBeforeComposingTextForDebug}")
        }
        add("LM.shouldPassThroughToLegacy = ${languageModelFacilitator.shouldPassThroughToLegacy()}")
        add("LM.isTransformerDisabledDueToTimeout = ${languageModelFacilitator.isTransformerDisabled()}")
        add("expected cursor = ${inputLogic.mConnection.mExpectedSelStart}:${inputLogic.mConnection.mExpectedSelEnd}")
    }

    fun debugInfoS() = debugInfo().joinToString("\n")

    @UsedForTesting
    override fun recycle() {
        inputLogic.recycle()
    }
}