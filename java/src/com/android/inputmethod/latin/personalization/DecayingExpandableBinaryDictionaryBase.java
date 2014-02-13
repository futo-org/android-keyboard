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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.utils.LanguageModelParam;

import java.io.File;
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
    private static final boolean DBG_DUMP_ON_CLOSE = false;

    /** Any pair being typed or picked */
    public static final int FREQUENCY_FOR_TYPED = 2;

    public static final int FREQUENCY_FOR_WORDS_IN_DICTS = FREQUENCY_FOR_TYPED;
    public static final int FREQUENCY_FOR_WORDS_NOT_IN_DICTS = Dictionary.NOT_A_PROBABILITY;

    /** The locale for this dictionary. */
    public final Locale mLocale;

    private final String mDictName;

    /* package */ DecayingExpandableBinaryDictionaryBase(final Context context,
            final String dictName, final Locale locale, final String dictionaryType,
            final File dictFile) {
        super(context, getDictName(dictName, locale, dictFile), locale, dictionaryType,
                true /* isUpdatable */, dictFile);
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
        attributeMap.put(DictionaryHeader.USES_FORGETTING_CURVE_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.HAS_HISTORICAL_INFO_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, mDictName);
        attributeMap.put(DictionaryHeader.DICTIONARY_LOCALE_KEY, mLocale.toString());
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
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
