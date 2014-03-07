/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.Arrays;

/**
 * A string with a probability.
 *
 * This represents an "attribute", that is either a bigram or a shortcut.
 */
public final class WeightedString {
    public final String mWord;
    public ProbabilityInfo mProbabilityInfo;

    public WeightedString(final String word, final int probability) {
        this(word, new ProbabilityInfo(probability));
    }

    public WeightedString(final String word, final ProbabilityInfo probabilityInfo) {
        mWord = word;
        mProbabilityInfo = probabilityInfo;
    }

    @UsedForTesting
    public int getProbability() {
        return mProbabilityInfo.mProbability;
    }

    public void setProbability(final int probability) {
        mProbabilityInfo = new ProbabilityInfo(probability);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { mWord, mProbabilityInfo});
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WeightedString)) return false;
        final WeightedString w = (WeightedString)o;
        return mWord.equals(w.mWord) && mProbabilityInfo.equals(w.mProbabilityInfo);
    }
}