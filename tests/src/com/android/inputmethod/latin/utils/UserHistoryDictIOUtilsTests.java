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

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.Ver3DictDecoder;
import com.android.inputmethod.latin.makedict.Ver3DictEncoder;
import com.android.inputmethod.latin.personalization.UserHistoryDictionaryBigramList;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.BigramDictionaryInterface;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.OnAddWordListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Unit tests for UserHistoryDictIOUtils
 */
@LargeTest
public class UserHistoryDictIOUtilsTests extends AndroidTestCase
    implements BigramDictionaryInterface {

    private static final String TAG = UserHistoryDictIOUtilsTests.class.getSimpleName();
    private static final int UNIGRAM_FREQUENCY = 50;
    private static final int BIGRAM_FREQUENCY = 100;
    private static final ArrayList<String> NOT_HAVE_BIGRAM = new ArrayList<String>();
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS = new FormatSpec.FormatOptions(2);
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";

    /**
     * Return same frequency for all words and bigrams
     */
    @Override
    public int getFrequency(String word1, String word2) {
        if (word1 == null) return UNIGRAM_FREQUENCY;
        return BIGRAM_FREQUENCY;
    }

    // Utilities for Testing

    private void addWord(final String word,
            final HashMap<String, ArrayList<String> > addedWords) {
        if (!addedWords.containsKey(word)) {
            addedWords.put(word, new ArrayList<String>());
        }
    }

    private void addBigram(final String word1, final String word2,
            final HashMap<String, ArrayList<String> > addedWords) {
        addWord(word1, addedWords);
        addWord(word2, addedWords);
        addedWords.get(word1).add(word2);
    }

    private void addBigramToBigramList(final String word1, final String word2,
            final HashMap<String, ArrayList<String> > addedWords,
            final UserHistoryDictionaryBigramList bigramList) {
        bigramList.addBigram(null, word1);
        bigramList.addBigram(word1, word2);

        addBigram(word1, word2, addedWords);
    }

    private void checkWordInFusionDict(final FusionDictionary dict, final String word,
            final ArrayList<String> expectedBigrams) {
        final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, word);
        assertNotNull(ptNode);
        assertTrue(ptNode.isTerminal());

        for (final String bigram : expectedBigrams) {
            assertNotNull(ptNode.getBigram(bigram));
        }
    }

    private void checkWordsInFusionDict(final FusionDictionary dict,
            final HashMap<String, ArrayList<String> > bigrams) {
        for (final String word : bigrams.keySet()) {
            if (bigrams.containsKey(word)) {
                checkWordInFusionDict(dict, word, bigrams.get(word));
            } else {
                checkWordInFusionDict(dict, word, NOT_HAVE_BIGRAM);
            }
        }
    }

    private void checkWordInBigramList(
            final UserHistoryDictionaryBigramList bigramList, final String word,
            final ArrayList<String> expectedBigrams) {
        // check unigram
        final HashMap<String,Byte> unigramMap = bigramList.getBigrams(null);
        assertTrue(unigramMap.containsKey(word));

        // check bigrams
        final ArrayList<String> actualBigrams = new ArrayList<String>(
                bigramList.getBigrams(word).keySet());

        Collections.sort(expectedBigrams);
        Collections.sort(actualBigrams);
        assertEquals(expectedBigrams, actualBigrams);
    }

    private void checkWordsInBigramList(final UserHistoryDictionaryBigramList bigramList,
            final HashMap<String, ArrayList<String> > addedWords) {
        for (final String word : addedWords.keySet()) {
            if (addedWords.containsKey(word)) {
                checkWordInBigramList(bigramList, word, addedWords.get(word));
            } else {
                checkWordInBigramList(bigramList, word, NOT_HAVE_BIGRAM);
            }
        }
    }

    private void writeDictToFile(final File file,
            final UserHistoryDictionaryBigramList bigramList) {
        final DictEncoder dictEncoder = new Ver3DictEncoder(file);
        UserHistoryDictIOUtils.writeDictionary(dictEncoder, this, bigramList, FORMAT_OPTIONS);
    }

    private void readDictFromFile(final File file, final OnAddWordListener listener) {
        final DictDecoder dictDecoder = FormatSpec.getDictDecoder(file, DictDecoder.USE_BYTEARRAY);
        try {
            dictDecoder.openDictBuffer();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file not found", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }
        UserHistoryDictIOUtils.readDictionaryBinary(dictDecoder, listener);
    }

    public void testGenerateFusionDictionary() {
        final UserHistoryDictionaryBigramList originalList = new UserHistoryDictionaryBigramList();

        final HashMap<String, ArrayList<String> > addedWords =
                new HashMap<String, ArrayList<String>>();
        addBigramToBigramList("this", "is", addedWords, originalList);
        addBigramToBigramList("this", "was", addedWords, originalList);
        addBigramToBigramList("hello", "world", addedWords, originalList);

        final FusionDictionary fusionDict =
                UserHistoryDictIOUtils.constructFusionDictionary(this, originalList);

        checkWordsInFusionDict(fusionDict, addedWords);
    }

    public void testReadAndWrite() {
        final Context context = getContext();

        File file = null;
        try {
            file = File.createTempFile("testReadAndWrite", TEST_DICT_FILE_EXTENSION,
                    getContext().getCacheDir());
        } catch (IOException e) {
            Log.d(TAG, "IOException while creating a temporary file", e);
        }
        assertNotNull(file);

        // make original dictionary
        final UserHistoryDictionaryBigramList originalList = new UserHistoryDictionaryBigramList();
        final HashMap<String, ArrayList<String>> addedWords = CollectionUtils.newHashMap();
        addBigramToBigramList("this" , "is"   , addedWords, originalList);
        addBigramToBigramList("this" , "was"  , addedWords, originalList);
        addBigramToBigramList("is"   , "not"  , addedWords, originalList);
        addBigramToBigramList("hello", "world", addedWords, originalList);

        // write to file
        writeDictToFile(file, originalList);

        // make result dict.
        final UserHistoryDictionaryBigramList resultList = new UserHistoryDictionaryBigramList();
        final OnAddWordListener listener = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency, final int shortcutFreq) {
                Log.d(TAG, "in: setUnigram: " + word + "," + frequency);
                resultList.addBigram(null, word, (byte)frequency);
            }
            @Override
            public void setBigram(final String word1, final String word2, final int frequency) {
                Log.d(TAG, "in: setBigram: " + word1 + "," + word2 + "," + frequency);
                resultList.addBigram(word1, word2, (byte)frequency);
            }
        };

        // load from file
        readDictFromFile(file, listener);
        checkWordsInBigramList(resultList, addedWords);

        // add new bigram
        addBigramToBigramList("hello", "java", addedWords, resultList);

        // rewrite
        writeDictToFile(file, resultList);
        final UserHistoryDictionaryBigramList resultList2 = new UserHistoryDictionaryBigramList();
        final OnAddWordListener listener2 = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency, final int shortcutFreq) {
                Log.d(TAG, "in: setUnigram: " + word + "," + frequency);
                resultList2.addBigram(null, word, (byte)frequency);
            }
            @Override
            public void setBigram(final String word1, final String word2, final int frequency) {
                Log.d(TAG, "in: setBigram: " + word1 + "," + word2 + "," + frequency);
                resultList2.addBigram(word1, word2, (byte)frequency);
            }
        };

        // load from file
        readDictFromFile(file, listener2);
        checkWordsInBigramList(resultList2, addedWords);
    }
}
