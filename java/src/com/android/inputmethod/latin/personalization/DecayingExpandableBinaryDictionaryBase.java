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
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.BinaryDictionary.LanguageModelParam;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.OnAddWordListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is a base class of a dictionary that supports decaying for the personalized language
 * model.
 */
public abstract class DecayingExpandableBinaryDictionaryBase extends ExpandableBinaryDictionary {
    private static final String TAG = DecayingExpandableBinaryDictionaryBase.class.getSimpleName();
    public static final boolean DBG_SAVE_RESTORE = false;
    private static final boolean DBG_DUMP_ON_CLOSE = false;

    /** Any pair being typed or picked */
    public static final int FREQUENCY_FOR_TYPED = 2;

    public static final int FREQUENCY_FOR_WORDS_IN_DICTS = FREQUENCY_FOR_TYPED;
    public static final int FREQUENCY_FOR_WORDS_NOT_IN_DICTS = Dictionary.NOT_A_PROBABILITY;

    public static final int REQUIRED_BINARY_DICTIONARY_VERSION = 4;

    /** Locale for which this user history dictionary is storing words */
    private final Locale mLocale;

    private final String mDictName;

    /* package */ DecayingExpandableBinaryDictionaryBase(final Context context,
            final Locale locale, final String dictionaryType, final String dictName) {
        super(context, dictName, locale, dictionaryType, true);
        mLocale = locale;
        mDictName = dictName;
        if (mLocale != null && mLocale.toString().length() > 1) {
            reloadDictionaryIfRequired();
        }
    }

    // Creates an instance that uses a given dictionary file for testing.
    @UsedForTesting
    /* package */ DecayingExpandableBinaryDictionaryBase(final Context context,
            final Locale locale, final String dictionaryType, final String dictName,
            final File dictFile) {
        super(context, dictName, locale, dictionaryType, true, dictFile);
        mLocale = locale;
        mDictName = dictName;
        if (mLocale != null && mLocale.toString().length() > 1) {
            reloadDictionaryIfRequired();
        }
    }

    @Override
    public void close() {
        if (DBG_DUMP_ON_CLOSE) {
            dumpAllWordsForDebug();
        }
        // Flush pending writes.
        asyncFlushBinaryDictionary();
    }

    @Override
    protected Map<String, String> getHeaderAttributeMap() {
        HashMap<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.SUPPORTS_DYNAMIC_UPDATE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.USES_FORGETTING_CURVE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.HAS_HISTORICAL_INFO_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_ID_ATTRIBUTE, mDictName);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_LOCALE_ATTRIBUTE, mLocale.toString());
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_VERSION_ATTRIBUTE,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
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

    @Override
    protected boolean matchesExpectedBinaryDictFormatVersionForThisType(final int formatVersion) {
        // This class is using format 4 because it's used by all version 4 dictionaries.
        // TODO: remove this when all dynamically generated dicts use version 4.
        return formatVersion == REQUIRED_BINARY_DICTIONARY_VERSION;
    }

    public void addMultipleDictionaryEntriesToDictionary(
            final ArrayList<LanguageModelParam> languageModelParams,
            final ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback callback) {
        if (languageModelParams == null || languageModelParams.isEmpty()) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        addMultipleDictionaryEntriesDynamically(languageModelParams, callback);
    }

    /**
     * Pair will be added to the decaying dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public void addToDictionary(final String word0, final String word1, final boolean isValid,
            final int timestamp) {
        if (word1.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH ||
                (word0 != null && word0.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH)) {
            return;
        }
        final int frequency = isValid ?
                FREQUENCY_FOR_WORDS_IN_DICTS : FREQUENCY_FOR_WORDS_NOT_IN_DICTS;
        addWordDynamically(word1, frequency, null /* shortcutTarget */, 0 /* shortcutFreq */,
                false /* isNotAWord */, false /* isBlacklisted */, timestamp);
        // Do not insert a word as a bigram of itself
        if (word1.equals(word0)) {
            return;
        }
        if (null != word0) {
            addBigramDynamically(word0, word1, frequency, timestamp);
        }
    }

    @Override
    protected void loadDictionaryAsync() {
        // Never loaded to memory in Java side.
    }

    @UsedForTesting
    public void dumpAllWordsForDebug() {
        runAfterGcForDebug(new Runnable() {
            @Override
            public void run() {
                dumpAllWordsForDebugLocked();
            }
        });
    }

    private void dumpAllWordsForDebugLocked() {
        Log.d(TAG, "dumpAllWordsForDebug started.");
        final OnAddWordListener listener = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency, final int shortcutFreq) {
                Log.d(TAG, "load unigram: " + word + "," + frequency);
            }

            @Override
            public void setBigram(final String word0, final String word1, final int frequency) {
                if (word0.length() < Constants.DICTIONARY_MAX_WORD_LENGTH
                        && word1.length() < Constants.DICTIONARY_MAX_WORD_LENGTH) {
                    Log.d(TAG, "load bigram: " + word0 + "," + word1 + "," + frequency);
                } else {
                    Log.d(TAG, "Skip inserting a too long bigram: " + word0 + "," + word1 + ","
                            + frequency);
                }
            }
        };

        // Load the dictionary from binary file
        final File dictFile = new File(mContext.getFilesDir(), mDictName);
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
        } catch (UnsupportedFormatException e) {
            Log.d(TAG, "Unsupported format, can't read the dictionary", e);
        }
    }

    @UsedForTesting
    public void clearAndFlushDictionary() {
        // Clear the node structure on memory
        clear();
        // Then flush the cleared state of the dictionary on disk.
        asyncFlushBinaryDictionary();
    }

    /* package */ void decayIfNeeded() {
        runGCIfRequired(false /* mindsBlockByGC */);
    }
}
