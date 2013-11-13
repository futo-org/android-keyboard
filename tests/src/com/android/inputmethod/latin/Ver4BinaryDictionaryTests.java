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
import android.util.Log;

import com.android.inputmethod.latin.makedict.BinaryDictEncoderUtils;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver4DictEncoder;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

// TODO: Add a test to evaluate the speed of operations of Ver4 dictionary.
@LargeTest
public class Ver4BinaryDictionaryTests extends AndroidTestCase {
    private static final String TAG = Ver4BinaryDictionaryTests.class.getSimpleName();
    private static final String TEST_LOCALE = "test";
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(4, true /* supportsDynamicUpdate */);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // TODO: remove after native code support dictionary creation.
    private DictionaryOptions getDictionaryOptions(final String id, final String version) {
        final DictionaryOptions options = new DictionaryOptions(new HashMap<String, String>(),
                false /* germanUmlautProcessing */, false /* frenchLigatureProcessing */);
        options.mAttributes.put("version", version);
        options.mAttributes.put("dictionary", id);
        return options;
    }

    // TODO: remove after native code support dictionary creation.
    private File getTrieFile(final String id, final String version) {
        return new File(getContext().getCacheDir() + "/" + id + "." + version,
                TEST_LOCALE + "." + version + FormatSpec.TRIE_FILE_EXTENSION);
    }

    public void testIsValidDictionary() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);

        BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertFalse(binaryDictionary.isValidDictionary());

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));
        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }

        binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
    }

    // TODO: Add large tests.
    public void testReadProbability() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));

        final int frequency = 100;
        dict.add("a", frequency, null, false /* isNotAWord */);
        dict.add("aaa", frequency, null, false /* isNotAWord */);
        dict.add("ab", frequency, null, false /* isNotAWord */);

        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
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
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));

        final int unigramFrequency = 1;
        final int bigramFrequency0 = 150;
        final int bigramFrequency1 = 1;
        final int bigramFrequency2 = 255;
        dict.add("a", unigramFrequency, null, false /* isNotAWord */);
        dict.add("aaa", unigramFrequency, null, false /* isNotAWord */);
        dict.add("ab", unigramFrequency, null, false /* isNotAWord */);
        dict.setBigram("a", "aaa", bigramFrequency0);
        dict.setBigram("a", "ab", bigramFrequency1);
        dict.setBigram("aaa", "ab", bigramFrequency2);

        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        assertTrue(binaryDictionary.isValidDictionary());

        assertEquals(getCalculatedBigramProbabiliy(binaryDictionary, unigramFrequency,
                bigramFrequency0), binaryDictionary.getBigramProbability("a", "aaa"));
        assertEquals(getCalculatedBigramProbabiliy(binaryDictionary, unigramFrequency,
                bigramFrequency1), binaryDictionary.getBigramProbability("a", "ab"));
        assertEquals(getCalculatedBigramProbabiliy(binaryDictionary, unigramFrequency,
                bigramFrequency2), binaryDictionary.getBigramProbability("aaa", "ab"));

        assertFalse(binaryDictionary.isValidBigram("aaa", "a"));
        assertFalse(binaryDictionary.isValidBigram("ab", "a"));
        assertFalse(binaryDictionary.isValidBigram("ab", "aaa"));
    }

    // TODO: Add large tests.
    public void testWriteUnigrams() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));
        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());

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
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));
        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());

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
    }

    public void testRemoveBigramWords() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));
        final DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());

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

}
