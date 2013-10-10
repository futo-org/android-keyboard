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
import com.android.inputmethod.compat.ActivityManagerCompatUtils;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.AbstractDictionaryWriter;
import com.android.inputmethod.latin.ExpandableDictionary;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.ExpandableDictionary.NextWord;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.BigramDictionaryInterface;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils.ForgettingCurveParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

// Currently this class is used to implement dynamic prodiction dictionary.
// TODO: Move to native code.
public class DynamicPersonalizationDictionaryWriter extends AbstractDictionaryWriter {
    private static final String TAG = DynamicPersonalizationDictionaryWriter.class.getSimpleName();
    /** Maximum number of pairs. Pruning will start when databases goes above this number. */
    public static final int DEFAULT_MAX_HISTORY_BIGRAMS = 10000;
    public static final int LOW_MEMORY_MAX_HISTORY_BIGRAMS = 2000;

    /** Any pair being typed or picked */
    private static final int FREQUENCY_FOR_TYPED = 2;

    private static final int BINARY_DICT_VERSION = 3;
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(BINARY_DICT_VERSION, true /* supportsDynamicUpdate */);

    private final UserHistoryDictionaryBigramList mBigramList =
            new UserHistoryDictionaryBigramList();
    private final ExpandableDictionary mExpandableDictionary;
    private final int mMaxHistoryBigrams;

    public DynamicPersonalizationDictionaryWriter(final Context context, final String dictType) {
        super(context, dictType);
        mExpandableDictionary = new ExpandableDictionary(dictType);
        final boolean isLowRamDevice = ActivityManagerCompatUtils.isLowRamDevice(context);
        mMaxHistoryBigrams = isLowRamDevice ?
                LOW_MEMORY_MAX_HISTORY_BIGRAMS : DEFAULT_MAX_HISTORY_BIGRAMS;
    }

    @Override
    public void clear() {
        mBigramList.evictAll();
        mExpandableDictionary.clearDictionary();
    }

    /**
     * Adds a word unigram to the fusion dictionary. Call updateBinaryDictionary when all changes
     * are done to update the binary dictionary.
     * @param word The word to add.
     * @param shortcutTarget A shortcut target for this word, or null if none.
     * @param frequency The frequency for this unigram.
     * @param shortcutFreq The frequency of the shortcut (0~15, with 15 = whitelist). Ignored
     *   if shortcutTarget is null.
     * @param isNotAWord true if this is not a word, i.e. shortcut only.
     */
    @Override
    public void addUnigramWord(final String word, final String shortcutTarget, final int frequency,
            final int shortcutFreq, final boolean isNotAWord) {
        if (mBigramList.size() > mMaxHistoryBigrams * 2) {
            // Too many entries: just stop adding new vocabulary and wait next refresh.
            return;
        }
        mExpandableDictionary.addWord(word, shortcutTarget, frequency, shortcutFreq);
        mBigramList.addBigram(null, word, (byte)frequency);
    }

    @Override
    public void addBigramWords(final String word0, final String word1, final int frequency,
            final boolean isValid, final long lastModifiedTime) {
        if (mBigramList.size() > mMaxHistoryBigrams * 2) {
            // Too many entries: just stop adding new vocabulary and wait next refresh.
            return;
        }
        if (lastModifiedTime > 0) {
            mExpandableDictionary.setBigramAndGetFrequency(word0, word1,
                    new ForgettingCurveParams(frequency, System.currentTimeMillis(),
                            lastModifiedTime));
            mBigramList.addBigram(word0, word1, (byte)frequency);
        } else {
            mExpandableDictionary.setBigramAndGetFrequency(word0, word1,
                    new ForgettingCurveParams(isValid));
            mBigramList.addBigram(word0, word1, (byte)frequency);
        }
    }

    @Override
    public void removeBigramWords(final String word0, final String word1) {
        if (mBigramList.removeBigram(word0, word1)) {
            mExpandableDictionary.removeBigram(word0, word1);
        }
    }

    @Override
    protected void writeDictionary(final DictEncoder dictEncoder,
            final Map<String, String> attributeMap) throws IOException, UnsupportedFormatException {
        UserHistoryDictIOUtils.writeDictionary(dictEncoder,
                new FrequencyProvider(mBigramList, mExpandableDictionary, mMaxHistoryBigrams),
                mBigramList, FORMAT_OPTIONS);
    }

    private static class FrequencyProvider implements BigramDictionaryInterface {
        private final UserHistoryDictionaryBigramList mBigramList;
        private final ExpandableDictionary mExpandableDictionary;
        private final int mMaxHistoryBigrams;

        public FrequencyProvider(final UserHistoryDictionaryBigramList bigramList,
                final ExpandableDictionary expandableDictionary, final int maxHistoryBigrams) {
            mBigramList = bigramList;
            mExpandableDictionary = expandableDictionary;
            mMaxHistoryBigrams = maxHistoryBigrams;
        }

        @Override
        public int getFrequency(final String word0, final String word1) {
            final int freq;
            if (word0 == null) { // unigram
                freq = FREQUENCY_FOR_TYPED;
            } else { // bigram
                final NextWord nw = mExpandableDictionary.getBigramWord(word0, word1);
                if (nw != null) {
                    final ForgettingCurveParams forgettingCurveParams = nw.getFcParams();
                    final byte prevFc = mBigramList.getBigrams(word0).get(word1);
                    final byte fc = forgettingCurveParams.getFc();
                    final boolean isValid = forgettingCurveParams.isValid();
                    if (prevFc > 0 && prevFc == fc) {
                        freq = fc & 0xFF;
                    } else if (UserHistoryForgettingCurveUtils.
                            needsToSave(fc, isValid, mBigramList.size() <= mMaxHistoryBigrams)) {
                        freq = fc & 0xFF;
                    } else {
                        // Delete this entry
                        freq = -1;
                    }
                } else {
                    // Delete this entry
                    freq = -1;
                }
            }
            return freq;
        }
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        return mExpandableDictionary.getSuggestions(composer, prevWord, proximityInfo,
                blockOffensiveWords, additionalFeaturesOptions);
    }

    @Override
    public boolean isValidWord(final String word) {
        return mExpandableDictionary.isValidWord(word);
    }

    @UsedForTesting
    public boolean isInBigramListForTests(final String word) {
        // TODO: Use native method to determine whether the word is in dictionary or not
        return mBigramList.containsKey(word) || mBigramList.getBigrams(null).containsKey(word);
    }
}
