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

package com.android.inputmethod.latin.personalization;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.OnAddWordListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is a base class of a dictionary that supports decaying for the personalized language
 * model.
 */
public abstract class DecayingExpandableBinaryDictionaryBase extends ExpandableBinaryDictionary {
    private static final String TAG = DecayingExpandableBinaryDictionaryBase.class.getSimpleName();
    public static final boolean DBG_SAVE_RESTORE = false;
    private static final boolean DBG_STRESS_TEST = false;
    private static final boolean PROFILE_SAVE_RESTORE = LatinImeLogger.sDBG;

    /** Any pair being typed or picked */
    public static final int FREQUENCY_FOR_TYPED = 2;

    public static final int FREQUENCY_FOR_WORDS_IN_DICTS = FREQUENCY_FOR_TYPED;
    public static final int FREQUENCY_FOR_WORDS_NOT_IN_DICTS = Dictionary.NOT_A_PROBABILITY;

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    private final String mFileName;

    private final SharedPreferences mPrefs;

    private final ArrayList<PersonalizationDictionaryUpdateSession> mSessions =
            CollectionUtils.newArrayList();

    // Should always be false except when we use this class for test
    @UsedForTesting boolean mIsTest = false;

    /* package */ DecayingExpandableBinaryDictionaryBase(final Context context,
            final String locale, final SharedPreferences sp, final String dictionaryType,
            final String fileName) {
        super(context, fileName, dictionaryType, true);
        mLocale = locale;
        mFileName = fileName;
        mPrefs = sp;
        if (mLocale != null && mLocale.length() > 1) {
            asyncLoadDictionaryToMemory();
            reloadDictionaryIfRequired();
        }
    }

    @Override
    public void close() {
        if (!ExpandableBinaryDictionary.ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE) {
            closeBinaryDictionary();
        }
        // Flush pending writes.
        // TODO: Remove after this class become to use a dynamic binary dictionary.
        asyncFlashAllBinaryDictionary();
        Settings.writeLastUserHistoryWriteTime(mPrefs, mLocale);
    }

    @Override
    protected Map<String, String> getHeaderAttributeMap() {
        HashMap<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.SUPPORTS_DYNAMIC_UPDATE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.USES_FORGETTING_CURVE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_ID_ATTRIBUTE, mFileName);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_LOCALE_ATTRIBUTE, mLocale);
        return attributeMap;
    }

    @Override
    protected boolean hasContentChanged() {
        return false;
    }

    @Override
    protected boolean needsToReloadBeforeWriting() {
        return false;
    }

    /**
     * Return whether the passed charsequence is in the dictionary.
     */
    @Override
    public boolean isValidWord(final String word) {
     // Words included only in the user history should be treated as not in dictionary words.
        return false;
    }

    /**
     * Pair will be added to the decaying dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public void addToDictionary(final String word0, final String word1, final boolean isValid) {
        if (word1.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH ||
                (word0 != null && word0.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH)) {
            return;
        }
        final int frequency = ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE ?
                (isValid ? FREQUENCY_FOR_WORDS_IN_DICTS : FREQUENCY_FOR_WORDS_NOT_IN_DICTS) :
                        FREQUENCY_FOR_TYPED;
        addWordDynamically(word1, null /* shortcutTarget */, frequency, 0 /* shortcutFreq */,
                false /* isNotAWord */);
        // Do not insert a word as a bigram of itself
        if (word1.equals(word0)) {
            return;
        }
        if (null != word0) {
            addBigramDynamically(word0, word1, frequency, isValid);
        }
    }

    public void cancelAddingUserHistory(final String word0, final String word1) {
        removeBigramDynamically(word0, word1);
    }

    @Override
    protected void loadDictionaryAsync() {
        final int[] profTotalCount = { 0 };
        final String locale = getLocale();
        if (DBG_STRESS_TEST) {
            try {
                Log.w(TAG, "Start stress in loading: " + locale);
                Thread.sleep(15000);
                Log.w(TAG, "End stress in loading");
            } catch (InterruptedException e) {
            }
        }
        final long last = Settings.readLastUserHistoryWriteTime(mPrefs, locale);
        final long now = System.currentTimeMillis();
        final ExpandableBinaryDictionary dictionary = this;
        final OnAddWordListener listener = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency, final int shortcutFreq) {
                if (DBG_SAVE_RESTORE) {
                    Log.d(TAG, "load unigram: " + word + "," + frequency);
                }
                addWord(word, shortcutTarget, frequency, shortcutFreq, false /* isNotAWord */);
                ++profTotalCount[0];
            }

            @Override
            public void setBigram(final String word0, final String word1, final int frequency) {
                if (word0.length() < Constants.DICTIONARY_MAX_WORD_LENGTH
                        && word1.length() < Constants.DICTIONARY_MAX_WORD_LENGTH) {
                    if (DBG_SAVE_RESTORE) {
                        Log.d(TAG, "load bigram: " + word0 + "," + word1 + "," + frequency);
                    }
                    ++profTotalCount[0];
                    addBigram(word0, word1, frequency, last);
                }
            }
        };

        // Load the dictionary from binary file
        final File dictFile = new File(mContext.getFilesDir(), mFileName);
        final DictDecoder dictDecoder = FormatSpec.getDictDecoder(dictFile,
                DictDecoder.USE_BYTEARRAY);
        if (dictDecoder == null) {
            // This is an expected condition: we don't have a user history dictionary for this
            // language yet. It will be created sometime later.
            return;
        }

        try {
            dictDecoder.openDictBuffer();
            UserHistoryDictIOUtils.readDictionaryBinary(dictDecoder, listener);
        } catch (IOException e) {
            Log.d(TAG, "IOException on opening a bytebuffer", e);
        } finally {
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.d(TAG, "PROF: Load UserHistoryDictionary: "
                        + locale + ", " + diff + "ms. load " + profTotalCount[0] + "entries.");
            }
        }
    }

    protected String getLocale() {
        return mLocale;
    }

    public void registerUpdateSession(PersonalizationDictionaryUpdateSession session) {
        session.setPredictionDictionary(this);
        mSessions.add(session);
        session.onDictionaryReady();
    }

    public void unRegisterUpdateSession(PersonalizationDictionaryUpdateSession session) {
        mSessions.remove(session);
    }

    @UsedForTesting
    public void clearAndFlushDictionary() {
        // Clear the node structure on memory
        clear();
        // Then flush the cleared state of the dictionary on disk.
        asyncFlashAllBinaryDictionary();
    }

    /* package */ void decayIfNeeded() {
        runGCIfRequired(false /* mindsBlockByGC */);
    }
}
