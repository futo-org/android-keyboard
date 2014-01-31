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
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@LargeTest
public class BinaryDictionaryDecayingTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";
    private static final int DUMMY_PROBABILITY = 0;

    private int mCurrentTime = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCurrentTime = 0;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        stopTestModeInNativeCode();
    }

    private void addUnigramWord(final BinaryDictionary binaryDictionary, final String word,
            final int probability) {
        binaryDictionary.addUnigramWord(word, probability, "" /* shortcutTarget */,
                BinaryDictionary.NOT_A_PROBABILITY /* shortcutProbability */,
                false /* isNotAWord */, false /* isBlacklisted */,
                mCurrentTime /* timestamp */);
    }

    private void addBigramWords(final BinaryDictionary binaryDictionary, final String word0,
            final String word1, final int probability) {
        binaryDictionary.addBigramWords(word0, word1, probability,
                mCurrentTime /* timestamp */);
    }

    private void forcePassingShortTime(final BinaryDictionary binaryDictionary) {
        // 4 days.
        final int timeToElapse = (int)TimeUnit.SECONDS.convert(4, TimeUnit.DAYS);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
        binaryDictionary.flushWithGC();
    }

    private void forcePassingLongTime(final BinaryDictionary binaryDictionary) {
        // 60 days.
        final int timeToElapse = (int)TimeUnit.SECONDS.convert(60, TimeUnit.DAYS);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
        binaryDictionary.flushWithGC();
    }

    private File createEmptyDictionaryAndGetFile(final String dictId,
            final int formatVersion) throws IOException {
        if (formatVersion == FormatSpec.VERSION4) {
            return createEmptyVer4DictionaryAndGetFile(dictId);
        } else {
            throw new IOException("Dictionary format version " + formatVersion
                    + " is not supported.");
        }
    }

    private File createEmptyVer4DictionaryAndGetFile(final String dictId) throws IOException {
        final File file = File.createTempFile(dictId, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        FileUtils.deleteRecursively(file);
        Map<String, String> attributeMap = new HashMap<String, String>();
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_ID_KEY, dictId);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_LOCALE_KEY, dictId);
        attributeMap.put(FormatSpec.FileHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        attributeMap.put(FormatSpec.FileHeader.USES_FORGETTING_CURVE_KEY,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(FormatSpec.FileHeader.HAS_HISTORICAL_INFO_KEY,
                FormatSpec.FileHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionary.createEmptyDictFile(file.getAbsolutePath(),
                FormatSpec.VERSION4, attributeMap)) {
            return file;
        } else {
            throw new IOException("Empty dictionary " + file.getAbsolutePath()
                    + " cannot be created.");
        }
    }

    private static int setCurrentTimeForTestMode(final int currentTime) {
        return BinaryDictionary.setCurrentTimeForTest(currentTime);
    }

    private static int stopTestModeInNativeCode() {
        return BinaryDictionary.setCurrentTimeForTest(-1);
    }

    public void testReadDictInJavaSide() {
        testReadDictInJavaSide(FormatSpec.VERSION4);
    }

    private void testReadDictInJavaSide(final int formatVersion) {
        setCurrentTimeForTestMode(mCurrentTime);
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "ab", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "aaa", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "aaa", DUMMY_PROBABILITY);
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        final DictDecoder dictDecoder = FormatSpec.getDictDecoder(dictFile);
        try {
            final FusionDictionary dict = dictDecoder.readDictionaryBinary(null,
                    false /* deleteDictIfBroken */);
            PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, "a");
            assertNotNull(ptNode);
            assertTrue(ptNode.isTerminal());
            assertNotNull(ptNode.getBigram("aaa"));
            ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, "ab");
            assertNotNull(ptNode);
            assertTrue(ptNode.isTerminal());
            ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, "aaa");
            assertNotNull(ptNode);
            assertTrue(ptNode.isTerminal());
        } catch (IOException e) {
            fail("IOException while reading dictionary: " + e);
        } catch (UnsupportedFormatException e) {
            fail("Unsupported format: " + e);
        }
        dictFile.delete();
    }

    public void testControlCurrentTime() {
        testControlCurrentTime(FormatSpec.VERSION4);
    }

    private void testControlCurrentTime(final int formatVersion) {
        final int TEST_COUNT = 1000;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int startTime = stopTestModeInNativeCode();
        for (int i = 0; i < TEST_COUNT; i++) {
            final int currentTime = random.nextInt(Integer.MAX_VALUE);
            final int currentTimeInNativeCode = setCurrentTimeForTestMode(currentTime);
            assertEquals(currentTime, currentTimeInNativeCode);
        }
        final int endTime = stopTestModeInNativeCode();
        final int MAX_ALLOWED_ELAPSED_TIME = 10;
        assertTrue(startTime <= endTime && endTime <= startTime + MAX_ALLOWED_ELAPSED_TIME);
    }

    public void testAddValidAndInvalidWords() {
        testAddValidAndInvalidWords(FormatSpec.VERSION4);
    }

    private void testAddValidAndInvalidWords(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        addUnigramWord(binaryDictionary, "a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        addUnigramWord(binaryDictionary, "a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        addUnigramWord(binaryDictionary, "a", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidWord("a"));
        addUnigramWord(binaryDictionary, "a", Dictionary.NOT_A_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("a"));

        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("b"));

        addBigramWords(binaryDictionary, "a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        addBigramWords(binaryDictionary, "a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        addBigramWords(binaryDictionary, "a", "b", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));
        addBigramWords(binaryDictionary, "a", "b", Dictionary.NOT_A_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));

        addUnigramWord(binaryDictionary, "c", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "c", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "c"));

        // Add bigrams of not valid unigrams.
        addBigramWords(binaryDictionary, "x", "y", Dictionary.NOT_A_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("x", "y"));
        addBigramWords(binaryDictionary, "x", "y", DUMMY_PROBABILITY);
        assertFalse(binaryDictionary.isValidBigram("x", "y"));

        binaryDictionary.close();
        dictFile.delete();
    }

    public void testDecayingProbability() {
        testDecayingProbability(FormatSpec.VERSION4);
    }

    private void testDecayingProbability(final int formatVersion) {
        File dictFile = null;
        try {
            dictFile = createEmptyDictionaryAndGetFile("TestBinaryDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);

        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingShortTime(binaryDictionary);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));

        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "a", DUMMY_PROBABILITY);
        addUnigramWord(binaryDictionary, "b", DUMMY_PROBABILITY);
        addBigramWords(binaryDictionary, "a", "b", DUMMY_PROBABILITY);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertTrue(binaryDictionary.isValidBigram("a", "b"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidBigram("a", "b"));

        binaryDictionary.close();
        dictFile.delete();
    }

    public void testAddManyUnigramsToDecayingDict() {
        testAddManyUnigramsToDecayingDict(FormatSpec.VERSION4);
    }

    private void testAddManyUnigramsToDecayingDict(final int formatVersion) {
        final int unigramCount = 30000;
        final int unigramTypedCount = 100000;
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
        setCurrentTimeForTestMode(mCurrentTime);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> words = new ArrayList<String>();

        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
        }

        final int maxUnigramCount = Integer.parseInt(
                binaryDictionary.getPropertyForTest(BinaryDictionary.MAX_UNIGRAM_COUNT_QUERY));
        for (int i = 0; i < unigramTypedCount; i++) {
            final String word = words.get(random.nextInt(words.size()));
            addUnigramWord(binaryDictionary, word, DUMMY_PROBABILITY);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int unigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    forcePassingShortTime(binaryDictionary);
                }
                final int unigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(unigramCountBeforeGC > unigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) <= maxUnigramCount);
        forcePassingLongTime(binaryDictionary);
        assertEquals(0, Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)));
    }

    public void testOverflowUnigrams() {
        testOverflowUnigrams(FormatSpec.VERSION4);
    }

    private void testOverflowUnigrams(final int formatVersion) {
        final int unigramCount = 20000;
        final int eachUnigramTypedCount = 5;
        final int strongUnigramTypedCount = 20;
        final int weakUnigramTypedCount = 1;
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
        setCurrentTimeForTestMode(mCurrentTime);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        final String strong = "strong";
        final String weak = "weak";
        for (int j = 0; j < strongUnigramTypedCount; j++) {
            addUnigramWord(binaryDictionary, strong, DUMMY_PROBABILITY);
        }
        for (int j = 0; j < weakUnigramTypedCount; j++) {
            addUnigramWord(binaryDictionary, weak, DUMMY_PROBABILITY);
        }
        assertTrue(binaryDictionary.isValidWord(strong));
        assertTrue(binaryDictionary.isValidWord(weak));

        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            for (int j = 0; j < eachUnigramTypedCount; j++) {
                addUnigramWord(binaryDictionary, word, DUMMY_PROBABILITY);
            }
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int unigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(binaryDictionary.isValidWord(strong));
                assertTrue(binaryDictionary.isValidWord(weak));
                binaryDictionary.flushWithGC();
                final int unigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(unigramCountBeforeGC > unigramCountAfterGC);
                assertFalse(binaryDictionary.isValidWord(weak));
                assertTrue(binaryDictionary.isValidWord(strong));
                break;
            }
        }
    }

    public void testAddManyBigramsToDecayingDict() {
        testAddManyBigramsToDecayingDict(FormatSpec.VERSION4);
    }

    private void testAddManyBigramsToDecayingDict(final int formatVersion) {
        final int unigramCount = 5000;
        final int bigramCount = 30000;
        final int bigramTypedCount = 100000;
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
        setCurrentTimeForTestMode(mCurrentTime);

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
                binaryDictionary.getPropertyForTest(BinaryDictionary.MAX_BIGRAM_COUNT_QUERY));
        for (int i = 0; i < bigramTypedCount; ++i) {
            final Pair<String, String> bigram = bigrams.get(random.nextInt(bigrams.size()));
            addUnigramWord(binaryDictionary, bigram.first, DUMMY_PROBABILITY);
            addUnigramWord(binaryDictionary, bigram.second, DUMMY_PROBABILITY);
            addBigramWords(binaryDictionary, bigram.first, bigram.second, DUMMY_PROBABILITY);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int bigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    forcePassingShortTime(binaryDictionary);
                }
                final int bigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                assertTrue(bigramCountBeforeGC > bigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) <= maxBigramCount);
        forcePassingLongTime(binaryDictionary);
        assertEquals(0, Integer.parseInt(binaryDictionary.getPropertyForTest(
                BinaryDictionary.BIGRAM_COUNT_QUERY)));
    }

    public void testOverflowBigrams() {
        testOverflowBigrams(FormatSpec.VERSION4);
    }

    private void testOverflowBigrams(final int formatVersion) {
        final int bigramCount = 20000;
        final int unigramCount = 1000;
        final int unigramTypedCount = 20;
        final int eachBigramTypedCount = 5;
        final int strongBigramTypedCount = 20;
        final int weakBigramTypedCount = 1;
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
        setCurrentTimeForTestMode(mCurrentTime);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        final ArrayList<String> words = new ArrayList<String>();
        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            for (int j = 0; j < unigramTypedCount; j++) {
                addUnigramWord(binaryDictionary, word, DUMMY_PROBABILITY);
            }
        }
        final String strong = "strong";
        final String weak = "weak";
        final String target = "target";
        for (int j = 0; j < unigramTypedCount; j++) {
            addUnigramWord(binaryDictionary, strong, DUMMY_PROBABILITY);
            addUnigramWord(binaryDictionary, weak, DUMMY_PROBABILITY);
            addUnigramWord(binaryDictionary, target, DUMMY_PROBABILITY);
        }
        binaryDictionary.flushWithGC();
        for (int j = 0; j < strongBigramTypedCount; j++) {
            addBigramWords(binaryDictionary, strong, target, DUMMY_PROBABILITY);
        }
        for (int j = 0; j < weakBigramTypedCount; j++) {
            addBigramWords(binaryDictionary, weak, target, DUMMY_PROBABILITY);
        }
        assertTrue(binaryDictionary.isValidBigram(strong, target));
        assertTrue(binaryDictionary.isValidBigram(weak, target));

        for (int i = 0; i < bigramCount; i++) {
            final int word0Index = random.nextInt(words.size());
            final String word0 = words.get(word0Index);
            final int index = random.nextInt(words.size() - 1);
            final int word1Index = (index >= word0Index) ? index + 1 : index;
            final String word1 = words.get(word1Index);

            for (int j = 0; j < eachBigramTypedCount; j++) {
                addBigramWords(binaryDictionary, word0, word1, DUMMY_PROBABILITY);
            }
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int bigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                binaryDictionary.flushWithGC();
                final int bigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForTest(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                assertTrue(bigramCountBeforeGC > bigramCountAfterGC);
                assertTrue(binaryDictionary.isValidBigram(strong, target));
                assertFalse(binaryDictionary.isValidBigram(weak, target));
                break;
            }
        }
    }
}
