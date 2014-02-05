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

import com.android.inputmethod.latin.BinaryDictionary;

public final class ProbabilityInfo {
    public final int mProbability;
    // mTimestamp, mLevel and mCount are historical info. These values are depend on the
    // implementation in native code; thus, we must not use them and have any assumptions about
    // them except for tests.
    public final int mTimestamp;
    public final int mLevel;
    public final int mCount;

    public ProbabilityInfo(final int probability) {
        this(probability, BinaryDictionary.NOT_A_VALID_TIMESTAMP, 0, 0);
    }

    public ProbabilityInfo(final int probability, final int timestamp, final int level,
            final int count) {
        mProbability = probability;
        mTimestamp = timestamp;
        mLevel = level;
        mCount = count;
    }

    @Override
    public String toString() {
        return mTimestamp + ":" + mLevel + ":" + mCount;
    }
}