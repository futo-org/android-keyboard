package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.utils.JniUtils
import java.util.Arrays
import java.util.Locale

@OptIn(DelicateCoroutinesApi::class)
val LanguageModelScope = newSingleThreadContext("LanguageModel")

class ModelLoadingException(message: String): Exception(message)

data class ComposeInfo(
    val partialWord: String,
    val xCoords: IntArray,
    val yCoords: IntArray,
    val inputMode: Int
)

class LanguageModel(
    val applicationContext: Context,
    val lifecycleScope: LifecycleCoroutineScope,
    val modelInfoLoader: ModelInfoLoader,
    val locale: Locale
) {
    private suspend fun loadModel() = withContext(LanguageModelScope) {
        withContext(Dispatchers.Main) { JniUtils.loadNativeLibrary() }

        val modelPath = modelInfoLoader.path.absolutePath
        mNativeState = openNative(modelPath)

        // TODO: Not sure how to handle finetuned model being corrupt. Maybe have finetunedA.gguf and finetunedB.gguf and swap between them
        if (mNativeState == 0L) {
            throw ModelLoadingException("Failed to load models $modelPath")
        }
    }


    private fun getComposeInfo(composedData: ComposedData): ComposeInfo {
        var partialWord = composedData.mTypedWord

        val inputPointers = composedData.mInputPointers
        val isGesture = composedData.mIsBatchMode

        var inputMode = 0
        if (isGesture) {
            partialWord = ""
        }

        val xCoords: IntArray = inputPointers.xCoordinates.toList().toIntArray()
        val yCoords: IntArray = inputPointers.yCoordinates.toList().toIntArray()

        return ComposeInfo(
            partialWord = partialWord,
            xCoords = xCoords,
            yCoords = yCoords,
            inputMode = inputMode
        )
    }

    private fun getContext(composeInfo: ComposeInfo, ngramContext: NgramContext): String {
        var context = ngramContext.extractPrevWordsContext()
            .replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim { it <= ' ' }
        if (ngramContext.fullContext.isNotEmpty()) {
            context = ngramContext.fullContext
            context = context.substring(context.lastIndexOf("\n") + 1).trim { it <= ' ' }
        }

        var partialWord = composeInfo.partialWord
        if (partialWord.isNotEmpty() && context.endsWith(partialWord)) {
            context = context.substring(0, context.length - partialWord.length).trim { it <= ' ' }
        }

        return context
    }

    private fun safeguardComposeInfo(composeInfo: ComposeInfo): ComposeInfo {
        var resultingInfo = composeInfo

        if (resultingInfo.partialWord.isNotEmpty()) {
            resultingInfo = resultingInfo.copy(partialWord = resultingInfo.partialWord.trim { it <= ' ' })
        }

        if (resultingInfo.partialWord.length > 40) {
            resultingInfo = resultingInfo.copy(
                partialWord = resultingInfo.partialWord.substring(0, 40),
            )
        }

        if(resultingInfo.xCoords.size > 40 && resultingInfo.yCoords.size > 40) {
            resultingInfo = resultingInfo.copy(
                xCoords = resultingInfo.xCoords.slice(0 until 40).toIntArray(),
                yCoords = resultingInfo.yCoords.slice(0 until 40).toIntArray(),
            )
        }

        return resultingInfo
    }

    private var prevSafeguardContextResult = ""
    private fun findLongestMatch(needle: String, haystack: String): Int {
        var result = -1 to 0
        var ni = 0
        for(i in 0 until haystack.length + 1) {
            if(ni < needle.length && i < haystack.length && haystack[i] == needle[ni]) {
                ni++
            } else {
                if(ni >= result.second && ni > 0) {
                    result = i - ni to ni
                }
                ni = 0
            }
        }

        return result.first
    }

    private fun safeguardContext(ctx: String): String {
        var context = ctx

        // Start with what we used previously
        val matchStart = findLongestMatch(prevSafeguardContextResult, context)
        if(matchStart != -1) context = context.substring(matchStart)

        // Trim the context
        val stillNeedTrimming = { context: String -> context.length > 70 || context.count { it == ' ' } > 16 }
        if (stillNeedTrimming(context)) {
            val v = context.indexOfLast { it == '.' || it == '?' || it == '!' }
            if (v != -1) {
                context = context.substring(v + 1).trim { it <= ' ' }
            }
        }

        while (stillNeedTrimming(context) && context.contains(",")) {
            context = context.substring(context.indexOf(",") + 1).trim { it <= ' ' }
        }

        if (stillNeedTrimming(context) && context.contains(" ")) {
            context = context.split(' ').takeLast(5).joinToString(separator = " ")
        }

        if (context.length > 144) {
            // This context probably contains some spam without adequate whitespace to trim, set it to blank
            context = ""
        }

        prevSafeguardContextResult = context

        return context
    }

    private fun addPersonalDictionary(ctx: String, personalDictionary: List<String>) : String {
        var context = ctx

        if (personalDictionary.isNotEmpty()) {
            val glossary = StringBuilder()
            for (s in personalDictionary) {
                glossary.append(s.trim { it <= ' ' }).append(", ")
            }
            if (glossary.length > 2) {
                context = """
                    (Glossary: ${glossary.substring(0, glossary.length - 2)})
                    
                    $context
                    """.trimIndent()
            }
        }

        return context
    }

    suspend fun rescoreSuggestions(
        suggestedWords: SuggestedWords,
        composedData: ComposedData,
        ngramContext: NgramContext,
        personalDictionary: List<String>,
    ): List<SuggestedWordInfo>? = withContext(LanguageModelScope) {
        if (mNativeState == 0L) {
            loadModel()
            Log.d("LanguageModel", "Exiting because mNativeState == 0")
            return@withContext null
        }

        var composeInfo = withContext(Dispatchers.Main) {
            getComposeInfo(composedData)
        }

        if(composeInfo.xCoords.size != composeInfo.yCoords.size) {
            Log.w("LanguageModel", "Dropping composeInfo in rescoreSuggestions with mismatching coords size")
            return@withContext null
        }

        var context = getContext(composeInfo, ngramContext)

        composeInfo = safeguardComposeInfo(composeInfo)
        context = safeguardContext(context)
        context = addPersonalDictionary(context, personalDictionary)

        val wordStrings = suggestedWords.mSuggestedWordInfoList.map { it.mWord }.toTypedArray()
        val wordScoresInput = suggestedWords.mSuggestedWordInfoList.map { it.mScore }.toTypedArray().toIntArray()
        val wordScoresOutput = IntArray(wordScoresInput.size) { 0 }

        rescoreSuggestionsNative(
            mNativeState,
            context,

            wordStrings,
            wordScoresInput,

            wordScoresOutput
        )

        return@withContext suggestedWords.mSuggestedWordInfoList.mapIndexed { index, suggestedWordInfo ->
            Log.i("LanguageModel", "Suggestion [${suggestedWordInfo.word}] reweighted, from ${suggestedWordInfo.mScore} to ${wordScoresOutput[index]}")
            SuggestedWordInfo(
                suggestedWordInfo.word,
                suggestedWordInfo.mPrevWordsContext,

                wordScoresOutput[index],
                suggestedWordInfo.mKindAndFlags,

                suggestedWordInfo.mSourceDict,
                suggestedWordInfo.mIndexOfTouchPointOfSecondWord,

                suggestedWordInfo.mAutoCommitFirstWordConfidence
            )
        }.sortedByDescending { it.mScore }
    }

    private suspend fun loadModelIfNeeded() = if(mNativeState == 0L) {
        loadModel()
        false
    } else {
        true
    }

    private fun getSuggestionsInternal(
        proximityInfoHandle: Long,
        context: String,
        composeInfo: ComposeInfo,
        autocorrectThreshold: Float,
        bannedWords: Array<String>
    ): ArrayList<SuggestedWordInfo> {
        val maxResults = 128
        val outProbabilities = FloatArray(maxResults)
        val outStrings = arrayOfNulls<String>(maxResults)
        getSuggestionsNative(
            mNativeState,
            proximityInfoHandle,
            context,
            composeInfo.partialWord,
            composeInfo.inputMode,
            composeInfo.xCoords,
            composeInfo.yCoords,
            autocorrectThreshold,
            bannedWords,
            outStrings,
            outProbabilities
        )
        val suggestions = ArrayList<SuggestedWordInfo>()
        var kind = SuggestedWordInfo.KIND_PREDICTION
        val resultMode = outStrings[maxResults - 1]
        var canAutocorrect = resultMode == "autocorrect"
        for (i in 0 until maxResults) {
            if (outStrings[i] == null) continue
            if (composeInfo.partialWord.isNotEmpty() && composeInfo.partialWord
                    .equals(outStrings[i]!!.trim { it <= ' ' }, ignoreCase = true)) {
                // If this prediction matches the partial word ignoring case, and this is the top
                // prediction, then we can break.
                if (i == 0) {
                    break
                } else {
                    // Otherwise, we cannot autocorrect to the top prediction unless the model is
                    // super confident about this
                    if (outProbabilities[i] * 2.5f >= outProbabilities[0]) {
                        canAutocorrect = false
                    }
                }
            }
        }
        if (canAutocorrect && composeInfo.partialWord.length > 1) {
            kind =
                SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
        }

        // It's a bit ugly to communicate "clueless" with negative score, but then again
        // it sort of makes sense
        var probMult = 500000.0f
        var probOffset = 100000.0f
        if (resultMode == "clueless") {
            probMult = 10.0f
            probOffset = -100000.0f
        }
        for (i in 0 until maxResults - 1) {
            if (outStrings[i] == null) continue
            var currKind = kind
            val word = outStrings[i]!!.trim { it <= ' ' }
            if (word == composeInfo.partialWord) {
                currKind = currKind or SuggestedWordInfo.KIND_FLAG_EXACT_MATCH
            }
            suggestions.add(
                SuggestedWordInfo(
                    word,
                    context,
                    (outProbabilities[i] * probMult + probOffset).toInt(),
                    currKind,
                    null,
                    0,
                    0
                )
            )
        }

        for (suggestion in suggestions) {
            suggestion.mOriginatesFromTransformerLM = true
        }

        return suggestions
    }

    suspend fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        autocorrectThreshold: Float,
        personalDictionary: List<String>,
        bannedWords: Array<String>
    ): ArrayList<SuggestedWordInfo>? = withContext(LanguageModelScope) {
        if(!loadModelIfNeeded()) return@withContext null
        if(composedData.mIsBatchMode) return@withContext null
        var composeInfo = getComposeInfo(composedData)
        var context = getContext(composeInfo, ngramContext)

        composeInfo = safeguardComposeInfo(composeInfo)
        context = safeguardContext(context)

        context = addPersonalDictionary(context, personalDictionary)

        return@withContext getSuggestionsInternal(proximityInfoHandle, context, composeInfo, autocorrectThreshold, bannedWords)
    }

    suspend fun closeInternalLocked() = withContext(LanguageModelScope) {
        if (mNativeState != 0L) {
            closeNative(mNativeState)
            mNativeState = 0
        }
    }

    var mNativeState: Long = 0
    private external fun openNative(sourceDir: String): Long
    private external fun closeNative(state: Long)
    private external fun getSuggestionsNative( // inputs
        state: Long,
        proximityInfoHandle: Long,
        context: String,
        partialWord: String,
        inputMode: Int,
        inComposeX: IntArray,
        inComposeY: IntArray,
        thresholdSetting: Float,
        bannedWords: Array<String>,  // outputs
        outStrings: Array<String?>,
        outProbs: FloatArray
    )

    private external fun rescoreSuggestionsNative(
        state: Long,
        context: String,

        inSuggestedWords: Array<String>,
        inSuggestedScores: IntArray,

        outSuggestedScores: IntArray
    )
}
