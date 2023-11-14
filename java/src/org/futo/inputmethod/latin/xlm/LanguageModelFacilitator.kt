package org.futo.inputmethod.latin.xlm;

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBar
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.actions.VoiceInputAction
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.inflateInlineSuggestion
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.xlm.LanguageModel;
import org.futo.inputmethod.latin.utils.SuggestionResults
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.LatinIMELegacy
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.Suggest
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.keyboard.KeyboardSwitcher

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
                proximityInfoHandle,
                settingsForPrediction,
                -1,
                0.0f,
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

        withContext(Dispatchers.Default) {
            sharedFlow.conflate().collect { value ->
                println("LatinIME: Collecting")
                processUpdateSuggestionStrip(value)
            }
        }
    }

    public fun updateSuggestionStripAsync(inputStyle: Int) {
        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions()) {
            inputLogic.mSuggestionStripViewAccessor.showSuggestionStrip(SuggestedWords.getEmptyInstance())
            return
        }

        if(!settingsValues.mTransformerPredictionEnabled) {
            // TODO: Call old path
            return
        }

        val wordComposer = inputLogic.mWordComposer
        val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations, 2)

        val values = PredictionInputValues(
            wordComposer.getComposedDataSnapshot(),
            ngramContext,
            inputStyle,
            ++currentSequenceId
        )

        lifecycleScope.launch {
            println("LatinIME: Emitting values")
            sharedFlow.emit(values)
        }
    }

    private val historyLog: MutableList<HistoryLogForTraining> = mutableListOf()

    public fun addToHistory(
        word: String,
        wasAutoCapitalized: Boolean,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        blockPotentiallyOffensive: Boolean,
        importance: Int) {
        
        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val lastIdx = wordCtx.lastIndexOf(committedNgramCtx)
        if(lastIdx == -1) {
            println("addToHistory: extraction failed, couldn't find ngram ctx in full ctx")
            return
        }

        val misspelledWord = wordCtx.substring(
            lastIdx + committedNgramCtx.length
        )
        if(misspelledWord.isNotBlank() && (!(misspelledWord.startsWith(" ") || committedNgramCtx == " ") || misspelledWord.endsWith(" ") || misspelledWord.trim().contains(" "))) {
            println("addToHistory: extraction failed bad context. wordCtx=[$wordCtx]  --   committedNgramCtx=[$committedNgramCtx]  --  word=[$word]  --  fullNgram=[$ngramContext]")
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
        println("addToHistory: Adding $logToAdd")
    }

    public fun unlearnFromHistory(
        word: String,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        eventType: Int
    ) {
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
            println("addToHistory: UNLEARN Couldn't find key $keyToSearch")
        } else {
            println("addToHistory: Unlearning ${historyLog[logToRemove]}")
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