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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.ComposedData;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class provides binary dictionary reading operations with locking. An instance of this class
 * can be used by multiple threads. Note that different session IDs must be used when multiple
 * threads get suggestions using this class.
 */
public final class ReadOnlyBinaryDictionary extends Dictionary {
    /**
     * A lock for accessing binary dictionary. Only closing binary dictionary is the operation
     * that change the state of dictionary.
     */
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();

    private final BinaryDictionary mBinaryDictionary;

    public ReadOnlyBinaryDictionary(final String filename, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale, final String dictType) {
        super(dictType, locale);
        mBinaryDictionary = new BinaryDictionary(filename, offset, length, useFullEditDistance,
                locale, dictType, false /* isUpdatable */);
    }

    public boolean isValidDictionary() {
        return mBinaryDictionary.isValidDictionary();
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final ComposedData composedData,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int sessionId, final float weightForLocale,
            final float[] inOutWeightOfLangModelVsSpatialModel) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getSuggestions(composedData, ngramContext,
                        proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                        weightForLocale, inOutWeightOfLangModelVsSpatialModel);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public boolean isInDictionary(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.isInDictionary(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public boolean shouldAutoCommit(final SuggestedWordInfo candidate) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.shouldAutoCommit(candidate);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public int getFrequency(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getFrequency(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getMaxFrequencyOfExactMatches(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    public void close() {
        mLock.writeLock().lock();
        try {
            mBinaryDictionary.close();
        } finally {
            mLock.writeLock().unlock();
        }
    }
}
