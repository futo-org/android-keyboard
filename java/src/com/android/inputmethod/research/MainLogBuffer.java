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

import android.util.Log;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.util.LinkedList;
import java.util.Random;

/**
 * MainLogBuffer is a FixedLogBuffer that tracks the state of LogUnits to make privacy guarantees.
 *
 * There are three forms of privacy protection: 1) only words in the main dictionary are allowed to
 * be logged in enough detail to determine their contents, 2) only a subset of words are logged
 * in detail, such as 10%, and 3) no numbers are logged.
 *
 * This class maintains a list of LogUnits, each corresponding to a word.  As the user completes
 * words, they are added here.  But if the user backs up over their current word to edit a word
 * entered earlier, then it is pulled out of this LogBuffer, changes are then added to the end of
 * the LogUnit, and it is pushed back in here when the user is done.  Because words may be pulled
 * back out even after they are pushed in, we must not publish the contents of this LogBuffer too
 * quickly.  However, we cannot let the contents pile up either, or it will limit the editing that
 * a user can perform.
 *
 * To balance these requirements (keep history so user can edit, flush history so it does not pile
 * up), the LogBuffer is considered "complete" when the user has entered enough words to form an
 * n-gram, followed by enough additional non-detailed words (that are in the 90%, as per above).
 * Once complete, the n-gram may be published to flash storage (via the ResearchLog class).
 * However, the additional non-detailed words are retained, in case the user backspaces to edit
 * them.  The MainLogBuffer then continues to add words, publishing individual non-detailed words
 * as new words arrive.  After enough non-detailed words have been pushed out to account for the
 * 90% between words, the words at the front of the LogBuffer can be published as an n-gram again.
 *
 * If the words that would form the valid n-gram are not in the dictionary, then words are pushed
 * through the LogBuffer one at a time until an n-gram is found that is entirely composed of
 * dictionary words.
 *
 * If the user closes a session, then the entire LogBuffer is flushed, publishing any embedded
 * n-gram containing dictionary words.
 */
public class MainLogBuffer extends FixedLogBuffer {
    private static final String TAG = MainLogBuffer.class.getSimpleName();
    private static final boolean DEBUG = false && ProductionFlag.IS_EXPERIMENTAL_DEBUG;

    // The size of the n-grams logged.  E.g. N_GRAM_SIZE = 2 means to sample bigrams.
    public static final int N_GRAM_SIZE = 2;
    // The number of words between n-grams to omit from the log.  If debugging, record 50% of all
    // words.  Otherwise, only record 10%.
    private static final int DEFAULT_NUMBER_OF_WORDS_BETWEEN_SAMPLES =
            ProductionFlag.IS_EXPERIMENTAL_DEBUG ? 2 : 18;

    private final ResearchLog mResearchLog;
    private Suggest mSuggest;

    /* package for test */ int mNumWordsBetweenNGrams;

    // Counter for words left to suppress before an n-gram can be sampled.  Reset to mMinWordPeriod
    // after a sample is taken.
    /* package for test */ int mNumWordsUntilSafeToSample;

    public MainLogBuffer(final ResearchLog researchLog) {
        super(N_GRAM_SIZE + DEFAULT_NUMBER_OF_WORDS_BETWEEN_SAMPLES);
        mResearchLog = researchLog;
        mNumWordsBetweenNGrams = DEFAULT_NUMBER_OF_WORDS_BETWEEN_SAMPLES;
        final Random random = new Random();
        mNumWordsUntilSafeToSample = DEBUG ? 0 : random.nextInt(mNumWordsBetweenNGrams + 1);
    }

    public void setSuggest(final Suggest suggest) {
        mSuggest = suggest;
    }

    public void resetWordCounter() {
        mNumWordsUntilSafeToSample = mNumWordsBetweenNGrams;
    }

    /**
     * Determines whether uploading the n words at the front the MainLogBuffer will not violate
     * user privacy.
     *
     * The size of the MainLogBuffer is just enough to hold one n-gram, its corrections, and any
     * non-character data that is typed between words.  The decision about privacy is made based on
     * the buffer's entire content.  If it is decided that the privacy risks are too great to upload
     * the contents of this buffer, a censored version of the LogItems may still be uploaded.  E.g.,
     * the screen orientation and other characteristics about the device can be uploaded without
     * revealing much about the user.
     */
    public boolean isNGramSafe() {
        // Check that we are not sampling too frequently.  Having sampled recently might disclose
        // too much of the user's intended meaning.
        if (mNumWordsUntilSafeToSample > 0) {
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
        final LinkedList<LogUnit> logUnits = getLogUnits();
        final int length = logUnits.size();
        int wordsNeeded = N_GRAM_SIZE;
        for (int i = 0; i < length && wordsNeeded > 0; i++) {
            final LogUnit logUnit = logUnits.get(i);
            final String word = logUnit.getWord();
            if (word == null) {
                // Digits outside words are a privacy threat.
                if (logUnit.mayContainDigit()) {
                    return false;
                }
            } else {
                // Words not in the dictionary are a privacy threat.
                if (ResearchLogger.hasLetters(word) && !(dictionary.isValidWord(word))) {
                    if (DEBUG) {
                        Log.d(TAG, "NOT SAFE!: hasLetters: " + ResearchLogger.hasLetters(word)
                                + ", isValid: " + (dictionary.isValidWord(word)));
                    }
                    return false;
                }
            }
        }
        // All checks have passed; this buffer's content can be safely uploaded.
        return true;
    }

    public boolean isNGramComplete() {
        final LinkedList<LogUnit> logUnits = getLogUnits();
        final int length = logUnits.size();
        int wordsNeeded = N_GRAM_SIZE;
        for (int i = 0; i < length && wordsNeeded > 0; i++) {
            final LogUnit logUnit = logUnits.get(i);
            final String word = logUnit.getWord();
            if (word != null) {
                wordsNeeded--;
            }
        }
        return wordsNeeded == 0;
    }

    @Override
    protected void onShiftOut(final LogUnit logUnit) {
        if (mResearchLog != null) {
            mResearchLog.publish(logUnit,
                    ResearchLogger.IS_LOGGING_EVERYTHING /* isIncludingPrivateData */);
        }
        if (logUnit.hasWord()) {
            if (mNumWordsUntilSafeToSample > 0) {
                mNumWordsUntilSafeToSample--;
                Log.d(TAG, "wordsUntilSafeToSample now at " + mNumWordsUntilSafeToSample);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "shiftedOut " + (logUnit.hasWord() ? logUnit.getWord() : ""));
        }
    }
}
