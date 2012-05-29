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

/**
 * Abstract base class for a dictionary that can do a fuzzy search for words based on a set of key
 * strokes.
 */
public abstract class Dictionary {
    /**
     * The weight to give to a word if it's length is the same as the number of typed characters.
     */
    protected static final int FULL_WORD_SCORE_MULTIPLIER = 2;

    public static final int UNIGRAM = 0;
    public static final int BIGRAM = 1;

    public static final int NOT_A_PROBABILITY = -1;
    /**
     * Interface to be implemented by classes requesting words to be fetched from the dictionary.
     * @see #getWords(WordComposer, CharSequence, WordCallback, ProximityInfo)
     */
    public interface WordCallback {
        /**
         * Adds a word to a list of suggestions. The word is expected to be ordered based on
         * the provided score.
         * @param word the character array containing the word
         * @param wordOffset starting offset of the word in the character array
         * @param wordLength length of valid characters in the character array
         * @param score the score of occurrence. This is normalized between 1 and 255, but
         * can exceed those limits
         * @param dicTypeId of the dictionary where word was from
         * @param dataType tells type of this data, either UNIGRAM or BIGRAM
         * @return true if the word was added, false if no more words are required
         */
        boolean addWord(char[] word, int wordOffset, int wordLength, int score, int dicTypeId,
                int dataType);
    }

    /**
     * Searches for words in the dictionary that match the characters in the composer. Matched
     * words are added through the callback object.
     * @param composer the key sequence to match
     * @param prevWordForBigrams the previous word, or null if none
     * @param callback the callback object to send matched words to as possible candidates
     * @param proximityInfo the object for key proximity. May be ignored by some implementations.
     * @see WordCallback#addWord(char[], int, int, int, int, int)
     */
    abstract public void getWords(final WordComposer composer,
            final CharSequence prevWordForBigrams, final WordCallback callback,
            final ProximityInfo proximityInfo);

    /**
     * Searches for pairs in the bigram dictionary that matches the previous word and all the
     * possible words following are added through the callback object.
     * @param composer the key sequence to match
     * @param previousWord the word before
     * @param callback the callback object to send possible word following previous word
     */
    public void getBigrams(final WordComposer composer, final CharSequence previousWord,
            final WordCallback callback) {
        // empty base implementation
    }

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
}
