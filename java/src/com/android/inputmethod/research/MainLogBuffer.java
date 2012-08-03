/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Suggest;

import java.util.Random;

public class MainLogBuffer extends LogBuffer {
    // The size of the n-grams logged.  E.g. N_GRAM_SIZE = 2 means to sample bigrams.
    private static final int N_GRAM_SIZE = 2;
    // The number of words between n-grams to omit from the log.
    private static final int DEFAULT_NUMBER_OF_WORDS_BETWEEN_SAMPLES = 18;

    private final ResearchLog mResearchLog;
    private Suggest mSuggest;

    // The minimum periodicity with which n-grams can be sampled.  E.g. mWinWordPeriod is 10 if
    // every 10th bigram is sampled, i.e., words 1-8 are not, but the bigram at words 9 and 10, etc.
    // for 11-18, and the bigram at words 19 and 20.  If an n-gram is not safe (e.g. it  contains a
    // number in the middle or an out-of-vocabulary word), then sampling is delayed until a safe
    // n-gram does appear.
    /* package for test */ int mMinWordPeriod;

    // Counter for words left to suppress before an n-gram can be sampled.  Reset to mMinWordPeriod
    // after a sample is taken.
    /* package for test */ int mWordsUntilSafeToSample;

    public MainLogBuffer(final ResearchLog researchLog) {
        super(N_GRAM_SIZE);
        mResearchLog = researchLog;
        mMinWordPeriod = DEFAULT_NUMBER_OF_WORDS_BETWEEN_SAMPLES + N_GRAM_SIZE;
        final Random random = new Random();
        mWordsUntilSafeToSample = random.nextInt(mMinWordPeriod);
    }

    public void setSuggest(Suggest suggest) {
        mSuggest = suggest;
    }

    @Override
    public void shiftIn(final LogUnit newLogUnit) {
        super.shiftIn(newLogUnit);
        if (newLogUnit.hasWord()) {
            if (mWordsUntilSafeToSample > 0) {
                mWordsUntilSafeToSample--;
            }
        }
    }

    public void resetWordCounter() {
        mWordsUntilSafeToSample = mMinWordPeriod;
    }

    /**
     * Determines whether the content of the MainLogBuffer can be safely uploaded in its complete
     * form and still protect the user's privacy.
     *
     * The size of the MainLogBuffer is just enough to hold one n-gram, its corrections, and any
     * non-character data that is typed between words.  The decision about privacy is made based on
     * the buffer's entire content.  If it is decided that the privacy risks are too great to upload
     * the contents of this buffer, a censored version of the LogItems may still be uploaded.  E.g.,
     * the screen orientation and other characteristics about the device can be uploaded without
     * revealing much about the user.
     */
    public boolean isSafeToLog() {
        // Check that we are not sampling too frequently.  Having sampled recently might disclose
        // too much of the user's intended meaning.
        if (mWordsUntilSafeToSample > 0) {
            return false;
        }
        if (mSuggest == null || !mSuggest.hasMainDictionary()) {
            // Main dictionary is unavailable.  Since we cannot check it, we cannot tell if a word
            // is out-of-vocabulary or not.  Therefore, we must judge the entire buffer contents to
            // potentially pose a privacy risk.
            return false;
        }
        // Reload the dictionary in case it has changed (e.g., because the user has changed
        // languages).
        final Dictionary dictionary = mSuggest.getMainDictionary();
        if (dictionary == null) {
            return false;
        }
        // Check each word in the buffer.  If any word poses a privacy threat, we cannot upload the
        // complete buffer contents in detail.
        final int length = mLogUnits.size();
        for (int i = 0; i < length; i++) {
            final LogUnit logUnit = mLogUnits.get(i);
            final String word = logUnit.getWord();
            if (word == null) {
                // Digits outside words are a privacy threat.
                if (logUnit.hasDigit()) {
                    return false;
                }
            } else {
                // Words not in the dictionary are a privacy threat.
                if (!(dictionary.isValidWord(word))) {
                    return false;
                }
            }
        }
        // All checks have passed; this buffer's content can be safely uploaded.
        return true;
    }

    @Override
    protected void onShiftOut(LogUnit logUnit) {
        if (mResearchLog != null) {
            mResearchLog.publish(logUnit, false /* isIncludingPrivateData */);
        }
    }
}
