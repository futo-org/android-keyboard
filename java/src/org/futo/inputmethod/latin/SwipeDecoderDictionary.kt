package org.futo.inputmethod.latin

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.ml.inference.SwipeDecoder
import java.io.File
import java.util.ArrayList
import java.util.Locale

@Serializable
data class Input(
    val x: Float,
    val y: Float,
    val t: Float
)

@Serializable
data class Inputs(val inputs: List<Input>)

val SwipeModelSetting = SettingsKey(booleanPreferencesKey("_experimental_swipe_model"), true)
val SwipeVocabulary2Setting = SettingsKey(booleanPreferencesKey("_experimental_swipe_vocab2"), true)
val SwipeLanguageModelSetting = SettingsKey(booleanPreferencesKey("_experimental_swipe_language_model"), true)

class SwipeDecoderDictionary(val context: Context, val locale: Locale) : Dictionary("swipe", locale) {
    companion object {
        private var prevKeyboard: Keyboard? = null

        @JvmStatic
        fun updateKeyboard(keyboard: Keyboard) {
            prevKeyboard = keyboard
        }

        @JvmStatic
        fun canBeUsed(): Boolean {
            val settings = Settings.getInstance().current
            if(!settings.mGestureInputEnabled) {
                Log.d("SwipeDecoderDictionary", "Inactive because gesture input is disabled.")
                return false
            }
            if(settings.mIsNumberRowEnabled || settings.mIsArrowRowEnabled) {
                Log.d("SwipeDecoderDictionary", "Inactive because extra row is enabled.")
                return false
            }
            if(prevKeyboard?.mId?.mKeyboardLayoutSetName != "qwerty") {
                Log.d("SwipeDecoderDictionary", "Inactive because keyboard layout set does not match: [${prevKeyboard?.mId?.mKeyboardLayoutSetName}]")
                return false
            }

            return true
        }
    }
    init {
        if(locale.language != "en") throw IllegalStateException("SwipeDecoderDictionary is only for English")
    }

    var decoderHasLM = false
    var decoderHasVocab2 = false
    var decoder: SwipeDecoder? = null

    private fun getOrInitDecoder(): SwipeDecoder {
        val needLM = context.getSetting(SwipeLanguageModelSetting)
        val needVocab2 = context.getSetting(SwipeVocabulary2Setting)
        decoder?.let {
            if(decoderHasLM == needLM && decoderHasVocab2 == needVocab2) {
                return it
            } else {
                it.close()
                decoder = null
            }
        }

        val swipeModelPath = getModelPath()
        val languageModelPath = getLMPath()
        val vocabPath = getVocabPath(needVocab2)
        decoderHasLM = needLM
        decoderHasVocab2 = needVocab2
        val decoder = if(needLM) {
            SwipeDecoder(
                swipeModelPath,
                vocabPath,
                useExpansion = true,
                lmModelPath = languageModelPath,
                lmVocabPath = vocabPath,
                lmAlpha = 1.0f
            )
        } else {
            SwipeDecoder(
                swipeModelPath,
                vocabPath,
                useExpansion = true,
            )
        }

        this.decoder = decoder
        return decoder
    }



