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

import com.android.inputmethod.latin.makedict.BinaryDictEncoderUtils;
import com.android.inputmethod.latin.makedict.FormatSpec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// TODO: Add a test to evaluate the speed of operations of Ver4 dictionary.
@LargeTest
public class Ver4BinaryDictionaryTests extends AndroidTestCase {
    private static final String TEST_LOCALE = "test";
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // Note that dictVersion is different from dictionary format version and it never affects the
    // dictionary format.
    // TODO: Rename dictVersion to understandable name such as dictRevision.
    private File createEmptyDictionaryAndGetTrieFile(final String dictVersion) throws IOException {
        final File file = File.createTempFile(dictVersion, TEST_DICT_FILE_EXTENSION,
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

    public void testIsValidDictionary() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
    }

    // TODO: Add large tests.
    public void testReadProbability() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int frequency = 100;
        binaryDictionary.addUnigramWord("a", frequency);
        binaryDictionary.addUnigramWord("aaa", frequency);
        binaryDictionary.addUnigramWord("ab", frequency);

        assertEquals(frequency, binaryDictionary.getFrequency("a"));
        assertEquals(frequency, binaryDictionary.getFrequency("aaa"));
        assertEquals(frequency, binaryDictionary.getFrequency("ab"));
    }

    public static int getCalculatedBigramProbabiliy(final BinaryDictionary binaryDictionary,
            final int unigramFrequency, final int bigramFrequency) {
        final int bigramFrequencyDiff = BinaryDictEncoderUtils.getBigramFrequencyDiff(
                unigramFrequency, bigramFrequency);
        return binaryDictionary.calculateProbability(unigramFrequency, bigramFrequencyDiff);
    }

    // TODO: Add large tests.
    public void testReadBigrams() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int unigramFrequency = 1;
        final int bigramFrequency0 = 10;
        final int bigramFrequency1 = 1;
        final int bigramFrequency2 = 15;
        binaryDictionary.addUnigramWord("a", unigramFrequency);
        binaryDictionary.addUnigramWord("aaa", unigramFrequency);
        binaryDictionary.addUnigramWord("ab", unigramFrequency);
        binaryDictionary.addBigramWords("a", "aaa", bigramFrequency0);
        binaryDictionary.addBigramWords("a", "ab", bigramFrequency1);
        binaryDictionary.addBigramWords("aaa", "ab", bigramFrequency2);

        assertEquals(binaryDictionary.calculateProbability(unigramFrequency,
                bigramFrequency0), binaryDictionary.getBigramProbability("a", "aaa"));
        assertEquals(binaryDictionary.calculateProbability(unigramFrequency,
                bigramFrequency1), binaryDictionary.getBigramProbability("a", "ab"));
        assertEquals(binaryDictionary.calculateProbability(unigramFrequency,
                bigramFrequency2), binaryDictionary.getBigramProbability("aaa", "ab"));

        assertFalse(binaryDictionary.isValidBigram("aaa", "a"));
        assertFalse(binaryDictionary.isValidBigram("ab", "a"));
        assertFalse(binaryDictionary.isValidBigram("ab", "aaa"));
    }

    // TODO: Add large tests.
    public void testWriteUnigrams() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int probability = 100;
        binaryDictionary.addUnigramWord("aaa", probability);
        binaryDictionary.addUnigramWord("abc", probability);
        binaryDictionary.addUnigramWord("bcd", probability);
        binaryDictionary.addUnigramWord("x", probability);
        binaryDictionary.addUnigramWord("y", probability);

        assertEquals(probability, binaryDictionary.getFrequency("aaa"));
        assertEquals(probability, binaryDictionary.getFrequency("abc"));
        assertEquals(probability, binaryDictionary.getFrequency("bcd"));
        assertEquals(probability, binaryDictionary.getFrequency("x"));
        assertEquals(probability, binaryDictionary.getFrequency("y"));
    }

    public void testWriteBigrams() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
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
    }

    public void testRemoveBigramWords() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
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
    }

    public void testFlushDictionary() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        File trieFile = null;
        try {
            trieFile = createEmptyDictionaryAndGetTrieFile(dictVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int probability = 100;
        binaryDictionary.addUnigramWord("aaa", probability);
        binaryDictionary.addUnigramWord("abcd", probability);
        // Close without flushing.
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("abcd"));

        binaryDictionary.addUnigramWord("aaa", probability);
        binaryDictionary.addUnigramWord("abcd", probability);
        binaryDictionary.flush();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(probability, binaryDictionary.getFrequency("aaa"));
        assertEquals(probability, binaryDictionary.getFrequency("abcd"));
        binaryDictionary.addUnigramWord("bcde", probability);
        binaryDictionary.flush();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertEquals(probability, binaryDictionary.getFrequency("bcde"));
        binaryDictionary.close();
    }

}
