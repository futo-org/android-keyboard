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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.Dictionary;

public class LanguageModelParam {
    public final String mTargetWord;
    public final int[] mWord0;
    public final int[] mWord1;
    // TODO: this needs to be a list of shortcuts
    public final int[] mShortcutTarget;
    public final int mUnigramProbability;
    public final int mBigramProbability;
    public final int mShortcutProbability;
    public final boolean mIsNotAWord;
    public final boolean mIsBlacklisted;
    // Time stamp in seconds.
    public final int mTimestamp;

    // Constructor for unigram. TODO: support shortcuts
    public LanguageModelParam(final String word, final int unigramProbability,
            final int timestamp) {
        this(null /* word0 */, word, unigramProbability, Dictionary.NOT_A_PROBABILITY, timestamp);
    }

    // Constructor for unigram and bigram.
    public LanguageModelParam(final String word0, final String word1,
            final int unigramProbability, final int bigramProbability,
            final int timestamp) {
        mTargetWord = word1;
        mWord0 = (word0 == null) ? null : StringUtils.toCodePointArray(word0);
        mWord1 = StringUtils.toCodePointArray(word1);
        mShortcutTarget = null;
        mUnigramProbability = unigramProbability;
        mBigramProbability = bigramProbability;
        mShortcutProbability = Dictionary.NOT_A_PROBABILITY;
        mIsNotAWord = false;
        mIsBlacklisted = false;
        mTimestamp = timestamp;
    }
}
