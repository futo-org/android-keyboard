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
public class UnigramProperty {
    public final String mCodePoints;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklisted;
    public final boolean mHasBigrams;
    public final boolean mHasShortcuts;
    public final int mProbability;
    // mTimestamp, mLevel and mCount are historical info. These values are depend on the
    // implementation in native code; thus, we must not use them and have any assumptions about
    // them except for tests.
    public final int mTimestamp;
    public final int mLevel;
    public final int mCount;
    public final ArrayList<WeightedString> mShortcutTargets = CollectionUtils.newArrayList();

    private static int getCodePointCount(final int[] codePoints) {
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == 0) {
                return i;
            }
        }
        return codePoints.length;
    }

    // This represents invalid unigram when the probability is BinaryDictionary.NOT_A_PROBABILITY.
    public UnigramProperty(final int[] codePoints, final boolean isNotAWord,
            final boolean isBlacklisted, final boolean hasBigram,
            final boolean hasShortcuts, final int probability, final int timestamp,
            final int level, final int count, final ArrayList<int[]> shortcutTargets,
            final ArrayList<Integer> shortcutProbabilities) {
        mCodePoints = new String(codePoints, 0 /* offset */, getCodePointCount(codePoints));
        mIsNotAWord = isNotAWord;
        mIsBlacklisted = isBlacklisted;
        mHasBigrams = hasBigram;
        mHasShortcuts = hasShortcuts;
        mProbability = probability;
        mTimestamp = timestamp;
        mLevel = level;
        mCount = count;
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
        return mProbability != BinaryDictionary.NOT_A_PROBABILITY;
    }
}