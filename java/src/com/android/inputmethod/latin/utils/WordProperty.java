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


package com.android.inputmethod.latin.utils;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.util.ArrayList;

// This has information that belong to a unigram. This class has some detailed attributes such as
// historical information but they have to be checked only for testing purpose.
@UsedForTesting
public class WordProperty {
    public final String mCodePoints;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklisted;
    public final boolean mHasBigrams;
    public final boolean mHasShortcuts;
    public final ProbabilityInfo mProbabilityInfo;
    public final ArrayList<WeightedString> mBigramTargets = CollectionUtils.newArrayList();
    public final ArrayList<ProbabilityInfo> mBigramProbabilityInfo = CollectionUtils.newArrayList();
    public final ArrayList<WeightedString> mShortcutTargets = CollectionUtils.newArrayList();

    // TODO: Use this kind of Probability class for dictionary read/write code under the makedict
    // package.
    public static final class ProbabilityInfo {
        public final int mProbability;
        // mTimestamp, mLevel and mCount are historical info. These values are depend on the
        // implementation in native code; thus, we must not use them and have any assumptions about
        // them except for tests.
        public final int mTimestamp;
        public final int mLevel;
        public final int mCount;

        public ProbabilityInfo(final int[] probabilityInfo) {
            mProbability = probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_PROBABILITY_INDEX];
            mTimestamp = probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX];
            mLevel = probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_LEVEL_INDEX];
            mCount = probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_COUNT_INDEX];
        }

        @Override
        public String toString() {
            return mTimestamp + ":" + mLevel + ":" + mCount;
        }
    }

    private static int getCodePointCount(final int[] codePoints) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == 0) {
                return i;
            }
        }
        return codePoints.length;
    }

    // This represents invalid word when the probability is BinaryDictionary.NOT_A_PROBABILITY.
    public WordProperty(final int[] codePoints, final boolean isNotAWord,
            final boolean isBlacklisted, final boolean hasBigram,
            final boolean hasShortcuts, final int[] probabilityInfo,
            final ArrayList<int[]> bigramTargets, final ArrayList<int[]> bigramProbabilityInfo,
            final ArrayList<int[]> shortcutTargets,
            final ArrayList<Integer> shortcutProbabilities) {
        mCodePoints = new String(codePoints, 0 /* offset */, getCodePointCount(codePoints));
        mIsNotAWord = isNotAWord;
        mIsBlacklisted = isBlacklisted;
        mHasBigrams = hasBigram;
        mHasShortcuts = hasShortcuts;
        mProbabilityInfo = new ProbabilityInfo(probabilityInfo);

        final int bigramTargetCount = bigramTargets.size();
        for (int i = 0; i < bigramTargetCount; i++) {
            final int[] bigramTargetCodePointArray = bigramTargets.get(i);
            final String bigramTargetString = new String(bigramTargetCodePointArray,
                    0 /* offset */, getCodePointCount(bigramTargetCodePointArray));
            final ProbabilityInfo bigramProbability =
                    new ProbabilityInfo(bigramProbabilityInfo.get(i));
            mBigramTargets.add(
                    new WeightedString(bigramTargetString, bigramProbability.mProbability));
            mBigramProbabilityInfo.add(bigramProbability);
        }

        final int shortcutTargetCount = shortcutTargets.size();
        for (int i = 0; i < shortcutTargetCount; i++) {
            final int[] shortcutTargetCodePointArray = shortcutTargets.get(i);
            final String shortcutTargetString = new String(shortcutTargetCodePointArray,
                    0 /* offset */, getCodePointCount(shortcutTargetCodePointArray));
            mShortcutTargets.add(
                    new WeightedString(shortcutTargetString, shortcutProbabilities.get(i)));
        }
    }

    @UsedForTesting
    public boolean isValid() {
        return mProbabilityInfo.mProbability != BinaryDictionary.NOT_A_PROBABILITY;
    }

    @Override
    public String toString() {
        // TODO: Move this logic to CombinedInputOutput.
        final StringBuffer builder = new StringBuffer();
        builder.append(" word=" + mCodePoints);
        builder.append(",");
        builder.append("f=" + mProbabilityInfo.mProbability);
        if (mIsNotAWord) {
            builder.append(",");
            builder.append("not_a_word=true");
        }
        if (mIsBlacklisted) {
            builder.append(",");
            builder.append("blacklisted=true");
        }
        if (mProbabilityInfo.mTimestamp != BinaryDictionary.NOT_A_VALID_TIMESTAMP) {
            builder.append(",");
            builder.append("historicalInfo=" + mProbabilityInfo);
        }
        builder.append("\n");
        for (int i = 0; i < mBigramTargets.size(); i++) {
            builder.append("  bigram=" + mBigramTargets.get(i).mWord);
            builder.append(",");
            builder.append("f=" + mBigramTargets.get(i).mFrequency);
            if (mBigramProbabilityInfo.get(i).mTimestamp
                    != BinaryDictionary.NOT_A_VALID_TIMESTAMP) {
                builder.append(",");
                builder.append("historicalInfo=" + mBigramProbabilityInfo.get(i));
            }
            builder.append("\n");
        }
        for (int i = 0; i < mShortcutTargets.size(); i++) {
            builder.append("  shortcut=" + mShortcutTargets.get(i).mWord);
            builder.append(",");
            builder.append("f=" + mShortcutTargets.get(i).mFrequency);
            builder.append("\n");
        }
        return builder.toString();
    }
}