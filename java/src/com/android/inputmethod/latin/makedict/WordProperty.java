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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.utils.CombinedFormatUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility class for a word with a probability.
 *
 * This is chiefly used to iterate a dictionary.
 */
public final class WordProperty implements Comparable<WordProperty> {
    public final String mWord;
    public final ProbabilityInfo mProbabilityInfo;
    public final ArrayList<WeightedString> mShortcutTargets;
    public final ArrayList<WeightedString> mBigrams;
    // TODO: Support mIsBeginningOfSentence.
    public final boolean mIsBeginningOfSentence;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklistEntry;
    public final boolean mHasShortcuts;
    public final boolean mHasBigrams;

    private int mHashCode = 0;

    @UsedForTesting
    public WordProperty(final String word, final ProbabilityInfo probabilityInfo,
            final ArrayList<WeightedString> shortcutTargets,
            final ArrayList<WeightedString> bigrams,
            final boolean isNotAWord, final boolean isBlacklistEntry) {
        mWord = word;
        mProbabilityInfo = probabilityInfo;
        mShortcutTargets = shortcutTargets;
        mBigrams = bigrams;
        mIsBeginningOfSentence = false;
        mIsNotAWord = isNotAWord;
        mIsBlacklistEntry = isBlacklistEntry;
        mHasBigrams = bigrams != null && !bigrams.isEmpty();
        mHasShortcuts = shortcutTargets != null && !shortcutTargets.isEmpty();
    }

    private static ProbabilityInfo createProbabilityInfoFromArray(final int[] probabilityInfo) {
      return new ProbabilityInfo(
              probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_PROBABILITY_INDEX],
              probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX],
              probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_LEVEL_INDEX],
              probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_COUNT_INDEX]);
    }

    // Construct word property using information from native code.
    // This represents invalid word when the probability is BinaryDictionary.NOT_A_PROBABILITY.
    public WordProperty(final int[] codePoints, final boolean isNotAWord,
            final boolean isBlacklisted, final boolean hasBigram, final boolean hasShortcuts,
            final boolean isBeginningOfSentence, final int[] probabilityInfo,
            final ArrayList<int[]> bigramTargets, final ArrayList<int[]> bigramProbabilityInfo,
            final ArrayList<int[]> shortcutTargets,
            final ArrayList<Integer> shortcutProbabilities) {
        mWord = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints);
        mProbabilityInfo = createProbabilityInfoFromArray(probabilityInfo);
        mShortcutTargets = new ArrayList<>();
        mBigrams = new ArrayList<>();
        mIsBeginningOfSentence = isBeginningOfSentence;
        mIsNotAWord = isNotAWord;
        mIsBlacklistEntry = isBlacklisted;
        mHasShortcuts = hasShortcuts;
        mHasBigrams = hasBigram;

        final int bigramTargetCount = bigramTargets.size();
        for (int i = 0; i < bigramTargetCount; i++) {
            final String bigramTargetString =
                    StringUtils.getStringFromNullTerminatedCodePointArray(bigramTargets.get(i));
            mBigrams.add(new WeightedString(bigramTargetString,
                    createProbabilityInfoFromArray(bigramProbabilityInfo.get(i))));
        }

        final int shortcutTargetCount = shortcutTargets.size();
        for (int i = 0; i < shortcutTargetCount; i++) {
            final String shortcutTargetString =
                    StringUtils.getStringFromNullTerminatedCodePointArray(shortcutTargets.get(i));
            mShortcutTargets.add(
                    new WeightedString(shortcutTargetString, shortcutProbabilities.get(i)));
        }
    }

    public int getProbability() {
        return mProbabilityInfo.mProbability;
    }

    private static int computeHashCode(WordProperty word) {
        return Arrays.hashCode(new Object[] {
                word.mWord,
                word.mProbabilityInfo,
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
    public int compareTo(final WordProperty w) {
        if (getProbability() < w.getProbability()) return 1;
        if (getProbability() > w.getProbability()) return -1;
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
        if (!(o instanceof WordProperty)) return false;
        WordProperty w = (WordProperty)o;
        return mProbabilityInfo.equals(w.mProbabilityInfo) && mWord.equals(w.mWord)
                && mShortcutTargets.equals(w.mShortcutTargets) && mBigrams.equals(w.mBigrams)
                && mIsNotAWord == w.mIsNotAWord && mIsBlacklistEntry == w.mIsBlacklistEntry
                && mHasBigrams == w.mHasBigrams && mHasShortcuts && w.mHasBigrams;
    }

    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            mHashCode = computeHashCode(this);
        }
        return mHashCode;
    }

    @UsedForTesting
    public boolean isValid() {
        return getProbability() != BinaryDictionary.NOT_A_PROBABILITY;
    }

    @Override
    public String toString() {
        return CombinedFormatUtils.formatWordProperty(this);
    }
}
