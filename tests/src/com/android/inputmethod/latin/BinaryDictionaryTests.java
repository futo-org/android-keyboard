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

import com.android.inputmethod.latin.makedict.CodePointUtils;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver3DictEncoder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

@LargeTest
public class BinaryDictionaryTests extends AndroidTestCase {
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(3 /* version */, true /* supportsDynamicUpdate */);
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

    private File createEmptyDictionaryAndGetFile(final String filename) throws IOException,
            UnsupportedFormatException {
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(new HashMap<String,String>(), false, false));
        final File file = File.createTempFile(filename, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        final DictEncoder dictEncoder = new Ver3DictEncoder(file);
        dictEncoder.writeDictionary(dict, FORMAT_OPTIONS);
        return file;
    }

    public void testIsValidDictionary() {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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
        final int wordCount = 1000;
        final int codePointSetSize = 50;
        final int seed = 123456789;

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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
        final int wordCount = 100;
        final int bigramCount = 1000;
        final int codePointSetSize = 50;
        final int seed = 11111;

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final ArrayList<String> words = new ArrayList<String>();
        // Test a word that isn't contained within the dictionary.
        final Random random = new Random(seed);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final int[] unigramProbabilities = new int[wordCount];
        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities[i] = unigramProbability;
            binaryDictionary.addUnigramWord(word, unigramProbability);
        }

        final int[][] probabilities = new int[wordCount][wordCount];

        for (int i = 0; i < wordCount; ++i) {
            for (int j = 0; j < wordCount; ++j) {
                probabilities[i][j] = Dictionary.NOT_A_PROBABILITY;
            }
        }

        for (int i = 0; i < bigramCount; i++) {
            final int word0Index = random.nextInt(wordCount);
            final int word1Index = random.nextInt(wordCount);
            final String word0 = words.get(word0Index);
            final String word1 = words.get(word1Index);
            final int bigramProbability = random.nextInt(0xF);
            probabilities[word0Index][word1Index] = binaryDictionary.calculateProbability(
                    unigramProbabilities[word1Index], bigramProbability);
            binaryDictionary.addBigramWords(word0, word1, bigramProbability);
        }

        for (int i = 0; i < words.size(); i++) {
            for (int j = 0; j < words.size(); j++) {
                assertEquals(probabilities[i][j],
                        binaryDictionary.getBigramProbability(words.get(i), words.get(j)));
            }
        }

        dictFile.delete();
    }

    public void testRemoveBigramWords() {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary");
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
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

        assertEquals(-1, binaryDictionary.getFrequency("aaa"));
        assertEquals(-1, binaryDictionary.getFrequency("abcd"));

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
}
