/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.util.Log;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Note: this class is used as a parameter type of a native method. You should be careful when you
// rename this class or field name. See BinaryDictionary#addMultipleDictionaryEntriesNative().
public final class LanguageModelParam {
    private static final String TAG = LanguageModelParam.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_TOKEN = false;

    // For now, these probability values are being referred to only when we add new entries to
    // decaying dynamic binary dictionaries. When these are referred to, what matters is 0 or
    // non-0. Thus, it's not meaningful to compare 10, 100, and so on.
    // TODO: Revise the logic in ForgettingCurveUtils in native code.
    private static final int UNIGRAM_PROBABILITY_FOR_VALID_WORD = 100;
    private static final int UNIGRAM_PROBABILITY_FOR_OOV_WORD = Dictionary.NOT_A_PROBABILITY;
    private static final int BIGRAM_PROBABILITY_FOR_VALID_WORD = 10;
    private static final int BIGRAM_PROBABILITY_FOR_OOV_WORD = Dictionary.NOT_A_PROBABILITY;

    public final CharSequence mTargetWord;
    public final int[] mWord0;
    public final int[] mWord1;
    // TODO: this needs to be a list of shortcuts
    public final int[] mShortcutTarget;
    public final int mUnigramProbability;
    public final int mBigramProbability;
    public final int mShortcutProbability;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklisted;
    // Time stamp in seconds.
    public final int mTimestamp;

    // Constructor for unigram. TODO: support shortcuts
    public LanguageModelParam(final CharSequence word, final int unigramProbability,
            final int timestamp) {
        this(null /* word0 */, word, unigramProbability, Dictionary.NOT_A_PROBABILITY, timestamp);
    }

    // Constructor for unigram and bigram.
    public LanguageModelParam(final CharSequence word0, final CharSequence word1,
            final int unigramProbability, final int bigramProbability,
            final int timestamp) {
        mTargetWord = word1;
        mWord0 = (word0 == null) ? null : StringUtils.toCodePointArray(word0);
        mWord1 = StringUtils.toCodePointArray(word1);
        mShortcutTarget = null;
        mUnigramProbability = unigramProbability;
        mBigramProbability = bigramProbability;
        mShortcutProbability = Dictionary.NOT_A_PROBABILITY;
        mIsNotAWord = false;
        mIsBlacklisted = false;
        mTimestamp = timestamp;
    }

    // Process a list of words and return a list of {@link LanguageModelParam} objects.
    public static ArrayList<LanguageModelParam> createLanguageModelParamsFrom(
            final List<String> tokens, final int timestamp,
            final DictionaryFacilitator dictionaryFacilitator,
            final SpacingAndPunctuations spacingAndPunctuations,
            final DistracterFilter distracterFilter) {
        final ArrayList<LanguageModelParam> languageModelParams = new ArrayList<>();
        final int N = tokens.size();
        PrevWordsInfo prevWordsInfo = PrevWordsInfo.EMPTY_PREV_WORDS_INFO;
        for (int i = 0; i < N; ++i) {
            final String tempWord = tokens.get(i);
            if (StringUtils.isEmptyStringOrWhiteSpaces(tempWord)) {
                // just skip this token
                if (DEBUG_TOKEN) {
                    Log.d(TAG, "--- isEmptyStringOrWhiteSpaces: \"" + tempWord + "\"");
                }
                continue;
            }
            if (!DictionaryInfoUtils.looksValidForDictionaryInsertion(
                    tempWord, spacingAndPunctuations)) {
                if (DEBUG_TOKEN) {
                    Log.d(TAG, "--- not looksValidForDictionaryInsertion: \""
                            + tempWord + "\"");
                }
                // Sentence terminator found. Split.
                prevWordsInfo = PrevWordsInfo.EMPTY_PREV_WORDS_INFO;
                continue;
            }
            if (DEBUG_TOKEN) {
                Log.d(TAG, "--- word: \"" + tempWord + "\"");
            }
            final LanguageModelParam languageModelParam =
                    detectWhetherVaildWordOrNotAndGetLanguageModelParam(
                            prevWordsInfo, tempWord, timestamp, dictionaryFacilitator,
                            distracterFilter);
            if (languageModelParam == null) {
                continue;
            }
            languageModelParams.add(languageModelParam);
            prevWordsInfo = prevWordsInfo.getNextPrevWordsInfo(
                    new PrevWordsInfo.WordInfo(tempWord));
        }
        return languageModelParams;
    }

    private static LanguageModelParam detectWhetherVaildWordOrNotAndGetLanguageModelParam(
            final PrevWordsInfo prevWordsInfo, final String targetWord, final int timestamp,
            final DictionaryFacilitator dictionaryFacilitator,
            final DistracterFilter distracterFilter) {
        final Locale locale = dictionaryFacilitator.getLocale();
        if (locale == null) {
            return null;
        }
        if (dictionaryFacilitator.isValidWord(targetWord, false /* ignoreCase */)) {
            return createAndGetLanguageModelParamOfWord(prevWordsInfo, targetWord, timestamp,
                    true /* isValidWord */, locale, distracterFilter);
        }

        final String lowerCaseTargetWord = targetWord.toLowerCase(locale);
        if (dictionaryFacilitator.isValidWord(lowerCaseTargetWord, false /* ignoreCase */)) {
            // Add the lower-cased word.
            return createAndGetLanguageModelParamOfWord(prevWordsInfo, lowerCaseTargetWord,
                    timestamp, true /* isValidWord */, locale, distracterFilter);
        }

        // Treat the word as an OOV word.
        return createAndGetLanguageModelParamOfWord(prevWordsInfo, targetWord, timestamp,
                false /* isValidWord */, locale, distracterFilter);
    }

    private static LanguageModelParam createAndGetLanguageModelParamOfWord(
            final PrevWordsInfo prevWordsInfo, final String targetWord, final int timestamp,
            final boolean isValidWord, final Locale locale,
            final DistracterFilter distracterFilter) {
        final String word;
        if (StringUtils.getCapitalizationType(targetWord) == StringUtils.CAPITALIZE_FIRST
                && !prevWordsInfo.isValid() && !isValidWord) {
            word = targetWord.toLowerCase(locale);
        } else {
            word = targetWord;
        }
        // Check whether the word is a distracter to words in the dictionaries.
        if (distracterFilter.isDistracterToWordsInDictionaries(prevWordsInfo, word, locale)) {
            if (DEBUG) {
                Log.d(TAG, "The word (" + word + ") is a distracter. Skip this word.");
            }
            return null;
        }
        final int unigramProbability = isValidWord ?
                UNIGRAM_PROBABILITY_FOR_VALID_WORD : UNIGRAM_PROBABILITY_FOR_OOV_WORD;
        if (!prevWordsInfo.isValid()) {
            if (DEBUG) {
                Log.d(TAG, "--- add unigram: current("
                        + (isValidWord ? "Valid" : "OOV") + ") = " + word);
            }
            return new LanguageModelParam(word, unigramProbability, timestamp);
        }
        if (DEBUG) {
            Log.d(TAG, "--- add bigram: prev = " + prevWordsInfo + ", current("
                    + (isValidWord ? "Valid" : "OOV") + ") = " + word);
        }
        final int bigramProbability = isValidWord ?
                BIGRAM_PROBABILITY_FOR_VALID_WORD : BIGRAM_PROBABILITY_FOR_OOV_WORD;
        return new LanguageModelParam(prevWordsInfo.mPrevWordsInfo[0].mWord, word,
                unigramProbability, bigramProbability, timestamp);
    }
}
