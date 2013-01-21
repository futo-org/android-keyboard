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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility class for a word with a frequency.
 *
 * This is chiefly used to iterate a dictionary.
 */
public final class Word implements Comparable<Word> {
    public final String mWord;
    public final int mFrequency;
    public final ArrayList<WeightedString> mShortcutTargets;
    public final ArrayList<WeightedString> mBigrams;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklistEntry;

    private int mHashCode = 0;

    public Word(final String word, final int frequency,
            final ArrayList<WeightedString> shortcutTargets,
            final ArrayList<WeightedString> bigrams,
            final boolean isNotAWord, final boolean isBlacklistEntry) {
        mWord = word;
        mFrequency = frequency;
        mShortcutTargets = shortcutTargets;
        mBigrams = bigrams;
        mIsNotAWord = isNotAWord;
        mIsBlacklistEntry = isBlacklistEntry;
    }

    private static int computeHashCode(Word word) {
        return Arrays.hashCode(new Object[] {
                word.mWord,
                word.mFrequency,
                word.mShortcutTargets.hashCode(),
                word.mBigrams.hashCode(),
                word.mIsNotAWord,
                word.mIsBlacklistEntry
        });
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
        if (o == this) return true;
        if (!(o instanceof Word)) return false;
        Word w = (Word)o;
        return mFrequency == w.mFrequency && mWord.equals(w.mWord)
                && mShortcutTargets.equals(w.mShortcutTargets)
                && mBigrams.equals(w.mBigrams)
                && mIsNotAWord == w.mIsNotAWord
                && mIsBlacklistEntry == w.mIsBlacklistEntry;
    }

    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            mHashCode = computeHashCode(this);
        }
        return mHashCode;
    }
}
