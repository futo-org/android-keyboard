package org.futo.inputmethod.latin

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.internal.isAlphabet
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValues
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.ml.inference.SwipeDecoder
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Serializable
data class Input(
    val x: Float,
    val y: Float,
    val t: Float
)

@Serializable
data class Inputs(val data: List<Input>)

internal fun getKeyXY(key: Key, keyboard: Keyboard): Pair<Float, Float> {
    var xMid = key.drawX + key.drawWidth/2.0f
    var yMid = key.y + key.verticalGap/2.0f + key.height/2.0f

    xMid /= keyboard.mBaseWidth
    yMid *= (1.0f / (keyboard.mBaseHeight - keyboard.mPadding.bottom)) * (4.0f / 3.0f)

    return xMid to yMid
}

internal fun getKeyYBottom(key: Key, keyboard: Keyboard): Float =
    (key.y + key.verticalGap + key.height) *
            ((1.0f / (keyboard.mBaseHeight - keyboard.mPadding.bottom)) * (4.0f / 3.0f))


data class LayoutInfoForModel(
    val letters: String,
    val xs: List<Float>,
    val ys: List<Float>,
    val decoder: String,
    val lm: String,
    val sx: Float, val sy: Float,
    val ox: Float, val oy: Float,
) {
    companion object {
        val DEFAULT = LayoutInfoForModel(
            letters = "",
            xs = emptyList(),
            ys = emptyList(),
            decoder = "",
            lm = "",
            sx = 1.0f, sy = 1.0f,
            ox = 0.0f, oy = 0.0f
        )

        @JvmStatic
        fun buildLayoutInfo(context: Context, keyboard: Keyboard, settingsValues: SettingsValues): LayoutInfoForModel? =
            (context.getSetting(SwipeSpecialDecoderSetting).let {
                if(it) SpecialDecoder.matchLayout(keyboard, settingsValues)
                else null
            } ?: run {
                val keys = keyboard.sortedKeys
                    .filter { settingsValues.isWordCodePoint(it.code) && !Character.isDigit(it.code) }
                    .sortedBy { it.code }
                    .distinctBy { it.code } // The engine currently can't handle letters existing multiple times. Sorry custom layouts!
                val letters = keys.joinToString(separator="") { Character.toString(Character.toLowerCase(it.code)) }

                // Guard against non-alphabet keyboards
                if(!keyboard.mId.mElement.kind.isAlphabet || letters.length < 6) return@run null

                val positions = keys.map { getKeyXY(it, keyboard) }

                val yScale = 1.0f / (keys.maxOf { getKeyYBottom(it, keyboard) }.coerceAtLeast(1.0f))

                val xs = positions.map { it.first }
                val ys = positions.map { it.second * yScale }

                return LayoutInfoForModel(
                    letters = letters,
                    xs = xs, ys = ys,
                    sx = 1.0f, sy = yScale,
                    ox = 0.0f, oy = 0.0f,
                    decoder = "",
                    lm = SpecialContextLM.match(settingsValues),
                )
            }).let {
                if(!context.getSetting(SwipeLanguageModelSetting)) {
                    it?.copy(lm="")
                } else {
                    it
                }
            }
    }
}

private class SpecialContextLM private constructor(
    val language: String,
    val asset: String
) {
    companion object {
        private val contextLMs = listOf(
            SpecialContextLM("en", "english_contextlm/hungry_jellyfish.pte")
        )

        fun match(settingsValues: SettingsValues): String {
            if(settingsValues.mMultilingualLocales.size > 1) return ""

            return contextLMs.firstOrNull { it.language == settingsValues.mLocale.language }?.asset ?: ""
        }
    }
}

