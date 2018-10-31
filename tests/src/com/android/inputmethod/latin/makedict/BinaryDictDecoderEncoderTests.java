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

package com.android.inputmethod.latin.makedict;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.common.CodePointUtils;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Unit tests for BinaryDictDecoderUtils and BinaryDictEncoderUtils.
 */
@LargeTest
public class BinaryDictDecoderEncoderTests extends AndroidTestCase {
    private static final String TAG = BinaryDictDecoderEncoderTests.class.getSimpleName();
    private static final int DEFAULT_MAX_UNIGRAMS = 300;
    private static final int DEFAULT_CODE_POINT_SET_SIZE = 50;
    private static final int LARGE_CODE_POINT_SET_SIZE = 300;
    private static final int UNIGRAM_FREQ = 10;
    private static final int BIGRAM_FREQ = 50;
    private static final int TOLERANCE_OF_BIGRAM_FREQ = 5;

    private static final ArrayList<String> sWords = new ArrayList<>();
    private static final ArrayList<String> sWordsWithVariousCodePoints = new ArrayList<>();
    private static final SparseArray<List<Integer>> sEmptyBigrams = new SparseArray<>();
    private static final SparseArray<List<Integer>> sStarBigrams = new SparseArray<>();
    private static final SparseArray<List<Integer>> sChainBigrams = new SparseArray<>();

    final Random mRandom;

    public BinaryDictDecoderEncoderTests() {
        this(System.currentTimeMillis(), DEFAULT_MAX_UNIGRAMS);
    }

