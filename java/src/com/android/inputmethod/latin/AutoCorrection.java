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

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;

public class AutoCorrection {
    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = AutoCorrection.class.getSimpleName();
    private boolean mHasAutoCorrection;
    private CharSequence mAutoCorrectionWord;
    private double mNormalizedScore;

    public void init() {
        mHasAutoCorrection = false;
        mAutoCorrectionWord = null;
        mNormalizedScore = Integer.MIN_VALUE;
    }

    public boolean hasAutoCorrection() {
        return mHasAutoCorrection;
    }

    public CharSequence getAutoCorrectionWord() {
        return mAutoCorrectionWord;
    }

    public double getNormalizedScore() {
        return mNormalizedScore;
    }

    public void updateAutoCorrectionStatus(Map<String, Dictionary> dictionaries,
            WordComposer wordComposer, ArrayList<CharSequence> suggestions, int[] sortedScores,
            CharSequence typedWord, double autoCorrectionThreshold, int correctionMode,
            CharSequence whitelistedWord) {
        if (hasAutoCorrectionForWhitelistedWord(whitelistedWord)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = whitelistedWord;
        } else if (hasAutoCorrectionForTypedWord(
                dictionaries, wordComposer, suggestions, typedWord, correctionMode)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = typedWord;
        } else if (hasAutoCorrectionForBinaryDictionary(wordComposer, suggestions, correctionMode,
                sortedScores, typedWord, autoCorrectionThreshold)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = suggestions.get(0);
        }
    }

    public static boolean isValidWord(
            Map<String, Dictionary> dictionaries, CharSequence word, boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final CharSequence lowerCasedWord = word.toString().toLowerCase();
        for (final String key : dictionaries.keySet()) {
            if (key.equals(Suggest.DICT_KEY_WHITELIST)) continue;
            final Dictionary dictionary = dictionaries.get(key);
            if (dictionary.isValidWord(word)
                    || (ignoreCase && dictionary.isValidWord(lowerCasedWord))) {
                return true;
            }
        }
        return false;
    }

    public static boolean allowsToBeAutoCorrected(
            Map<String, Dictionary> dictionaries, CharSequence word, boolean ignoreCase) {
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

    private boolean hasAutoCorrectionForTypedWord(Map<String, Dictionary> dictionaries,
            WordComposer wordComposer, ArrayList<CharSequence> suggestions, CharSequence typedWord,
            int correctionMode) {
        if (TextUtils.isEmpty(typedWord)) return false;
        boolean allowsAutoCorrect = allowsToBeAutoCorrected(dictionaries, typedWord, false);
        return wordComposer.size() > 1 && suggestions.size() > 0 && !allowsAutoCorrect
                && (correctionMode == Suggest.CORRECTION_FULL
                || correctionMode == Suggest.CORRECTION_FULL_BIGRAM);
    }

    private boolean hasAutoCorrectionForBinaryDictionary(WordComposer wordComposer,
            ArrayList<CharSequence> suggestions, int correctionMode, int[] sortedScores,
            CharSequence typedWord, double autoCorrectionThreshold) {
        if (wordComposer.size() > 1 && (correctionMode == Suggest.CORRECTION_FULL
                || correctionMode == Suggest.CORRECTION_FULL_BIGRAM)
                && typedWord != null && suggestions.size() > 0 && sortedScores.length > 0) {
            final CharSequence autoCorrectionSuggestion = suggestions.get(0);
            final int autoCorrectionSuggestionScore = sortedScores[0];
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            mNormalizedScore = Utils.calcNormalizedScore(
                    typedWord,autoCorrectionSuggestion, autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + typedWord + "," + autoCorrectionSuggestion + ","
                        + autoCorrectionSuggestionScore + ", " + mNormalizedScore
                        + "(" + autoCorrectionThreshold + ")");
            }
            if (mNormalizedScore >= autoCorrectionThreshold) {
                if (DBG) {
                    Log.d(TAG, "Auto corrected by S-threshold.");
                }
                return true;
            }
        }
        return false;
    }

}