private class SpecialDecoder private constructor(
    val asset: String,

    val language: String,
    val layoutLetters: String,

    // These are normalized such that the lowest value is always 0, and highest value is always 1
    val layoutXs: List<Float>,
    val layoutYs: List<Float>,

    val xOffset: Float,
    val xScale: Float,
    val yOffset: Float,
    val yScale: Float
) {
    val maxXDeviation = xScale * 0.1
    val maxYDeviation = yScale * 0.1

    companion object {
        private val specialDecoders = listOf(
            SpecialDecoder(
                asset = "english_decoder/model_fp32.pte",
                language = "en",
                layoutLetters = "abcdefghijklmnopqrstuvwxyz",
                layoutXs = listOf(0.055555555555555566f, 0.611111111111111f, 0.38888888888888895f, 0.2777777777777778f, 0.22222222222222227f, 0.38888888888888895f, 0.5000000000000001f, 0.611111111111111f, 0.7777777777777778f, 0.7222222222222222f, 0.8333333333333334f, 0.9444444444444445f, 0.8333333333333334f, 0.7222222222222222f, 0.888888888888889f, 1.0f, 0.0f, 0.33333333333333337f, 0.1666666666666667f, 0.44444444444444453f, 0.6666666666666667f, 0.5000000000000001f, 0.11111111111111112f, 0.2777777777777778f, 0.5555555555555556f, 0.1666666666666667f),
                layoutYs = listOf(0.5f, 1.0f, 1.0f, 0.5f, 0.0f, 0.5f, 0.5f, 0.5f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f),
                xOffset = 0.05f,
                xScale = 0.8999999999999999f,
                yOffset = 0.1667f,
                yScale = 0.6666000000000001f,
            )
        )

        internal fun matchLayout(keyboard: Keyboard, settingsValues: SettingsValues): LayoutInfoForModel? {
            if(settingsValues.mMultilingualLocales.size > 1) return null

            val keys = keyboard.sortedKeys.associate { Character.toLowerCase(it.code) to it }

            return specialDecoders.firstNotNullOfOrNull { decoder ->
                if(keyboard.mId.mLocale.language != decoder.language) return@firstNotNullOfOrNull null

                // In case the layout has multiple repeated instances of the same letter, let's forget
                // about using a special decoder, since this should never occur in a regular layout.
                if(decoder.layoutLetters.any { letter -> keyboard.sortedKeys.count { key ->
                        Character.toLowerCase(key.code) == letter.code
                } > 1}) return@firstNotNullOfOrNull null

                // Make sure the letters are a perfect match!
                val expectedLayoutLetters = decoder.layoutLetters.map { it.code }.toSortedSet()
                val ourLayoutLetters = keys.keys.filter { !Character.isDigit(it) && settingsValues.isWordCodePoint(it) }.toSortedSet()
                if(expectedLayoutLetters != ourLayoutLetters) return@firstNotNullOfOrNull null

                val relevantKeysN = decoder.layoutLetters.map { keys[it.code] }
                if(relevantKeysN.any { it == null }) return@firstNotNullOfOrNull null
                val relevantKeys = relevantKeysN.filterNotNull()
                val positionsNotNormalized = relevantKeys.map { getKeyXY(it, keyboard) }

                val minX = positionsNotNormalized.minOf { it.first }
                val minY = positionsNotNormalized.minOf { it.second }

                val maxX = positionsNotNormalized.maxOf { it.first }
                val maxY = positionsNotNormalized.maxOf { it.second }

                val offsetX = minX
                val offsetY = minY
                val scaleX = maxX - offsetX
                val scaleY = maxY - offsetY

                if(scaleX == 0.0f || scaleY == 0.0f) return@firstNotNullOfOrNull null

                val normX = positionsNotNormalized.map { (it.first - offsetX) / scaleX }
                val normY = positionsNotNormalized.map { (it.second - offsetY) / scaleY }

                val matches = decoder.layoutXs.zip(normX).all { (a, b) -> abs(b - a) < decoder.maxXDeviation }
                           && decoder.layoutYs.zip(normY).all { (a, b) -> abs(b - a) < decoder.maxYDeviation }

                if(!matches) return@firstNotNullOfOrNull null

                val dsx = decoder.xScale / scaleX
                val dsy = decoder.yScale / scaleY
                val dox = decoder.xOffset - offsetX * dsx
                val doy = decoder.yOffset - offsetY * dsy
                LayoutInfoForModel(
                    decoder = decoder.asset,
                    letters = decoder.layoutLetters,
                    xs = decoder.layoutXs.map { it * decoder.xScale + decoder.xOffset },
                    ys = decoder.layoutYs.map { it * decoder.yScale + decoder.yOffset },
                    sx = dsx, sy = dsy, ox = dox, oy = doy,
                    lm = SpecialContextLM.match(settingsValues),
                )
            }
        }
    }
}

val SwipeModelSetting = SettingsKey(booleanPreferencesKey("_experimental_swipe_model"), true)
val SwipeSpecialDecoderSetting = SettingsKey(booleanPreferencesKey("_experimental_swipe_special_decoder"), true)
val SwipeLanguageModelSetting = SettingsKey(booleanPreferencesKey("_experimental_swipe_language_model"), true)

