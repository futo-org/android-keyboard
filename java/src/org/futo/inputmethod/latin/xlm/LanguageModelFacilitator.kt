package org.futo.inputmethod.latin.xlm;

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.Suggest
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.utils.SuggestionResults


val AutocorrectThresholdSetting = SettingsKey(
    floatPreferencesKey("lm_autocorrect_threshold"),
    18.0f
)

public class LanguageModelFacilitator(
    val context: Context,
    val inputLogic: InputLogic,
    val dictionaryFacilitator: DictionaryFacilitator,
    val settings: Settings,
    val keyboardSwitcher: KeyboardSwitcher,
    val lifecycleScope: LifecycleCoroutineScope
) {
    private var languageModel: LanguageModel? = null
    data class PredictionInputValues(
        val composedData: ComposedData,
        val ngramContext: NgramContext,
        val inputStyle: Int,
        val sequenceId: Int
    )
    private val sharedFlow = MutableSharedFlow<PredictionInputValues>(replay = 0, extraBufferCapacity = 1)

    private var currentSequenceId = 0
    private val sequenceIdFinishedFlow = MutableSharedFlow<Int>(replay = 4, extraBufferCapacity = 4)

    private val computationSemaphore = Semaphore(1)
    public fun hasPendingUpdate(): Boolean =
        computationSemaphore.availablePermits == 0

    public fun blockUntilComplete() {
        runBlocking {
            try {
                withTimeout(1000L) {
                    computationSemaphore.acquire()
                    computationSemaphore.release()
                    try {
                        sequenceIdFinishedFlow.first { it >= currentSequenceId }
                    } catch (ignored: Exception) {

                    }
                }
            } catch(e: TimeoutCancellationException) {
                println("Failed to complete prediction within 1000ms!")
            }
        }
    }

    private suspend fun processUpdateSuggestionStrip(values: PredictionInputValues) {
        computationSemaphore.acquire()

        val autocorrectThreshold = context.getSetting(AutocorrectThresholdSetting)

        try {
            val job = Job()
            CoroutineScope(Dispatchers.Default + job).launch {
                delay(500)
                inputLogic.mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            }

            val locale = dictionaryFacilitator.locale
            if(languageModel == null) {
                languageModel = LanguageModel(context, "lm", locale)
            }

            val settingsValues = settings.current

            val keyboard = keyboardSwitcher.getKeyboard()
            val settingsForPrediction = SettingsValuesForSuggestion(
                settingsValues.mBlockPotentiallyOffensive,
                settingsValues.mTransformerPredictionEnabled
            )
            val proximityInfoHandle = keyboard.getProximityInfo().getNativeProximityInfo()
            
            val suggestionResults = SuggestionResults(
                3, values.ngramContext.isBeginningOfSentenceContext(), false)

            val lmSuggestions = languageModel!!.getSuggestions(
                values.composedData,
                values.ngramContext,
                keyboardSwitcher.mainKeyboardView.mKeyDetector,
                settingsForPrediction,
                proximityInfoHandle,
                -1,
                autocorrectThreshold,
                floatArrayOf())
            
            if(lmSuggestions == null) {
                job.cancel()
                inputLogic.mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
                return
            }

            suggestionResults.addAll(lmSuggestions)
            if(suggestionResults.mRawSuggestions != null) {
                suggestionResults.mRawSuggestions.addAll(lmSuggestions)
            }

            val wordComposer = inputLogic.mWordComposer
            val suggestedWords = Suggest.obtainNonBatchedInputSuggestedWords(
                wordComposer, values.inputStyle, true, -1, locale, suggestionResults, settingsValues.mAutoCorrectionThreshold)

            job.cancel()
            inputLogic.mSuggestionStripViewAccessor.showSuggestionStrip(suggestedWords)

            if(values.composedData.mIsBatchMode) {
                inputLogic.showBatchSuggestions(suggestedWords, values.inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH);
            }
            sequenceIdFinishedFlow.emit(values.sequenceId)
        } finally {
            computationSemaphore.release()
        }
    }

    public suspend fun destroyModel() {
        println("LanguageModelFacilitator is destroying model!")
        computationSemaphore.acquire()
        languageModel?.closeInternalLocked()
        languageModel = null
        computationSemaphore.release()
    }

    public fun launchProcessor() = lifecycleScope.launch {
        println("LatinIME: Starting processor")
        launch {
            withContext(Dispatchers.Default) {
                TrainingWorkerStatus.lmRequest.collect {
                    if (it == LanguageModelFacilitatorRequest.ResetModel) {
                        destroyModel()
                    }else if(it == LanguageModelFacilitatorRequest.ClearTrainingLog) {
                        historyLog.clear()
                        saveHistoryLog()
                    }
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                sharedFlow.conflate().collect { value ->
                    println("LatinIME: Collecting")
                    processUpdateSuggestionStrip(value)
                }
            }
        }

        scheduleTrainingWorkerBackground(context)
    }

    public fun shouldPassThroughToLegacy(): Boolean =
        (!settings.current.mTransformerPredictionEnabled) ||
                (languageModel?.let {
                    it.getLocale().language != dictionaryFacilitator.locale.language
                } ?: false)

    public fun updateSuggestionStripAsync(inputStyle: Int) {
        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions()) {
            inputLogic.mSuggestionStripViewAccessor.showSuggestionStrip(SuggestedWords.getEmptyInstance())
            return
        }

        if(!inputLogic.mConnection.isConnected) return

        try {
            val wordComposer = inputLogic.mWordComposer
            val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(
                settingsValues.mSpacingAndPunctuations,
                2
            )

            val values = PredictionInputValues(
                wordComposer.composedDataSnapshot,
                ngramContext,
                inputStyle,
                ++currentSequenceId
            )

            lifecycleScope.launch {
                println("LatinIME: Emitting values")
                sharedFlow.emit(values)
            }
        } catch(e: Exception) {
            println("Failed to get context, composed data snapshot, etc: $e")
            e.printStackTrace()
        }
    }

    private val historyLog: MutableList<HistoryLogForTraining> = mutableListOf()

    public fun addToHistory(
        word: String,
        wasAutoCapitalized: Boolean,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        blockPotentiallyOffensive: Boolean,
        importance: Int
    ) {
        if(shouldPassThroughToLegacy()) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val lastIdx = wordCtx.lastIndexOf(committedNgramCtx)
        if(lastIdx == -1) {
            //println("addToHistory: extraction failed, couldn't find ngram ctx in full ctx")
            return
        }

        val misspelledWord = wordCtx.substring(
            lastIdx + committedNgramCtx.length
        )
        if(misspelledWord.isNotBlank() && (!(misspelledWord.startsWith(" ") || committedNgramCtx == " ") || misspelledWord.endsWith(" ") || misspelledWord.trim().contains(" "))) {
            //println("addToHistory: extraction failed bad context. wordCtx=[$wordCtx]  --   committedNgramCtx=[$committedNgramCtx]  --  word=[$word]  --  fullNgram=[$ngramContext]")
            return
        }

        val ctxBeforeMisspelledWord = wordCtx.dropLast(misspelledWord.length)

        val key = committedNgramCtx.trim() + " " + word.trim()
        val logToAdd = if(misspelledWord.isNotBlank()) {
            // Correcting (ctx) misspelled -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                misspelledWord.trim(),
                word,
                importance,
                timeStampInSeconds
            )
        } else {
            // Predicted (ctx) -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                null,
                word,
                importance,
                timeStampInSeconds
            )
        }

        historyLog.add(logToAdd)
        //println("addToHistory: Adding $logToAdd")
    }

    public fun unlearnFromHistory(
        word: String,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        eventType: Int
    ) {
        if(shouldPassThroughToLegacy()) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val keyToSearch = committedNgramCtx.trim() + " " + word.trim()

        val logToRemove = historyLog.indexOfLast {
            it.key.startsWith(keyToSearch) || it.key == keyToSearch
        }

        if(logToRemove == -1) {
            //println("addToHistory: UNLEARN Couldn't find key $keyToSearch")
        } else {
            //println("addToHistory: Unlearning ${historyLog[logToRemove]}")
            historyLog.removeAt(logToRemove)
        }
    }

    public fun saveHistoryLog() {
        saveHistoryLogBackup(context, historyLog)
    }

    public fun loadHistoryLog() {
        assert(historyLog.isEmpty())
        loadHistoryLogBackup(context, historyLog)
    }
}