    private fun getVocabPath(vocab2: Boolean): String {
        val assets = context.assets
        val tmpDir = context.codeCacheDir
        val vocabFile = File(tmpDir, "swipe_vocab.txt")

        if(vocabFile.exists()) vocabFile.delete()

        val fname = if(vocab2) "vocabulary2.txt" else "vocabulary.txt"
        assets.open(fname).use { inputStream ->
            vocabFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return vocabFile.absolutePath
    }

    private fun getModelPath(): String {
        val assets = context.assets
        val tmpDir = context.codeCacheDir
        val modelFile = File(tmpDir, "hot_squid.pte")

        if (modelFile.exists()) modelFile.delete()
        assets.open("hot_squid.pte").use { inputStream ->
            modelFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return modelFile.absolutePath
    }


    private fun getLMPath(): String {
        val assets = context.assets
        val tmpDir = context.codeCacheDir
        val modelFile = File(tmpDir, "hungry_jellyfish.pte")

        if(modelFile.exists()) modelFile.delete()
        assets.open("hungry_jellyfish.pte").use { inputStream ->
            modelFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return modelFile.absolutePath
    }

    override fun getNextValidCodePoints(composedData: ComposedData?): ArrayList<Int> {
        return arrayListOf()
    }

    private fun getPredictions(
        composedData: ComposedData,
        ngramContext: NgramContext?
    ): ArrayList<SuggestedWords.SuggestedWordInfo>? {
        val decoder = getOrInitDecoder()
        val wordsContext = ngramContext?.fullContext?.split(' ')?.takeLast(10) ?: emptyList()
        decoder.setContext(wordsContext)

        val results = decoder.predictNext()

        //Log.d("SwipeDecoderDictionary", "getPredictions results=${results}")
        val list = ArrayList<SuggestedWords.SuggestedWordInfo>(results.size)
        results.forEach {
            list.add(SuggestedWords.SuggestedWordInfo(
                it.word, "", (it.score * 1000.0f + 10000.0f).toInt(), SuggestedWords.SuggestedWordInfo.KIND_CORRECTION, this, 0, 0
            ).apply {
                mOriginatesFromSwipeModel = true
            })
        }


        return list
    }

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWords.SuggestedWordInfo>? {
        if(context.getSetting(SwipeModelSetting) == false) return null

        //Log.d("SwipeDecoderDictionary", "isBatchMode=${composedData.mIsBatchMode} inputPointerSize=${composedData.mInputPointers.pointerSize} typedWord=${composedData.mTypedWord}")
        if(!composedData.mIsBatchMode && composedData.mInputPointers.pointerSize == 0 && composedData.mTypedWord.isEmpty()) {
            return getPredictions(
                composedData,
                ngramContext
            )
        }

        if(!composedData.mIsBatchMode) return null

        val pointers = composedData.mInputPointers
        val count = pointers.pointerSize
        if(count == 0) return null

        val keyboardWidth = prevKeyboard?.mOccupiedWidth ?: run {
            Log.e("SwipeDecoderDictionary", "Could not determine keyboard width!")
            context.resources.displayMetrics.widthPixels
        }

        val keyboardHeight = prevKeyboard?.mOccupiedHeight ?: run {
            Log.e("SwipeDecoderDictionary", "Could not determine keyboard height! Cannot continue.")
            return null
        }

        val xCoords = pointers.xCoordinates.take(count).map { it.toFloat() / keyboardWidth }.toFloatArray()
        val yCoords = pointers.yCoordinates.take(count).map {
            minOf(1.0f, (it.toFloat() / keyboardHeight) * (4.0f / 3.0f))
        }.toFloatArray()
        val times = pointers.times.take(count).map { it.toFloat() }.toFloatArray()

        val wordsContext = ngramContext?.fullContext?.split(' ')?.takeLast(10) ?: emptyList()
        val decoder = getOrInitDecoder()
        decoder.setContext(wordsContext)

        val results = decoder.recognize(xCoords, yCoords, times)


        if(false) {
            val inputs = Inputs(xCoords.zip(yCoords.zip(times)).map {
                val x = it.first
                val y = it.second.first
                val t = it.second.second

                Input(x, y, t)
            })
            Log.d(
                "SwipeDecoderDictionary",
                "Inputs = ${Json.encodeToString(Inputs.serializer(), inputs)}"
            )
            Log.d("SwipeDecoderDictionary", "transformed fx ${xCoords[0]}")
            Log.d("SwipeDecoderDictionary", "transformed fy ${yCoords[0]}")
            Log.d("SwipeDecoderDictionary", "transformed lx ${xCoords.last()}")
            Log.d("SwipeDecoderDictionary", "transformed ly ${yCoords.last()}")
        }

        val list = ArrayList<SuggestedWords.SuggestedWordInfo>(results.size)
        results.forEach {
            list.add(SuggestedWords.SuggestedWordInfo(
                it.word, "", (it.score * 1000.0f + 10000.0f).toInt(), SuggestedWords.SuggestedWordInfo.KIND_CORRECTION, this, 0, 0
            ).apply {
                mOriginatesFromSwipeModel = true
            })
        }

        //Log.d("SwipeDecoderDictionary", "Results: $results")

        return list
    }

    override fun isInDictionary(word: String?): Boolean {
        return false
    }
}