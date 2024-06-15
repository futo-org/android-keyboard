package org.futo.inputmethod.latin.xlm;

import android.content.Context
import android.util.Log
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
import org.futo.inputmethod.latin.BinaryDictionary
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.Suggest
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.actions.PersistentEmojiState
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.utils.AsyncResultHolder
import org.futo.inputmethod.latin.utils.SuggestionResults


val AutocorrectThresholdSetting = SettingsKey(
    floatPreferencesKey("lm_autocorrect_threshold"),
    4.0f
)

val BinaryDictTransformerWeightSetting = SettingsKey(
    floatPreferencesKey("binary_dict_result_weight"),
    3.4f
)

private fun SuggestedWordInfo.add(other: SuggestedWordInfo): SuggestedWordInfo {
    assert(mWord == other.mWord)

    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        (mScore.coerceAtLeast(0).toLong() + other.mScore.coerceAtLeast(0).toLong())
            .coerceAtMost(
                Int.MAX_VALUE.toLong()
            ).toInt(),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        null,
        0,
        0
    )

    result.mOriginatesFromTransformerLM = mOriginatesFromTransformerLM || other.mOriginatesFromTransformerLM

    return result
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length

    val dist = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) {
        dist[i][0] = i
    }
    for (j in 0..len2) {
        dist[0][j] = j
    }

    for (j in 1..len2) {
        for (i in 1..len1) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dist[i][j] = minOf(
                dist[i - 1][j] + 1,
                dist[i][j - 1] + 1,
                dist[i - 1][j - 1] + cost
            )
        }
    }

    return dist[len1][len2]
}

private fun areWordsRoughlyEqual(word1: String, word2: String, threshold: Int): Boolean {
    val distance = levenshteinDistance(word1, word2)
    return distance <= threshold
}



