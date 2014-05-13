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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;

/**
 * This class is used to prevent distracters being added to personalization
 * or user history dictionaries
 */
public class DistracterFilter {
    private final Suggest mSuggest;
    private final Keyboard mKeyboard;

    // If the score of the top suggestion exceeds this value, the tested word (e.g.,
    // an OOV, a misspelling, or an in-vocabulary word) would be considered as a distracter to
    // words in dictionary. The greater the threshold is, the less likely the tested word would
    // become a distracter, which means the tested word will be more likely to be added to
    // the dictionary.
    private static final float DISTRACTER_WORD_SCORE_THRESHOLD = 2.0f;

    /**
     * Create a DistracterFilter instance.
     *
     * @param suggest an instance of Suggest which will be used to obtain a list of suggestions
     *                for a potential distracter
     * @param keyboard the keyboard that is currently being used. This information is needed
     *                 when calling mSuggest.getSuggestedWords(...) to obtain a list of suggestions.
     */
    public DistracterFilter(final Suggest suggest, final Keyboard keyboard) {
        mSuggest = suggest;
        mKeyboard = keyboard;
    }

    public static DistracterFilter createDistracterFilter(final Suggest suggest,
            final KeyboardSwitcher keyboardSwitcher) {
        final MainKeyboardView mainKeyboardView = keyboardSwitcher.getMainKeyboardView();
        // TODO: Create Keyboard when mainKeyboardView is null.
        // TODO: Figure out the most reasonable keyboard for the filter. Refer to the
        // spellchecker's logic.
        final Keyboard keyboard = (mainKeyboardView != null) ?
                mainKeyboardView.getKeyboard() : null;
        final DistracterFilter distracterFilter = new DistracterFilter(suggest, keyboard);
        return distracterFilter;
    }

    private static boolean suggestionExceedsDistracterThreshold(
            final SuggestedWordInfo suggestion, final String consideredWord,
            final float distracterThreshold) {
        if (null != suggestion) {
            final int suggestionScore = suggestion.mScore;
            final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                    consideredWord, suggestion.mWord, suggestionScore);
            if (normalizedScore > distracterThreshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether a word is a distracter to words in dictionaries.
     *
     * @param prevWord the previous word, or null if none.
     * @param testedWord the word that will be tested to see whether it is a distracter to words
     *                   in dictionaries.
     * @return true if testedWord is a distracter, otherwise false.
     */
    public boolean isDistracterToWordsInDictionaries(final String prevWord,
            final String testedWord) {
        if (mSuggest == null) {
            return false;
        }

        final WordComposer composer = new WordComposer();
        final int[] codePoints = StringUtils.toCodePointArray(testedWord);
        final int[] coordinates;
        if (null == mKeyboard) {
            coordinates = CoordinateUtils.newCoordinateArray(codePoints.length,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        } else {
            coordinates = mKeyboard.getCoordinates(codePoints);
        }
        composer.setComposingWord(codePoints, coordinates, prevWord);

        final int trailingSingleQuotesCount = composer.trailingSingleQuotesCount();
        final String consideredWord = trailingSingleQuotesCount > 0 ? testedWord.substring(0,
                testedWord.length() - trailingSingleQuotesCount) : testedWord;
        final AsyncResultHolder<Boolean> holder = new AsyncResultHolder<Boolean>();
        final OnGetSuggestedWordsCallback callback = new OnGetSuggestedWordsCallback() {
            @Override
            public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                if (suggestedWords != null && suggestedWords.size() > 1) {
                    // The suggestedWordInfo at 0 is the typed word. The 1st suggestion from
                    // the decoder is at index 1.
                    final SuggestedWordInfo firstSuggestion = suggestedWords.getInfo(1);
                    final boolean hasStrongDistractor = suggestionExceedsDistracterThreshold(
                            firstSuggestion, consideredWord, DISTRACTER_WORD_SCORE_THRESHOLD);
                    holder.set(hasStrongDistractor);
                }
            }
        };
        mSuggest.getSuggestedWords(composer, prevWord, mKeyboard.getProximityInfo(),
                true /* blockOffensiveWords */, true /* isCorrectionEnbaled */,
                null /* additionalFeaturesOptions */, 0 /* sessionId */,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER, callback);

        return holder.get(false /* defaultValue */, Constants.GET_SUGGESTED_WORDS_TIMEOUT);
    }
}
