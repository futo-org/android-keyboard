package org.futo.inputmethod.engine.general

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.futo.inputmethod.annotations.UsedForTesting
import org.futo.inputmethod.engine.ExpandableSuggestionBarConfiguration
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.engine.NonExpandableSuggestionBar
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.event.InputTransaction
import org.futo.inputmethod.keyboard.KeyboardActionListener
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.Dictionary
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
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.utils.AsyncResultHolder
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicInteger

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


    fun resetDictionaryFacilitator(force: Boolean = false, reloadAllDicts: Boolean = false) {
        if(!context.isDirectBootUnlocked) return

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
            reloadAllDicts,
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
                        resetDictionaryFacilitator(force = true, reloadAllDicts = true)
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

    private val expandableExpandableCfg = ExpandableSuggestionBarConfiguration(true, false)
    private var expandableCfg: ExpandableSuggestionBarConfiguration = NonExpandableSuggestionBar
    override fun onStartInput() {
        expandableCfg = if(helper.context.getSetting(UseExpandableSuggestionsForGeneralIME)) {
            expandableExpandableCfg
        } else {
            NonExpandableSuggestionBar
        }
        resetSwipeSuggestionSession()

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
        resetSwipeSuggestionSession()
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
        val selectionChanged = oldSelStart != newSelStart || oldSelEnd != newSelEnd

        val cursorMovedByUser = inputLogic.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd,
            Settings.getInstance().current
        )

        if (swipeSuggestionSelectionUpdatesToIgnore > 0) {
            swipeSuggestionSelectionUpdatesToIgnore -= 1
        } else if (selectionChanged && cursorMovedByUser) {
            resetSwipeSuggestionSession()
        }
    }

    override fun isGestureHandlingAvailable(): Boolean =
        dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()

    private fun onEventInternal(event: Event, ignoreSuggestionUpdate: Boolean = false) {
        helper.requestCursorUpdate()

        if (isSwipeActionsModeEnabled() && event.eventType != Event.EVENT_TYPE_SUGGESTION_PICKED) {
            resetSwipeSuggestionSession()
        }

        val swipeActionPunctuationTransaction = handleSwipeActionTrailingSpacePunctuation(event)

        val inputTransaction = swipeActionPunctuationTransaction ?: when (event.eventType) {
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

    override fun onGetSuggestedWords(
        suggestedWords: SuggestedWords,
        inputStyle: Int,
        sequenceNumber: Int
    ) {
        if(sequenceNumber < sequenceId.get() && inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH) {
            return
        }
        val unfilteredWords = when {
            suggestedWords.isEmpty && (inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH ||
                    inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH
                    ) -> inputLogic.mSuggestedWords

            else -> suggestedWords
        }

        val words = unfilteredWords?.let { blacklist.filterBlacklistedSuggestions(it) } ?: SuggestedWords.getEmptyInstance()

        showSuggestionStrip(words)
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
    var lmUpdateJob: Job? = null
    private fun updateSuggestionsDictionaryInternal(inputStyle: Int, sequenceNumber: Int) {
        // This method returns null for us if LM is disabled
        val predictionInputValues = languageModelFacilitator.makePredictionInputValues(inputStyle)

        var dictResult: SuggestedWords? = null
        var lmResult: ArrayList<SuggestedWordInfo>? = null
        if(predictionInputValues != null) {
            // This runs asynchronously
            val lmResultHolder = AsyncResultHolder<ArrayList<SuggestedWordInfo>?>("LMSuggest")
            lmUpdateJob?.cancel()
            lmUpdateJob = helper.lifecycleScope.launch(languageModelFacilitator.languageModelScope) {
                val result = languageModelFacilitator.getLanguageModelSuggestions(predictionInputValues)
                lmResultHolder.set(result ?: arrayListOf())
            }

            // This runs synchronously
            inputLogic.getSuggestedWords(
                settings.current,
                helper.keyboardSwitcher.keyboard ?: return,
                helper.keyboardShiftMode,
                inputStyle,
                sequenceNumber
            ) { suggestedWords -> dictResult = suggestedWords }

            // Wait for LM to report result
            lmResult = lmResultHolder.get(null, 350L)
            if(lmResult == null) languageModelFacilitator.reportTimeout()
        } else {
            inputLogic.getSuggestedWords(
                settings.current,
                helper.keyboardSwitcher.keyboard ?: return,
                helper.keyboardShiftMode,
                inputStyle,
                sequenceNumber
            ) { suggestedWords -> dictResult = suggestedWords }
        }

        @Suppress("KotlinConstantConditions")
        when {
            !lmResult.isNullOrEmpty() && dictResult != null && predictionInputValues != null -> {
                val processed = languageModelFacilitator.processAndMergeSuggestions(
                    predictionInputValues,
                    dictResult,
                    lmResult
                )
                if(processed != null) {
                    onGetSuggestedWords(processed, inputStyle, sequenceNumber)
                } else {
                    throwIfDebug(IllegalStateException(
                        "The processAndMergeSuggestions method should not typically return null"
                    ))

                    onGetSuggestedWords(dictResult, inputStyle, sequenceNumber)
                }
            }

            dictResult != null -> {
                onGetSuggestedWords(dictResult, inputStyle, sequenceNumber)
            }

            // Note: we don't support LM results but not dict
            else -> {
                setNeutralSuggestionStrip()
            }
        }
    }


    private var sequenceId = AtomicInteger(0)
    private val sequenceIdCompleted = AtomicInteger(0)
    private val computationMutex = Mutex()
    private var timeTakenToUpdate = 40L
    private var swipeSuggestionIndex = -1
    private var swipeSuggestionWord: String? = null
    private var swipeSuggestionCandidates: List<SuggestedWordInfo>? = null
    private var swipeSuggestionRestingWord: String? = null
    private var swipeSuggestionRevertWord: String? = null
    private var swipeSuggestionSelectionUpdatesToIgnore = 0

    private fun resetSwipeSuggestionSession() {
        swipeSuggestionIndex = -1
        swipeSuggestionWord = null
        swipeSuggestionCandidates = null
        swipeSuggestionRestingWord = null
        swipeSuggestionRevertWord = null
    }

    private fun moveCursorForSwipeSuggestions(steps: Int) {
        swipeSuggestionSelectionUpdatesToIgnore += 1

        if (steps < 0) {
            inputLogic.cursorLeft(-steps, false, false)
        } else {
            inputLogic.cursorRight(steps, false, false)
        }
    }

    private fun replaceCommittedSwipeSuggestionIfNeeded(
        currentSwipeWord: String?,
        replacement: String
    ): Boolean {
        val resolvedSwipeWord = currentSwipeWord ?: inputLogic.mLastComposedWord.mCommittedWord?.toString()
        if (resolvedSwipeWord.isNullOrEmpty() || inputLogic.mConnection.hasSelection()) {
            return false
        }

        if (!inputLogic.mConnection.sameAsTextBeforeCursor(resolvedSwipeWord)) {
            return false
        }

        helper.requestCursorUpdate()
        inputLogic.mConnection.beginBatchEdit()
        inputLogic.mConnection.finishComposingText()
        inputLogic.mConnection.deleteTextBeforeCursor(resolvedSwipeWord.length)
        inputLogic.mConnection.commitText(replacement, 1)
        inputLogic.mConnection.endBatchEdit()
        inputLogic.mConnection.send()
        helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
        return true
    }

    private fun shouldResetSwipeSuggestionSessionForTouchedWord(touchedWord: String?): Boolean {
        if (swipeSuggestionCandidates == null && swipeSuggestionRestingWord == null && swipeSuggestionWord == null) {
            return false
        }

        if (touchedWord.isNullOrEmpty()) {
            return false
        }

        return touchedWord != swipeSuggestionWord && touchedWord != swipeSuggestionRestingWord
    }

    private fun getSwipeSuggestionInfo(
        candidates: List<SuggestedWordInfo>,
        word: String
    ): SuggestedWordInfo {
        val existing = candidates.firstOrNull { it.mWord == word }
        if (existing != null) {
            return existing
        }

        return SuggestedWordInfo(
            word,
            "",
            SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_TYPED,
            Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE
        )
    }

    private fun isSwipeActionsModeEnabled(): Boolean {
        return settings.current.mGestureActionsEnabled
    }

    private fun sendDeleteKeypress() {
        onEvent(
            Event.createSoftwareKeypressEvent(
                Event.NOT_A_CODE_POINT,
                Constants.CODE_DELETE,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false
            )
        )
    }

    private fun performSwipeWordDelete() {
        helper.requestCursorUpdate()

        val result = inputLogic.onWordBackspace(
            settings.current,
            helper.keyboardShiftMode,
            helper.currentKeyboardScriptId
        )
        val inputTransaction = result.mInputTransaction

        inputLogic.mConnection.send()

        when (inputTransaction.requiredShiftUpdate) {
            InputTransaction.SHIFT_UPDATE_LATER,
            InputTransaction.SHIFT_UPDATE_NOW ->
                helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
        }

        if (inputTransaction.requiresUpdateSuggestions()) {
            updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING)
        }

        showDeletedTextUndoSuggestion(result.mDeletedText)
    }

    private fun moveCursorToLastWordIfTrailingSpace(): Boolean {
        val beforeCursor = inputLogic.mConnection.getTextBeforeCursor(1, 0)?.toString()
        if (!beforeCursor.isNullOrEmpty()
            && beforeCursor.last() == ' '
            && inputLogic.mConnection.hasCursorPosition()
            && !inputLogic.mConnection.hasSelection()) {
            moveCursorForSwipeSuggestions(-1)
            return true
        }

        return false
    }

    private fun restoreCursorIfMoved(movedCursorToLastWord: Boolean) {
        if (movedCursorToLastWord) {
            moveCursorForSwipeSuggestions(1)
        }
    }

    private fun getSwipeActionTrailingSpacePunctuationCodePoint(event: Event): Int? {
        if (!isSwipeActionsModeEnabled()) {
            return null
        }

        val codePoint = when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> event.mCodePoint
            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> {
                val text = event.getTextToCommit().toString()
                if (text.codePointCount(0, text.length) != 1) {
                    return null
                }
                text.codePointAt(0)
            }
            else -> return null
        }

        if (codePoint == Event.NOT_A_CODE_POINT
            || Character.isWhitespace(codePoint)
            || !settings.current.isWordSeparator(codePoint)
            || !settings.current.isUsuallyFollowedBySpace(codePoint)
            || settings.current.isOptionallyPrecededBySpace(codePoint)) {
            return null
        }

        val beforeCursor = inputLogic.mConnection.getTextBeforeCursor(1, 0)?.toString()
        return if (!beforeCursor.isNullOrEmpty()
            && beforeCursor.last() == ' '
            && inputLogic.mConnection.hasCursorPosition()
            && !inputLogic.mConnection.hasSelection()) {
            codePoint
        } else {
            null
        }
    }

    private fun handleSwipeActionTrailingSpacePunctuation(event: Event): InputTransaction? {
        val punctuationCodePoint = getSwipeActionTrailingSpacePunctuationCodePoint(event)
            ?: return null
        inputLogic.mConnection.removeTrailingSpace()

        val normalizedEvent = Event.createSoftwareKeypressEvent(
            punctuationCodePoint,
            punctuationCodePoint,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE,
            false
        )

        val punctuationTransaction = inputLogic.onCodeInput(
            settings.current,
            normalizedEvent,
            helper.keyboardShiftMode,
            helper.currentKeyboardScriptId
        )

        val codePointAfterCursor = inputLogic.mConnection.getCodePointAfterCursor()
        if (inputLogic.mConnection.spaceFollowsCursor()
            || (codePointAfterCursor != Constants.NOT_A_CODE
                && Character.isWhitespace(codePointAfterCursor))) {
            return punctuationTransaction
        }

        return inputLogic.onCodeInput(
            settings.current,
            Event.createSoftwareKeypressEvent(
                Constants.CODE_SPACE,
                Constants.CODE_SPACE,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false
            ),
            helper.keyboardShiftMode,
            helper.currentKeyboardScriptId
        )
    }

    private fun getSwipePunctuationCycle(): List<String> {
        val cycle = LinkedHashSet<String>()

        val suggestedPunctuation = settings.current.mSpacingAndPunctuations.mSuggestPuncList
        for (index in 0 until suggestedPunctuation.size()) {
            val punctuation = suggestedPunctuation.getWord(index)
            if (!punctuation.isNullOrEmpty() && punctuation.codePointCount(0, punctuation.length) == 1) {
                cycle.add(punctuation)
            }
        }

        if (cycle.isEmpty()) {
            return emptyList()
        }

        val ordered = cycle.toMutableList()
        val commaIndex = ordered.indexOf(",")
        if (commaIndex > 0) {
            ordered.removeAt(commaIndex)
            ordered.add(0, ",")
        }

        return ordered
    }

    private fun replacePunctuationWith(replacement: String): Boolean {
        if (replacement.codePointCount(0, replacement.length) != 1) {
            return false
        }

        resetSwipeSuggestionSession()
        sendDeleteKeypress()

        val replacementCodePoint = replacement.codePointAt(0)
        onEvent(
            Event.createSoftwareKeypressEvent(
                replacementCodePoint,
                replacementCodePoint,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false
            )
        )

        return true
    }

    private fun trySwipeCyclePunctuation(direction: Int): Boolean {
        val beforeCursor = inputLogic.mConnection.getTextBeforeCursor(1, 0)?.toString()
        if (beforeCursor.isNullOrEmpty()) {
            return false
        }

        val currentPunctuation = beforeCursor.last().toString()
        val cycle = getSwipePunctuationCycle()
        if (cycle.size < 2) {
            return false
        }

        val currentIndex = cycle.indexOf(currentPunctuation)
        if (currentIndex < 0) {
            if (currentPunctuation == ".") {
                val replacement = if (direction == KeyboardActionListener.SWIPE_ACTION_UP) {
                    cycle.last()
                } else {
                    cycle.first()
                }
                return replacePunctuationWith(replacement)
            }

            return false
        }

        val step = if (direction == KeyboardActionListener.SWIPE_ACTION_UP) -1 else 1
        val nextIndex = (currentIndex + step + cycle.size) % cycle.size
        val replacement = cycle[nextIndex]
        if (replacement == currentPunctuation) {
            return false
        }

        return replacePunctuationWith(replacement)
    }

    fun updateSuggestions(inputStyle: Int) {
        updateSuggestionJob?.cancel()

        val isSwipe = inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH
                || inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH

        if(!isSwipe) {
            if (!settings.current.needsToLookupSuggestions()) {
                setNeutralSuggestionStrip()
                return
            }

            if (!inputLogic.mWordComposer.isComposingWord && !settings.current.mBigramPredictionEnabled) {
                setNeutralSuggestionStrip()
                return
            }
        }

        val seqId = sequenceId.incrementAndGet()

        val delayTime = when {
            // With transformer off keep 40ms static delay for legacy reasons (less battery use)
            languageModelFacilitator.shouldPassThroughToLegacy() -> 40L

            // On fast devices, prefer to wait less to improve responsiveness
            else -> 40L.coerceAtMost(timeTakenToUpdate / 2).coerceAtLeast(16L)
        }

        updateSuggestionJob = helper.lifecycleScope.launch {
            when(inputStyle) {
                SuggestedWords.INPUT_STYLE_TYPING -> delay(delayTime)
            }
            withContext(NonCancellable + dictionaryScope) {
                computationMutex.withLock {
                    // double check in case sequence id incremented after we acquired scope
                    if (sequenceId.get() > seqId) return@withContext

                    val t0 = System.currentTimeMillis()
                    updateSuggestionsDictionaryInternal(inputStyle, seqId)
                    val t1 = System.currentTimeMillis()
                    timeTakenToUpdate = (timeTakenToUpdate + (t1 - t0)) / 2

                    if(BuildConfig.DEBUG) Log.d(TAG, "Time taken for suggestions update = ${t1-t0} ms (avg $timeTakenToUpdate ms)")

                    sequenceIdCompleted.set(seqId)
                }
            }
        }
    }

    fun ensureSuggestionsCompleted(): Boolean {
        val currJob = updateSuggestionJob

        val seqId = sequenceId.get()
        if(sequenceIdCompleted.get() < seqId) {
            currJob?.cancel()

            val newJob = helper.lifecycleScope.launch(NonCancellable + dictionaryScope) {
                if(sequenceIdCompleted.get() < seqId) {
                    updateSuggestionsDictionaryInternal(SuggestedWords.INPUT_STYLE_TYPING, seqId)
                    sequenceIdCompleted.set(seqId)
                }
            }

            updateSuggestionJob = newJob

            return runBlocking {
                (withTimeoutOrNull(450L) {
                    newJob.join()
                    true
                } == true)
            }
        }
        return true
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

            showDeletedTextUndoSuggestion(selection?.toString())
        } else {
            onUpWithPointerActive()
        }
    }

    private fun showDeletedTextUndoSuggestion(deletedText: String?) {
        if (deletedText == null) {
            return
        }

        val info = ArrayList<SuggestedWordInfo?>()
        info.add(
            SuggestedWordInfo(
                deletedText,
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

    override fun onSwipeAction(direction: Int) {
        if (!isSwipeActionsModeEnabled()) return

        when (direction) {
            KeyboardActionListener.SWIPE_ACTION_RIGHT -> {
                resetSwipeSuggestionSession()
                onEvent(
                    Event.createSoftwareKeypressEvent(
                        Constants.CODE_SPACE,
                        Constants.CODE_SPACE,
                        Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE,
                        false
                    )
                )
            }

            KeyboardActionListener.SWIPE_ACTION_LEFT -> {
                resetSwipeSuggestionSession()
                setNeutralSuggestionStrip()
                performSwipeWordDelete()
            }

            KeyboardActionListener.SWIPE_ACTION_UP,
            KeyboardActionListener.SWIPE_ACTION_DOWN -> {
                val lastComposedWordAtSwipeStart = inputLogic.mLastComposedWord
                val movedCursorToLastWord = moveCursorToLastWordIfTrailingSpace()

                if (trySwipeCyclePunctuation(direction)) {
                    restoreCursorIfMoved(movedCursorToLastWord)
                    return
                }

                inputLogic.restartSuggestionsOnWordTouchedByCursor(
                    settings.current,
                    null,
                    false,
                    helper.currentKeyboardScriptId
                )

                if (!ensureSuggestionsCompleted()) {
                    restoreCursorIfMoved(movedCursorToLastWord)
                    return
                }

                val touchedWord = inputLogic.mWordComposer.typedWord

                if (shouldResetSwipeSuggestionSessionForTouchedWord(touchedWord)) {
                    resetSwipeSuggestionSession()
                }

                if (swipeSuggestionRestingWord == null) {
                    swipeSuggestionRestingWord = touchedWord
                }

                if (swipeSuggestionRevertWord == null) {
                    val committedWord = lastComposedWordAtSwipeStart.mCommittedWord?.toString()
                    val typedWordFromLastCommit = lastComposedWordAtSwipeStart.mTypedWord
                    if (lastComposedWordAtSwipeStart.canRevertCommit()
                        && !typedWordFromLastCommit.isNullOrEmpty()
                        && !committedWord.isNullOrEmpty()
                        && touchedWord == committedWord
                        && typedWordFromLastCommit != committedWord) {
                        swipeSuggestionRevertWord = typedWordFromLastCommit
                    }
                }

                val candidates = swipeSuggestionCandidates ?: run {
                    val suggestions = inputLogic.mSuggestedWords
                    val rebuiltCandidates = ArrayList<SuggestedWordInfo>()
                    val seen = HashSet<String>()
                    for (index in 0 until suggestions.size()) {
                        val info = suggestions.getInfo(index)
                        if (!info.isKindOf(SuggestedWordInfo.KIND_UNDO) && seen.add(info.mWord)) {
                            rebuiltCandidates.add(info)
                        }
                    }
                    swipeSuggestionCandidates = rebuiltCandidates
                    rebuiltCandidates
                }

                if (direction == KeyboardActionListener.SWIPE_ACTION_UP) {
                    val restingWord = swipeSuggestionRestingWord
                    val currentWord = swipeSuggestionWord ?: touchedWord

                    if (!swipeSuggestionWord.isNullOrEmpty()
                        && swipeSuggestionIndex in candidates.indices
                        && candidates[swipeSuggestionIndex].mWord == swipeSuggestionWord) {
                        val previousIndex = (swipeSuggestionIndex - 1 + candidates.size) % candidates.size
                        val selected = if (!restingWord.isNullOrEmpty()
                            && swipeSuggestionIndex == 0) {
                            getSwipeSuggestionInfo(candidates, restingWord)
                        } else {
                            candidates[previousIndex]
                        }
                        if (!replaceCommittedSwipeSuggestionIfNeeded(
                                swipeSuggestionWord ?: currentWord,
                                selected.mWord
                            )) {
                            onEvent(Event.createSuggestionPickedEvent(selected))
                        }
                        if (!restingWord.isNullOrEmpty() && selected.mWord == restingWord) {
                            swipeSuggestionIndex = -1
                            swipeSuggestionWord = null
                        } else {
                            swipeSuggestionIndex = previousIndex
                            swipeSuggestionWord = selected.mWord
                        }
                        restoreCursorIfMoved(movedCursorToLastWord)
                        return
                    }

                    val revertWord = swipeSuggestionRevertWord
                    if (!revertWord.isNullOrEmpty()
                        && !currentWord.isNullOrEmpty()
                        && currentWord == restingWord
                        && revertWord != currentWord) {
                        val selected = getSwipeSuggestionInfo(candidates, revertWord)
                        onEvent(Event.createSuggestionPickedEvent(selected))
                        swipeSuggestionIndex = -1
                        swipeSuggestionWord = null
                        swipeSuggestionRestingWord = selected.mWord
                        swipeSuggestionRevertWord = null
                        swipeSuggestionCandidates = null
                        restoreCursorIfMoved(movedCursorToLastWord)
                        return
                    }

                    restoreCursorIfMoved(movedCursorToLastWord)
                    return
                }

                if (candidates.size < 2) {
                    resetSwipeSuggestionSession()
                    restoreCursorIfMoved(movedCursorToLastWord)
                    return
                }

                val typedWord = inputLogic.mWordComposer.typedWord
                val currentWord = swipeSuggestionWord ?: swipeSuggestionRestingWord ?: typedWord
                val typedWordIndex = if (typedWord != null) {
                    candidates.indexOfFirst { it.mWord == typedWord }
                } else {
                    -1
                }
                val currentWordIndex = if (currentWord != null) {
                    candidates.indexOfFirst { it.mWord == currentWord }
                } else {
                    -1
                }

                val baseIndex = if (swipeSuggestionWord != null
                    && swipeSuggestionIndex in candidates.indices
                    && candidates[swipeSuggestionIndex].mWord == swipeSuggestionWord) {
                    swipeSuggestionIndex
                } else if (currentWordIndex >= 0) {
                    currentWordIndex
                } else if (typedWordIndex >= 0) {
                    typedWordIndex
                } else {
                    0
                }

                val step = 1
                var nextIndex = (baseIndex + step + candidates.size) % candidates.size
                if (currentWord != null && candidates[nextIndex].mWord == currentWord) {
                    nextIndex = (nextIndex + step + candidates.size) % candidates.size
                }

                val selected = candidates[nextIndex]
                if (currentWord != null && selected.mWord == currentWord) {
                    restoreCursorIfMoved(movedCursorToLastWord)
                    return
                }

                if (!replaceCommittedSwipeSuggestionIfNeeded(
                        swipeSuggestionWord ?: currentWord,
                        selected.mWord
                    )) {
                    onEvent(Event.createSuggestionPickedEvent(selected))
                }
                swipeSuggestionIndex = nextIndex
                swipeSuggestionWord = selected.mWord

                restoreCursorIfMoved(movedCursorToLastWord)
            }
        }
    }

    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {
        // GeneralIME does nothing
    }

    override fun clearUserHistoryDictionaries() {
        dictionaryFacilitator.clearUserHistoryDictionary(context)
        resetDictionaryFacilitator(force = true)
    }

    override fun requestSuggestionRefresh() {
        inputLogic.mSuggestedWords?.let {
            onGetSuggestedWords(
                it,
                it.mInputStyle,
                sequenceIdCompleted.get()
            )
        }
    }

    override fun getCurrentAutoCapsState(): Int =
        inputLogic.getCurrentAutoCapsState(settings.current)

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {
        inputLogic.mWordComposer.setCombiners(layout.mainLayout.combiners)
    }

    override fun setNeutralSuggestionStrip() {
        inputLogic.setSuggestedWords(SuggestedWords.getEmptyInstance())
        helper.setNeutralSuggestionStrip(expandableCfg)
    }

    val blacklist = SuggestionBlacklist(Settings.getInstance(), helper.context, helper.lifecycleScope)
    override fun showSuggestionStrip(words: SuggestedWords?) {
        inputLogic.setSuggestedWords(words)

        if(settings.current.isSuggestionsEnabledPerUserSettings) {
            helper.showSuggestionStrip(words, expandableCfg)
        } else {
            helper.setNeutralSuggestionStrip(expandableCfg)
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
        add("dictionary loaded = ${dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()}, ${!dictionaryFacilitator.hasAtLeastOneUninitializedMainDictionary()}")
        add("autoCapsFlags = ${getCurrentAutoCapsState()}")
    }

    fun debugInfoS() = debugInfo().joinToString("\n")

    @UsedForTesting
    override fun recycle() {
        inputLogic.recycle()
    }

    fun cursorStepped(steps: Int, overWords: Boolean) {
        if(!settings.current.mVibrateOn) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            helper.keyboardSwitcher.mainKeyboardView?.performHapticFeedback(
                if(overWords) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        };
    }

    override fun hasMoreTextToDelete(): Boolean =
        inputLogic.mConnection.codePointBeforeCursor != Constants.NOT_A_CODE
                || inputLogic.mWordComposer.isComposingWord

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        val dictionaryScope = Dispatchers.Default.limitedParallelism(1)
    }
}
