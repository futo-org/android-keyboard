package org.futo.inputmethod.latin.xlm;

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import org.futo.inputmethod.engine.general.OnGetSuggestedWordsCallbackWithInputStyle
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.BinaryDictionary
import org.futo.inputmethod.latin.Dictionary
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
import kotlin.math.ceil


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


internal fun SuggestedWordInfo.scoreAtLeast(other: SuggestedWordInfo): SuggestedWordInfo {
    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        mScore.coerceAtLeast(other.mScore + 1),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        null,
        0,
        0
    )

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
    val suggestionBlacklist: SuggestionBlacklist,
    val suggestedWordsCallback: OnGetSuggestedWordsCallbackWithInputStyle
) {
    private val userDictionary = UserDictionaryObserver(context)

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
    private val sequenceIdFinishedFlow = MutableSharedFlow<Pair<PredictionInputValues, SuggestedWords?>>(replay = 1, extraBufferCapacity = 1)

    private val computationSemaphore = Semaphore(1)
    public fun hasPendingUpdate(): Boolean =
        computationSemaphore.availablePermits == 0


    private var numConsecutiveTimeouts = 0
    private var transformerDisabled = false
    public fun blockUntilComplete(): Boolean {
        if(languageModel == null) return false
        runBlocking {
            try {
                withTimeout(700L) {
                    computationSemaphore.acquire()
                    computationSemaphore.release()
                    val result = try {
                        sequenceIdFinishedFlow.first { it.first.sequenceId >= currentSequenceId }
                    } catch (ignored: Exception) {
                        null
                    }

                    // If it's non-null the processing thread is waiting on the main thread to send this to callback, so just send it ourselves
                    if(result?.second != null) {
                        suggestedWordsCallback.onGetSuggestedWords(
                            result.second!!,
                            result.first.inputStyle,
                            result.first.sequenceId
                        )
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

    private var skipLanguage: String? = null
    private suspend fun runLanguageModel(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(transformerDisabled) return null

        val locale = dictionaryFacilitator.primaryLocale ?: return null
        if ((languageModel == null && locale.language != skipLanguage) || (languageModel != null && languageModel?.locale?.language != locale.language)) {
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

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return null

        val settingsValues = settings.current ?: return null

        val keyboard = keyboardSwitcher.keyboard ?: return null
        val settingsForPrediction = SettingsValuesForSuggestion(
            settingsValues.mBlockPotentiallyOffensive,
            settingsValues.mTransformerPredictionEnabled
        )
        val proximityInfoHandle = keyboard.proximityInfo.nativeProximityInfo

        val autocorrectThreshold = context.getSetting(AutocorrectThresholdSetting)

        try {
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
        }catch (e: ModelLoadingException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Unable to load Transformer model for ${locale.getDisplayLanguage(locale)}, it may be corrupted or unsupported.",
                    Toast.LENGTH_LONG
                ).show()
                transformerDisabled = true
                e.printStackTrace()
            }
            return null
        }
    }

    private suspend fun processUpdateSuggestionStrip(values: PredictionInputValues) {
        if(keyboardSwitcher.keyboard == null) return

        computationSemaphore.acquire()

        val suggestedWords = try {
            inputLogic.mWordComposer.setAutoCorrection(null)

            if(values.composedData.mTypedWord.length > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH-1) {
                suggestedWordsCallback.onGetSuggestedWords(
                    SuggestedWords.getEmptyInstance(),
                    values.inputStyle,
                    values.sequenceId
                )
                return
            }

            var transformerWeight = context.getSetting(BinaryDictTransformerWeightSetting)
            if(dictionaryFacilitator.locales.size > 1) transformerWeight = 1.0f

            val holder = AsyncResultHolder<SuggestedWords?>("Suggest")

            inputLogic.getSuggestedWords(
                settings.current,
                keyboardSwitcher.keyboard ?: return,
                keyboardSwitcher.keyboardShiftMode,
                values.inputStyle,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER
            ) { suggestedWords ->
                holder.set(suggestedWords)
            }

            val job = Job()
            CoroutineScope(Dispatchers.Default + job).launch {
                delay(500L)
                suggestedWordsCallback.onGetSuggestedWords(
                    SuggestedWords.getEmptyInstance(),
                    values.inputStyle,
                    values.sequenceId
                )
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

                    sequenceIdFinishedFlow.emit(Pair(values, finalResults))

                    withContext(Dispatchers.Main) {
                        suggestedWordsCallback.onGetSuggestedWords(
                            finalResults,
                            values.inputStyle,
                            values.sequenceId
                        )
                    }

                    sequenceIdFinishedFlow.emit(Pair(values, null))
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

            // In some cases the LM will predict an uppercased word but dictionary predicts lowercased,
            // we should prefer the lowercase version to reduce automatically capitalizing which can be
            // annoying
            val bothAlgorithmsCameToSameConclusionButLowerCased = maxWordDict?.mWord == maxWord?.mWord?.lowercase()
            if(bothAlgorithmsCameToSameConclusionButLowerCased && maxWord != null && maxWordDict != null) {
                val clone = maxWordDict.scoreAtLeast(maxWord)
                autocorrectWord = clone
                suggestionResults.add(clone)
                filtered.add(maxWordDict)
            }

            if(transformerWeight <= 0.0f) {
                if(suggestedWordsDictList.isNullOrEmpty()) {
                    transformerWeight = 1.0f
                }
            }

            // Add reweightedSuggestions, with space replacement logic. It can replace one of the LM
            // suggestions if the top dictionary result has a space, based on heuristics about the
            // relative quality of the LM suggestion
            val spaceReplacementPossible = maxWordDict != null && maxWordDict.word.count { it == ' ' } == 1
            var spaceReplacementPerformed = false
            for(i in 0 until reweightedSuggestions.size) {
                val word = reweightedSuggestions[i]
                if(filtered.contains(word)) continue

                if(!spaceReplacementPerformed && spaceReplacementPossible && (
                            // If the dict score is high enough, allow the space suggestion
                            ((maxWordDict.mScore) > (word.mScore / 3))
                                    // Most LM-generated dashed suggestions are distractions, so accept the space suggestion
                                    || (word.word.contains('-'))
                                    // If the typed word is much longer than the transformer word, just accept the space suggestion
                                    || (values.composedData.mTypedWord.length > ceil(word.word.length * 3.0 / 2.0))
                            )
                ) {
                    val clone = maxWordDict.scoreAtLeast(word)
                    suggestionResults.add(clone)
                    spaceReplacementPerformed = true
                    continue
                }

                suggestionResults.add(word)
            }

            if(maxWordDict?.mSourceDict?.mDictType == Dictionary.TYPE_USER_HISTORY
                && maxWordDict.mScore > 100
                && maxWord != null
            ) {
                val clone = maxWordDict.scoreAtLeast(maxWord)
                suggestionResults.add(clone)
            }

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

            val settingsValues = settings.current ?: return
            val locale = dictionaryFacilitator.primaryLocale ?: return
            val wordComposer = inputLogic.mWordComposer ?: return

            val suggestedWords = Suggest.obtainNonBatchedInputSuggestedWords(
                wordComposer,
                values.inputStyle,
                settingsValues.mAutoCorrectionEnabledPerUserSettings,
                -1,
                locale,
                suggestionResults,
                settingsValues.mAutoCorrectionThreshold,
                settingsValues.mIsNumberRowEnabled
            )

            job.cancel()

            // TODO
            if(values.sequenceId < currentSequenceId) return

            suggestedWords
        } finally {
            computationSemaphore.release()
        }

        sequenceIdFinishedFlow.emit(Pair(values, suggestedWords))

        withContext(Dispatchers.Main) {
            suggestedWordsCallback.onGetSuggestedWords(
                suggestedWords,
                values.inputStyle,
                values.sequenceId
            )
        }

        sequenceIdFinishedFlow.emit(Pair(values, null))
    }

    public suspend fun destroyModel() {
        Log.d("LanguageModelFacilitator", "destroyModel called")
        languageModel?.closeInternalLocked()
        languageModel = null
    }

    public fun close() {
        userDictionary.unregister()
    }

    private var trainingEnabled = false

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

        trainingEnabled = context.getSetting(USE_TRANSFORMER_FINETUNING)
        launch {
            withContext(Dispatchers.Default) {
                val shouldTrain = context.getSettingFlow(USE_TRANSFORMER_FINETUNING)
                shouldTrain.collect {

                    if(!trainingEnabled && it) {
                        scheduleTrainingWorkerBackground(context)
                    }

                    trainingEnabled = it
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                context.getSettingFlow(SHOW_EMOJI_SUGGESTIONS).collect { shouldSuggestEmojis = it }
            }
        }

        if(trainingEnabled) {
            scheduleTrainingWorkerBackground(context)
        }
    }

    public fun shouldPassThroughToLegacy(): Boolean = when {
        (!settings.current.mTransformerPredictionEnabled) -> true
        (dictionaryFacilitator.primaryLocale.language == skipLanguage) -> true
        else -> false
    }

    public fun updateSuggestionStripAsync(inputStyle: Int) {
        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions() && inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH) {
            suggestedWordsCallback.onGetSuggestedWords(
                SuggestedWords.getEmptyInstance(),
                inputStyle,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER
            )
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
        if(settings.current?.mInputAttributes?.mNoLearning != false) return

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return

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
                dictionaryFacilitator.primaryLocale.language,
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
                dictionaryFacilitator.primaryLocale.language,
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

    public fun isTransformerDisabled(): Boolean = transformerDisabled

    var ignoringNextUpdate = false
    fun ignoreNextUpdate() {
        ignoringNextUpdate = true
    }
}