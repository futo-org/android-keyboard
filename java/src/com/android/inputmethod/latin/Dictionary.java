/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;

/**
 * Abstract base class for a dictionary that can do a fuzzy search for words based on a set of key
 * strokes.
 */
public abstract class Dictionary {
    public static final int NOT_A_PROBABILITY = -1;

    public static final String TYPE_USER_TYPED = "user_typed";
    public static final String TYPE_APPLICATION_DEFINED = "application_defined";
    public static final String TYPE_HARDCODED = "hardcoded"; // punctuation signs and such
    public static final String TYPE_MAIN = "main";
    public static final String TYPE_CONTACTS = "contacts";
    // User dictionary, the system-managed one.
    public static final String TYPE_USER = "user";
    // User history dictionary internal to LatinIME.
    public static final String TYPE_USER_HISTORY = "history";
    // Spawned by resuming suggestions. Comes from a span that was in the TextView.
    public static final String TYPE_RESUMED = "resumed";
    protected final String mDictType;

    public Dictionary(final String dictType) {
        mDictType = dictType;
    }

    /**
     * Searches for suggestions for a given context. For the moment the context is only the
     * previous word.
     * @param composer the key sequence to match with coordinate info, as a WordComposer
     * @param prevWord the previous word, or null if none
     * @param proximityInfo the object for key proximity. May be ignored by some implementations.
     * @param blockOffensiveWords whether to block potentially offensive words
     * @return the list of suggestions (possibly null if none)
     */
    // TODO: pass more context than just the previous word, to enable better suggestions (n-gram
    // and more)
    abstract public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords);

    // The default implementation of this method ignores sessionId.
    // Subclasses that want to use sessionId need to override this method.
    public ArrayList<SuggestedWordInfo> getSuggestionsWithSessionId(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int sessionId) {
        return getSuggestions(composer, prevWord, proximityInfo, blockOffensiveWords);
    }

    /**
     * Checks if the given word occurs in the dictionary
     * @param word the word to search for. The search should be case-insensitive.
     * @return true if the word exists, false otherwise
     */
    abstract public boolean isValidWord(final String word);

    public int getFrequency(final String word) {
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
    protected boolean same(final char[] word, final int length, final String typedWord) {
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
