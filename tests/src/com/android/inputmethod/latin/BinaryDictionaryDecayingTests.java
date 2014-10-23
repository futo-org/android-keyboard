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

import com.android.inputmethod.latin.NgramContext.WordInfo;
import com.android.inputmethod.latin.common.CodePointUtils;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.WordInputEventForPersonalization;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@LargeTest
public class BinaryDictionaryDecayingTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";
    private static final int DUMMY_PROBABILITY = 0;
    private static final int[] DICT_FORMAT_VERSIONS =
            new int[] { FormatSpec.VERSION4, FormatSpec.VERSION4_DEV };
    private static final String DICTIONARY_ID = "TestDecayingBinaryDictionary";

    private int mCurrentTime = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCurrentTime = 0;
        mDictFilesToBeDeleted.clear();
    }

    @Override
    protected void tearDown() throws Exception {
        for (final File dictFile : mDictFilesToBeDeleted) {
            dictFile.delete();
        }
        mDictFilesToBeDeleted.clear();
        stopTestModeInNativeCode();
        super.tearDown();
    }

    private static boolean supportsBeginningOfSentence(final int formatVersion) {
        return formatVersion > FormatSpec.VERSION401;
    }

    private static boolean supportsNgram(final int formatVersion) {
        return formatVersion >= FormatSpec.VERSION4_DEV;
    }

    private void onInputWord(final BinaryDictionary binaryDictionary, final String word,
            final boolean isValidWord) {
        binaryDictionary.updateEntriesForWordWithNgramContext(NgramContext.EMPTY_PREV_WORDS_INFO,
                word, isValidWord, 1 /* count */, mCurrentTime /* timestamp */);
    }

    private void onInputWordWithPrevWord(final BinaryDictionary binaryDictionary, final String word,
            final boolean isValidWord, final String prevWord) {
        binaryDictionary.updateEntriesForWordWithNgramContext(
                new NgramContext(new WordInfo(prevWord)), word, isValidWord, 1 /* count */,
                mCurrentTime /* timestamp */);
    }

    private void onInputWordWithPrevWords(final BinaryDictionary binaryDictionary,
            final String word, final boolean isValidWord, final String prevWord,
            final String prevPrevWord) {
        binaryDictionary.updateEntriesForWordWithNgramContext(
                new NgramContext(new WordInfo(prevWord), new WordInfo(prevPrevWord)), word,
                isValidWord, 1 /* count */, mCurrentTime /* timestamp */);
    }

    private void onInputWordWithBeginningOfSentenceContext(
            final BinaryDictionary binaryDictionary, final String word, final boolean isValidWord) {
        binaryDictionary.updateEntriesForWordWithNgramContext(NgramContext.BEGINNING_OF_SENTENCE,
                word, isValidWord, 1 /* count */, mCurrentTime /* timestamp */);
    }

    private static boolean isValidBigram(final BinaryDictionary binaryDictionary,
            final String word0, final String word1) {
        return binaryDictionary.isValidNgram(new NgramContext(new WordInfo(word0)), word1);
    }

    private static boolean isValidTrigram(final BinaryDictionary binaryDictionary,
            final String word0, final String word1, final String word2) {
        return binaryDictionary.isValidNgram(
                new NgramContext(new WordInfo(word1), new WordInfo(word0)), word2);
    }

    private void forcePassingShortTime(final BinaryDictionary binaryDictionary) {
        // 30 days.
        final int timeToElapse = (int)TimeUnit.SECONDS.convert(30, TimeUnit.DAYS);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
        binaryDictionary.flushWithGC();
    }

    private void forcePassingLongTime(final BinaryDictionary binaryDictionary) {
        // 365 days.
        final int timeToElapse = (int)TimeUnit.SECONDS.convert(365, TimeUnit.DAYS);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
        binaryDictionary.flushWithGC();
    }

    private HashSet<File> mDictFilesToBeDeleted = new HashSet<>();

    private File createEmptyDictionaryAndGetFile(final int formatVersion) {
        return createEmptyDictionaryWithAttributeMapAndGetFile(formatVersion,
                new HashMap<String, String>());
    }

    private File createEmptyDictionaryWithAttributeMapAndGetFile(final int formatVersion,
            final HashMap<String, String> attributeMap) {
        if (formatVersion == FormatSpec.VERSION4
                || formatVersion == FormatSpec.VERSION4_ONLY_FOR_TESTING
                || formatVersion == FormatSpec.VERSION4_DEV) {
            try {
                final File dictFile = createEmptyVer4DictionaryAndGetFile(formatVersion,
                        attributeMap);
                mDictFilesToBeDeleted.add(dictFile);
                return dictFile;
            } catch (final IOException e) {
                fail(e.toString());
            }
        } else {
            fail("Dictionary format version " + formatVersion + " is not supported.");
        }
        return null;
    }

    private File createEmptyVer4DictionaryAndGetFile(final int formatVersion,
            final HashMap<String, String> attributeMap)
            throws IOException {
        final File file = File.createTempFile(DICTIONARY_ID, TEST_DICT_FILE_EXTENSION,
                getContext().getCacheDir());
        FileUtils.deleteRecursively(file);
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, DICTIONARY_ID);
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        attributeMap.put(DictionaryHeader.USES_FORGETTING_CURVE_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.HAS_HISTORICAL_INFO_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionaryUtils.createEmptyDictFile(file.getAbsolutePath(), formatVersion,
                LocaleUtils.constructLocaleFromString(TEST_LOCALE), attributeMap)) {
            return file;
        }
        throw new IOException("Empty dictionary " + file.getAbsolutePath()
                + " cannot be created. Foramt version: " + formatVersion);
    }

    private static BinaryDictionary getBinaryDictionary(final File dictFile) {
        return new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, dictFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
    }

    private static int setCurrentTimeForTestMode(final int currentTime) {
        return BinaryDictionaryUtils.setCurrentTimeForTest(currentTime);
    }

    private static int stopTestModeInNativeCode() {
        return BinaryDictionaryUtils.setCurrentTimeForTest(-1);
    }

    public void testReadDictInJavaSide() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testReadDictInJavaSide(formatVersion);
        }
    }

    private void testReadDictInJavaSide(final int formatVersion) {
        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWord(binaryDictionary, "ab", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "aaa", true /* isValidWord */, "a");
        binaryDictionary.flushWithGC();
        binaryDictionary.close();

        final DictDecoder dictDecoder =
                BinaryDictIOUtils.getDictDecoder(dictFile, 0, dictFile.length());
        try {
            final FusionDictionary dict =
                    dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
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
    }

    public void testControlCurrentTime() {
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
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddValidAndInvalidWords(formatVersion);
        }
    }

    private void testAddValidAndInvalidWords(final int formatVersion) {
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);

        onInputWord(binaryDictionary, "a", false /* isValidWord */);
        assertFalse(binaryDictionary.isValidWord("a"));
        onInputWord(binaryDictionary, "a", false /* isValidWord */);
        onInputWord(binaryDictionary, "a", false /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("a"));

        onInputWord(binaryDictionary, "b", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("b"));

        onInputWordWithPrevWord(binaryDictionary, "b", false /* isValidWord */, "a");
        assertFalse(isValidBigram(binaryDictionary, "a", "b"));
        onInputWordWithPrevWord(binaryDictionary, "b", false /* isValidWord */, "a");
        assertTrue(isValidBigram(binaryDictionary, "a", "b"));

        onInputWordWithPrevWord(binaryDictionary, "c", true /* isValidWord */, "a");
        assertTrue(isValidBigram(binaryDictionary, "a", "c"));

        // Add bigrams of not valid unigrams.
        onInputWordWithPrevWord(binaryDictionary, "y", false /* isValidWord */, "x");
        assertFalse(isValidBigram(binaryDictionary, "x", "y"));
        onInputWordWithPrevWord(binaryDictionary, "y", true /* isValidWord */, "x");
        assertFalse(isValidBigram(binaryDictionary, "x", "y"));

        if (!supportsNgram(formatVersion)) {
            return;
        }

        onInputWordWithPrevWords(binaryDictionary, "c", false /* isValidWord */, "b", "a");
        assertFalse(isValidTrigram(binaryDictionary, "a", "b", "c"));
        assertFalse(isValidBigram(binaryDictionary, "b", "c"));
        onInputWordWithPrevWords(binaryDictionary, "c", false /* isValidWord */, "b", "a");
        assertTrue(isValidTrigram(binaryDictionary, "a", "b", "c"));
        assertTrue(isValidBigram(binaryDictionary, "b", "c"));

        onInputWordWithPrevWords(binaryDictionary, "d", true /* isValidWord */, "b", "a");
        assertTrue(isValidTrigram(binaryDictionary, "a", "b", "d"));
        assertTrue(isValidBigram(binaryDictionary, "b", "d"));

        onInputWordWithPrevWords(binaryDictionary, "cd", true /* isValidWord */, "b", "a");
        assertTrue(isValidTrigram(binaryDictionary, "a", "b", "cd"));
    }

    public void testDecayingProbability() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testDecayingProbability(formatVersion);
        }
    }

    private void testDecayingProbability(final int formatVersion) {
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);

        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingShortTime(binaryDictionary);
        assertTrue(binaryDictionary.isValidWord("a"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidWord("a"));

        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "b", true /* isValidWord */, "a");
        assertTrue(isValidBigram(binaryDictionary, "a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(isValidBigram(binaryDictionary, "a", "b"));

        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "b", true /* isValidWord */, "a");
        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "b", true /* isValidWord */, "a");
        onInputWord(binaryDictionary, "a", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "b", true /* isValidWord */, "a");
        assertTrue(isValidBigram(binaryDictionary, "a", "b"));
        forcePassingShortTime(binaryDictionary);
        assertTrue(isValidBigram(binaryDictionary, "a", "b"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(isValidBigram(binaryDictionary, "a", "b"));

        if (!supportsNgram(formatVersion)) {
            return;
        }

        onInputWord(binaryDictionary, "ab", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "bc", true /* isValidWord */, "ab");
        onInputWordWithPrevWords(binaryDictionary, "cd", true /* isValidWord */, "bc", "ab");
        assertTrue(isValidTrigram(binaryDictionary, "ab", "bc", "cd"));
        forcePassingShortTime(binaryDictionary);
        assertFalse(isValidTrigram(binaryDictionary, "ab", "bc", "cd"));

        onInputWord(binaryDictionary, "ab", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "bc", true /* isValidWord */, "ab");
        onInputWordWithPrevWords(binaryDictionary, "cd", true /* isValidWord */, "bc", "ab");
        onInputWord(binaryDictionary, "ab", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "bc", true /* isValidWord */, "ab");
        onInputWordWithPrevWords(binaryDictionary, "cd", true /* isValidWord */, "bc", "ab");
        onInputWord(binaryDictionary, "ab", true /* isValidWord */);
        onInputWordWithPrevWord(binaryDictionary, "bc", true /* isValidWord */, "ab");
        onInputWordWithPrevWords(binaryDictionary, "cd", true /* isValidWord */, "bc", "ab");
        forcePassingShortTime(binaryDictionary);
        assertTrue(isValidTrigram(binaryDictionary, "ab", "bc", "cd"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(isValidTrigram(binaryDictionary, "ab", "bc", "cd"));

        binaryDictionary.close();
    }

    public void testAddManyUnigramsToDecayingDict() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddManyUnigramsToDecayingDict(formatVersion);
        }
    }

    private void testAddManyUnigramsToDecayingDict(final int formatVersion) {
        final int unigramCount = 30000;
        final int unigramTypedCount = 100000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        setCurrentTimeForTestMode(mCurrentTime);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> words = new ArrayList<>();

        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
        }

        final int maxUnigramCount = Integer.parseInt(
                binaryDictionary.getPropertyForGettingStats(
                        BinaryDictionary.MAX_UNIGRAM_COUNT_QUERY));
        for (int i = 0; i < unigramTypedCount; i++) {
            final String word = words.get(random.nextInt(words.size()));
            onInputWord(binaryDictionary, word, true /* isValidWord */);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int unigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    forcePassingShortTime(binaryDictionary);
                }
                final int unigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(unigramCountBeforeGC > unigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)) <= maxUnigramCount);
        forcePassingLongTime(binaryDictionary);
        assertEquals(0, Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.UNIGRAM_COUNT_QUERY)));
    }

    public void testOverflowUnigrams() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testOverflowUnigrams(formatVersion);
        }
    }

    private void testOverflowUnigrams(final int formatVersion) {
        final int unigramCount = 20000;
        final int eachUnigramTypedCount = 2;
        final int strongUnigramTypedCount = 20;
        final int weakUnigramTypedCount = 1;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        setCurrentTimeForTestMode(mCurrentTime);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        final String strong = "strong";
        final String weak = "weak";
        for (int j = 0; j < strongUnigramTypedCount; j++) {
            onInputWord(binaryDictionary, strong, true /* isValidWord */);
        }
        for (int j = 0; j < weakUnigramTypedCount; j++) {
            onInputWord(binaryDictionary, weak, true /* isValidWord */);
        }
        assertTrue(binaryDictionary.isValidWord(strong));
        assertTrue(binaryDictionary.isValidWord(weak));

        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            for (int j = 0; j < eachUnigramTypedCount; j++) {
                onInputWord(binaryDictionary, word, true /* isValidWord */);
            }
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int unigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(binaryDictionary.isValidWord(strong));
                assertTrue(binaryDictionary.isValidWord(weak));
                binaryDictionary.flushWithGC();
                final int unigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.UNIGRAM_COUNT_QUERY));
                assertTrue(unigramCountBeforeGC > unigramCountAfterGC);
                assertFalse(binaryDictionary.isValidWord(weak));
                assertTrue(binaryDictionary.isValidWord(strong));
                break;
            }
        }
    }

    public void testAddManyBigramsToDecayingDict() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testAddManyBigramsToDecayingDict(formatVersion);
        }
    }

    private void testAddManyBigramsToDecayingDict(final int formatVersion) {
        final int maxUnigramCount = 5000;
        final int maxBigramCount = 10000;
        final HashMap<String, String> attributeMap = new HashMap<>();
        attributeMap.put(DictionaryHeader.MAX_UNIGRAM_COUNT_KEY, String.valueOf(maxUnigramCount));
        attributeMap.put(DictionaryHeader.MAX_BIGRAM_COUNT_KEY, String.valueOf(maxBigramCount));

        final int unigramCount = 5000;
        final int bigramCount = 30000;
        final int bigramTypedCount = 100000;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);

        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryWithAttributeMapAndGetFile(formatVersion,
                attributeMap);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> words = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigrams = new ArrayList<>();

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
            final Pair<String, String> bigram = new Pair<>(word0, word1);
            bigrams.add(bigram);
        }

        for (int i = 0; i < bigramTypedCount; ++i) {
            final Pair<String, String> bigram = bigrams.get(random.nextInt(bigrams.size()));
            onInputWord(binaryDictionary, bigram.first, true /* isValidWord */);
            onInputWordWithPrevWord(binaryDictionary, bigram.second, true /* isValidWord */,
                    bigram.first);

            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int bigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                while (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                    forcePassingShortTime(binaryDictionary);
                }
                final int bigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                assertTrue(bigramCountBeforeGC > bigramCountAfterGC);
            }
        }

        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) > 0);
        assertTrue(Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.BIGRAM_COUNT_QUERY)) <= maxBigramCount);
        forcePassingLongTime(binaryDictionary);
        assertEquals(0, Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                BinaryDictionary.BIGRAM_COUNT_QUERY)));
    }

    public void testOverflowBigrams() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testOverflowBigrams(formatVersion);
        }
    }

    private void testOverflowBigrams(final int formatVersion) {
        final int maxUnigramCount = 5000;
        final int maxBigramCount = 10000;
        final HashMap<String, String> attributeMap = new HashMap<>();
        attributeMap.put(DictionaryHeader.MAX_UNIGRAM_COUNT_KEY, String.valueOf(maxUnigramCount));
        attributeMap.put(DictionaryHeader.MAX_BIGRAM_COUNT_KEY, String.valueOf(maxBigramCount));

        final int bigramCount = 20000;
        final int unigramCount = 1000;
        final int unigramTypedCount = 20;
        final int eachBigramTypedCount = 2;
        final int strongBigramTypedCount = 20;
        final int weakBigramTypedCount = 1;
        final int codePointSetSize = 50;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryWithAttributeMapAndGetFile(formatVersion,
                attributeMap);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);

        final ArrayList<String> words = new ArrayList<>();
        for (int i = 0; i < unigramCount; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            words.add(word);
            for (int j = 0; j < unigramTypedCount; j++) {
                onInputWord(binaryDictionary, word, true /* isValidWord */);
            }
        }
        final String strong = "strong";
        final String weak = "weak";
        final String target = "target";
        for (int j = 0; j < unigramTypedCount; j++) {
            onInputWord(binaryDictionary, weak, true /* isValidWord */);
            onInputWord(binaryDictionary, strong, true /* isValidWord */);
        }
        binaryDictionary.flushWithGC();
        for (int j = 0; j < strongBigramTypedCount; j++) {
            onInputWordWithPrevWord(binaryDictionary, target, true /* isValidWord */, strong);
        }
        for (int j = 0; j < weakBigramTypedCount; j++) {
            onInputWordWithPrevWord(binaryDictionary, target, true /* isValidWord */, weak);
        }
        assertTrue(isValidBigram(binaryDictionary, strong, target));
        assertTrue(isValidBigram(binaryDictionary, weak, target));

        for (int i = 0; i < bigramCount; i++) {
            final int word0Index = random.nextInt(words.size());
            final String word0 = words.get(word0Index);
            final int index = random.nextInt(words.size() - 1);
            final int word1Index = (index >= word0Index) ? index + 1 : index;
            final String word1 = words.get(word1Index);

            for (int j = 0; j < eachBigramTypedCount; j++) {
                onInputWordWithPrevWord(binaryDictionary, word1, true /* isValidWord */, word0);
            }
            if (binaryDictionary.needsToRunGC(true /* mindsBlockByGC */)) {
                final int bigramCountBeforeGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                binaryDictionary.flushWithGC();
                final int bigramCountAfterGC =
                        Integer.parseInt(binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.BIGRAM_COUNT_QUERY));
                assertTrue(bigramCountBeforeGC > bigramCountAfterGC);
                assertTrue(isValidBigram(binaryDictionary, strong, target));
                assertFalse(isValidBigram(binaryDictionary, weak, target));
                break;
            }
        }
    }

    public void testDictMigration() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testDictMigration(FormatSpec.VERSION4_ONLY_FOR_TESTING, formatVersion);
        }
    }

    private void testDictMigration(final int fromFormatVersion, final int toFormatVersion) {
        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryAndGetFile(fromFormatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        onInputWord(binaryDictionary, "aaa", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("aaa"));
        onInputWord(binaryDictionary, "ccc", true /* isValidWord */);
        onInputWord(binaryDictionary, "ccc", true /* isValidWord */);
        onInputWord(binaryDictionary, "ccc", true /* isValidWord */);
        onInputWord(binaryDictionary, "ccc", true /* isValidWord */);
        onInputWord(binaryDictionary, "ccc", true /* isValidWord */);

        onInputWordWithPrevWord(binaryDictionary, "abc", true /* isValidWord */, "aaa");
        assertTrue(isValidBigram(binaryDictionary, "aaa", "abc"));
        onInputWordWithPrevWord(binaryDictionary, "bbb", false /* isValidWord */, "aaa");
        assertFalse(binaryDictionary.isValidWord("bbb"));
        assertFalse(isValidBigram(binaryDictionary, "aaa", "bbb"));

        if (supportsNgram(toFormatVersion)) {
            onInputWordWithPrevWords(binaryDictionary, "xyz", true, "abc", "aaa");
            assertTrue(isValidTrigram(binaryDictionary, "aaa", "abc", "xyz"));
            onInputWordWithPrevWords(binaryDictionary, "def", false, "abc", "aaa");
            assertFalse(isValidTrigram(binaryDictionary, "aaa", "abc", "def"));
        }

        assertEquals(fromFormatVersion, binaryDictionary.getFormatVersion());
        assertTrue(binaryDictionary.migrateTo(toFormatVersion));
        assertTrue(binaryDictionary.isValidDictionary());
        assertEquals(toFormatVersion, binaryDictionary.getFormatVersion());
        assertTrue(binaryDictionary.isValidWord("aaa"));
        assertFalse(binaryDictionary.isValidWord("bbb"));
        assertTrue(binaryDictionary.getFrequency("aaa") < binaryDictionary.getFrequency("ccc"));
        onInputWord(binaryDictionary, "bbb", false /* isValidWord */);
        assertTrue(binaryDictionary.isValidWord("bbb"));
        assertTrue(isValidBigram(binaryDictionary, "aaa", "abc"));
        assertFalse(isValidBigram(binaryDictionary, "aaa", "bbb"));
        onInputWordWithPrevWord(binaryDictionary, "bbb", false /* isValidWord */, "aaa");
        assertTrue(isValidBigram(binaryDictionary, "aaa", "bbb"));

        if (supportsNgram(toFormatVersion)) {
            assertTrue(isValidTrigram(binaryDictionary, "aaa", "abc", "xyz"));
            assertFalse(isValidTrigram(binaryDictionary, "aaa", "abc", "def"));
            onInputWordWithPrevWords(binaryDictionary, "def", false, "abc", "aaa");
            assertTrue(isValidTrigram(binaryDictionary, "aaa", "abc", "def"));
        }

        binaryDictionary.close();
    }

    public void testBeginningOfSentence() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            if (supportsBeginningOfSentence(formatVersion)) {
                testBeginningOfSentence(formatVersion);
            }
        }
    }

    private void testBeginningOfSentence(final int formatVersion) {
        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);

        binaryDictionary.addUnigramEntry("", DUMMY_PROBABILITY, "" /* shortcutTarget */,
                Dictionary.NOT_A_PROBABILITY /* shortcutProbability */,
                true /* isBeginningOfSentence */, true /* isNotAWord */,
                false /* isPossiblyOffensive */, mCurrentTime);
        final NgramContext beginningOfSentenceContext = NgramContext.BEGINNING_OF_SENTENCE;
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "aaa", true /* isValidWord */);
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "aaa", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "aaa", true /* isValidWord */);
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "bbb", true /* isValidWord */);
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "bbb"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "bbb", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        assertTrue(binaryDictionary.isValidNgram(beginningOfSentenceContext, "bbb"));
        forcePassingLongTime(binaryDictionary);
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "bbb"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "aaa", true /* isValidWord */);
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "aaa", true /* isValidWord */);
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "bbb", true /* isValidWord */);
        assertFalse(binaryDictionary.isValidNgram(beginningOfSentenceContext, "bbb"));
        onInputWordWithBeginningOfSentenceContext(binaryDictionary, "bbb", true /* isValidWord */);
        assertTrue(binaryDictionary.isValidNgram(beginningOfSentenceContext, "aaa"));
        assertTrue(binaryDictionary.isValidNgram(beginningOfSentenceContext, "bbb"));
        binaryDictionary.close();
    }

    public void testRemoveUnigrams() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testRemoveUnigrams(formatVersion);
        }
    }

    private void testRemoveUnigrams(final int formatVersion) {
        final int unigramInputCount = 20;
        setCurrentTimeForTestMode(mCurrentTime);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);

        onInputWord(binaryDictionary, "aaa", false /* isValidWord */);
        assertFalse(binaryDictionary.isValidWord("aaa"));
        for (int i = 0; i < unigramInputCount; i++) {
            onInputWord(binaryDictionary, "aaa", false /* isValidWord */);
        }
        assertTrue(binaryDictionary.isValidWord("aaa"));
        assertTrue(binaryDictionary.removeUnigramEntry("aaa"));
        assertFalse(binaryDictionary.isValidWord("aaa"));

        binaryDictionary.close();
    }

    public void testUpdateEntriesForInputEvents() {
        for (final int formatVersion : DICT_FORMAT_VERSIONS) {
            testUpdateEntriesForInputEvents(formatVersion);
        }
    }

    private void testUpdateEntriesForInputEvents(final int formatVersion) {
        setCurrentTimeForTestMode(mCurrentTime);
        final int codePointSetSize = 20;
        final int EVENT_COUNT = 1000;
        final double CONTINUE_RATE = 0.9;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final File dictFile = createEmptyDictionaryAndGetFile(formatVersion);

        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        final ArrayList<String> unigrams = new ArrayList<>();
        final ArrayList<Pair<String, String>> bigrams = new ArrayList<>();
        final ArrayList<Pair<Pair<String, String>, String>> trigrams = new ArrayList<>();

        final WordInputEventForPersonalization[] inputEvents =
                new WordInputEventForPersonalization[EVENT_COUNT];
        NgramContext ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO;
        int prevWordCount = 0;
        for (int i = 0; i < inputEvents.length; i++) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            inputEvents[i] = new WordInputEventForPersonalization(word, ngramContext,
                    true /* isValid */, mCurrentTime);
            unigrams.add(word);
            if (prevWordCount >= 2) {
                final Pair<String, String> prevWordsPair = bigrams.get(bigrams.size() - 1);
                trigrams.add(new Pair<>(prevWordsPair, word));
            }
            if (prevWordCount >= 1) {
                bigrams.add(new Pair<>(ngramContext.getNthPrevWord(1 /* n */).toString(), word));
            }
            if (random.nextDouble() > CONTINUE_RATE) {
                ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO;
                prevWordCount = 0;
            } else {
                ngramContext = ngramContext.getNextNgramContext(new WordInfo(word));
                prevWordCount++;
            }
        }
        final BinaryDictionary binaryDictionary = getBinaryDictionary(dictFile);
        binaryDictionary.updateEntriesForInputEvents(inputEvents);

        for (final String word : unigrams) {
            assertTrue(binaryDictionary.isInDictionary(word));
        }
        for (final Pair<String, String> bigram : bigrams) {
            assertTrue(isValidBigram(binaryDictionary, bigram.first, bigram.second));
        }
        if (!supportsNgram(formatVersion)) {
            return;
        }
        for (final Pair<Pair<String, String>, String> trigram : trigrams) {
            assertTrue(isValidTrigram(binaryDictionary, trigram.first.first, trigram.first.second,
                    trigram.second));
        }
    }
}
