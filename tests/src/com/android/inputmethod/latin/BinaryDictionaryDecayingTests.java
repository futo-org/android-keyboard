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

import com.android.inputmethod.latin.makedict.FormatSpec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@LargeTest
public class BinaryDictionaryDecayingTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";

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
        binaryDictionary.flushWithGC();
    }

    private void forcePassingLongTime(final BinaryDictionary binaryDictionary) {
        // Currently, probabilities are decayed when GC is run. All entries that have never been
        // typed in 32 GCs are removed.
        final int count = 32;
        for (int i = 0; i < count; i++) {
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

        binaryDictionary.close();
        dictFile.delete();
    }

    // TODO: Add large tests.
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
}
