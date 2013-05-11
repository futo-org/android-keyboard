/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

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
public abstract class MainLogBuffer extends FixedLogBuffer {
    private static final String TAG = MainLogBuffer.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;

    // The size of the n-grams logged.  E.g. N_GRAM_SIZE = 2 means to sample bigrams.
    public static final int N_GRAM_SIZE = 2;

    // TODO: Remove dependence on Suggest, and pass in Dictionary as a parameter to an appropriate
    // method.
    private final Suggest mSuggest;
    @UsedForTesting
    private Dictionary mDictionaryForTesting;
    private boolean mIsStopping = false;

    /* package for test */ int mNumWordsBetweenNGrams;

    // Counter for words left to suppress before an n-gram can be sampled.  Reset to mMinWordPeriod
    // after a sample is taken.
    /* package for test */ int mNumWordsUntilSafeToSample;

    public MainLogBuffer(final int wordsBetweenSamples, final int numInitialWordsToIgnore,
            final Suggest suggest) {
        super(N_GRAM_SIZE + wordsBetweenSamples);
        mNumWordsBetweenNGrams = wordsBetweenSamples;
        mNumWordsUntilSafeToSample = DEBUG ? 0 : numInitialWordsToIgnore;
        mSuggest = suggest;
    }

    @UsedForTesting
    /* package for test */ void setDictionaryForTesting(final Dictionary dictionary) {
        mDictionaryForTesting = dictionary;
    }

    private Dictionary getDictionary() {
        if (mDictionaryForTesting != null) {
            return mDictionaryForTesting;
        }
        if (mSuggest == null || !mSuggest.hasMainDictionary()) return null;
        return mSuggest.getMainDictionary();
    }

    public void setIsStopping() {
        mIsStopping = true;
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
    private boolean isSafeNGram(final ArrayList<LogUnit> logUnits, final int minNGramSize) {
        // Bypass privacy checks when debugging.
        if (ResearchLogger.IS_LOGGING_EVERYTHING) {
            if (mIsStopping) {
                return true;
            }
            // Only check that it is the right length.  If not, wait for later words to make
            // complete n-grams.
            int numWordsInLogUnitList = 0;
            final int length = logUnits.size();
            for (int i = 0; i < length; i++) {
                final LogUnit logUnit = logUnits.get(i);
                numWordsInLogUnitList += logUnit.getNumWords();
            }
            return numWordsInLogUnitList >= minNGramSize;
        }

        // Check that we are not sampling too frequently.  Having sampled recently might disclose
        // too much of the user's intended meaning.
        if (mNumWordsUntilSafeToSample > 0) {
            return false;
        }
        // Reload the dictionary in case it has changed (e.g., because the user has changed
        // languages).
        final Dictionary dictionary = getDictionary();
        if (dictionary == null) {
            // Main dictionary is unavailable.  Since we cannot check it, we cannot tell if a
            // word is out-of-vocabulary or not.  Therefore, we must judge the entire buffer
            // contents to potentially pose a privacy risk.
            return false;
        }

        // Check each word in the buffer.  If any word poses a privacy threat, we cannot upload
        // the complete buffer contents in detail.
        int numWordsInLogUnitList = 0;
        final int length = logUnits.size();
        for (final LogUnit logUnit : logUnits) {
            if (!logUnit.hasOneOrMoreWords()) {
                // Digits outside words are a privacy threat.
                if (logUnit.mayContainDigit()) {
                    return false;
                }
            } else {
                numWordsInLogUnitList += logUnit.getNumWords();
                final String[] words = logUnit.getWordsAsStringArray();
                for (final String word : words) {
                    // Words not in the dictionary are a privacy threat.
                    if (ResearchLogger.hasLetters(word) && !(dictionary.isValidWord(word))) {
                        if (DEBUG) {
                            Log.d(TAG, "\"" + word + "\" NOT SAFE!: hasLetters: "
                                    + ResearchLogger.hasLetters(word)
                                    + ", isValid: " + (dictionary.isValidWord(word)));
                        }
                        return false;
                    }
                }
            }
        }

        // Finally, only return true if the ngram is the right size.
        return numWordsInLogUnitList == minNGramSize;
    }

    public void shiftAndPublishAll() throws IOException {
        final LinkedList<LogUnit> logUnits = getLogUnits();
        while (!logUnits.isEmpty()) {
            publishLogUnitsAtFrontOfBuffer();
        }
    }

    @Override
    protected final void onBufferFull() {
        try {
            publishLogUnitsAtFrontOfBuffer();
        } catch (final IOException e) {
            if (DEBUG) {
                Log.w(TAG, "IOException when publishing front of LogBuffer", e);
            }
        }
    }

    protected final void publishLogUnitsAtFrontOfBuffer() throws IOException {
        // TODO: Refactor this method to require fewer passes through the LogUnits.  Should really
        // require only one pass.
        ArrayList<LogUnit> logUnits = peekAtFirstNWords(N_GRAM_SIZE);
        if (isSafeNGram(logUnits, N_GRAM_SIZE)) {
            // Good n-gram at the front of the buffer.  Publish it, disclosing details.
            publish(logUnits, true /* canIncludePrivateData */);
            shiftOutWords(N_GRAM_SIZE);
            mNumWordsUntilSafeToSample = mNumWordsBetweenNGrams;
            return;
        }
        // No good n-gram at front, and buffer is full.  Shift out up through the first logUnit
        // with associated words (or if there is none, all the existing logUnits).
        logUnits.clear();
        LogUnit logUnit = shiftOut();
        while (logUnit != null) {
            logUnits.add(logUnit);
            final int numWords = logUnit.getNumWords();
            if (numWords > 0) {
                mNumWordsUntilSafeToSample = Math.max(0, mNumWordsUntilSafeToSample - numWords);
                break;
            }
            logUnit = shiftOut();
        }
        publish(logUnits, false /* canIncludePrivateData */);
    }

    /**
     * Called when a list of logUnits should be published.
     *
     * It is the subclass's responsibility to implement the publication.
     *
     * @param logUnits The list of logUnits to be published.
     * @param canIncludePrivateData Whether the private data in the logUnits can be included in
     * publication.
     *
     * @throws IOException if publication to the log file is not possible
     */
    protected abstract void publish(final ArrayList<LogUnit> logUnits,
            final boolean canIncludePrivateData) throws IOException;

    @Override
    protected int shiftOutWords(final int numWords) {
        final int numWordsShiftedOut = super.shiftOutWords(numWords);
        mNumWordsUntilSafeToSample = Math.max(0, mNumWordsUntilSafeToSample - numWordsShiftedOut);
        if (DEBUG) {
            Log.d(TAG, "wordsUntilSafeToSample now at " + mNumWordsUntilSafeToSample);
        }
        return numWordsShiftedOut;
    }
}
