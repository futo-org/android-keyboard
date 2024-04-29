package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import org.futo.inputmethod.keyboard.KeyDetector
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import java.util.Arrays
import java.util.Locale

@OptIn(DelicateCoroutinesApi::class)
val LanguageModelScope = newSingleThreadContext("LanguageModel")

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
        val modelPath = modelInfoLoader.path.absolutePath
        mNativeState = openNative(modelPath)

        // TODO: Not sure how to handle finetuned model being corrupt. Maybe have finetunedA.gguf and finetunedB.gguf and swap between them
        if (mNativeState == 0L) {
            throw RuntimeException("Failed to load models $modelPath")
        }
    }


    private fun getComposeInfo(composedData: ComposedData, keyDetector: KeyDetector): ComposeInfo {
        var partialWord = composedData.mTypedWord

        val inputPointers = composedData.mInputPointers
        val isGesture = composedData.mIsBatchMode
        val inputSize: Int = inputPointers.pointerSize

        val xCoords: IntArray
        val yCoords: IntArray
        var inputMode = 0
        if (isGesture) {
            /*Log.w("LanguageModel", "Using experimental gesture support")
            inputMode = 1
            val xCoordsList = mutableListOf<Int>()
            val yCoordsList = mutableListOf<Int>()
            // Partial word is gonna be derived from batch data
            partialWord = convertToString(
                composedData.mInputPointers.xCoordinates,
                composedData.mInputPointers.yCoordinates,
                inputSize,
                keyDetector,
                xCoordsList,
                yCoordsList
            )
            xCoords = IntArray(xCoordsList.size)
            yCoords = IntArray(yCoordsList.size)
            for (i in xCoordsList.indices) xCoords[i] = xCoordsList[i]
            for (i in yCoordsList.indices) yCoords[i] = yCoordsList[i]*/

            partialWord = ""

            xCoords = IntArray(composedData.mInputPointers.pointerSize)
            yCoords = IntArray(composedData.mInputPointers.pointerSize)
            val xCoordsI = composedData.mInputPointers.xCoordinates
            val yCoordsI = composedData.mInputPointers.yCoordinates
            for (i in 0 until composedData.mInputPointers.pointerSize) xCoords[i] = xCoordsI[i]
            for (i in 0 until composedData.mInputPointers.pointerSize) yCoords[i] = yCoordsI[i]
        } else {
            xCoords = IntArray(composedData.mInputPointers.pointerSize)
            yCoords = IntArray(composedData.mInputPointers.pointerSize)
            val xCoordsI = composedData.mInputPointers.xCoordinates
            val yCoordsI = composedData.mInputPointers.yCoordinates
            for (i in 0 until composedData.mInputPointers.pointerSize) xCoords[i] = xCoordsI[i]
            for (i in 0 until composedData.mInputPointers.pointerSize) yCoords[i] = yCoordsI[i]
        }

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

    private fun safeguardContext(ctx: String): String {
        var context = ctx

        // Trim the context
        while (context.length > 128) {
            context = if (context.contains(".") || context.contains("?") || context.contains("!")) {
                val v = Arrays.stream(
                    intArrayOf(
                        context.indexOf("."),
                        context.indexOf("?"),
                        context.indexOf("!")
                    )
                ).filter { i: Int -> i != -1 }.min().orElse(-1)
                if (v == -1) break // should be unreachable
                context.substring(v + 1).trim { it <= ' ' }
            } else if (context.contains(",")) {
                context.substring(context.indexOf(",") + 1).trim { it <= ' ' }
            } else if (context.contains(" ")) {
                context.substring(context.indexOf(" ") + 1).trim { it <= ' ' }
            } else {
                break
            }
        }
        if (context.length > 400) {
            // This context probably contains some spam without adequate whitespace to trim, set it to blank
            context = ""
        }

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
        keyDetector: KeyDetector,
        personalDictionary: List<String>,
    ): List<SuggestedWordInfo>? = withContext(LanguageModelScope) {
        if (mNativeState == 0L) {
            loadModel()
            Log.d("LanguageModel", "Exiting because mNativeState == 0")
            return@withContext null
        }

        var composeInfo = getComposeInfo(composedData, keyDetector)
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

    suspend fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        keyDetector: KeyDetector,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        proximityInfoHandle: Long,
        sessionId: Int,
        autocorrectThreshold: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?,
        personalDictionary: List<String>,
        bannedWords: Array<String>
    ): ArrayList<SuggestedWordInfo>? = withContext(LanguageModelScope) {
        if (mNativeState == 0L) {
            loadModel()
            Log.d("LanguageModel", "Exiting because mNativeState == 0")
            return@withContext null
        }

        // Disable gesture for now
        if(composedData.mIsBatchMode) {
            return@withContext null
        }


        var composeInfo = getComposeInfo(composedData, keyDetector)
        var context = getContext(composeInfo, ngramContext)

        composeInfo = safeguardComposeInfo(composeInfo)
        context = safeguardContext(context)
        context = addPersonalDictionary(context, personalDictionary)


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
        if (composeInfo.partialWord.isNotEmpty() && canAutocorrect) {
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

        /*
        if(kind == SuggestedWords.SuggestedWordInfo.KIND_PREDICTION) {
            // TODO: Forcing the thing to appear
            for (int i = suggestions.size(); i < 3; i++) {
                String word = " ";
                for (int j = 0; j < i; j++) word += " ";

                suggestions.add(new SuggestedWords.SuggestedWordInfo(word, context, 1, kind, this, 0, 0));
            }
        }
        */

        for (suggestion in suggestions) {
            suggestion.mOriginatesFromTransformerLM = true
        }

        return@withContext suggestions
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