    public BinaryDictDecoderEncoderTests(final long seed, final int maxUnigrams) {
        super();
        BinaryDictionaryUtils.setCurrentTimeForTest(0);
        Log.e(TAG, "Testing dictionary: seed is " + seed);
        mRandom = new Random(seed);
        sWords.clear();
        sWordsWithVariousCodePoints.clear();
        generateWords(maxUnigrams, mRandom);

        for (int i = 0; i < sWords.size(); ++i) {
            sChainBigrams.put(i, new ArrayList<Integer>());
            if (i > 0) {
                sChainBigrams.get(i - 1).add(i);
            }
        }

        sStarBigrams.put(0, new ArrayList<Integer>());
        // MAX - 1 because we added one above already
        final int maxBigrams = Math.min(sWords.size(), FormatSpec.MAX_BIGRAMS_IN_A_PTNODE - 1);
        for (int i = 1; i < maxBigrams; ++i) {
            sStarBigrams.get(0).add(i);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        BinaryDictionaryUtils.setCurrentTimeForTest(0);
    }

    @Override
    protected void tearDown() throws Exception {
        // Quit test mode.
        BinaryDictionaryUtils.setCurrentTimeForTest(-1);
        super.tearDown();
    }

    private static void generateWords(final int number, final Random random) {
        final int[] codePointSet = CodePointUtils.generateCodePointSet(DEFAULT_CODE_POINT_SET_SIZE,
                random);
        final Set<String> wordSet = new HashSet<>();
        while (wordSet.size() < number) {
            wordSet.add(CodePointUtils.generateWord(random, codePointSet));
        }
        sWords.addAll(wordSet);

        final int[] largeCodePointSet = CodePointUtils.generateCodePointSet(
                LARGE_CODE_POINT_SET_SIZE, random);
        wordSet.clear();
        while (wordSet.size() < number) {
            wordSet.add(CodePointUtils.generateWord(random, largeCodePointSet));
        }
        sWordsWithVariousCodePoints.addAll(wordSet);
    }

    /**
     * Adds unigrams to the dictionary.
     */
    private static void addUnigrams(final int number, final FusionDictionary dict,
            final List<String> words) {
        for (int i = 0; i < number; ++i) {
            final String word = words.get(i);
            final ArrayList<WeightedString> shortcuts = new ArrayList<>();
            dict.add(word, new ProbabilityInfo(UNIGRAM_FREQ), false /* isNotAWord */,
                    false /* isPossiblyOffensive */);
        }
    }

    private static void addBigrams(final FusionDictionary dict,
            final List<String> words,
            final SparseArray<List<Integer>> bigrams) {
        for (int i = 0; i < bigrams.size(); ++i) {
            final int w1 = bigrams.keyAt(i);
            for (int w2 : bigrams.valueAt(i)) {
                dict.setBigram(words.get(w1), words.get(w2), new ProbabilityInfo(BIGRAM_FREQ));
            }
        }
    }

//    The following is useful to dump the dictionary into a textual file, but it can't compile
//    on-device, so it's commented out.
//    private void dumpToCombinedFileForDebug(final FusionDictionary dict, final String filename)
//            throws IOException {
//        com.android.inputmethod.latin.dicttool.CombinedInputOutput.writeDictionaryCombined(
//                new java.io.FileWriter(new File(filename)), dict);
//    }

    private static long timeWritingDictToFile(final File file, final FusionDictionary dict,
            final FormatSpec.FormatOptions formatOptions) {

        long now = -1, diff = -1;

        try {
            final DictEncoder dictEncoder = BinaryDictUtils.getDictEncoder(file, formatOptions);

            now = System.currentTimeMillis();
            // If you need to dump the dict to a textual file, uncomment the line below and the
            // function above
            // dumpToCombinedFileForDebug(file, "/tmp/foo");
            dictEncoder.writeDictionary(dict, formatOptions);
            diff = System.currentTimeMillis() - now;
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException", e);
        }

        return diff;
    }

    private static void checkDictionary(final FusionDictionary dict, final List<String> words,
            final SparseArray<List<Integer>> bigrams) {
        assertNotNull(dict);

        // check unigram
        for (final String word : words) {
            final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, word);
            assertNotNull(ptNode);
        }

        // check bigram
        for (int i = 0; i < bigrams.size(); ++i) {
            final int w1 = bigrams.keyAt(i);
            for (final int w2 : bigrams.valueAt(i)) {
                final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray,
                        words.get(w1));
                assertNotNull(words.get(w1) + "," + words.get(w2), ptNode.getBigram(words.get(w2)));
            }
        }
    }

    private static String outputOptions(final int bufferType,
            final FormatSpec.FormatOptions formatOptions) {
        final String result = " : buffer type = "
                + ((bufferType == BinaryDictUtils.USE_BYTE_BUFFER) ? "byte buffer" : "byte array");
        return result + " : version = " + formatOptions.mVersion;
    }

    // Tests for readDictionaryBinary and writeDictionaryBinary

    private static long timeReadingAndCheckDict(final File file, final List<String> words,
            final SparseArray<List<Integer>> bigrams, final int bufferType) {
        long now, diff = -1;

        FusionDictionary dict = null;
        try {
            final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length(),
                    bufferType);
            now = System.currentTimeMillis();
            dict = dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
            diff  = System.currentTimeMillis() - now;
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }

        checkDictionary(dict, words, bigrams);
        return diff;
    }

    // Tests for readDictionaryBinary and writeDictionaryBinary
    private String runReadAndWrite(final List<String> words,
            final SparseArray<List<Integer>> bigrams,
            final int bufferType, final FormatSpec.FormatOptions formatOptions,
            final String message) {

        final String dictName = "runReadAndWrite";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion, formatOptions,
                getContext().getCacheDir());

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(words.size(), dict, words);
        addBigrams(dict, words, bigrams);
        checkDictionary(dict, words, bigrams);

        final long write = timeWritingDictToFile(file, dict, formatOptions);
        final long read = timeReadingAndCheckDict(file, words, bigrams, bufferType);

        return "PROF: read=" + read + "ms, write=" + write + "ms :" + message
                + " : " + outputOptions(bufferType, formatOptions);
    }

    private void runReadAndWriteTests(final List<String> results, final int bufferType,
            final FormatSpec.FormatOptions formatOptions) {
        results.add(runReadAndWrite(sWords, sEmptyBigrams, bufferType,
                formatOptions, "unigram"));
        results.add(runReadAndWrite(sWords, sChainBigrams, bufferType,
                formatOptions, "chain"));
        results.add(runReadAndWrite(sWords, sStarBigrams, bufferType,
                formatOptions, "star"));
        results.add(runReadAndWrite(sWords, sEmptyBigrams, bufferType, formatOptions,
                "unigram with shortcuts"));
        results.add(runReadAndWrite(sWords, sChainBigrams, bufferType, formatOptions,
                "chain with shortcuts"));
        results.add(runReadAndWrite(sWords, sStarBigrams, bufferType, formatOptions,
                "star with shortcuts"));
        results.add(runReadAndWrite(sWordsWithVariousCodePoints, sEmptyBigrams,
                bufferType, formatOptions,
                "unigram with various code points"));
    }

    public void testCharacterTableIsPresent() throws IOException, UnsupportedFormatException {
        final String[] wordSource = {"words", "used", "for", "testing", "a", "code point", "table"};
        final List<String> words = Arrays.asList(wordSource);
        final String correctCodePointTable = "toesdrniawuplgfcb ";
        final String dictName = "codePointTableTest";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final String codePointTableAttribute = DictionaryHeader.CODE_POINT_TABLE_KEY;
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion,
                BinaryDictUtils.STATIC_OPTIONS, getContext().getCacheDir());

        // Write a test dictionary
        final DictEncoder dictEncoder = new Ver2DictEncoder(file,
                Ver2DictEncoder.CODE_POINT_TABLE_ON);
        final FormatSpec.FormatOptions formatOptions =
                new FormatSpec.FormatOptions(
                        FormatSpec.MINIMUM_SUPPORTED_STATIC_VERSION);
        final FusionDictionary sourcedict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(words.size(), sourcedict, words);
        dictEncoder.writeDictionary(sourcedict, formatOptions);

        // Read the dictionary
        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length(),
                DictDecoder.USE_BYTEARRAY);
        final DictionaryHeader fileHeader = dictDecoder.readHeader();
        // Check if codePointTable is present
        assertTrue("codePointTable is not present",
                fileHeader.mDictionaryOptions.mAttributes.containsKey(codePointTableAttribute));
        final String codePointTable =
                fileHeader.mDictionaryOptions.mAttributes.get(codePointTableAttribute);
        // Check if codePointTable is correct
        assertEquals("codePointTable is incorrect", codePointTable, correctCodePointTable);
    }

    // Unit test for CharEncoding.readString and CharEncoding.writeString.
    public void testCharEncoding() {
        // the max length of a word in sWords is less than 50.
        // See generateWords.
        final byte[] buffer = new byte[50 * 3];
        final DictBuffer dictBuffer = new ByteArrayDictBuffer(buffer);
        for (final String word : sWords) {
            Arrays.fill(buffer, (byte) 0);
            CharEncoding.writeString(buffer, 0, word, null);
            dictBuffer.position(0);
            final String str = CharEncoding.readString(dictBuffer);
            assertEquals(word, str);
        }
    }

    public void testReadAndWriteWithByteBuffer() {
        final List<String> results = new ArrayList<>();

        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_BUFFER,
                BinaryDictUtils.STATIC_OPTIONS);
        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_BUFFER,
                BinaryDictUtils.DYNAMIC_OPTIONS_WITHOUT_TIMESTAMP);
        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_BUFFER,
                BinaryDictUtils.DYNAMIC_OPTIONS_WITH_TIMESTAMP);
        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    public void testReadAndWriteWithByteArray() {
        final List<String> results = new ArrayList<>();

        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_ARRAY,
                BinaryDictUtils.STATIC_OPTIONS);
        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_ARRAY,
                BinaryDictUtils.DYNAMIC_OPTIONS_WITHOUT_TIMESTAMP);
        runReadAndWriteTests(results, BinaryDictUtils.USE_BYTE_ARRAY,
                BinaryDictUtils.DYNAMIC_OPTIONS_WITH_TIMESTAMP);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    // Tests for readUnigramsAndBigramsBinary

    private static void checkWordMap(final List<String> expectedWords,
            final SparseArray<List<Integer>> expectedBigrams,
            final TreeMap<Integer, String> resultWords,
            final TreeMap<Integer, Integer> resultFrequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> resultBigrams,
            final boolean checkProbability) {
        // check unigrams
        final Set<String> actualWordsSet = new HashSet<>(resultWords.values());
        final Set<String> expectedWordsSet = new HashSet<>(expectedWords);
        assertEquals(actualWordsSet, expectedWordsSet);
        if (checkProbability) {
            for (int freq : resultFrequencies.values()) {
                assertEquals(freq, UNIGRAM_FREQ);
            }
        }

        // check bigrams
        final HashMap<String, Set<String>> expBigrams = new HashMap<>();
        for (int i = 0; i < expectedBigrams.size(); ++i) {
            final String word1 = expectedWords.get(expectedBigrams.keyAt(i));
            for (int w2 : expectedBigrams.valueAt(i)) {
                if (expBigrams.get(word1) == null) {
                    expBigrams.put(word1, new HashSet<String>());
                }
                expBigrams.get(word1).add(expectedWords.get(w2));
            }
        }

        final HashMap<String, Set<String>> actBigrams = new HashMap<>();
        for (Entry<Integer, ArrayList<PendingAttribute>> entry : resultBigrams.entrySet()) {
            final String word1 = resultWords.get(entry.getKey());
            final int unigramFreq = resultFrequencies.get(entry.getKey());
            for (PendingAttribute attr : entry.getValue()) {
                final String word2 = resultWords.get(attr.mAddress);
                if (actBigrams.get(word1) == null) {
                    actBigrams.put(word1, new HashSet<String>());
                }
                actBigrams.get(word1).add(word2);

                if (checkProbability) {
                    final int bigramFreq = BinaryDictIOUtils.reconstructBigramFrequency(
                            unigramFreq, attr.mFrequency);
                    assertTrue(Math.abs(bigramFreq - BIGRAM_FREQ) < TOLERANCE_OF_BIGRAM_FREQ);
                }
            }
        }
        assertEquals(actBigrams, expBigrams);
    }

    private static long timeAndCheckReadUnigramsAndBigramsBinary(final File file,
            final List<String> words, final SparseArray<List<Integer>> bigrams,
            final int bufferType, final boolean checkProbability) {
        final TreeMap<Integer, String> resultWords = new TreeMap<>();
        final TreeMap<Integer, ArrayList<PendingAttribute>> resultBigrams = new TreeMap<>();
        final TreeMap<Integer, Integer> resultFreqs = new TreeMap<>();

        long now = -1, diff = -1;
        try {
            final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length(),
                    bufferType);
            now = System.currentTimeMillis();
            dictDecoder.readUnigramsAndBigramsBinary(resultWords, resultFreqs, resultBigrams);
            diff = System.currentTimeMillis() - now;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException", e);
        }

        checkWordMap(words, bigrams, resultWords, resultFreqs, resultBigrams, checkProbability);
        return diff;
    }

    private String runReadUnigramsAndBigramsBinary(final ArrayList<String> words,
            final SparseArray<List<Integer>> bigrams, final int bufferType,
            final FormatSpec.FormatOptions formatOptions, final String message) {
        final String dictName = "runReadUnigrams";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion, formatOptions,
                getContext().getCacheDir());

        // making the dictionary from lists of words.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(words.size(), dict, words);
        addBigrams(dict, words, bigrams);

        timeWritingDictToFile(file, dict, formatOptions);

        // Caveat: Currently, the Java code to read a v4 dictionary doesn't calculate the
        // probability when there's a timestamp for the entry.
        // TODO: Abandon the Java code, and implement the v4 dictionary reading code in native.
        long wordMap = timeAndCheckReadUnigramsAndBigramsBinary(file, words, bigrams, bufferType,
                !formatOptions.mHasTimestamp /* checkProbability */);
        long fullReading = timeReadingAndCheckDict(file, words, bigrams,
                bufferType);

        return "readDictionaryBinary=" + fullReading + ", readUnigramsAndBigramsBinary=" + wordMap
                + " : " + message + " : " + outputOptions(bufferType, formatOptions);
    }

    private void runReadUnigramsAndBigramsTests(final ArrayList<String> results,
            final int bufferType, final FormatSpec.FormatOptions formatOptions) {
        results.add(runReadUnigramsAndBigramsBinary(sWords, sEmptyBigrams, bufferType,
                formatOptions, "unigram"));
        results.add(runReadUnigramsAndBigramsBinary(sWords, sChainBigrams, bufferType,
                formatOptions, "chain"));
        results.add(runReadUnigramsAndBigramsBinary(sWords, sStarBigrams, bufferType,
                formatOptions, "star"));
    }

    public void testReadUnigramsAndBigramsBinaryWithByteBuffer() {
        final ArrayList<String> results = new ArrayList<>();

        runReadUnigramsAndBigramsTests(results, BinaryDictUtils.USE_BYTE_BUFFER,
                BinaryDictUtils.STATIC_OPTIONS);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    public void testReadUnigramsAndBigramsBinaryWithByteArray() {
        final ArrayList<String> results = new ArrayList<>();

        runReadUnigramsAndBigramsTests(results, BinaryDictUtils.USE_BYTE_ARRAY,
                BinaryDictUtils.STATIC_OPTIONS);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    // Tests for getTerminalPosition
    private static String getWordFromBinary(final DictDecoder dictDecoder, final int address) {
        if (dictDecoder.getPosition() != 0) dictDecoder.setPosition(0);

        DictionaryHeader fileHeader = null;
        try {
            fileHeader = dictDecoder.readHeader();
        } catch (IOException e) {
            return null;
        } catch (UnsupportedFormatException e) {
            return null;
        }
        if (fileHeader == null) return null;
        return BinaryDictDecoderUtils.getWordAtPosition(dictDecoder, fileHeader.mBodyOffset,
                address).mWord;
    }

    private static long checkGetTerminalPosition(final DictDecoder dictDecoder, final String word,
            final boolean contained) {
        long diff = -1;
        int position = -1;
        try {
            final long now = System.nanoTime();
            position = dictDecoder.getTerminalPosition(word);
            diff = System.nanoTime() - now;
        } catch (IOException e) {
            Log.e(TAG, "IOException while getTerminalPosition", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException while getTerminalPosition", e);
        }

        assertEquals(FormatSpec.NOT_VALID_WORD != position, contained);
        if (contained) assertEquals(getWordFromBinary(dictDecoder, position), word);
        return diff;
    }

    private void runGetTerminalPosition(final ArrayList<String> words,
            final SparseArray<List<Integer>> bigrams, final int bufferType,
            final FormatOptions formatOptions, final String message) {
        final String dictName = "testGetTerminalPosition";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion, formatOptions,
                getContext().getCacheDir());

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(sWords.size(), dict, sWords);
        addBigrams(dict, words, bigrams);
        timeWritingDictToFile(file, dict, formatOptions);

        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length(),
                DictDecoder.USE_BYTEARRAY);
        try {
            dictDecoder.openDictBuffer();
        } catch (IOException e) {
            Log.e(TAG, "IOException while opening the buffer", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "IOException while opening the buffer", e);
        }
        assertTrue("Can't get the buffer", dictDecoder.isDictBufferOpen());

        try {
            // too long word
            final String longWord = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
            assertEquals(FormatSpec.NOT_VALID_WORD, dictDecoder.getTerminalPosition(longWord));

            // null
            assertEquals(FormatSpec.NOT_VALID_WORD, dictDecoder.getTerminalPosition(null));

            // empty string
            assertEquals(FormatSpec.NOT_VALID_WORD, dictDecoder.getTerminalPosition(""));
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }

        // Test a word that is contained within the dictionary.
        long sum = 0;
        for (int i = 0; i < sWords.size(); ++i) {
            final long time = checkGetTerminalPosition(dictDecoder, sWords.get(i), true);
            sum += time == -1 ? 0 : time;
        }
        Log.d(TAG, "per search : " + (((double)sum) / sWords.size() / 1000000) + " : " + message
                + " : " + outputOptions(bufferType, formatOptions));

        // Test a word that isn't contained within the dictionary.
        final int[] codePointSet = CodePointUtils.generateCodePointSet(DEFAULT_CODE_POINT_SET_SIZE,
                mRandom);
        for (int i = 0; i < 1000; ++i) {
            final String word = CodePointUtils.generateWord(mRandom, codePointSet);
            if (sWords.indexOf(word) != -1) continue;
            checkGetTerminalPosition(dictDecoder, word, false);
        }
    }

    private void runGetTerminalPositionTests(final int bufferType,
            final FormatOptions formatOptions) {
        runGetTerminalPosition(sWords, sEmptyBigrams, bufferType, formatOptions, "unigram");
    }

    public void testGetTerminalPosition() {
        final ArrayList<String> results = new ArrayList<>();

        runGetTerminalPositionTests(BinaryDictUtils.USE_BYTE_ARRAY,
                BinaryDictUtils.STATIC_OPTIONS);
        runGetTerminalPositionTests(BinaryDictUtils.USE_BYTE_BUFFER,
                BinaryDictUtils.STATIC_OPTIONS);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    public void testVer2DictGetWordProperty() {
        final FormatOptions formatOptions = BinaryDictUtils.STATIC_OPTIONS;
        final ArrayList<String> words = sWords;
        final String dictName = "testGetWordProperty";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(words.size(), dict, words);
        addBigrams(dict, words, sEmptyBigrams);
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion, formatOptions,
                getContext().getCacheDir());
        file.delete();
        timeWritingDictToFile(file, dict, formatOptions);
        final BinaryDictionary binaryDictionary = new BinaryDictionary(file.getAbsolutePath(),
                0 /* offset */, file.length(), true /* useFullEditDistance */,
                Locale.ENGLISH, dictName, false /* isUpdatable */);
        for (final String word : words) {
            final WordProperty wordProperty = binaryDictionary.getWordProperty(word,
                    false /* isBeginningOfSentence */);
            assertEquals(word, wordProperty.mWord);
            assertEquals(UNIGRAM_FREQ, wordProperty.getProbability());
        }
    }

    public void testVer2DictIteration() {
        final FormatOptions formatOptions = BinaryDictUtils.STATIC_OPTIONS;
        final ArrayList<String> words = sWords;
        final SparseArray<List<Integer>> bigrams = sEmptyBigrams;
        final String dictName = "testGetWordProperty";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(words.size(), dict, words);
        addBigrams(dict, words, bigrams);
        final File file = BinaryDictUtils.getDictFile(dictName, dictVersion, formatOptions,
                getContext().getCacheDir());
        timeWritingDictToFile(file, dict, formatOptions);
        Log.d(TAG, file.getAbsolutePath());
        final BinaryDictionary binaryDictionary = new BinaryDictionary(file.getAbsolutePath(),
                0 /* offset */, file.length(), true /* useFullEditDistance */,
                Locale.ENGLISH, dictName, false /* isUpdatable */);

        final HashSet<String> wordSet = new HashSet<>(words);
        final HashSet<Pair<String, String>> bigramSet = new HashSet<>();

        for (int i = 0; i < words.size(); i++) {
            final List<Integer> bigramList = bigrams.get(i);
            if (bigramList != null) {
                for (final Integer word1Index : bigramList) {
                    final String word1 = words.get(word1Index);
                    bigramSet.add(new Pair<>(words.get(i), word1));
                }
            }
        }
        int token = 0;
        do {
            final BinaryDictionary.GetNextWordPropertyResult result =
                    binaryDictionary.getNextWordProperty(token);
            final WordProperty wordProperty = result.mWordProperty;
            final String word0 = wordProperty.mWord;
            assertEquals(UNIGRAM_FREQ, wordProperty.mProbabilityInfo.mProbability);
            wordSet.remove(word0);
            if (wordProperty.mHasNgrams) {
                for (final WeightedString bigramTarget : wordProperty.getBigrams()) {
                    final String word1 = bigramTarget.mWord;
                    final Pair<String, String> bigram = new Pair<>(word0, word1);
                    assertTrue(bigramSet.contains(bigram));
                    bigramSet.remove(bigram);
                }
            }
            token = result.mNextToken;
        } while (token != 0);
        assertTrue(wordSet.isEmpty());
        assertTrue(bigramSet.isEmpty());
    }
}
