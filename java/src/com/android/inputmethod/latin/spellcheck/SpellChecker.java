/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.spellcheck;

import android.content.Context;
import android.content.res.Resources;

import com.android.inputmethod.compat.ArraysCompatUtils;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Dictionary.DataType;
import com.android.inputmethod.latin.Dictionary.WordCallback;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.WordComposer;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Implements spell checking methods.
 */
public class SpellChecker {

    public final Dictionary mDictionary;

    public SpellChecker(final Context context, final Locale locale) {
        final Resources resources = context.getResources();
        final int fallbackResourceId = Utils.getMainDictionaryResourceId(resources);
        mDictionary = DictionaryFactory.createDictionaryFromManager(context, locale,
                fallbackResourceId);
    }

    // Note : this must be reentrant
    /**
     * Finds out whether a word is in the dictionary or not.
     *
     * @param text the sequence containing the word to check for.
     * @param start the index of the first character of the word in text.
     * @param end the index of the next-to-last character in text.
     * @return true if the word is in the dictionary, false otherwise.
     */
    public boolean isCorrect(final CharSequence text, final int start, final int end) {
        return mDictionary.isValidWord(text.subSequence(start, end));
    }

    private static class SuggestionsGatherer implements WordCallback {
        private final int DEFAULT_SUGGESTION_LENGTH = 16;
        private final List<String> mSuggestions = new LinkedList<String>();
        private int[] mScores = new int[DEFAULT_SUGGESTION_LENGTH];
        private int mLength = 0;

        @Override
        synchronized public boolean addWord(char[] word, int wordOffset, int wordLength, int score,
                int dicTypeId, DataType dataType) {
            if (mLength >= mScores.length) {
                final int newLength = mScores.length * 2;
                mScores = new int[newLength];
            }
            final int positionIndex = ArraysCompatUtils.binarySearch(mScores, 0, mLength, score);
            // binarySearch returns the index if the element exists, and -<insertion index> - 1
            // if it doesn't. See documentation for binarySearch.
            final int insertionIndex = positionIndex >= 0 ? positionIndex : -positionIndex - 1;
            System.arraycopy(mScores, insertionIndex, mScores, insertionIndex + 1,
                    mLength - insertionIndex);
            mLength += 1;
            mScores[insertionIndex] = score;
            mSuggestions.add(insertionIndex, new String(word, wordOffset, wordLength));
            return true;
        }

        public List<String> getGatheredSuggestions() {
            return mSuggestions;
        }
    }

    // Note : this must be reentrant
    /**
     * Gets a list of suggestions for a specific string.
     *
     * This returns a list of possible corrections for the text passed as an
     * arguments. It may split or group words, and even perform grammatical
     * analysis.
     *
     * @param text the sequence containing the word to check for.
     * @param start the index of the first character of the word in text.
     * @param end the index of the next-to-last character in text.
     * @return a list of possible suggestions to replace the text.
     */
    public List<String> getSuggestions(final CharSequence text, final int start, final int end) {
        final SuggestionsGatherer suggestionsGatherer = new SuggestionsGatherer();
        final WordComposer composer = new WordComposer();
        for (int i = start; i < end; ++i) {
            int character = text.charAt(i);
            composer.add(character, new int[] { character },
                    WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
        }
        mDictionary.getWords(composer, suggestionsGatherer, ProximityInfo.getDummyProximityInfo());
        return suggestionsGatherer.getGatheredSuggestions();
    }
}
