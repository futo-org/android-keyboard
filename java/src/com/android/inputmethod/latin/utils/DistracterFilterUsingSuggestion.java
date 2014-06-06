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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.PrevWordsInfo;

/**
 * This class is used to prevent distracters being added to personalization
 * or user history dictionaries
 */
// TODO: Rename.
public class DistracterFilterUsingSuggestion implements DistracterFilter {
    private static final String TAG = DistracterFilterUsingSuggestion.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final long TIMEOUT_TO_WAIT_LOADING_DICTIONARIES_IN_SECONDS = 120;

    private final Context mContext;
    private final DictionaryFacilitator mDictionaryFacilitator;
    private final Object mLock = new Object();

    /**
     * Create a DistracterFilter instance.
     *
     * @param context the context.
     */
    public DistracterFilterUsingSuggestion(final Context context) {
        mContext = context;
        mDictionaryFacilitator = new DictionaryFacilitator();
    }

    @Override
    public void close() {
        mDictionaryFacilitator.closeDictionaries();
    }

    @Override
    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
    }

    private void loadDictionariesForLocale(final Locale newlocale) throws InterruptedException {
        mDictionaryFacilitator.resetDictionaries(mContext, newlocale,
                false /* useContactsDict */, false /* usePersonalizedDicts */,
                false /* forceReloadMainDictionary */, null /* listener */);
        mDictionaryFacilitator.waitForLoadingMainDictionary(
                TIMEOUT_TO_WAIT_LOADING_DICTIONARIES_IN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Determine whether a word is a distracter to words in dictionaries.
     *
     * @param prevWordsInfo the information of previous words. Not used for now.
     * @param testedWord the word that will be tested to see whether it is a distracter to words
     *                   in dictionaries.
     * @param locale the locale of word.
     * @return true if testedWord is a distracter, otherwise false.
     */
    @Override
    public boolean isDistracterToWordsInDictionaries(final PrevWordsInfo prevWordsInfo,
            final String testedWord, final Locale locale) {
        if (locale == null) {
            return false;
        }
        if (!locale.equals(mDictionaryFacilitator.getLocale())) {
            synchronized (mLock) {
                // Reset dictionaries for the locale.
                try {
                    loadDictionariesForLocale(locale);
                } catch (final InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for loading dicts in DistracterFilter",
                            e);
                    return false;
                }
            }
        }
        // The tested word is a distracter when there is a word that is exact matched to the tested
        // word and its probability is higher than the tested word's probability.
        final int perfectMatchFreq = mDictionaryFacilitator.getFrequency(testedWord);
        final int exactMatchFreq = mDictionaryFacilitator.getMaxFrequencyOfExactMatches(testedWord);
        final boolean isDistracter = perfectMatchFreq < exactMatchFreq;
        if (DEBUG) {
            Log.d(TAG, "testedWord: " + testedWord);
            Log.d(TAG, "perfectMatchFreq: " + perfectMatchFreq);
            Log.d(TAG, "exactMatchFreq: " + exactMatchFreq);
            Log.d(TAG, "isDistracter: " + isDistracter);
        }
        return isDistracter;
    }
}
