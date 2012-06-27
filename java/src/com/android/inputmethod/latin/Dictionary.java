/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;

/**
 * Abstract base class for a dictionary that can do a fuzzy search for words based on a set of key
 * strokes.
 */
public abstract class Dictionary {
    /**
     * The weight to give to a word if it's length is the same as the number of typed characters.
     */
    protected static final int FULL_WORD_SCORE_MULTIPLIER = 2;

    public static final int NOT_A_PROBABILITY = -1;

    /**
     * Searches for words in the dictionary that match the characters in the composer. Matched
     * words are returned as an ArrayList.
     * @param composer the key sequence to match with coordinate info, as a WordComposer
     * @param prevWordForBigrams the previous word, or null if none
     * @param proximityInfo the object for key proximity. May be ignored by some implementations.
     * @return the list of suggestions
     */
    abstract public ArrayList<SuggestedWordInfo> getWords(final WordComposer composer,
            final CharSequence prevWordForBigrams, final ProximityInfo proximityInfo);

    /**
     * Searches for pairs in the bigram dictionary that matches the previous word.
     * @param composer the key sequence to match
     * @param previousWord the word before
     * @return the list of suggestions
     */
    public abstract ArrayList<SuggestedWordInfo> getBigrams(final WordComposer composer,
            final CharSequence previousWord);

    /**
     * Checks if the given word occurs in the dictionary
     * @param word the word to search for. The search should be case-insensitive.
     * @return true if the word exists, false otherwise
     */
    abstract public boolean isValidWord(CharSequence word);

    public int getFrequency(CharSequence word) {
        return NOT_A_PROBABILITY;
    }

    /**
     * Compares the contents of the character array with the typed word and returns true if they
     * are the same.
     * @param word the array of characters that make up the word
     * @param length the number of valid characters in the character array
     * @param typedWord the word to compare with
     * @return true if they are the same, false otherwise.
     */
    protected boolean same(final char[] word, final int length, final CharSequence typedWord) {
        if (typedWord.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (word[i] != typedWord.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Override to clean up any resources.
     */
    public void close() {
        // empty base implementation
    }

    /**
     * Subclasses may override to indicate that this Dictionary is not yet properly initialized.
     */

    public boolean isInitialized() {
        return true;
    }
}
