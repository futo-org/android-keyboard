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
import android.text.TextUtils;
import android.util.Pair;

import com.android.inputmethod.latin.BinaryDictionary.LanguageModelParam;
import com.android.inputmethod.latin.makedict.CodePointUtils;
import com.android.inputmethod.latin.makedict.FormatSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

// TODO Use the seed passed as an argument for makedict test.
@LargeTest
public class BinaryDictionaryTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private File createEmptyDictionaryAndGetFile(final String dictId,
            final int formatVersion) throws IOException {
        if (formatVersion == 3) {
            return createEmptyVer3DictionaryAndGetFile(dictId);
        } else if (formatVersion == 4) {
            return createEmptyVer4DictionaryAndGetFile(dictId);
        } else {
            throw new IOException("Dictionary format version " + formatVersion
                    + " is not supported.");
        }
    }

    private File createEmptyVer4DictionaryAndGetFile(final String dictId) throws IOException {
        final File file = File.createTempFile(dictId, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        file.delete();
        file.mkdir();
        Map<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.SUPPORTS_DYNAMIC_UPDATE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionary.createEmptyDictFile(file.getAbsolutePath(),
                4 /* dictVersion */, attributeMap)) {
            return new File(file, FormatSpec.TRIE_FILE_EXTENSION);
        } else {
            throw new IOException("Empty dictionary " + file.getAbsolutePath() + " "
                    + FormatSpec.TRIE_FILE_EXTENSION + " cannot be created.");
        }
    }

    private File createEmptyVer3DictionaryAndGetFile(final String dictId) throws IOException {
        final File file = File.createTempFile(dictId, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        file.delete();
        Map<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.SUPPORTS_DYNAMIC_UPDATE_ATTRIBUTE,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionary.createEmptyDictFile(file.getAbsolutePath(), 3 /* dictVersion */,
                attributeMap)) {
            return file;
        } else {
            throw new IOException(
                    "Empty dictionary " + file.getAbsolutePath() + " cannot be created.");
        }
    }

    public void testIsValidDictionary() {
        testIsValidDictionary(3 /* formatVersion */);
        testIsValidDictionary(4 /* formatVersion */);
    }

    private void testIsValidDictionary(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue("binaryDictionary must be valid for existing valid dictionary file.",
                binaryDictionary.isValidDictionary());
        binaryDictionary.close();
        assertFalse("binaryDictionary must be invalid after closing.",
                binaryDictionary.isValidDictionary());
        dictFile.delete();
        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(), 0 /* offset */,
                dictFile.length(), true /* useFullEditDistance */, Locale.getDefault(),
                TEST_LOCALE, true /* isUpdatable */);
        assertFalse("binaryDictionary must be invalid for not existing dictionary file.",
                binaryDictionary.isValidDictionary());
        binaryDictionary.close();
    }

    public void testAddUnigramWord() {
        testAddUnigramWord(3 /* formatVersion */);
        testAddUnigramWord(4 /* formatVersion */);
    }

    private void testAddUnigramWord(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int probability = 100;
        binaryDictionary.addUnigramWord("aaa", probability);
        // Reallocate and create.
        binaryDictionary.addUnigramWord("aab", probability);
        // Insert into children.
        binaryDictionary.addUnigramWord("aac", probability);
        // Make terminal.
        binaryDictionary.addUnigramWord("aa", probability);
        // Create children.
        binaryDictionary.addUnigramWord("aaaa", probability);
        // Reallocate and make termianl.
        binaryDictionary.addUnigramWord("a", probability);

        final int updatedProbability = 200;
        // Update.
        binaryDictionary.addUnigramWord("aaa", updatedProbability);

        assertEquals(probability, binaryDictionary.getFrequency("aab"));
        assertEquals(probability, binaryDictionary.getFrequency("aac"));
        assertEquals(probability, binaryDictionary.getFrequency("aa"));
        assertEquals(probability, binaryDictionary.getFrequency("aaaa"));
        assertEquals(probability, binaryDictionary.getFrequency("a"));
        assertEquals(updatedProbability, binaryDictionary.getFrequency("aaa"));

        dictFile.delete();
    }

    public void testRandomlyAddUnigramWord() {
        testRandomlyAddUnigramWord(3 /* formatVersion */);
        testRandomlyAddUnigramWord(4 /* formatVersion */);
    }

    private void testRandomlyAddUnigramWord(final int formatVersion) {
        final int wordCount = 1000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final HashMap<String, Integer> probabilityMap = new HashMap<String, Integer>();
        // Test a word that isn't contained within the dictionary.
        final Random random = new Random(seed);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            probabilityMap.put(word, random.nextInt(0xFF));
        }
        for (String word : probabilityMap.keySet()) {
            binaryDictionary.addUnigramWord(word, probabilityMap.get(word));
        }
        for (String word : probabilityMap.keySet()) {
            assertEquals(word, (int)probabilityMap.get(word), binaryDictionary.getFrequency(word));
        }
        dictFile.delete();
    }

    public void testAddBigramWords() {
        testAddBigramWords(3 /* formatVersion */);
        testAddBigramWords(4 /* formatVersion */);
    }

    private void testAddBigramWords(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int unigramProbability = 100;
        final int bigramProbability = 10;
        final int updatedBigramProbability = 15;
        binaryDictionary.addUnigramWord("aaa", unigramProbability);
        binaryDictionary.addUnigramWord("abb", unigramProbability);
        binaryDictionary.addUnigramWord("bcc", unigramProbability);
        binaryDictionary.addBigramWords("aaa", "abb", bigramProbability);
        binaryDictionary.addBigramWords("aaa", "bcc", bigramProbability);
        binaryDictionary.addBigramWords("abb", "aaa", bigramProbability);
        binaryDictionary.addBigramWords("abb", "bcc", bigramProbability);

        final int probability = binaryDictionary.calculateProbability(unigramProbability,
                bigramProbability);
        assertEquals(true, binaryDictionary.isValidBigram("aaa", "abb"));
        assertEquals(true, binaryDictionary.isValidBigram("aaa", "bcc"));
        assertEquals(true, binaryDictionary.isValidBigram("abb", "aaa"));
        assertEquals(true, binaryDictionary.isValidBigram("abb", "bcc"));
        assertEquals(probability, binaryDictionary.getBigramProbability("aaa", "abb"));
        assertEquals(probability, binaryDictionary.getBigramProbability("aaa", "bcc"));
        assertEquals(probability, binaryDictionary.getBigramProbability("abb", "aaa"));
        assertEquals(probability, binaryDictionary.getBigramProbability("abb", "bcc"));

        binaryDictionary.addBigramWords("aaa", "abb", updatedBigramProbability);
        final int updatedProbability = binaryDictionary.calculateProbability(unigramProbability,
                updatedBigramProbability);
        assertEquals(updatedProbability, binaryDictionary.getBigramProbability("aaa", "abb"));

        assertEquals(false, binaryDictionary.isValidBigram("bcc", "aaa"));
        assertEquals(false, binaryDictionary.isValidBigram("bcc", "bbc"));
        assertEquals(false, binaryDictionary.isValidBigram("aaa", "aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                binaryDictionary.getBigramProbability("bcc", "aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                binaryDictionary.getBigramProbability("bcc", "bbc"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                binaryDictionary.getBigramProbability("aaa", "aaa"));

        // Testing bigram link.
        binaryDictionary.addUnigramWord("abcde", unigramProbability);
        binaryDictionary.addUnigramWord("fghij", unigramProbability);
        binaryDictionary.addBigramWords("abcde", "fghij", bigramProbability);
        binaryDictionary.addUnigramWord("fgh", unigramProbability);
        binaryDictionary.addUnigramWord("abc", unigramProbability);
        binaryDictionary.addUnigramWord("f", unigramProbability);
        assertEquals(probability, binaryDictionary.getBigramProbability("abcde", "fghij"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                binaryDictionary.getBigramProbability("abcde", "fgh"));
        binaryDictionary.addBigramWords("abcde", "fghij", updatedBigramProbability);
        assertEquals(updatedProbability, binaryDictionary.getBigramProbability("abcde", "fghij"));

        dictFile.delete();
    }

    public void testRandomlyAddBigramWords() {
        testRandomlyAddBigramWords(3 /* formatVersion */);
        testRandomlyAddBigramWords(4 /* formatVersion */);
    }

    private void testRandomlyAddBigramWords(final int formatVersion) {
        final int wordCount = 100;
        final int bigramCount = 1000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final ArrayList<String> words = new ArrayList<String>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<Pair<String,String>>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<String, Integer>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities =
                new HashMap<Pair<String, String>, Integer>();

        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            binaryDictionary.addUnigramWord(word, unigramProbability);
        }

        for (int i = 0; i < bigramCount; i++) {
            final String word0 = words.get(random.nextInt(wordCount));
            final String word1 = words.get(random.nextInt(wordCount));
            if (TextUtils.equals(word0, word1)) {
                continue;
            }
            final Pair<String, String> bigram = new Pair<String, String>(word0, word1);
            bigramWords.add(bigram);
            final int bigramProbability = random.nextInt(0xF);
            bigramProbabilities.put(bigram, bigramProbability);
            binaryDictionary.addBigramWords(word0, word1, bigramProbability);
        }

        for (final Pair<String, String> bigram : bigramWords) {
            final int unigramProbability = unigramProbabilities.get(bigram.second);
            final int bigramProbability = bigramProbabilities.get(bigram);
            final int probability = binaryDictionary.calculateProbability(unigramProbability,
                    bigramProbability);
            assertEquals(probability,
                    binaryDictionary.getBigramProbability(bigram.first, bigram.second));
        }

        dictFile.delete();
    }

    public void testRemoveBigramWords() {
        testRemoveBigramWords(3 /* formatVersion */);
        testRemoveBigramWords(4 /* formatVersion */);
    }

    private void testRemoveBigramWords(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final int unigramProbability = 100;
        final int bigramProbability = 10;
        binaryDictionary.addUnigramWord("aaa", unigramProbability);
        binaryDictionary.addUnigramWord("abb", unigramProbability);
        binaryDictionary.addUnigramWord("bcc", unigramProbability);
        binaryDictionary.addBigramWords("aaa", "abb", bigramProbability);
        binaryDictionary.addBigramWords("aaa", "bcc", bigramProbability);
        binaryDictionary.addBigramWords("abb", "aaa", bigramProbability);
        binaryDictionary.addBigramWords("abb", "bcc", bigramProbability);

        assertEquals(true, binaryDictionary.isValidBigram("aaa", "abb"));
        assertEquals(true, binaryDictionary.isValidBigram("aaa", "bcc"));
        assertEquals(true, binaryDictionary.isValidBigram("abb", "aaa"));
        assertEquals(true, binaryDictionary.isValidBigram("abb", "bcc"));

        binaryDictionary.removeBigramWords("aaa", "abb");
        assertEquals(false, binaryDictionary.isValidBigram("aaa", "abb"));
        binaryDictionary.addBigramWords("aaa", "abb", bigramProbability);
        assertEquals(true, binaryDictionary.isValidBigram("aaa", "abb"));


        binaryDictionary.removeBigramWords("aaa", "bcc");
        assertEquals(false, binaryDictionary.isValidBigram("aaa", "bcc"));
        binaryDictionary.removeBigramWords("abb", "aaa");
        assertEquals(false, binaryDictionary.isValidBigram("abb", "aaa"));
        binaryDictionary.removeBigramWords("abb", "bcc");
        assertEquals(false, binaryDictionary.isValidBigram("abb", "bcc"));

        binaryDictionary.removeBigramWords("aaa", "abb");
        // Test remove non-existing bigram operation.
        binaryDictionary.removeBigramWords("aaa", "abb");
        binaryDictionary.removeBigramWords("bcc", "aaa");

        dictFile.delete();
    }

    public void testFlushDictionary() {
        testFlushDictionary(3 /* formatVersion */);
        testFlushDictionary(4 /* formatVersion */);
    }

    private void testFlushDictionary(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int probability = 100;
        binaryDictionary.addUnigramWord("aaa", probability);
        binaryDictionary.addUnigramWord("abcd", probability);
        // Close without flushing.
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("abcd"));

        binaryDictionary.addUnigramWord("aaa", probability);
        binaryDictionary.addUnigramWord("abcd", probability);
        binaryDictionary.flush();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(probability, binaryDictionary.getFrequency("aaa"));
        assertEquals(probability, binaryDictionary.getFrequency("abcd"));
        binaryDictionary.addUnigramWord("bcde", probability);
        binaryDictionary.flush();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertEquals(probability, binaryDictionary.getFrequency("bcde"));
        binaryDictionary.close();

        dictFile.delete();
    }

    public void testFlushWithGCDictionary() {
        testFlushWithGCDictionary(3 /* formatVersion */);
        testFlushWithGCDictionary(4 /* formatVersion */);
    }

    private void testFlushWithGCDictionary(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int unigramProbability = 100;
        final int bigramProbability = 10;
        binaryDictionary.addUnigramWord("aaa", unigramProbability);
        binaryDictionary.addUnigramWord("abb", unigramProbability);
        binaryDictionary.addUnigramWord("bcc", unigramProbability);
        binaryDictionary.addBigramWords("aaa", "abb", bigramProbability);
        binaryDictionary.addBigramWords("aaa", "bcc", bigramProbability);
        binaryDictionary.addBigramWords("abb", "aaa", bigramProbability);
        binaryDictionary.addBigramWords("abb", "bcc", bigramProbability);
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final int probability = binaryDictionary.calculateProbability(unigramProbability,
                bigramProbability);
        assertEquals(unigramProbability, binaryDictionary.getFrequency("aaa"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("abb"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("bcc"));
        assertEquals(probability, binaryDictionary.getBigramProbability("aaa", "abb"));
        assertEquals(probability, binaryDictionary.getBigramProbability("aaa", "bcc"));
        assertEquals(probability, binaryDictionary.getBigramProbability("abb", "aaa"));
        assertEquals(probability, binaryDictionary.getBigramProbability("abb", "bcc"));
        assertEquals(false, binaryDictionary.isValidBigram("bcc", "aaa"));
        assertEquals(false, binaryDictionary.isValidBigram("bcc", "bbc"));
        assertEquals(false, binaryDictionary.isValidBigram("aaa", "aaa"));
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        dictFile.delete();
    }

    public void testAddBigramWordsAndFlashWithGC() {
        testAddBigramWordsAndFlashWithGC(3 /* formatVersion */);
        testAddBigramWordsAndFlashWithGC(4 /* formatVersion */);
    }

    // TODO: Evaluate performance of GC
    private void testAddBigramWordsAndFlashWithGC(final int formatVersion) {
        final int wordCount = 100;
        final int bigramCount = 1000;
        final int codePointSetSize = 30;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }

        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final ArrayList<String> words = new ArrayList<String>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<Pair<String,String>>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<String, Integer>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities =
                new HashMap<Pair<String, String>, Integer>();

        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            binaryDictionary.addUnigramWord(word, unigramProbability);
        }

        for (int i = 0; i < bigramCount; i++) {
            final String word0 = words.get(random.nextInt(wordCount));
            final String word1 = words.get(random.nextInt(wordCount));
            if (TextUtils.equals(word0, word1)) {
                continue;
            }
            final Pair<String, String> bigram = new Pair<String, String>(word0, word1);
            bigramWords.add(bigram);
            final int bigramProbability = random.nextInt(0xF);
            bigramProbabilities.put(bigram, bigramProbability);
            binaryDictionary.addBigramWords(word0, word1, bigramProbability);
        }

        binaryDictionary.flushWithGC();
        binaryDictionary.close();
        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        for (final Pair<String, String> bigram : bigramWords) {
            final int unigramProbability = unigramProbabilities.get(bigram.second);
            final int bigramProbability = bigramProbabilities.get(bigram);
            final int probability = binaryDictionary.calculateProbability(unigramProbability,
                    bigramProbability);
            assertEquals(probability,
                    binaryDictionary.getBigramProbability(bigram.first, bigram.second));
        }

        dictFile.delete();
    }

    public void testRandomOperationsAndFlashWithGC() {
        testRandomOperationsAndFlashWithGC(3 /* formatVersion */);
        testRandomOperationsAndFlashWithGC(4 /* formatVersion */);
    }

    private void testRandomOperationsAndFlashWithGC(final int formatVersion) {
        final int flashWithGCIterationCount = 50;
        final int operationCountInEachIteration = 200;
        final int initialUnigramCount = 100;
        final float addUnigramProb = 0.5f;
        final float addBigramProb = 0.8f;
        final float removeBigramProb = 0.2f;
        final int codePointSetSize = 30;

        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }

        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final ArrayList<String> words = new ArrayList<String>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<Pair<String,String>>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<String, Integer>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities =
                new HashMap<Pair<String, String>, Integer>();
        for (int i = 0; i < initialUnigramCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            binaryDictionary.addUnigramWord(word, unigramProbability);
        }
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        for (int gcCount = 0; gcCount < flashWithGCIterationCount; gcCount++) {
            binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                    0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                    Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
            for (int opCount = 0; opCount < operationCountInEachIteration; opCount++) {
                // Add unigram.
                if (random.nextFloat() < addUnigramProb) {
                    final String word = CodePointUtils.generateWord(random, codePointSet);
                    words.add(word);
                    final int unigramProbability = random.nextInt(0xFF);
                    unigramProbabilities.put(word, unigramProbability);
                    binaryDictionary.addUnigramWord(word, unigramProbability);
                }
                // Add bigram.
                if (random.nextFloat() < addBigramProb && words.size() > 2) {
                    final int word0Index = random.nextInt(words.size());
                    int word1Index = random.nextInt(words.size() - 1);
                    if (word0Index <= word1Index) {
                        word1Index++;
                    }
                    final String word0 = words.get(word0Index);
                    final String word1 = words.get(word1Index);
                    if (TextUtils.equals(word0, word1)) {
                        continue;
                    }
                    final int bigramProbability = random.nextInt(0xF);
                    final Pair<String, String> bigram = new Pair<String, String>(word0, word1);
                    bigramWords.add(bigram);
                    bigramProbabilities.put(bigram, bigramProbability);
                    binaryDictionary.addBigramWords(word0, word1, bigramProbability);
                }
                // Remove bigram.
                if (random.nextFloat() < removeBigramProb && !bigramWords.isEmpty()) {
                    final int bigramIndex = random.nextInt(bigramWords.size());
                    final Pair<String, String> bigram = bigramWords.get(bigramIndex);
                    bigramWords.remove(bigramIndex);
                    bigramProbabilities.remove(bigram);
                    binaryDictionary.removeBigramWords(bigram.first, bigram.second);
                }
            }

            // Test whether the all unigram operations are collectlly handled.
            for (int i = 0; i < words.size(); i++) {
                final String word = words.get(i);
                final int unigramProbability = unigramProbabilities.get(word);
                assertEquals(word, unigramProbability, binaryDictionary.getFrequency(word));
            }
            // Test whether the all bigram operations are collectlly handled.
            for (int i = 0; i < bigramWords.size(); i++) {
                final Pair<String, String> bigram = bigramWords.get(i);
                final int unigramProbability = unigramProbabilities.get(bigram.second);
                final int probability;
                if (bigramProbabilities.containsKey(bigram)) {
                    final int bigramProbability = bigramProbabilities.get(bigram);
                    probability = binaryDictionary.calculateProbability(unigramProbability,
                            bigramProbability);
                } else {
                    probability = Dictionary.NOT_A_PROBABILITY;
                }
                assertEquals(probability,
                        binaryDictionary.getBigramProbability(bigram.first, bigram.second));
            }
            binaryDictionary.flushWithGC();
            binaryDictionary.close();
        }

        dictFile.delete();
    }

    public void testAddManyUnigramsAndFlushWithGC() {
        testAddManyUnigramsAndFlushWithGC(3 /* formatVersion */);
        testAddManyUnigramsAndFlushWithGC(4 /* formatVersion */);
    }

    private void testAddManyUnigramsAndFlushWithGC(final int formatVersion) {
        final int flashWithGCIterationCount = 3;
        final int codePointSetSize = 50;

        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }

        final ArrayList<String> words = new ArrayList<String>();
        final HashMap<String, Integer> unigramProbabilities = new HashMap<String, Integer>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        BinaryDictionary binaryDictionary;
        for (int i = 0; i < flashWithGCIterationCount; i++) {
            binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                    0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                    Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
            while(!binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final String word = CodePointUtils.generateWord(random, codePointSet);
                words.add(word);
                final int unigramProbability = random.nextInt(0xFF);
                unigramProbabilities.put(word, unigramProbability);
                binaryDictionary.addUnigramWord(word, unigramProbability);
            }

            for (int j = 0; j < words.size(); j++) {
                final String word = words.get(j);
                final int unigramProbability = unigramProbabilities.get(word);
                assertEquals(word, unigramProbability, binaryDictionary.getFrequency(word));
            }

            binaryDictionary.flushWithGC();
            binaryDictionary.close();
        }

        dictFile.delete();
    }

    public void testUnigramAndBigramCount() {
        testUnigramAndBigramCount(3 /* formatVersion */);
        testUnigramAndBigramCount(4 /* formatVersion */);
    }

    private void testUnigramAndBigramCount(final int formatVersion) {
        final int flashWithGCIterationCount = 10;
        final int codePointSetSize = 50;
        final int unigramCountPerIteration = 1000;
        final int bigramCountPerIteration = 2000;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }

        final ArrayList<String> words = new ArrayList<String>();
        final HashSet<Pair<String, String>> bigrams = new HashSet<Pair<String, String>>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        BinaryDictionary binaryDictionary;
        for (int i = 0; i < flashWithGCIterationCount; i++) {
            binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                    0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                    Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
            for (int j = 0; j < unigramCountPerIteration; j++) {
                final String word = CodePointUtils.generateWord(random, codePointSet);
                words.add(word);
                final int unigramProbability = random.nextInt(0xFF);
                binaryDictionary.addUnigramWord(word, unigramProbability);
            }
            for (int j = 0; j < bigramCountPerIteration; j++) {
                final String word0 = words.get(random.nextInt(words.size()));
                final String word1 = words.get(random.nextInt(words.size()));
                if (TextUtils.equals(word0, word1)) {
                    continue;
                }
                bigrams.add(new Pair<String, String>(word0, word1));
                final int bigramProbability = random.nextInt(0xF);
                binaryDictionary.addBigramWords(word0, word1, bigramProbability);
            }
            assertEquals(new HashSet<String>(words).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTests(BinaryDictionary.UNIGRAM_COUNT_QUERY)));
            assertEquals(new HashSet<Pair<String, String>>(bigrams).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTests(BinaryDictionary.BIGRAM_COUNT_QUERY)));
            binaryDictionary.flushWithGC();
            assertEquals(new HashSet<String>(words).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTests(BinaryDictionary.UNIGRAM_COUNT_QUERY)));
            assertEquals(new HashSet<Pair<String, String>>(bigrams).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTests(BinaryDictionary.BIGRAM_COUNT_QUERY)));
            binaryDictionary.close();
        }

        dictFile.delete();
    }

    public void testAddMultipleDictionaryEntries() {
        testAddMultipleDictionaryEntries(3 /* formatVersion */);
        testAddMultipleDictionaryEntries(4 /* formatVersion */);
    }

    private void testAddMultipleDictionaryEntries(final int formatVersion) {
        final int codePointSetSize = 20;
        final int lmParamCount = 1000;
        final double bigramContinueRate = 0.9;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<String, Integer>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities =
                new HashMap<Pair<String, String>, Integer>();

        final LanguageModelParam[] languageModelParams = new LanguageModelParam[lmParamCount];
        String prevWord = null;
        for (int i = 0; i < languageModelParams.length; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int probability = random.nextInt(0xFF);
            final int bigramProbability = random.nextInt(0xF);
            unigramProbabilities.put(word, probability);
            if (prevWord == null) {
                languageModelParams[i] = new LanguageModelParam(word, probability);
            } else {
                languageModelParams[i] = new LanguageModelParam(prevWord, word, probability,
                        bigramProbability);
                bigramProbabilities.put(new Pair<String, String>(prevWord, word),
                        bigramProbability);
            }
            prevWord = (random.nextDouble() < bigramContinueRate) ? word : null;
        }

        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        binaryDictionary.addMultipleDictionaryEntries(languageModelParams);

        for (Map.Entry<String, Integer> entry : unigramProbabilities.entrySet()) {
            assertEquals((int)entry.getValue(), binaryDictionary.getFrequency(entry.getKey()));
        }

        for (Map.Entry<Pair<String, String>, Integer> entry : bigramProbabilities.entrySet()) {
            final String word0 = entry.getKey().first;
            final String word1 = entry.getKey().second;
            final int unigramProbability = unigramProbabilities.get(word1);
            final int bigramProbability = entry.getValue();
            final int probability = binaryDictionary.calculateProbability(
                    unigramProbability, bigramProbability);
            assertEquals(probability, binaryDictionary.getBigramProbability(word0, word1));
        }
    }
}
