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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.IOException;
import java.util.ArrayList;

/**
 * An interface of a binary dictionary updater.
 */
@UsedForTesting
public interface DictUpdater extends DictDecoder {

    /**
     * Deletes the word from the binary dictionary.
     *
     * @param word the word to be deleted.
     */
    @UsedForTesting
    public void deleteWord(final String word) throws IOException, UnsupportedFormatException;

    /**
     * Inserts a word into a binary dictionary.
     *
     * @param word the word to be inserted.
     * @param frequency the frequency of the new word.
     * @param bigramStrings bigram list, or null if none.
     * @param shortcuts shortcut list, or null if none.
     * @param isBlackListEntry whether this should be a blacklist entry.
     */
    // TODO: Support batch insertion.
    @UsedForTesting
    public void insertWord(final String word, final int frequency,
            final ArrayList<WeightedString> bigramStrings,
            final ArrayList<WeightedString> shortcuts, final boolean isNotAWord,
            final boolean isBlackListEntry) throws IOException, UnsupportedFormatException;
}
