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

package com.android.inputmethod.latin.utils;

import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.PendingAttribute;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.personalization.UserHistoryDictionaryBigramList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Reads and writes Binary files for a UserHistoryDictionary.
 *
 * All the methods in this class are static.
 */
public final class UserHistoryDictIOUtils {
    private static final String TAG = UserHistoryDictIOUtils.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final String USES_FORGETTING_CURVE_KEY = "USES_FORGETTING_CURVE";
    private static final String USES_FORGETTING_CURVE_VALUE = "1";
    private static final String LAST_UPDATED_TIME_KEY = "date";

    public interface OnAddWordListener {
        /**
         * Callback to be notified when a word is added to the dictionary.
         * @param word The added word.
         * @param shortcutTarget A shortcut target for this word, or null if none.
         * @param frequency The frequency for this word.
         * @param shortcutFreq The frequency of the shortcut (0~15, with 15 = whitelist).
         *   Unspecified if shortcutTarget is null - do not rely on its value.
         */
        public void setUnigram(final String word, final String shortcutTarget, final int frequency,
                final int shortcutFreq);
        public void setBigram(final String word1, final String word2, final int frequency);
    }

    @UsedForTesting
    public interface BigramDictionaryInterface {
        public int getFrequency(final String word1, final String word2);
    }

    /**
     * Writes dictionary to file.
     */
    public static void writeDictionary(final DictEncoder dictEncoder,
            final BigramDictionaryInterface dict, final UserHistoryDictionaryBigramList bigrams,
            final FormatOptions formatOptions) {
        final FusionDictionary fusionDict = constructFusionDictionary(dict, bigrams);
        fusionDict.addOptionAttribute(USES_FORGETTING_CURVE_KEY, USES_FORGETTING_CURVE_VALUE);
        fusionDict.addOptionAttribute(LAST_UPDATED_TIME_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        try {
            dictEncoder.writeDictionary(fusionDict, formatOptions);
            Log.d(TAG, "end writing");
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
    }

    /**
     * Constructs a new FusionDictionary from BigramDictionaryInterface.
     */
    @UsedForTesting
    static FusionDictionary constructFusionDictionary(
            final BigramDictionaryInterface dict, final UserHistoryDictionaryBigramList bigrams) {
        final FusionDictionary fusionDict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(new HashMap<String, String>(), false,
                        false));
        int profTotal = 0;
        for (final String word1 : bigrams.keySet()) {
            final HashMap<String, Byte> word1Bigrams = bigrams.getBigrams(word1);
            for (final String word2 : word1Bigrams.keySet()) {
                final int freq = dict.getFrequency(word1, word2);
                if (freq == -1) {
                    // don't add this bigram.
                    continue;
                }
                if (DEBUG) {
                    if (word1 == null) {
                        Log.d(TAG, "add unigram: " + word2 + "," + Integer.toString(freq));
                    } else {
                        Log.d(TAG, "add bigram: " + word1
                                + "," + word2 + "," + Integer.toString(freq));
                    }
                    profTotal++;
                }
                if (word1 == null) { // unigram
                    fusionDict.add(word2, freq, null, false /* isNotAWord */);
                } else { // bigram
                    if (FusionDictionary.findWordInTree(fusionDict.mRootNodeArray, word1) == null) {
                        fusionDict.add(word1, 2, null, false /* isNotAWord */);
                    }
                    fusionDict.setBigram(word1, word2, freq);
                }
                bigrams.updateBigram(word1, word2, (byte)freq);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "add " + profTotal + "words");
        }
        return fusionDict;
    }

    /**
     * Reads dictionary from file.
     */
    public static void readDictionaryBinary(final DictDecoder dictDecoder,
            final OnAddWordListener dict) {
        final TreeMap<Integer, String> unigrams = CollectionUtils.newTreeMap();
        final TreeMap<Integer, Integer> frequencies = CollectionUtils.newTreeMap();
        final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams = CollectionUtils.newTreeMap();
        try {
            dictDecoder.readUnigramsAndBigramsBinary(unigrams, frequencies, bigrams);
        } catch (IOException e) {
            Log.e(TAG, "IO exception while reading file", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "ArrayIndexOutOfBoundsException while reading file", e);
        }
        addWordsFromWordMap(unigrams, frequencies, bigrams, dict);
    }

    /**
     * Adds all unigrams and bigrams in maps to OnAddWordListener.
     */
    @UsedForTesting
    static void addWordsFromWordMap(final TreeMap<Integer, String> unigrams,
            final TreeMap<Integer, Integer> frequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams,
            final OnAddWordListener to) {
        for (Entry<Integer, String> entry : unigrams.entrySet()) {
            final String word1 = entry.getValue();
            final int unigramFrequency = frequencies.get(entry.getKey());
            to.setUnigram(word1, null /* shortcutTarget */, unigramFrequency, 0 /* shortcutFreq */);
            final ArrayList<PendingAttribute> attrList = bigrams.get(entry.getKey());
            if (attrList != null) {
                for (final PendingAttribute attr : attrList) {
                    final String word2 = unigrams.get(attr.mAddress);
                    if (word1 == null || word2 == null) {
                        Log.e(TAG, "Invalid bigram pair detected: " + word1 + ", " + word2);
                        continue;
                    }
                    to.setBigram(word1, word2,
                            BinaryDictIOUtils.reconstructBigramFrequency(unigramFrequency,
                                    attr.mFrequency));
                }
            }
        }

    }
}