class SwipeDecoderDictionary(val context: Context, val locale: Locale) : Dictionary("swipe", locale) {
    companion object {
        const val SWIPE_MODEL = "universal_model/model_fp32.pte"

        private var prevKeyboard: Keyboard? = null
        var appliedLayoutInfo: LayoutInfoForModel = LayoutInfoForModel.DEFAULT
            private set

        // for debug info
        var appliedTries: LongArray? = null
        var appliedTrieWeights by mutableStateOf<FloatArray>(FloatArray(0))

        fun metadataFor(pteAsset: String): String
            = pteAsset.substringBeforeLast('/') + "/metadata.json"

        fun vocabFor(pteAsset: String): String {
            if(pteAsset.isEmpty()) return ""
            return pteAsset.substringBeforeLast('/') + "/vocab.txt"
        }

        @Serializable private data class ModelMetadata(val codename: String)
        private val CodenameParseJson = Json { ignoreUnknownKeys = true }
        fun parseMetadataToGetCodename(content: String): String =
            CodenameParseJson.decodeFromString<ModelMetadata>(content).codename

        private val createdFiles = mutableSetOf<String>()
        fun getFilePath(context: Context, assetName: String): String {
            if(assetName.isEmpty()) return ""

            val assets = context.assets
            val tmpDir = context.codeCacheDir
            val modelFile = File(tmpDir, assetName)

            if (modelFile.exists()) {
                if(assetName in createdFiles) return modelFile.absolutePath

                modelFile.delete()
            }
            modelFile.parentFile?.mkdirs()
            assets.open(assetName).use { inputStream ->
                modelFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            createdFiles.add(assetName)

            if(assetName.endsWith(".pte")) {
                // Ensure metadata is also present
                getFilePath(context, metadataFor(assetName))
            }

            return modelFile.absolutePath
        }


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

            return true
        }
    }

    var decoder: SwipeDecoder? = null

    private fun getOrInitDecoder(): SwipeDecoder = decoder ?: run {
        val swipeModelPath = getFilePath(context, SWIPE_MODEL)

        val decoder = SwipeDecoder(
            encoderPath = swipeModelPath,
            useExpansion = false, // ITrie contains expanded entries already
        )

        this.decoder = decoder
        applyPendingLayoutInfo()

        return decoder
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
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWords.SuggestedWordInfo?>? {
        throw UnsupportedOperationException("Use the non-dictionary method instead")
    }

    fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext?,
        useHighBeam: Boolean,
        trieWeights: FloatArray
    ): ArrayList<SuggestedWords.SuggestedWordInfo>? {
        if(context.getSetting(SwipeModelSetting) == false) return null

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

        val xCoords = pointers.xCoordinates.take(count).map {
            it.toFloat() / keyboardWidth * appliedLayoutInfo.sx + appliedLayoutInfo.ox
        }.toFloatArray()
        val yCoords = pointers.yCoordinates.take(count).map {
            minOf(1.0f, (it.toFloat() / keyboardHeight) * (4.0f / 3.0f) * appliedLayoutInfo.sy + appliedLayoutInfo.oy )
        }.toFloatArray()
        val times = pointers.times.take(count).map { it.toFloat() }.toFloatArray()

        val wordsContext = ngramContext?.fullContext?.split(' ')?.takeLast(10) ?: emptyList()
        val decoder = getOrInitDecoder()
        decoder.setContext(wordsContext)
        appliedTrieWeights = trieWeights

        val beamWidth = if(useHighBeam) 300 else 16
        val topK = if(useHighBeam) 4 else 1
        val results = decoder.recognize(xCoords, yCoords, times, topK=topK, beamWidth=beamWidth, trieWeights=trieWeights)


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
            Log.d("SwipeDecoderDictionary", "curr scale is  ${appliedLayoutInfo.sx} ${appliedLayoutInfo.sy}")
            Log.d("SwipeDecoderDictionary", "curr offset is ${appliedLayoutInfo.ox} ${appliedLayoutInfo.oy}")
            Log.d("SwipeDecoderDictionary", "outputs = ${results.joinToString { "Word(\"${it.word}\", score=${it.score}, lm=${it.lmScore}, ctc=${it.ctcScore})" }}")
        }

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

    data class PendingLayoutInfo(val layout: LayoutInfoForModel, val tries: List<Long>)
    private var pendingLayoutInfo: PendingLayoutInfo? = null
    private fun applyPendingLayoutInfo() {
        decoder?.let { d ->
            pendingLayoutInfo?.let { pend ->
                //Log.d("SwipeDecoderDictionary", "Applying layout info: $pend")
                d.setMode(
                    letters=pend.layout.letters,
                    cx=pend.layout.xs.toFloatArray(),
                    cy=pend.layout.ys.toFloatArray(),
                    tries=pend.tries.toLongArray(),
                    decoderPath=getFilePath(context, pend.layout.decoder),
                    lmModelPath=getFilePath(context, pend.layout.lm),
                    lmVocabPath=getFilePath(context, vocabFor(pend.layout.lm)),
                    lmAlpha=if(pend.layout.lm.isNotEmpty()) 1.0f else 0.0f
                )
                appliedLayoutInfo = pend.layout
                appliedTries = pend.tries.toLongArray()
            }
            pendingLayoutInfo = null
        }
    }

    fun updateKeyboard(pendingLayoutInfo: PendingLayoutInfo) {
        this.pendingLayoutInfo = pendingLayoutInfo
        applyPendingLayoutInfo()
    }

    override fun isInDictionary(word: String?): Boolean {
        return false
    }
}