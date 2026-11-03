package org.futo.inputmethod.latin

import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.actions.PersistentEmojiState
import java.util.ArrayList
import java.util.Locale

class EmojiDictionary(locale: Locale) : Dictionary(TYPE_EMOJI, locale) {
    override fun getNextValidCodePoints(composedData: ComposedData?): ArrayList<Int?>? {
        return arrayListOf()
    }

    // Usually for short texts like "it", we really mean the word "it" and not the flag of Italy
    private fun isWordValidForShortcut(word: String) =
        word.length > 2 || word.all { it.isUpperCase() }

    fun lookupEmoji(
        typedWord: String,
        ngramContext: NgramContext?,
        isBatchMode: Boolean?
    ): ArrayList<SuggestedWords.SuggestedWordInfo?>? {
        if(DataStoreHelper.getSetting(SHOW_EMOJI_SUGGESTIONS) == false) return arrayListOf()

        var candidates: List<String> = emptyList()

        if(!typedWord.isEmpty()) {
            if(isWordValidForShortcut(typedWord))
                candidates = PersistentEmojiState.getShortcuts(mLocale, typedWord.lowercase(mLocale))
        } else if((ngramContext?.prevWordCount ?: 0) > 0 && isBatchMode == false) {
            val prevWord = ngramContext?.getNthPrevWord(1)?.toString() ?: ""
            if(!prevWord.isEmpty()) {
                if(isWordValidForShortcut(prevWord))
                    candidates = PersistentEmojiState.getShortcuts(mLocale, prevWord.lowercase(mLocale))
            }
        }

        // Dedup after skin-tone: two base emojis can collapse to the same toned string.
        candidates = candidates.map { PersistentEmojiState.transformEmojiToLastSkinTone(it) }.distinct()

        // Threshold filter is strict (<), so THRESHOLD+0 survives. Data layer caps at
        // take(MAX_EMOJI_SUGGESTIONS).
        val baseScore = Suggest.SUPPRESS_SUGGEST_THRESHOLD + 1

        return ArrayList(candidates.mapIndexed { i, emoji ->
            SuggestedWords.SuggestedWordInfo(
                emoji,
                "",
                baseScore - i,
                SuggestedWords.SuggestedWordInfo.KIND_EMOJI_SUGGESTION,
                null,
                SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        })
    }

    override fun getSuggestions(
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWords.SuggestedWordInfo?>? =
        lookupEmoji(
            composedData?.mTypedWord ?: "",
            ngramContext,
            composedData?.mIsBatchMode
        )

    override fun isInDictionary(word: String?): Boolean {
        return false
    }
}
