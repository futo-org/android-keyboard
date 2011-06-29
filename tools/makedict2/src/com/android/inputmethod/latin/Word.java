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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.FusionDictionary.WeightedString;

import java.util.ArrayList;

/**
 * Utility class for a word with a frequency.
 *
 * This is chiefly used to iterate a dictionary.
 */
public class Word implements Comparable<Word> {
    final String mWord;
    final int mFrequency;
    final ArrayList<WeightedString> mBigrams;

    public Word(String word, int frequency, ArrayList<WeightedString> bigrams) {
        mWord = word;
        mFrequency = frequency;
        mBigrams = bigrams;
    }

    /**
     * Three-way comparison.
     *
     * A Word x is greater than a word y if x has a higher frequency. If they have the same
     * frequency, they are sorted in lexicographic order.
     */
    @Override
    public int compareTo(Word w) {
        if (mFrequency < w.mFrequency) return 1;
        if (mFrequency > w.mFrequency) return -1;
        return mWord.compareTo(w.mWord);
    }

    /**
     * Equality test.
     *
     * Words are equal if they have the same frequency, the same spellings, and the same
     * attributes.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Word)) return false;
        Word w = (Word)o;
        return mFrequency == w.mFrequency && mWord.equals(w.mWord)
                && mBigrams.equals(w.mBigrams);
    }
}
