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

    override fun getSuggestions(
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWords.SuggestedWordInfo?>? {
        if(DataStoreHelper.getSetting(SHOW_EMOJI_SUGGESTIONS) == false) return arrayListOf()

        val typedWord = composedData?.mTypedWord ?: ""

        var emoji: String? = null
        if(!typedWord.isEmpty()) {
            emoji = PersistentEmojiState.getShortcut(mLocale, typedWord.lowercase(mLocale))
        } else if((ngramContext?.prevWordCount ?: 0) > 0) {
            val prevWord = ngramContext?.getNthPrevWord(1)?.toString() ?: ""
            if(!prevWord.isEmpty()) {
                emoji = PersistentEmojiState.getShortcut(mLocale, prevWord.lowercase(mLocale))
            }
        }

        return if(emoji != null) {
            arrayListOf(
                SuggestedWords.SuggestedWordInfo(
                    emoji,
                    "",
                    SuggestedWords.SuggestedWordInfo.MAX_SCORE - 1,
                    SuggestedWords.SuggestedWordInfo.KIND_EMOJI_SUGGESTION,
                    null,
                    SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE
                )
            )
        } else {
            arrayListOf()
        }
    }

    override fun isInDictionary(word: String?): Boolean {
        return false
    }
}