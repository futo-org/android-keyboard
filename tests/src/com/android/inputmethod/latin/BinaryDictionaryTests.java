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

import com.android.inputmethod.latin.PrevWordsInfo.WordInfo;
import com.android.inputmethod.latin.makedict.CodePointUtils;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.WeightedString;
import com.android.inputmethod.latin.makedict.WordProperty;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;

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
    private static final int[] DICT_FORMAT_VERSIONS =
            new int[] { FormatSpec.VERSION4, FormatSpec.VERSION4_DEV };

    private static boolean canCheckBigramProbability(final int formatVersion) {
        return formatVersion > FormatSpec.VERSION401;
    }

    private static boolean supportsBeginningOfSentence(final int formatVersion) {
        return formatVersion > FormatSpec.VERSION401;
    }

    private File createEmptyDictionaryAndGetFile(final String dictId,
            final int formatVersion) throws IOException {
        if (formatVersion == FormatSpec.VERSION4
                || formatVersion == FormatSpec.VERSION4_ONLY_FOR_TESTING
                || formatVersion == FormatSpec.VERSION4_DEV) {
            return createEmptyVer4DictionaryAndGetFile(dictId, formatVersion);
        } else {
            throw new IOException("Dictionary format version " + formatVersion
                    + " is not supported.");
        }
    }

    private File createEmptyVer4DictionaryAndGetFile(final String dictId,
            final int formatVersion) throws IOException {
        final File file = File.createTempFile(dictId, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        file.delete();
        file.mkdir();
        Map<String, String> attributeMap = new HashMap<>();
        if (BinaryDictionaryUtils.createEmptyDictFile(file.getAbsolutePath(), formatVersion,
                Locale.ENGLISH, attributeMap)) {
            return file;
        } else {
            throw new IOException("Empty dictionary " + file.getAbsolutePath()
                    + " cannot be created. Format version: " + formatVersion);
        }
    }

    public void testIsValidDictionary() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testIsValidDictionary(formatVersion);
        }
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
        FileUtils.deleteRecursively(dictFile);
        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(), 0 /* offset */,
                dictFile.length(), true /* useFullEditDistance */, Locale.getDefault(),
                TEST_LOCALE, true /* isUpdatable */);
        assertFalse("binaryDictionary must be invalid for not existing dictionary file.",
                binaryDictionary.isValidDictionary());
        binaryDictionary.close();
    }

    public void testConstructingDictionaryOnMemory() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testConstructingDictionaryOnMemory(formatVersion);
        }
    }

    private void testConstructingDictionaryOnMemory(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        FileUtils.deleteRecursively(dictFile);
        assertFalse(dictFile.exists());
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                true /* useFullEditDistance */, Locale.getDefault(), TEST_LOCALE, formatVersion,
                new HashMap<String, String>());
        assertTrue(binaryDictionary.isValidDictionary());
        assertEquals(formatVersion, binaryDictionary.getFormatVersion());
        final int probability = 100;
        addUnigramWord(binaryDictionary, "word", probability);
        assertEquals(probability, binaryDictionary.getFrequency("word"));
        assertFalse(dictFile.exists());
        binaryDictionary.flush();
        assertTrue(dictFile.exists());
        assertTrue(binaryDictionary.isValidDictionary());
        assertEquals(formatVersion, binaryDictionary.getFormatVersion());
        assertEquals(probability, binaryDictionary.getFrequency("word"));
        binaryDictionary.close();
        dictFile.delete();
    }

    public void testAddTooLongWord() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddTooLongWord(formatVersion);
        }
    }

    private void testAddTooLongWord(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final StringBuffer stringBuilder = new StringBuffer();
        for (int i = 0; i < Constants.DICTIONARY_MAX_WORD_LENGTH; i++) {
            stringBuilder.append('a');
        }
        final String validLongWord = stringBuilder.toString();
        stringBuilder.append('a');
        final String invalidLongWord = stringBuilder.toString();
        final int probability = 100;
        addUnigramWord(binaryDictionary, "aaa", probability);
        addUnigramWord(binaryDictionary, validLongWord, probability);
        addUnigramWord(binaryDictionary, invalidLongWord, probability);
        // Too long short cut.
        binaryDictionary.addUnigramEntry("a", probability, invalidLongWord,
                10 /* shortcutProbability */, false /* isBeginningOfSentence */,
                false /* isNotAWord */, false /* isBlacklisted */,
                BinaryDictionary.NOT_A_VALID_TIMESTAMP);
        addUnigramWord(binaryDictionary, "abc", probability);
        final int updatedProbability = 200;
        // Update.
        addUnigramWord(binaryDictionary, validLongWord, updatedProbability);
        addUnigramWord(binaryDictionary, invalidLongWord, updatedProbability);
        addUnigramWord(binaryDictionary, "abc", updatedProbability);

        assertEquals(probability, binaryDictionary.getFrequency("aaa"));
        assertEquals(updatedProbability, binaryDictionary.getFrequency(validLongWord));
        assertEquals(BinaryDictionary.NOT_A_PROBABILITY,
                binaryDictionary.getFrequency(invalidLongWord));
        assertEquals(updatedProbability, binaryDictionary.getFrequency("abc"));
        dictFile.delete();
    }

    private static void addUnigramWord(final BinaryDictionary binaryDictionary, final String word,
            final int probability) {
        binaryDictionary.addUnigramEntry(word, probability, "" /* shortcutTarget */,
                BinaryDictionary.NOT_A_PROBABILITY /* shortcutProbability */,
                false /* isBeginningOfSentence */, false /* isNotAWord */,
                false /* isBlacklisted */, BinaryDictionary.NOT_A_VALID_TIMESTAMP /* timestamp */);
    }

    private static void addBigramWords(final BinaryDictionary binaryDictionary, final String word0,
            final String word1, final int probability) {
        binaryDictionary.addNgramEntry(new PrevWordsInfo(new WordInfo(word0)), word1, probability,
                BinaryDictionary.NOT_A_VALID_TIMESTAMP /* timestamp */);
    }

    private static boolean isValidBigram(final BinaryDictionary binaryDictionary,
            final String word0, final String word1) {
        return binaryDictionary.isValidNgram(new PrevWordsInfo(new WordInfo(word0)), word1);
    }

    private static void removeBigramEntry(final BinaryDictionary binaryDictionary,
            final String word0, final String word1) {
        binaryDictionary.removeNgramEntry(new PrevWordsInfo(new WordInfo(word0)), word1);
    }

    private static int getBigramProbability(final BinaryDictionary binaryDictionary,
            final String word0,  final String word1) {
        return binaryDictionary.getNgramProbability(new PrevWordsInfo(new WordInfo(word0)), word1);
    }

    public void testAddUnigramWord() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddUnigramWord(formatVersion);
        }
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
        addUnigramWord(binaryDictionary, "aaa", probability);
        // Reallocate and create.
        addUnigramWord(binaryDictionary, "aab", probability);
        // Insert into children.
        addUnigramWord(binaryDictionary, "aac", probability);
        // Make terminal.
        addUnigramWord(binaryDictionary, "aa", probability);
        // Create children.
        addUnigramWord(binaryDictionary, "aaaa", probability);
        // Reallocate and make termianl.
        addUnigramWord(binaryDictionary, "a", probability);

        final int updatedProbability = 200;
        // Update.
        addUnigramWord(binaryDictionary, "aaa", updatedProbability);

        assertEquals(probability, binaryDictionary.getFrequency("aab"));
        assertEquals(probability, binaryDictionary.getFrequency("aac"));
        assertEquals(probability, binaryDictionary.getFrequency("aa"));
        assertEquals(probability, binaryDictionary.getFrequency("aaaa"));
        assertEquals(probability, binaryDictionary.getFrequency("a"));
        assertEquals(updatedProbability, binaryDictionary.getFrequency("aaa"));

        dictFile.delete();
    }

    public void testRandomlyAddUnigramWord() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testRandomlyAddUnigramWord(formatVersion);
        }
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

        final HashMap<String, Integer> probabilityMap = new HashMap<>();
        // Test a word that isn't contained within the dictionary.
        final Random random = new Random(seed);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            probabilityMap.put(word, random.nextInt(0xFF));
        }
        for (String word : probabilityMap.keySet()) {
            addUnigramWord(binaryDictionary, word, probabilityMap.get(word));
        }
        for (String word : probabilityMap.keySet()) {
            assertEquals(word, (int)probabilityMap.get(word), binaryDictionary.getFrequency(word));
        }
        dictFile.delete();
    }

    public void testAddBigramWords() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddBigramWords(formatVersion);
        }
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
        final int bigramProbability = 150;
        final int updatedBigramProbability = 200;
        addUnigramWord(binaryDictionary, "aaa", unigramProbability);
        addUnigramWord(binaryDictionary, "abb", unigramProbability);
        addUnigramWord(binaryDictionary, "bcc", unigramProbability);
        addBigramWords(binaryDictionary, "aaa", "abb", bigramProbability);
        addBigramWords(binaryDictionary, "aaa", "bcc", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "aaa", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "bcc", bigramProbability);

        assertTrue(isValidBigram(binaryDictionary, "aaa", "abb"));
        assertTrue(isValidBigram(binaryDictionary, "aaa", "bcc"));
        assertTrue(isValidBigram(binaryDictionary, "abb", "aaa"));
        assertTrue(isValidBigram(binaryDictionary, "abb", "bcc"));
        if (canCheckBigramProbability(formatVersion)) {
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "aaa", "abb"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "aaa", "bcc"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "abb", "aaa"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "abb", "bcc"));
        }

        addBigramWords(binaryDictionary, "aaa", "abb", updatedBigramProbability);
        if (canCheckBigramProbability(formatVersion)) {
            assertEquals(updatedBigramProbability,
                    getBigramProbability(binaryDictionary, "aaa", "abb"));
        }

        assertFalse(isValidBigram(binaryDictionary, "bcc", "aaa"));
        assertFalse(isValidBigram(binaryDictionary, "bcc", "bbc"));
        assertFalse(isValidBigram(binaryDictionary, "aaa", "aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                getBigramProbability(binaryDictionary, "bcc", "aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                getBigramProbability(binaryDictionary, "bcc", "bbc"));
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                getBigramProbability(binaryDictionary, "aaa", "aaa"));

        // Testing bigram link.
        addUnigramWord(binaryDictionary, "abcde", unigramProbability);
        addUnigramWord(binaryDictionary, "fghij", unigramProbability);
        addBigramWords(binaryDictionary, "abcde", "fghij", bigramProbability);
        addUnigramWord(binaryDictionary, "fgh", unigramProbability);
        addUnigramWord(binaryDictionary, "abc", unigramProbability);
        addUnigramWord(binaryDictionary, "f", unigramProbability);

        if (canCheckBigramProbability(formatVersion)) {
            assertEquals(bigramProbability,
                    getBigramProbability(binaryDictionary, "abcde", "fghij"));
        }
        assertEquals(Dictionary.NOT_A_PROBABILITY,
                getBigramProbability(binaryDictionary, "abcde", "fgh"));
        addBigramWords(binaryDictionary, "abcde", "fghij", updatedBigramProbability);
        if (canCheckBigramProbability(formatVersion)) {
            assertEquals(updatedBigramProbability,
                    getBigramProbability(binaryDictionary, "abcde", "fghij"));
        }

        dictFile.delete();
    }

    public void testRandomlyAddBigramWords() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testRandomlyAddBigramWords(formatVersion);
        }
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

        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();

        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            addUnigramWord(binaryDictionary, word, unigramProbability);
        }

        for (int i = 0; i < bigramCount; i++) {
            final String word0 = words.get(random.nextInt(wordCount));
            final String word1 = words.get(random.nextInt(wordCount));
            if (TextUtils.equals(word0, word1)) {
                continue;
            }
            final Pair<String, String> bigram = new Pair<>(word0, word1);
            bigramWords.add(bigram);
            final int unigramProbability = unigramProbabilities.get(word1);
            final int bigramProbability =
                    unigramProbability + random.nextInt(0xFF - unigramProbability);
            bigramProbabilities.put(bigram, bigramProbability);
            addBigramWords(binaryDictionary, word0, word1, bigramProbability);
        }

        for (final Pair<String, String> bigram : bigramWords) {
            final int bigramProbability = bigramProbabilities.get(bigram);
            assertEquals(bigramProbability != Dictionary.NOT_A_PROBABILITY,
                    isValidBigram(binaryDictionary, bigram.first, bigram.second));
            if (canCheckBigramProbability(formatVersion)) {
                assertEquals(bigramProbability,
                        getBigramProbability(binaryDictionary, bigram.first, bigram.second));
            }
        }

        dictFile.delete();
    }

    public void testRemoveBigramWords() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testRemoveBigramWords(formatVersion);
        }
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
        final int bigramProbability = 150;
        addUnigramWord(binaryDictionary, "aaa", unigramProbability);
        addUnigramWord(binaryDictionary, "abb", unigramProbability);
        addUnigramWord(binaryDictionary, "bcc", unigramProbability);
        addBigramWords(binaryDictionary, "aaa", "abb", bigramProbability);
        addBigramWords(binaryDictionary, "aaa", "bcc", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "aaa", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "bcc", bigramProbability);

        assertTrue(isValidBigram(binaryDictionary, "aaa", "abb"));
        assertTrue(isValidBigram(binaryDictionary, "aaa", "bcc"));
        assertTrue(isValidBigram(binaryDictionary, "abb", "aaa"));
        assertTrue(isValidBigram(binaryDictionary, "abb", "bcc"));

        removeBigramEntry(binaryDictionary, "aaa", "abb");
        assertFalse(isValidBigram(binaryDictionary, "aaa", "abb"));
        addBigramWords(binaryDictionary, "aaa", "abb", bigramProbability);
        assertTrue(isValidBigram(binaryDictionary, "aaa", "abb"));


        removeBigramEntry(binaryDictionary, "aaa", "bcc");
        assertFalse(isValidBigram(binaryDictionary, "aaa", "bcc"));
        removeBigramEntry(binaryDictionary, "abb", "aaa");
        assertFalse(isValidBigram(binaryDictionary, "abb", "aaa"));
        removeBigramEntry(binaryDictionary, "abb", "bcc");
        assertFalse(isValidBigram(binaryDictionary, "abb", "bcc"));

        removeBigramEntry(binaryDictionary, "aaa", "abb");
        // Test remove non-existing bigram operation.
        removeBigramEntry(binaryDictionary, "aaa", "abb");
        removeBigramEntry(binaryDictionary, "bcc", "aaa");

        dictFile.delete();
    }

    public void testFlushDictionary() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testFlushDictionary(formatVersion);
        }
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
        addUnigramWord(binaryDictionary, "aaa", probability);
        addUnigramWord(binaryDictionary, "abcd", probability);
        // Close without flushing.
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("aaa"));
        assertEquals(Dictionary.NOT_A_PROBABILITY, binaryDictionary.getFrequency("abcd"));

        addUnigramWord(binaryDictionary, "aaa", probability);
        addUnigramWord(binaryDictionary, "abcd", probability);
        binaryDictionary.flush();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertEquals(probability, binaryDictionary.getFrequency("aaa"));
        assertEquals(probability, binaryDictionary.getFrequency("abcd"));
        addUnigramWord(binaryDictionary, "bcde", probability);
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
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testFlushWithGCDictionary(formatVersion);
        }
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
        final int bigramProbability = 150;
        addUnigramWord(binaryDictionary, "aaa", unigramProbability);
        addUnigramWord(binaryDictionary, "abb", unigramProbability);
        addUnigramWord(binaryDictionary, "bcc", unigramProbability);
        addBigramWords(binaryDictionary, "aaa", "abb", bigramProbability);
        addBigramWords(binaryDictionary, "aaa", "bcc", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "aaa", bigramProbability);
        addBigramWords(binaryDictionary, "abb", "bcc", bigramProbability);
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertEquals(unigramProbability, binaryDictionary.getFrequency("aaa"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("abb"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("bcc"));
        if (canCheckBigramProbability(formatVersion)) {
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "aaa", "abb"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "aaa", "bcc"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "abb", "aaa"));
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "abb", "bcc"));
        }
        assertFalse(isValidBigram(binaryDictionary, "bcc", "aaa"));
        assertFalse(isValidBigram(binaryDictionary, "bcc", "bbc"));
        assertFalse(isValidBigram(binaryDictionary, "aaa", "aaa"));
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        dictFile.delete();
    }

    public void testAddBigramWordsAndFlashWithGC() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddBigramWordsAndFlashWithGC(formatVersion);
        }
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

        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();

        for (int i = 0; i < wordCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            addUnigramWord(binaryDictionary, word, unigramProbability);
        }

        for (int i = 0; i < bigramCount; i++) {
            final String word0 = words.get(random.nextInt(wordCount));
            final String word1 = words.get(random.nextInt(wordCount));
            if (TextUtils.equals(word0, word1)) {
                continue;
            }
            final Pair<String, String> bigram = new Pair<>(word0, word1);
            bigramWords.add(bigram);
            final int unigramProbability = unigramProbabilities.get(word1);
            final int bigramProbability =
                    unigramProbability + random.nextInt(0xFF - unigramProbability);
            bigramProbabilities.put(bigram, bigramProbability);
            addBigramWords(binaryDictionary, word0, word1, bigramProbability);
        }

        binaryDictionary.flushWithGC();
        binaryDictionary.close();
        binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);


        for (final Pair<String, String> bigram : bigramWords) {
            final int bigramProbability = bigramProbabilities.get(bigram);
            assertEquals(bigramProbability != Dictionary.NOT_A_PROBABILITY,
                    isValidBigram(binaryDictionary, bigram.first, bigram.second));
            if (canCheckBigramProbability(formatVersion)) {
                assertEquals(bigramProbability,
                        getBigramProbability(binaryDictionary, bigram.first, bigram.second));
            }
        }

        dictFile.delete();
    }

    public void testRandomOperationsAndFlashWithGC() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testRandomOperationsAndFlashWithGC(formatVersion);
        }
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
        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigramWords = new ArrayList<>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();
        for (int i = 0; i < initialUnigramCount; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            final int unigramProbability = random.nextInt(0xFF);
            unigramProbabilities.put(word, unigramProbability);
            addUnigramWord(binaryDictionary, word, unigramProbability);
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
                    addUnigramWord(binaryDictionary, word, unigramProbability);
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
                    final int unigramProbability = unigramProbabilities.get(word1);
                    final int bigramProbability =
                            unigramProbability + random.nextInt(0xFF - unigramProbability);
                    final Pair<String, String> bigram = new Pair<>(word0, word1);
                    bigramWords.add(bigram);
                    bigramProbabilities.put(bigram, bigramProbability);
                    addBigramWords(binaryDictionary, word0, word1, bigramProbability);
                }
                // Remove bigram.
                if (random.nextFloat() < removeBigramProb && !bigramWords.isEmpty()) {
                    final int bigramIndex = random.nextInt(bigramWords.size());
                    final Pair<String, String> bigram = bigramWords.get(bigramIndex);
                    bigramWords.remove(bigramIndex);
                    bigramProbabilities.remove(bigram);
                    removeBigramEntry(binaryDictionary, bigram.first, bigram.second);
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
                final int probability;
                if (bigramProbabilities.containsKey(bigram)) {
                    final int bigramProbability = bigramProbabilities.get(bigram);
                    probability = bigramProbability;
                } else {
                    probability = Dictionary.NOT_A_PROBABILITY;
                }

                if (canCheckBigramProbability(formatVersion)) {
                    assertEquals(probability,
                            getBigramProbability(binaryDictionary, bigram.first, bigram.second));
                }
                assertEquals(probability != Dictionary.NOT_A_PROBABILITY,
                        isValidBigram(binaryDictionary, bigram.first, bigram.second));
            }
            binaryDictionary.flushWithGC();
            binaryDictionary.close();
        }

        dictFile.delete();
    }

    public void testAddManyUnigramsAndFlushWithGC() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddManyUnigramsAndFlushWithGC(formatVersion);
        }
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

        final ArrayList<String> words = new ArrayList<>();
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
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
                addUnigramWord(binaryDictionary, word, unigramProbability);
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
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testUnigramAndBigramCount(formatVersion);
        }
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

        final ArrayList<String> words = new ArrayList<>();
        final HashSet<Pair<String, String>> bigrams = new HashSet<>();
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
                addUnigramWord(binaryDictionary, word, unigramProbability);
            }
            for (int j = 0; j < bigramCountPerIteration; j++) {
                final String word0 = words.get(random.nextInt(words.size()));
                final String word1 = words.get(random.nextInt(words.size()));
                if (TextUtils.equals(word0, word1)) {
                    continue;
                }
                bigrams.add(new Pair<>(word0, word1));
                final int bigramProbability = random.nextInt(0xF);
                addBigramWords(binaryDictionary, word0, word1, bigramProbability);
            }
            assertEquals(new HashSet<>(words).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTest(BinaryDictionary.UNIGRAM_COUNT_QUERY)));
            assertEquals(new HashSet<>(bigrams).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTest(BinaryDictionary.BIGRAM_COUNT_QUERY)));
            binaryDictionary.flushWithGC();
            assertEquals(new HashSet<>(words).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTest(BinaryDictionary.UNIGRAM_COUNT_QUERY)));
            assertEquals(new HashSet<>(bigrams).size(), Integer.parseInt(
                    binaryDictionary.getPropertyForTest(BinaryDictionary.BIGRAM_COUNT_QUERY)));
            binaryDictionary.close();
        }

        dictFile.delete();
    }

    public void testAddMultipleDictionaryEntries() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddMultipleDictionaryEntries(formatVersion);
        }
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
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();

        final LanguageModelParam[] languageModelParams = new LanguageModelParam[lmParamCount];
        String prevWord = null;
        for (int i = 0; i < languageModelParams.length; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int probability = random.nextInt(0xFF);
            final int bigramProbability = probability + random.nextInt(0xFF - probability);
            unigramProbabilities.put(word, probability);
            if (prevWord == null) {
                languageModelParams[i] = new LanguageModelParam(word, probability,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP);
            } else {
                languageModelParams[i] = new LanguageModelParam(prevWord, word, probability,
                        bigramProbability, BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                bigramProbabilities.put(new Pair<>(prevWord, word),
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
            final int bigramProbability = entry.getValue();
            assertEquals(bigramProbability != Dictionary.NOT_A_PROBABILITY,
                    isValidBigram(binaryDictionary, word0, word1));
            if (canCheckBigramProbability(formatVersion)) {
                assertEquals(bigramProbability,
                        getBigramProbability(binaryDictionary, word0, word1));
            }
        }
    }

    public void testGetWordProperties() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testGetWordProperties(formatVersion);
        }
    }

    private void testGetWordProperties(final int formatVersion) {
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int UNIGRAM_COUNT = 1000;
        final int BIGRAM_COUNT = 1000;
        final int codePointSetSize = 20;
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final WordProperty invalidWordProperty = binaryDictionary.getWordProperty("dummyWord",
                false /* isBeginningOfSentence */);
        assertFalse(invalidWordProperty.isValid());

        final ArrayList<String> words = new ArrayList<>();
        final HashMap<String, Integer> wordProbabilities = new HashMap<>();
        final HashMap<String, HashSet<String>> bigrams = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();

        for (int i = 0; i < UNIGRAM_COUNT; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int unigramProbability = random.nextInt(0xFF);
            final boolean isNotAWord = random.nextBoolean();
            final boolean isBlacklisted = random.nextBoolean();
            // TODO: Add tests for historical info.
            binaryDictionary.addUnigramEntry(word, unigramProbability,
                    null /* shortcutTarget */, BinaryDictionary.NOT_A_PROBABILITY,
                    false /* isBeginningOfSentence */, isNotAWord, isBlacklisted,
                    BinaryDictionary.NOT_A_VALID_TIMESTAMP);
            if (binaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            words.add(word);
            wordProbabilities.put(word, unigramProbability);
            final WordProperty wordProperty = binaryDictionary.getWordProperty(word,
                    false /* isBeginningOfSentence */);
            assertEquals(word, wordProperty.mWord);
            assertTrue(wordProperty.isValid());
            assertEquals(isNotAWord, wordProperty.mIsNotAWord);
            assertEquals(isBlacklisted, wordProperty.mIsBlacklistEntry);
            assertEquals(false, wordProperty.mHasBigrams);
            assertEquals(false, wordProperty.mHasShortcuts);
            assertEquals(unigramProbability, wordProperty.mProbabilityInfo.mProbability);
            assertTrue(wordProperty.mShortcutTargets.isEmpty());
        }

        for (int i = 0; i < BIGRAM_COUNT; i++) {
            final int word0Index = random.nextInt(wordProbabilities.size());
            final int word1Index = random.nextInt(wordProbabilities.size());
            if (word0Index == word1Index) {
                continue;
            }
            final String word0 = words.get(word0Index);
            final String word1 = words.get(word1Index);
            final int unigramProbability = wordProbabilities.get(word1);
            final int bigramProbability =
                    unigramProbability + random.nextInt(0xFF - unigramProbability);
            addBigramWords(binaryDictionary, word0, word1, bigramProbability);
            if (binaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            if (!bigrams.containsKey(word0)) {
                final HashSet<String> bigramWord1s = new HashSet<>();
                bigrams.put(word0, bigramWord1s);
            }
            bigrams.get(word0).add(word1);
            bigramProbabilities.put(new Pair<>(word0, word1), bigramProbability);
        }

        for (int i = 0; i < words.size(); i++) {
            final String word0 = words.get(i);
            if (!bigrams.containsKey(word0)) {
                continue;
            }
            final HashSet<String> bigramWord1s = bigrams.get(word0);
            final WordProperty wordProperty = binaryDictionary.getWordProperty(word0,
                    false /* isBeginningOfSentence */);
            assertEquals(bigramWord1s.size(), wordProperty.mBigrams.size());
            for (int j = 0; j < wordProperty.mBigrams.size(); j++) {
                final String word1 = wordProperty.mBigrams.get(j).mWord;
                assertTrue(bigramWord1s.contains(word1));
                if (canCheckBigramProbability(formatVersion)) {
                    final int bigramProbability = bigramProbabilities.get(new Pair<>(word0, word1));
                    assertEquals(bigramProbability, wordProperty.mBigrams.get(j).getProbability());
                }
            }
        }
    }

    public void testIterateAllWords() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testIterateAllWords(formatVersion);
        }
    }

    private void testIterateAllWords(final int formatVersion) {
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int UNIGRAM_COUNT = 1000;
        final int BIGRAM_COUNT = 1000;
        final int codePointSetSize = 20;
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final WordProperty invalidWordProperty = binaryDictionary.getWordProperty("dummyWord",
                false /* isBeginningOfSentence */);
        assertFalse(invalidWordProperty.isValid());

        final ArrayList<String> words = new ArrayList<>();
        final HashMap<String, Integer> wordProbabilitiesToCheckLater = new HashMap<>();
        final HashMap<String, HashSet<String>> bigrams = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilitiesToCheckLater =
                new HashMap<>();

        for (int i = 0; i < UNIGRAM_COUNT; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int unigramProbability = random.nextInt(0xFF);
            addUnigramWord(binaryDictionary, word, unigramProbability);
            if (binaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            words.add(word);
            wordProbabilitiesToCheckLater.put(word, unigramProbability);
        }

        for (int i = 0; i < BIGRAM_COUNT; i++) {
            final int word0Index = random.nextInt(wordProbabilitiesToCheckLater.size());
            final int word1Index = random.nextInt(wordProbabilitiesToCheckLater.size());
            if (word0Index == word1Index) {
                continue;
            }
            final String word0 = words.get(word0Index);
            final String word1 = words.get(word1Index);
            final int unigramProbability = wordProbabilitiesToCheckLater.get(word1);
            final int bigramProbability =
                    unigramProbability + random.nextInt(0xFF - unigramProbability);
            addBigramWords(binaryDictionary, word0, word1, bigramProbability);
            if (binaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            if (!bigrams.containsKey(word0)) {
                final HashSet<String> bigramWord1s = new HashSet<>();
                bigrams.put(word0, bigramWord1s);
            }
            bigrams.get(word0).add(word1);
            bigramProbabilitiesToCheckLater.put(new Pair<>(word0, word1), bigramProbability);
        }

        final HashSet<String> wordSet = new HashSet<>(words);
        final HashSet<Pair<String, String>> bigramSet =
                new HashSet<>(bigramProbabilitiesToCheckLater.keySet());
        int token = 0;
        do {
            final BinaryDictionary.GetNextWordPropertyResult result =
                    binaryDictionary.getNextWordProperty(token);
            final WordProperty wordProperty = result.mWordProperty;
            final String word0 = wordProperty.mWord;
            assertEquals((int)wordProbabilitiesToCheckLater.get(word0),
                    wordProperty.mProbabilityInfo.mProbability);
            wordSet.remove(word0);
            final HashSet<String> bigramWord1s = bigrams.get(word0);
            for (int j = 0; j < wordProperty.mBigrams.size(); j++) {
                final String word1 = wordProperty.mBigrams.get(j).mWord;
                assertTrue(bigramWord1s.contains(word1));
                final Pair<String, String> bigram = new Pair<>(word0, word1);
                if (canCheckBigramProbability(formatVersion)) {
                    final int bigramProbability = bigramProbabilitiesToCheckLater.get(bigram);
                    assertEquals(bigramProbability, wordProperty.mBigrams.get(j).getProbability());
                }
                bigramSet.remove(bigram);
            }
            token = result.mNextToken;
        } while (token != 0);
        assertTrue(wordSet.isEmpty());
        assertTrue(bigramSet.isEmpty());
    }

    public void testAddShortcuts() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddShortcuts(formatVersion);
        }
    }

    private void testAddShortcuts(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final int unigramProbability = 100;
        final int shortcutProbability = 10;
        binaryDictionary.addUnigramEntry("aaa", unigramProbability, "zzz",
                shortcutProbability, false /* isBeginningOfSentence */,
                false /* isNotAWord */, false /* isBlacklisted */, 0 /* timestamp */);
        WordProperty wordProperty = binaryDictionary.getWordProperty("aaa",
                false /* isBeginningOfSentence */);
        assertEquals(1, wordProperty.mShortcutTargets.size());
        assertEquals("zzz", wordProperty.mShortcutTargets.get(0).mWord);
        assertEquals(shortcutProbability, wordProperty.mShortcutTargets.get(0).getProbability());
        final int updatedShortcutProbability = 2;
        binaryDictionary.addUnigramEntry("aaa", unigramProbability, "zzz",
                updatedShortcutProbability, false /* isBeginningOfSentence */,
                false /* isNotAWord */, false /* isBlacklisted */, 0 /* timestamp */);
        wordProperty = binaryDictionary.getWordProperty("aaa",
                false /* isBeginningOfSentence */);
        assertEquals(1, wordProperty.mShortcutTargets.size());
        assertEquals("zzz", wordProperty.mShortcutTargets.get(0).mWord);
        assertEquals(updatedShortcutProbability,
                wordProperty.mShortcutTargets.get(0).getProbability());
        binaryDictionary.addUnigramEntry("aaa", unigramProbability, "yyy",
                shortcutProbability, false /* isBeginningOfSentence */, false /* isNotAWord */,
                false /* isBlacklisted */, 0 /* timestamp */);
        final HashMap<String, Integer> shortcutTargets = new HashMap<>();
        shortcutTargets.put("zzz", updatedShortcutProbability);
        shortcutTargets.put("yyy", shortcutProbability);
        wordProperty = binaryDictionary.getWordProperty("aaa",
                false /* isBeginningOfSentence */);
        assertEquals(2, wordProperty.mShortcutTargets.size());
        for (WeightedString shortcutTarget : wordProperty.mShortcutTargets) {
            assertTrue(shortcutTargets.containsKey(shortcutTarget.mWord));
            assertEquals((int)shortcutTargets.get(shortcutTarget.mWord),
                    shortcutTarget.getProbability());
            shortcutTargets.remove(shortcutTarget.mWord);
        }
        shortcutTargets.put("zzz", updatedShortcutProbability);
        shortcutTargets.put("yyy", shortcutProbability);
        binaryDictionary.flushWithGC();
        wordProperty = binaryDictionary.getWordProperty("aaa",
                false /* isBeginningOfSentence */);
        assertEquals(2, wordProperty.mShortcutTargets.size());
        for (WeightedString shortcutTarget : wordProperty.mShortcutTargets) {
            assertTrue(shortcutTargets.containsKey(shortcutTarget.mWord));
            assertEquals((int)shortcutTargets.get(shortcutTarget.mWord),
                    shortcutTarget.getProbability());
            shortcutTargets.remove(shortcutTarget.mWord);
        }
    }

    public void testAddManyShortcuts() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddManyShortcuts(formatVersion);
        }
    }

    private void testAddManyShortcuts(final int formatVersion) {
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int UNIGRAM_COUNT = 1000;
        final int SHORTCUT_COUNT = 10000;
        final int codePointSetSize = 20;
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        final ArrayList<String> words = new ArrayList<>();
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<String, HashMap<String, Integer>> shortcutTargets = new HashMap<>();

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        for (int i = 0; i < UNIGRAM_COUNT; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int unigramProbability = random.nextInt(0xFF);
            addUnigramWord(binaryDictionary, word, unigramProbability);
            words.add(word);
            unigramProbabilities.put(word, unigramProbability);
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
        }
        for (int i = 0; i < SHORTCUT_COUNT; i++) {
            final String shortcutTarget = CodePointUtils.generateWord(random, codePointSet);
            final int shortcutProbability = random.nextInt(0xF);
            final String word = words.get(random.nextInt(words.size()));
            final int unigramProbability = unigramProbabilities.get(word);
            binaryDictionary.addUnigramEntry(word, unigramProbability, shortcutTarget,
                    shortcutProbability, false /* isBeginningOfSentence */, false /* isNotAWord */,
                    false /* isBlacklisted */, 0 /* timestamp */);
            if (shortcutTargets.containsKey(word)) {
                final HashMap<String, Integer> shortcutTargetsOfWord = shortcutTargets.get(word);
                shortcutTargetsOfWord.put(shortcutTarget, shortcutProbability);
            } else {
                final HashMap<String, Integer> shortcutTargetsOfWord = new HashMap<>();
                shortcutTargetsOfWord.put(shortcutTarget, shortcutProbability);
                shortcutTargets.put(word, shortcutTargetsOfWord);
            }
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
        }

        for (final String word : words) {
            final WordProperty wordProperty = binaryDictionary.getWordProperty(word,
                    false /* isBeginningOfSentence */);
            assertEquals((int)unigramProbabilities.get(word),
                    wordProperty.mProbabilityInfo.mProbability);
            if (!shortcutTargets.containsKey(word)) {
                // The word does not have shortcut targets.
                continue;
            }
            assertEquals(shortcutTargets.get(word).size(), wordProperty.mShortcutTargets.size());
            for (final WeightedString shortcutTarget : wordProperty.mShortcutTargets) {
                final String targetCodePonts = shortcutTarget.mWord;
                assertEquals((int)shortcutTargets.get(word).get(targetCodePonts),
                        shortcutTarget.getProbability());
            }
        }
    }

    public void testDictMigration() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testDictMigration(FormatSpec.VERSION4_ONLY_FOR_TESTING, formatVersion);
        }
    }

    private void testDictMigration(final int fromFormatVersion, final int toFormatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", fromFormatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final int unigramProbability = 100;
        addUnigramWord(binaryDictionary, "aaa", unigramProbability);
        addUnigramWord(binaryDictionary, "bbb", unigramProbability);
        final int bigramProbability = 150;
        addBigramWords(binaryDictionary, "aaa", "bbb", bigramProbability);
        final int shortcutProbability = 10;
        binaryDictionary.addUnigramEntry("ccc", unigramProbability, "xxx", shortcutProbability,
                false /* isBeginningOfSentence */, false /* isNotAWord */,
                false /* isBlacklisted */, 0 /* timestamp */);
        binaryDictionary.addUnigramEntry("ddd", unigramProbability, null /* shortcutTarget */,
                Dictionary.NOT_A_PROBABILITY, false /* isBeginningOfSentence */,
                true /* isNotAWord */, true /* isBlacklisted */, 0 /* timestamp */);
        binaryDictionary.addNgramEntry(PrevWordsInfo.BEGINNING_OF_SENTENCE,
                "aaa", bigramProbability, 0 /* timestamp */);
        assertEquals(unigramProbability, binaryDictionary.getFrequency("aaa"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("bbb"));
        assertTrue(isValidBigram(binaryDictionary, "aaa", "bbb"));
        assertEquals(fromFormatVersion, binaryDictionary.getFormatVersion());
        assertTrue(binaryDictionary.migrateTo(toFormatVersion));
        assertTrue(binaryDictionary.isValidDictionary());
        assertEquals(toFormatVersion, binaryDictionary.getFormatVersion());
        assertEquals(unigramProbability, binaryDictionary.getFrequency("aaa"));
        assertEquals(unigramProbability, binaryDictionary.getFrequency("bbb"));
        if (canCheckBigramProbability(toFormatVersion)) {
            assertEquals(bigramProbability, getBigramProbability(binaryDictionary, "aaa", "bbb"));
            assertEquals(bigramProbability, binaryDictionary.getNgramProbability(
                    PrevWordsInfo.BEGINNING_OF_SENTENCE, "aaa"));
        }
        assertTrue(isValidBigram(binaryDictionary, "aaa", "bbb"));
        WordProperty wordProperty = binaryDictionary.getWordProperty("ccc",
                false /* isBeginningOfSentence */);
        assertEquals(1, wordProperty.mShortcutTargets.size());
        assertEquals("xxx", wordProperty.mShortcutTargets.get(0).mWord);
        wordProperty = binaryDictionary.getWordProperty("ddd",
                false /* isBeginningOfSentence */);
        assertTrue(wordProperty.mIsBlacklistEntry);
        assertTrue(wordProperty.mIsNotAWord);
    }

    public void testLargeDictMigration() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testLargeDictMigration(FormatSpec.VERSION4_ONLY_FOR_TESTING, formatVersion);
        }
    }

    private void testLargeDictMigration(final int fromFormatVersion, final int toFormatVersion) {
        final int UNIGRAM_COUNT = 3000;
        final int BIGRAM_COUNT = 3000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", fromFormatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigrams = new ArrayList<>();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final HashMap<String, Integer> unigramProbabilities = new HashMap<>();
        final HashMap<Pair<String, String>, Integer> bigramProbabilities = new HashMap<>();

        for (int i = 0; i < UNIGRAM_COUNT; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            final int unigramProbability = random.nextInt(0xFF);
            addUnigramWord(binaryDictionary, word, unigramProbability);
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            words.add(word);
            unigramProbabilities.put(word, unigramProbability);
        }

        for (int i = 0; i < BIGRAM_COUNT; i++) {
            final int word0Index = random.nextInt(words.size());
            final int word1Index = random.nextInt(words.size());
            if (word0Index == word1Index) {
                continue;
            }
            final String word0 = words.get(word0Index);
            final String word1 = words.get(word1Index);
            final int unigramProbability = unigramProbabilities.get(word1);
            final int bigramProbability =
                    random.nextInt(0xFF - unigramProbability) + unigramProbability;
            addBigramWords(binaryDictionary, word0, word1, bigramProbability);
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDictionary.flushWithGC();
            }
            final Pair<String, String> bigram = new Pair<>(word0, word1);
            bigrams.add(bigram);
            bigramProbabilities.put(bigram, bigramProbability);
        }
        assertTrue(binaryDictionary.migrateTo(toFormatVersion));

        for (final String word : words) {
            assertEquals((int)unigramProbabilities.get(word), binaryDictionary.getFrequency(word));
        }
        assertEquals(unigramProbabilities.size(), Integer.parseInt(
                binaryDictionary.getPropertyForTest(BinaryDictionary.UNIGRAM_COUNT_QUERY)));

        for (final Pair<String, String> bigram : bigrams) {
            if (canCheckBigramProbability(toFormatVersion)) {
                assertEquals((int)bigramProbabilities.get(bigram),
                        getBigramProbability(binaryDictionary, bigram.first, bigram.second));
            }
            assertTrue(isValidBigram(binaryDictionary, bigram.first, bigram.second));
        }
        assertEquals(bigramProbabilities.size(), Integer.parseInt(
                binaryDictionary.getPropertyForTest(BinaryDictionary.BIGRAM_COUNT_QUERY)));
    }

    public void testBeginningOfSentence() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            if (supportsBeginningOfSentence(formatVersion)) {
                testBeginningOfSentence(formatVersion);
            }
        }
    }

    private void testBeginningOfSentence(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        final int dummyProbability = 0;
        final PrevWordsInfo prevWordsInfoBeginningOfSentence = PrevWordsInfo.BEGINNING_OF_SENTENCE;
        final int bigramProbability = 200;
        addUnigramWord(binaryDictionary, "aaa", dummyProbability);
        binaryDictionary.addNgramEntry(prevWordsInfoBeginningOfSentence, "aaa", bigramProbability,
                BinaryDictionary.NOT_A_VALID_TIMESTAMP /* timestamp */);
        assertEquals(bigramProbability,
                binaryDictionary.getNgramProbability(prevWordsInfoBeginningOfSentence, "aaa"));
        binaryDictionary.addNgramEntry(prevWordsInfoBeginningOfSentence, "aaa", bigramProbability,
                BinaryDictionary.NOT_A_VALID_TIMESTAMP /* timestamp */);
        addUnigramWord(binaryDictionary, "bbb", dummyProbability);
        binaryDictionary.addNgramEntry(prevWordsInfoBeginningOfSentence, "bbb", bigramProbability,
                BinaryDictionary.NOT_A_VALID_TIMESTAMP /* timestamp */);
        binaryDictionary.flushWithGC();
        assertEquals(bigramProbability,
                binaryDictionary.getNgramProbability(prevWordsInfoBeginningOfSentence, "aaa"));
        assertEquals(bigramProbability,
                binaryDictionary.getNgramProbability(prevWordsInfoBeginningOfSentence, "bbb"));
    }

    public void testGetMaxFrequencyOfExactMatches() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testGetMaxFrequencyOfExactMatches(formatVersion);
        }
    }

    private void testGetMaxFrequencyOfExactMatches(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        addUnigramWord(binaryDictionary, "abc", 10);
        addUnigramWord(binaryDictionary, "aBc", 15);
        assertEquals(15, binaryDictionary.getMaxFrequencyOfExactMatches("abc"));
        addUnigramWord(binaryDictionary, "ab'c", 20);
        assertEquals(20, binaryDictionary.getMaxFrequencyOfExactMatches("abc"));
        addUnigramWord(binaryDictionary, "a-b-c", 25);
        assertEquals(25, binaryDictionary.getMaxFrequencyOfExactMatches("abc"));
        addUnigramWord(binaryDictionary, "ab-'-'-'-c", 30);
        assertEquals(30, binaryDictionary.getMaxFrequencyOfExactMatches("abc"));
        addUnigramWord(binaryDictionary, "ab c", 255);
        assertEquals(30, binaryDictionary.getMaxFrequencyOfExactMatches("abc"));
    }
}