public class LanguageModelFacilitator(
    val context: Context,
    val inputLogic: InputLogic,
    val dictionaryFacilitator: DictionaryFacilitator,
    val settings: Settings,
    val keyboardSwitcher: KeyboardSwitcher,
    val lifecycleScope: LifecycleCoroutineScope,
    val suggestionBlacklist: SuggestionBlacklist
) {
    private val userDictionary = UserDictionaryObserver(context)
    private val emojiData = PersistentEmojiState()

    private var shouldSuggestEmojis = SHOW_EMOJI_SUGGESTIONS.default
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


    private var numConsecutiveTimeouts = 0
    private var transformerDisabled = false
    public fun blockUntilComplete(): Boolean {
        runBlocking {
            try {
                withTimeout(700L) {
                    computationSemaphore.acquire()
                    computationSemaphore.release()
                    try {
                        sequenceIdFinishedFlow.first { it >= currentSequenceId }
                    } catch (ignored: Exception) {

                    }
                }
                numConsecutiveTimeouts = 0
            } catch(e: TimeoutCancellationException) {
                Log.d("LanguageModelFacilitator", "Failed to complete prediction within the time!")
                numConsecutiveTimeouts += 1
                if(numConsecutiveTimeouts > 5) {
                    transformerDisabled = true
                    Log.w("LanguageModelFacilitator", "Temporarily disabling transformer due to continuous timeouts")
                }
                return@runBlocking false
            }
        }
        return true
    }

    private fun getEmojiCandidate(word: String): SuggestedWordInfo? {
        val emoji = emojiData.emojiAliases[word.lowercase()]

        if(emoji != null) {
            return SuggestedWordInfo(
                emoji.emoji,
                "",
                100,
                SuggestedWordInfo.KIND_EMOJI_SUGGESTION,
                null,
                SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        } else {
            return null
        }
    }

    private var skipLanguage: String? = null
    private suspend fun runLanguageModel(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(transformerDisabled) return null

        val locale = dictionaryFacilitator.locale ?: return null
        if ((languageModel == null && locale.language != skipLanguage) || (languageModel?.locale?.language != locale.language)) {
            skipLanguage = null
            Log.d(
                "LanguageModelFacilitator",
                "Calling closeInternalLocked on model due to seeming locale change"
            )
            languageModel?.closeInternalLocked()
            languageModel = null

            // TODO: Cache value so we're not hitting this repeatedly
            val options = ModelPaths.getModelOptions(context)
            val model = options[locale.language]
            if (model != null) {
                languageModel = LanguageModel(context, lifecycleScope, model, locale)
            } else {
                Log.d("LanguageModelFacilitator", "no model for ${locale.language}")
                skipLanguage = locale.language
                return null
            }
        }

        val settingsValues = settings.current ?: return null

        val keyboard = keyboardSwitcher.keyboard ?: return null
        val settingsForPrediction = SettingsValuesForSuggestion(
            settingsValues.mBlockPotentiallyOffensive,
            settingsValues.mTransformerPredictionEnabled
        )
        val proximityInfoHandle = keyboard.proximityInfo.nativeProximityInfo

        val autocorrectThreshold = context.getSetting(AutocorrectThresholdSetting)

        return languageModel?.getSuggestions(
            values.composedData,
            values.ngramContext,
            keyboardSwitcher.mainKeyboardView.mKeyDetector,
            settingsForPrediction,
            proximityInfoHandle,
            -1,
            autocorrectThreshold,
            floatArrayOf(),
            userDictionary.getWords().map { it.word },
            suggestionBlacklist.currentBlacklist.toTypedArray<String>()
        )
    }

    private suspend fun processUpdateSuggestionStrip(values: PredictionInputValues) {
        if(keyboardSwitcher.keyboard == null) return

        computationSemaphore.acquire()

        inputLogic.mWordComposer.setAutoCorrection(null)

        if(values.composedData.mTypedWord.length > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH) {
            inputLogic.mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        }

        try {
            var transformerWeight = context.getSetting(BinaryDictTransformerWeightSetting)

            val holder = AsyncResultHolder<SuggestedWords?>("Suggest")
            inputLogic.getSuggestedWords(
                settings.current,
                keyboardSwitcher.keyboard,
                keyboardSwitcher.keyboardShiftMode,
                values.inputStyle,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER
            ) { suggestedWords ->
                holder.set(suggestedWords)
            }

            val job = Job()
            CoroutineScope(Dispatchers.Default + job).launch {
                delay(500)
                inputLogic.mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            }


            val suggestionResults = SuggestionResults(
                14, values.ngramContext.isBeginningOfSentenceContext, false)

            val lmSuggestions = runLanguageModel(values)

            if(lmSuggestions == null) {
                holder.get(null, Constants.GET_SUGGESTED_WORDS_TIMEOUT.toLong())?.let { results ->
                    job.cancel()

                    val useRescoring = false

                    val finalResults = if(useRescoring && values.composedData.mIsBatchMode) {
                        val rescored = languageModel?.rescoreSuggestions(
                            results,
                            values.composedData,
                            values.ngramContext,
                            keyboardSwitcher.mainKeyboardView.mKeyDetector,
                            userDictionary.getWords().map { it.word }
                        )

                        if(rescored != null) {
                            SuggestedWords(
                                ArrayList(rescored),
                                // TODO: These should ideally not be null/false
                                null,
                                null,
                                false,
                                false,
                                false,
                                results.mInputStyle,
                                results.mSequenceNumber
                            )
                            // TODO: We need the swapping rejection thing, the rescored array is resorted without the swapping
                        } else {
                            results
                        }
                    } else {
                        results
                    }

                    finalResults.mSuggestedWordInfoList.removeAll {
                        !suggestionBlacklist.isSuggestedWordOk(it)
                    }

                    finalResults.mRawSuggestions?.removeAll {
                        !suggestionBlacklist.isSuggestedWordOk(it)
                    }

                    inputLogic.mSuggestionStripViewAccessor.showSuggestionStrip(finalResults)

                    if(values.composedData.mIsBatchMode) {
                        inputLogic.showBatchSuggestions(finalResults, values.inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH);
                    }

                    sequenceIdFinishedFlow.emit(values.sequenceId)
                }
                return
            }

            val reweightedSuggestions = lmSuggestions.mapIndexedNotNull { i, it ->
                if(transformerWeight == Float.NEGATIVE_INFINITY) { null } else {
                    SuggestedWordInfo(
                        it.mWord,
                        it.mPrevWordsContext,
                        (it.mScore.toFloat() * transformerWeight).toLong().coerceAtMost(Int.MAX_VALUE.toLong() - lmSuggestions.size)
                            .toInt() - i + (lmSuggestions.size - 1),
                        it.mKindAndFlags,
                        it.mSourceDict,
                        it.mIndexOfTouchPointOfSecondWord,
                        it.mAutoCommitFirstWordConfidence
                    ).apply {
                        this.mOriginatesFromTransformerLM = true
                    }
                }
            }

            val maxWord = reweightedSuggestions.maxByOrNull { it.mScore }

            val suggestedWordsDict = holder.get(null, Constants.GET_SUGGESTED_WORDS_TIMEOUT.toLong())

            val suggestedWordsDictList = suggestedWordsDict?.mSuggestedWordInfoList?.filter {
                suggestionBlacklist.isSuggestedWordOk(it)
            }

            val maxWordDict = suggestedWordsDictList?.maxByOrNull {
                if(it == suggestedWordsDict.typedWordInfo) { Int.MIN_VALUE } else { it.mScore }
            }

            val bothAlgorithmsCameToSameConclusion = maxWordDict?.mWord == maxWord?.mWord

            var autocorrectWord: SuggestedWordInfo? = null
            val filtered = mutableListOf<SuggestedWordInfo>()
            if(bothAlgorithmsCameToSameConclusion && maxWord != null && maxWordDict != null){
                // We can be pretty confident about autocorrecting this
                val clone = maxWord.add(maxWordDict)
                autocorrectWord = clone
                suggestionResults.add(clone)
                filtered.add(maxWordDict)
                filtered.add(maxWord)
            }

            if(transformerWeight <= 0.0f) {
                if(suggestedWordsDictList.isNullOrEmpty()) {
                    transformerWeight = 1.0f
                }
            }

            suggestionResults.addAll(reweightedSuggestions.filter { !filtered.contains(it) })
            if(suggestionResults.mRawSuggestions != null) {
                suggestionResults.mRawSuggestions.addAll(reweightedSuggestions.filter { !filtered.contains(it) })
            }

            if(transformerWeight != Float.POSITIVE_INFINITY) {
                suggestedWordsDictList?.let { words ->
                    suggestionResults.addAll(words.filter {
                        it != suggestedWordsDict.typedWordInfo && !filtered.contains(
                            it
                        )
                    }.take(10))
                }
            }

            if(values.composedData.mTypedWord.isNotEmpty() && shouldSuggestEmojis) {
                (getEmojiCandidate(values.composedData.mTypedWord)
                    ?: autocorrectWord?.let {
                        if(areWordsRoughlyEqual(autocorrectWord.mWord, values.composedData.mTypedWord, 2))
                            getEmojiCandidate(it.mWord)
                        else null
                    })?.let {
                    suggestionResults.add(it)
                }
            }else if(shouldSuggestEmojis) {
                val prevWord =
                    values.ngramContext.fullContext.split(" ").lastOrNull { it.isNotBlank() }
                if(prevWord != null) {
                    getEmojiCandidate(prevWord.trim())?.let {
                        suggestionResults.add(it)
                    }
                }
            }

            val settingsValues = settings.current ?: return
            val locale = dictionaryFacilitator.locale ?: return
            val wordComposer = inputLogic.mWordComposer ?: return

            val suggestedWords = Suggest.obtainNonBatchedInputSuggestedWords(
                wordComposer, values.inputStyle, true, -1, locale, suggestionResults, settingsValues.mAutoCorrectionThreshold)

            job.cancel()

            // TODO
            if(values.sequenceId < currentSequenceId) return
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
        Log.d("LanguageModelFacilitator", "destroyModel called")
        languageModel?.closeInternalLocked()
        languageModel = null
    }

    private var trainingEnabled = true

    public fun launchProcessor() = lifecycleScope.launch {
        Log.d("LanguageModelFacilitator", "Starting processor")
        launch {
            withContext(Dispatchers.Default) {
                TrainingWorkerStatus.lmRequest.collect {
                    if (it == LanguageModelFacilitatorRequest.ResetModel) {
                        Log.d("LanguageModelFacilitator", "ResetModel event received, destroying model")
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
                ModelPaths.modelOptionsUpdated.collect {
                    Log.d("LanguageModelFacilitator", "ModelPaths options updated, destroying model")
                    skipLanguage = null
                    destroyModel()
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                sharedFlow.conflate().collect { value ->
                    //Log.d("LanguageModelFacilitator", "Collecting")
                    processUpdateSuggestionStrip(value)
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                trainingEnabled = context.getSetting(USE_TRANSFORMER_FINETUNING)

                val shouldTrain = context.getSettingFlow(USE_TRANSFORMER_FINETUNING)
                shouldTrain.collect {
                    trainingEnabled = it
                }
            }
        }

        launch {
            emojiData.loadEmojis(context)
        }

        launch {
            withContext(Dispatchers.Default) {
                context.getSettingFlow(SHOW_EMOJI_SUGGESTIONS).collect { shouldSuggestEmojis = it }
            }
        }

        scheduleTrainingWorkerBackground(context)
    }

    public fun shouldPassThroughToLegacy(): Boolean =
        (!settings.current.mTransformerPredictionEnabled) ||
                (languageModel?.let {
                    it.locale.language != dictionaryFacilitator.locale.language
                } ?: false)

    public fun updateSuggestionStripAsync(inputStyle: Int) {
        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions()) {
            inputLogic.mSuggestionStripViewAccessor.showSuggestionStrip(SuggestedWords.getEmptyInstance())
            return
        }

        if(!inputLogic.mConnection.isConnected) return

        if(ignoringNextUpdate) {
            ignoringNextUpdate = false
            return
        }

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
                //Log.d("LanguageModelFacilitator", "Emitting values")
                sharedFlow.emit(values)
            }
        } catch(e: Exception) {
            Log.d("LanguageModelFacilitator", "Failed to get context, composed data snapshot, etc: $e")
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
        if(!trainingEnabled) return

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
                dictionaryFacilitator.locale.language,
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
                dictionaryFacilitator.locale.language,
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
        if(!trainingEnabled) return

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

    public fun onStartInput() {
        transformerDisabled = false
        numConsecutiveTimeouts = 0
        ignoringNextUpdate = false
    }

    var ignoringNextUpdate = false
    fun ignoreNextUpdate() {
        ignoringNextUpdate = true
    }
}