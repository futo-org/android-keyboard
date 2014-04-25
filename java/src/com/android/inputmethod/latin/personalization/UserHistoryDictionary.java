/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.personalization;

import android.content.Context;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;

import java.io.File;
import java.util.Locale;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public class UserHistoryDictionary extends DecayingExpandableBinaryDictionaryBase {
    /* package */ static final String NAME = UserHistoryDictionary.class.getSimpleName();

    /* package */ UserHistoryDictionary(final Context context, final Locale locale) {
        this(context, locale, null /* dictFile */);
    }

    public UserHistoryDictionary(final Context context, final Locale locale,
            final File dictFile) {
        super(context, getDictName(NAME, locale, dictFile), locale, Dictionary.TYPE_USER_HISTORY,
                dictFile);
    }

    @Override
    public boolean isValidWord(final String word) {
        // Strings out of this dictionary should not be considered existing words.
        return false;
    }

    /**
     * Pair will be added to the user history dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public static void addToDictionary(final ExpandableBinaryDictionary userHistoryDictionary,
            final String word0, final String word1, final boolean isValid, final int timestamp) {
        if (word1.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH ||
                (word0 != null && word0.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH)) {
            return;
        }
        final int frequency = isValid ?
                FREQUENCY_FOR_WORDS_IN_DICTS : FREQUENCY_FOR_WORDS_NOT_IN_DICTS;
        userHistoryDictionary.addWordDynamically(word1, frequency, null /* shortcutTarget */,
                0 /* shortcutFreq */, false /* isNotAWord */, false /* isBlacklisted */, timestamp);
        // Do not insert a word as a bigram of itself
        if (word1.equals(word0)) {
            return;
        }
        if (null != word0) {
            userHistoryDictionary.addBigramDynamically(word0, word1, frequency, timestamp);
        }
    }
}
