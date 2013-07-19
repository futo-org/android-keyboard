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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

public final class AutoCorrectionUtils {
    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = AutoCorrectionUtils.class.getSimpleName();
    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;

    private AutoCorrectionUtils() {
        // Purely static class: can't instantiate.
    }

    public static boolean isValidWord(final Suggest suggest, final String word,
            final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final ConcurrentHashMap<String, Dictionary> dictionaries = suggest.getUnigramDictionaries();
        final String lowerCasedWord = word.toLowerCase(suggest.mLocale);
        for (final String key : dictionaries.keySet()) {
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
            final String word) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = -1;
        for (final String key : dictionaries.keySet()) {
            final Dictionary dictionary = dictionaries.get(key);
            if (null == dictionary) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    public static boolean suggestionExceedsAutoCorrectionThreshold(
            final SuggestedWordInfo suggestion, final String consideredWord,
            final float autoCorrectionThreshold) {
        if (null != suggestion) {
            // Shortlist a whitelisted word
            if (suggestion.mKind == SuggestedWordInfo.KIND_WHITELIST) return true;
            final int autoCorrectionSuggestionScore = suggestion.mScore;
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                    consideredWord, suggestion.mWord, autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + consideredWord + "," + suggestion + ","
                        + autoCorrectionSuggestionScore + ", " + normalizedScore
                        + "(" + autoCorrectionThreshold + ")");
            }
            if (normalizedScore >= autoCorrectionThreshold) {
                if (DBG) {
                    Log.d(TAG, "Auto corrected by S-threshold.");
                }
                return !shouldBlockAutoCorrectionBySafetyNet(consideredWord, suggestion.mWord);
            }
        }
        return false;
    }

    // TODO: Resolve the inconsistencies between the native auto correction algorithms and
    // this safety net
    public static boolean shouldBlockAutoCorrectionBySafetyNet(final String typedWord,
            final String suggestion) {
        // Safety net for auto correction.
        // Actually if we hit this safety net, it's a bug.
        // If user selected aggressive auto correction mode, there is no need to use the safety
        // net.
        // If the length of typed word is less than MINIMUM_SAFETY_NET_CHAR_LENGTH,
        // we should not use net because relatively edit distance can be big.
        final int typedWordLength = typedWord.length();
        if (typedWordLength < MINIMUM_SAFETY_NET_CHAR_LENGTH) {
            return false;
        }
        final int maxEditDistanceOfNativeDictionary =
                (typedWordLength < 5 ? 2 : typedWordLength / 2) + 1;
        final int distance = BinaryDictionary.editDistance(typedWord, suggestion);
        if (DBG) {
            Log.d(TAG, "Autocorrected edit distance = " + distance
                    + ", " + maxEditDistanceOfNativeDictionary);
        }
        if (distance > maxEditDistanceOfNativeDictionary) {
            if (DBG) {
                Log.e(TAG, "Safety net: before = " + typedWord + ", after = " + suggestion);
                Log.e(TAG, "(Error) The edit distance of this correction exceeds limit. "
                        + "Turning off auto-correction.");
            }
            return true;
        } else {
            return false;
        }
    }
}
