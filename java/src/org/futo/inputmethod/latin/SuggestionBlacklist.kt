package org.futo.inputmethod.latin

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.settings.badWords
import org.futo.inputmethod.latin.uix.settings.isFiltered

class SuggestionBlacklist(val settings: Settings, val context: Context, val lifecycleScope: LifecycleCoroutineScope) {
    var offensiveWordsAdded = false
    var currentBlacklist: Set<String> = setOf()

    fun init() {
        lifecycleScope.launch {
            context.getSettingFlow(SUGGESTION_BLACKLIST).collect { value ->
                currentBlacklist = value + if(offensiveWordsAdded) { badWords } else { setOf() }
            }
        }
    }

    fun isSuggestedWordOk(word: SuggestedWordInfo): Boolean {
        return (word.mWord !in currentBlacklist) && (!offensiveWordsAdded || !isFiltered(word.mWord))
                 || (word.isKindOf(SuggestedWordInfo.KIND_TYPED))
    }

    fun filterBlacklistedSuggestions(suggestions: SuggestedWords): SuggestedWords {
        if(settings.current.mBlockPotentiallyOffensive && !offensiveWordsAdded) {
            currentBlacklist = currentBlacklist + badWords
            offensiveWordsAdded = true
        } else if(!settings.current.mBlockPotentiallyOffensive && offensiveWordsAdded) {
            currentBlacklist = context.getSetting(SUGGESTION_BLACKLIST)
            offensiveWordsAdded = false
        }

        val typedWord = when(suggestions.mInputStyle) {
            SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
            SuggestedWords.INPUT_STYLE_TAIL_BATCH -> null

            else -> suggestions.mTypedWordInfo
        }

        val filter: (SuggestedWordInfo) -> Boolean = { it -> isSuggestedWordOk(it) || (it == typedWord) }

        val shouldStillAutocorrect = suggestions.mWillAutoCorrect
                && (suggestions.size() > SuggestedWords.INDEX_OF_AUTO_CORRECTION)
                && filter(suggestions.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))

        val filtered = suggestions.mSuggestedWordInfoList.filter(filter)

        return SuggestedWords(
            ArrayList(filtered),
            suggestions.mRawSuggestions?.filter {
                (it.mWord !in currentBlacklist) || (it == suggestions.mTypedWordInfo)
            }?.let { ArrayList(it) },
            suggestions.mTypedWordInfo,
            suggestions.mTypedWordValid,
            shouldStillAutocorrect,
            suggestions.mIsObsoleteSuggestions,
            suggestions.mInputStyle,
            suggestions.mSequenceNumber,
            suggestions.mHighlightedCandidate
        )
    }
}