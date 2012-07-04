/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class AutoCorrection {
    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = AutoCorrection.class.getSimpleName();

    private AutoCorrection() {
        // Purely static class: can't instantiate.
    }

    public static CharSequence computeAutoCorrectionWord(
            final ConcurrentHashMap<String, Dictionary> dictionaries,
            final WordComposer wordComposer, final ArrayList<SuggestedWordInfo> suggestions,
            final CharSequence consideredWord, final float autoCorrectionThreshold,
            final CharSequence whitelistedWord) {
        if (hasAutoCorrectionForWhitelistedWord(whitelistedWord)) {
            return whitelistedWord;
        } else if (hasAutoCorrectionForConsideredWord(
                dictionaries, wordComposer, suggestions, consideredWord)) {
            return consideredWord;
        } else if (hasAutoCorrectionForBinaryDictionary(wordComposer, suggestions,
                consideredWord, autoCorrectionThreshold)) {
            return suggestions.get(0).mWord;
        }
        return null;
    }

    public static boolean isValidWord(final ConcurrentHashMap<String, Dictionary> dictionaries,
            CharSequence word, boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final CharSequence lowerCasedWord = word.toString().toLowerCase();
        for (final String key : dictionaries.keySet()) {
            if (key.equals(Suggest.DICT_KEY_WHITELIST)) continue;
            final Dictionary dictionary = dictionaries.get(key);
            // It's unclear how realistically 'dictionary' can be null, but the monkey is somehow
            // managing to get null in here. Presumably the language is changing to a language with
            // no main dictionary and the monkey manages to type a whole word before the thread
            // that reads the dictionary is started or something?
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)
                    || (ignoreCase && dictionary.isValidWord(lowerCasedWord))) {
                return true;
            }
        }
        return false;
    }

    public static int getMaxFrequency(final ConcurrentHashMap<String, Dictionary> dictionaries,
            CharSequence word) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = -1;
        for (final String key : dictionaries.keySet()) {
            if (key.equals(Suggest.DICT_KEY_WHITELIST)) continue;
            final Dictionary dictionary = dictionaries.get(key);
            if (null == dictionary) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    public static boolean allowsToBeAutoCorrected(
            final ConcurrentHashMap<String, Dictionary> dictionaries,
            final CharSequence word, final boolean ignoreCase) {
        final WhitelistDictionary whitelistDictionary =
                (WhitelistDictionary)dictionaries.get(Suggest.DICT_KEY_WHITELIST);
        // If "word" is in the whitelist dictionary, it should not be auto corrected.
        if (whitelistDictionary != null
                && whitelistDictionary.shouldForciblyAutoCorrectFrom(word)) {
            return true;
        }
        return !isValidWord(dictionaries, word, ignoreCase);
    }

    private static boolean hasAutoCorrectionForWhitelistedWord(CharSequence whiteListedWord) {
        return whiteListedWord != null;
    }

    private static boolean hasAutoCorrectionForConsideredWord(
            final ConcurrentHashMap<String, Dictionary> dictionaries,
            final WordComposer wordComposer, final ArrayList<SuggestedWordInfo> suggestions,
            final CharSequence consideredWord) {
        if (TextUtils.isEmpty(consideredWord)) return false;
        return wordComposer.size() > 1 && suggestions.size() > 0
                && !allowsToBeAutoCorrected(dictionaries, consideredWord, false);
    }

    private static boolean hasAutoCorrectionForBinaryDictionary(WordComposer wordComposer,
            ArrayList<SuggestedWordInfo> suggestions,
            CharSequence consideredWord, float autoCorrectionThreshold) {
        if (wordComposer.size() > 1 && suggestions.size() > 0) {
            final SuggestedWordInfo autoCorrectionSuggestion = suggestions.get(0);
            //final int autoCorrectionSuggestionScore = sortedScores[0];
            final int autoCorrectionSuggestionScore = autoCorrectionSuggestion.mScore;
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                    consideredWord.toString(), autoCorrectionSuggestion.mWord.toString(),
                    autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + consideredWord + "," + autoCorrectionSuggestion + ","
                        + autoCorrectionSuggestionScore + ", " + normalizedScore
                        + "(" + autoCorrectionThreshold + ")");
            }
            if (normalizedScore >= autoCorrectionThreshold) {
                if (DBG) {
                    Log.d(TAG, "Auto corrected by S-threshold.");
                }
                return true;
            }
        }
        return false;
    }

}
