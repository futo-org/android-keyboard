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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Pair;

import com.android.inputmethod.latin.makedict.CodePointUtils;
import com.android.inputmethod.latin.makedict.FormatSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@LargeTest
public class BinaryDictionaryDecayingTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";

    // Note that these are corresponding definitions in native code in
    // latinime::DynamicPatriciaTriePolicy.
    private static final String SET_NEEDS_TO_DECAY_FOR_TESTING_KEY =
            "SET_NEEDS_TO_DECAY_FOR_TESTING";

    private static final int DUMMY_PROBABILITY = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void forcePassingShortTime(final BinaryDictionary binaryDictionary) {
        // Entries having low probability would be suppressed once in 3 GCs.
        final int count = 3;
        for (int i = 0; i < count; i++) {
            binaryDictionary.getPropertyForTests(SET_NEEDS_TO_DECAY_FOR_TESTING_KEY);
            binaryDictionary.flushWithGC();
        }
    }

    private void forcePassingLongTime(final BinaryDictionary binaryDictionary) {
        // Currently, probabilities are decayed when GC is run. All entries that have never been
        // typed in 128 GCs would be removed.
        final int count = 128;
        for (int i = 0; i < count; i++) {
            binaryDictionary.getPropertyForTests(SET_NEEDS_TO_DECAY_FOR_TESTING_KEY);
            binaryDictionary.flushWithGC();
        }
    }

    private File createEmptyDictionaryAndGetFile(final String filename) throws IOException {
        final File file = File.createTempFile(filename, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        Map<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.SUPPORTS_DYNAMIC_UPDATE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.USES_FORGETTING_CURVE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionary.createEmptyDictFile(file.getAbsolutePath(),
                3 /* dictVersion */, attributeMap)) {
            return file;
        } else {
            throw new IOException("Empty dictionary cannot be created.");
        }
    }

    public void testAddValidAndInvalidWords() {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        binaryDictionary.addUnigramWord("a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        binaryDictionary.addUnigramWord("a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        binaryDictionary.addUnigramWord("a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        binaryDictionary.addUnigramWord("a", Dictionary.NOT_A_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("a"));

        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("b"));

        final int unigramProbability = binaryDictionary.getFrequency("a");
        binaryDictionary.addBigramWords("a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        binaryDictionary.addBigramWords("a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        binaryDictionary.addBigramWords("a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        binaryDictionary.addBigramWords("a", "b", Dictionary.NOT_A_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));

        binaryDictionary.addUnigramWord("c", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "c", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "c"));

        // Add bigrams of not valid unigrams.
        binaryDictionary.addBigramWords("x", "y", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("x", "y"));
        binaryDictionary.addBigramWords("x", "y", DUMMY_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("x", "y"));

        binaryDictionary.close();
        dictFile.delete();
    }

    public void testDecayingProbability() {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        forcePassingShortTime(binaryDictionary);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));

        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "b", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "b", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "b", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("a", DUMMY_PROBABILITY);
        binaryDictionary.addUnigramWord("b", DUMMY_PROBABILITY);
        binaryDictionary.addBigramWords("a", "b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));

        binaryDictionary.close();
        dictFile.delete();
    }

    public void testAddManyUnigramsToDecayingDict() {
        final int unigramCount = 30000;
        final int unigramTypedCount = 100000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> words = new ArrayList<String>();

        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
        }

        final int maxUnigramCount = Integer.parseInt(
                binaryDictionary.getPropertyForTests(BinaryDictionary.MAX_UNIGRAM_COUNT_QUERY));
        for (int i = 0; i < unigramTypedCount; i++) {
            final String word = words.get(random.nextInt(words.size()));
            binaryDictionary.addUnigramWord(word, DUMMY_PROBABILITY);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int unigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTests(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    binaryDictionary.flushWithGC();
                }
                final int unigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTests(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(unigramCountBeforeGC > unigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTests(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTests(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) <= maxUnigramCount);
    }

    public void testAddManyBigramsToDecayingDict() {
        final int unigramCount = 5000;
        final int bigramCount = 30000;
        final int bigramTypedCount = 100000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> words = new ArrayList<String>();
        final ArrayList<Pair<String, String>> bigrams = new ArrayList<Pair<String, String>>();

        for (int i = 0; i < unigramCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
        }
        for (int i = 0; i < bigramCount; ++i) {
            final int word0Index = random.nextInt(words.size());
            int word1Index = random.nextInt(words.size() - 1);
            if (word1Index >= word0Index) {
                word1Index += 1;
            }
            final String word0 = words.get(word0Index);
            final String word1 = words.get(word1Index);
            final Pair<String, String> bigram = new Pair<String, String>(word0, word1);
            bigrams.add(bigram);
        }

        final int maxBigramCount = Integer.parseInt(
                binaryDictionary.getPropertyForTests(BinaryDictionary.MAX_BIGRAM_COUNT_QUERY));
        for (int i = 0; i < bigramTypedCount; ++i) {
            final Pair<String, String> bigram = bigrams.get(random.nextInt(bigrams.size()));
            binaryDictionary.addUnigramWord(bigram.first, DUMMY_PROBABILITY);
            binaryDictionary.addUnigramWord(bigram.second, DUMMY_PROBABILITY);
            binaryDictionary.addBigramWords(bigram.first, bigram.second, DUMMY_PROBABILITY);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int bigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTests(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    binaryDictionary.flushWithGC();
                }
                final int bigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTests(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                assertTrue(bigramCountBeforeGC > bigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTests(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTests(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) <= maxBigramCount);
    }
}
