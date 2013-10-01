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
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.util.SparseArray;

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final int DEFAULT_MAX_UNIGRAMS = 100;
    private static final int DEFAULT_CODE_POINT_SET_SIZE = 50;
    private static final int UNIGRAM_FREQ = 10;
    private static final int BIGRAM_FREQ = 50;
    private static final int TOLERANCE_OF_BIGRAM_FREQ = 5;
    private static final int NUM_OF_NODES_HAVING_SHORTCUTS = 50;
    private static final int NUM_OF_SHORTCUTS = 5;

    private static final int USE_BYTE_ARRAY = 1;
    private static final int USE_BYTE_BUFFER = 2;

    private static final ArrayList<String> sWords = CollectionUtils.newArrayList();
    private static final SparseArray<List<Integer>> sEmptyBigrams =
            CollectionUtils.newSparseArray();
    private static final SparseArray<List<Integer>> sStarBigrams = CollectionUtils.newSparseArray();
    private static final SparseArray<List<Integer>> sChainBigrams =
            CollectionUtils.newSparseArray();
    private static final HashMap<String, List<String>> sShortcuts = CollectionUtils.newHashMap();

    private static final FormatSpec.FormatOptions VERSION2 = new FormatSpec.FormatOptions(2);
    private static final FormatSpec.FormatOptions VERSION3_WITHOUT_DYNAMIC_UPDATE =
            new FormatSpec.FormatOptions(3, false /* supportsDynamicUpdate */);
    private static final FormatSpec.FormatOptions VERSION3_WITH_DYNAMIC_UPDATE =
            new FormatSpec.FormatOptions(3, true /* supportsDynamicUpdate */);
    private static final FormatSpec.FormatOptions VERSION4_WITHOUT_DYNAMIC_UPDATE =
            new FormatSpec.FormatOptions(4, false /* supportsDynamicUpdate */);
    private static final FormatSpec.FormatOptions VERSION4_WITH_DYNAMIC_UPDATE =
            new FormatSpec.FormatOptions(4, true /* supportsDynamicUpdate */);

    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";

    public BinaryDictDecoderEncoderTests() {
        this(System.currentTimeMillis(), DEFAULT_MAX_UNIGRAMS);
    }

    public BinaryDictDecoderEncoderTests(final long seed, final int maxUnigrams) {
        super();
        Log.e(TAG, "Testing dictionary: seed is " + seed);
        final Random random = new Random(seed);
        sWords.clear();
        final int[] codePointSet = CodePointUtils.generateCodePointSet(DEFAULT_CODE_POINT_SET_SIZE,
                random);
        generateWords(maxUnigrams, random, codePointSet);

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

        sShortcuts.clear();
        for (int i = 0; i < NUM_OF_NODES_HAVING_SHORTCUTS; ++i) {
            final int from = Math.abs(random.nextInt()) % sWords.size();
            sShortcuts.put(sWords.get(from), new ArrayList<String>());
            for (int j = 0; j < NUM_OF_SHORTCUTS; ++j) {
                final int to = Math.abs(random.nextInt()) % sWords.size();
                sShortcuts.get(sWords.get(from)).add(sWords.get(to));
            }
        }
    }

    private DictEncoder getDictEncoder(final File file, final FormatOptions formatOptions) {
        if (formatOptions.mVersion == FormatSpec.VERSION4) {
            return new Ver4DictEncoder(getContext().getCacheDir());
        } else if (formatOptions.mVersion == 3 || formatOptions.mVersion == 2) {
            return new Ver3DictEncoder(file);
        } else {
            throw new RuntimeException("The format option has a wrong version : "
                    + formatOptions.mVersion);
        }
    }

    private void generateWords(final int number, final Random random, final int[] codePointSet) {
        final Set<String> wordSet = CollectionUtils.newHashSet();
        while (wordSet.size() < number) {
            wordSet.add(CodePointUtils.generateWord(random, codePointSet));
        }
        sWords.addAll(wordSet);
    }

    /**
     * Adds unigrams to the dictionary.
     */
    private void addUnigrams(final int number, final FusionDictionary dict,
            final List<String> words, final HashMap<String, List<String>> shortcutMap) {
        for (int i = 0; i < number; ++i) {
            final String word = words.get(i);
            final ArrayList<WeightedString> shortcuts = CollectionUtils.newArrayList();
            if (shortcutMap != null && shortcutMap.containsKey(word)) {
                for (final String shortcut : shortcutMap.get(word)) {
                    shortcuts.add(new WeightedString(shortcut, UNIGRAM_FREQ));
                }
            }
            dict.add(word, UNIGRAM_FREQ, (shortcutMap == null) ? null : shortcuts,
                    false /* isNotAWord */);
        }
    }

    private void addBigrams(final FusionDictionary dict,
            final List<String> words,
            final SparseArray<List<Integer>> bigrams) {
        for (int i = 0; i < bigrams.size(); ++i) {
            final int w1 = bigrams.keyAt(i);
            for (int w2 : bigrams.valueAt(i)) {
                dict.setBigram(words.get(w1), words.get(w2), BIGRAM_FREQ);
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

    private long timeWritingDictToFile(final File file, final FusionDictionary dict,
            final FormatSpec.FormatOptions formatOptions) {

        long now = -1, diff = -1;

        try {
            final DictEncoder dictEncoder = getDictEncoder(file, formatOptions);

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

    private void checkDictionary(final FusionDictionary dict, final List<String> words,
            final SparseArray<List<Integer>> bigrams,
            final HashMap<String, List<String>> shortcutMap) {
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

        // check shortcut
        if (shortcutMap != null) {
            for (final Entry<String, List<String>> entry : shortcutMap.entrySet()) {
                assertTrue(words.contains(entry.getKey()));
                final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray,
                        entry.getKey());
                for (final String word : entry.getValue()) {
                    assertNotNull("shortcut not found: " + entry.getKey() + ", " + word,
                            ptNode.getShortcut(word));
                }
            }
        }
    }

    private String outputOptions(final int bufferType,
            final FormatSpec.FormatOptions formatOptions) {
        String result = " : buffer type = "
                + ((bufferType == USE_BYTE_BUFFER) ? "byte buffer" : "byte array");
        result += " : version = " + formatOptions.mVersion;
        return result + ", supportsDynamicUpdate = " + formatOptions.mSupportsDynamicUpdate;
    }

    private DictionaryOptions getDictionaryOptions(final String id, final String version) {
        final DictionaryOptions options = new DictionaryOptions(new HashMap<String, String>(),
                false, false);
        options.mAttributes.put("version", version);
        options.mAttributes.put("dictionary", id);
        return options;
    }

    private File setUpDictionaryFile(final String name, final String version) {
        File file = null;
        try {
            file = new File(getContext().getCacheDir(), name + "." + version
                    + TEST_DICT_FILE_EXTENSION);
            file.createNewFile();
        } catch (IOException e) {
            // do nothing
        }
        assertTrue("Failed to create the dictionary file.", file.exists());
        return file;
    }

    private DictDecoder getDictDecoder(final File file, final int bufferType,
            final FormatOptions formatOptions, final DictionaryOptions dictOptions) {
        if (formatOptions.mVersion == FormatSpec.VERSION4) {
            final FileHeader header = new FileHeader(0, dictOptions, formatOptions);
            return FormatSpec.getDictDecoder(new File(getContext().getCacheDir(),
                    header.getId() + "." + header.getVersion()), bufferType);
        } else {
            return FormatSpec.getDictDecoder(file, bufferType);
        }
    }
    // Tests for readDictionaryBinary and writeDictionaryBinary

    private long timeReadingAndCheckDict(final File file, final List<String> words,
            final SparseArray<List<Integer>> bigrams,
            final HashMap<String, List<String>> shortcutMap, final int bufferType,
            final FormatOptions formatOptions, final DictionaryOptions dictOptions) {
        long now, diff = -1;

        FusionDictionary dict = null;
        try {
            final DictDecoder dictDecoder = getDictDecoder(file, bufferType, formatOptions,
                    dictOptions);
            now = System.currentTimeMillis();
            dict = dictDecoder.readDictionaryBinary(null, false /* deleteDictIfBroken */);
            diff  = System.currentTimeMillis() - now;
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }

        checkDictionary(dict, words, bigrams, shortcutMap);
        return diff;
    }

    // Tests for readDictionaryBinary and writeDictionaryBinary
    private String runReadAndWrite(final List<String> words,
            final SparseArray<List<Integer>> bigrams, final HashMap<String, List<String>> shortcuts,
            final int bufferType, final FormatSpec.FormatOptions formatOptions,
            final String message) {

        final String dictName = "runReadAndWrite";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = setUpDictionaryFile(dictName, dictVersion);

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(dictName, dictVersion));
        addUnigrams(words.size(), dict, words, shortcuts);
        addBigrams(dict, words, bigrams);
        checkDictionary(dict, words, bigrams, shortcuts);

        final long write = timeWritingDictToFile(file, dict, formatOptions);
        final long read = timeReadingAndCheckDict(file, words, bigrams, shortcuts, bufferType,
                formatOptions, dict.mOptions);

        return "PROF: read=" + read + "ms, write=" + write + "ms :" + message
                + " : " + outputOptions(bufferType, formatOptions);
    }

    private void runReadAndWriteTests(final List<String> results, final int bufferType,
            final FormatSpec.FormatOptions formatOptions) {
        results.add(runReadAndWrite(sWords, sEmptyBigrams, null /* shortcuts */, bufferType,
                formatOptions, "unigram"));
        results.add(runReadAndWrite(sWords, sChainBigrams, null /* shortcuts */, bufferType,
                formatOptions, "chain"));
        results.add(runReadAndWrite(sWords, sStarBigrams, null /* shortcuts */, bufferType,
                formatOptions, "star"));
        results.add(runReadAndWrite(sWords, sEmptyBigrams, sShortcuts, bufferType, formatOptions,
                "unigram with shortcuts"));
        results.add(runReadAndWrite(sWords, sChainBigrams, sShortcuts, bufferType, formatOptions,
                "chain with shortcuts"));
        results.add(runReadAndWrite(sWords, sStarBigrams, sShortcuts, bufferType, formatOptions,
                "star with shortcuts"));
    }

    // Unit test for CharEncoding.readString and CharEncoding.writeString.
    public void testCharEncoding() {
        // the max length of a word in sWords is less than 50.
        // See generateWords.
        final byte[] buffer = new byte[50 * 3];
        final DictBuffer dictBuffer = new ByteArrayDictBuffer(buffer);
        for (final String word : sWords) {
            Log.d("testReadAndWriteString", "write : " + word);
            Arrays.fill(buffer, (byte)0);
            CharEncoding.writeString(buffer, 0, word);
            dictBuffer.position(0);
            final String str = CharEncoding.readString(dictBuffer);
            assertEquals(word, str);
        }
    }

    public void testReadAndWriteWithByteBuffer() {
        final List<String> results = CollectionUtils.newArrayList();

        runReadAndWriteTests(results, USE_BYTE_BUFFER, VERSION2);
        runReadAndWriteTests(results, USE_BYTE_BUFFER, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_BUFFER, VERSION3_WITH_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_BUFFER, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_BUFFER, VERSION4_WITH_DYNAMIC_UPDATE);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    public void testReadAndWriteWithByteArray() {
        final List<String> results = CollectionUtils.newArrayList();

        runReadAndWriteTests(results, USE_BYTE_ARRAY, VERSION2);
        runReadAndWriteTests(results, USE_BYTE_ARRAY, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_ARRAY, VERSION3_WITH_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_ARRAY, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runReadAndWriteTests(results, USE_BYTE_ARRAY, VERSION4_WITH_DYNAMIC_UPDATE);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    // Tests for readUnigramsAndBigramsBinary

    private void checkWordMap(final List<String> expectedWords,
            final SparseArray<List<Integer>> expectedBigrams,
            final TreeMap<Integer, String> resultWords,
            final TreeMap<Integer, Integer> resultFrequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> resultBigrams) {
        // check unigrams
        final Set<String> actualWordsSet = new HashSet<String>(resultWords.values());
        final Set<String> expectedWordsSet = new HashSet<String>(expectedWords);
        assertEquals(actualWordsSet, expectedWordsSet);

        for (int freq : resultFrequencies.values()) {
            assertEquals(freq, UNIGRAM_FREQ);
        }

        // check bigrams
        final HashMap<String, List<String>> expBigrams = new HashMap<String, List<String>>();
        for (int i = 0; i < expectedBigrams.size(); ++i) {
            final String word1 = expectedWords.get(expectedBigrams.keyAt(i));
            for (int w2 : expectedBigrams.valueAt(i)) {
                if (expBigrams.get(word1) == null) {
                    expBigrams.put(word1, new ArrayList<String>());
                }
                expBigrams.get(word1).add(expectedWords.get(w2));
            }
        }

        final HashMap<String, List<String>> actBigrams = new HashMap<String, List<String>>();
        for (Entry<Integer, ArrayList<PendingAttribute>> entry : resultBigrams.entrySet()) {
            final String word1 = resultWords.get(entry.getKey());
            final int unigramFreq = resultFrequencies.get(entry.getKey());
            for (PendingAttribute attr : entry.getValue()) {
                final String word2 = resultWords.get(attr.mAddress);
                if (actBigrams.get(word1) == null) {
                    actBigrams.put(word1, new ArrayList<String>());
                }
                actBigrams.get(word1).add(word2);

                final int bigramFreq = BinaryDictIOUtils.reconstructBigramFrequency(
                        unigramFreq, attr.mFrequency);
                assertTrue(Math.abs(bigramFreq - BIGRAM_FREQ) < TOLERANCE_OF_BIGRAM_FREQ);
            }
        }

        assertEquals(actBigrams, expBigrams);
    }

    private long timeAndCheckReadUnigramsAndBigramsBinary(final File file, final List<String> words,
            final SparseArray<List<Integer>> bigrams, final int bufferType,
            final FormatOptions formatOptions, final DictionaryOptions dictOptions) {
        FileInputStream inStream = null;

        final TreeMap<Integer, String> resultWords = CollectionUtils.newTreeMap();
        final TreeMap<Integer, ArrayList<PendingAttribute>> resultBigrams =
                CollectionUtils.newTreeMap();
        final TreeMap<Integer, Integer> resultFreqs = CollectionUtils.newTreeMap();

        long now = -1, diff = -1;
        try {
            final DictDecoder dictDecoder = getDictDecoder(file, bufferType, formatOptions,
                    dictOptions);
            now = System.currentTimeMillis();
            dictDecoder.readUnigramsAndBigramsBinary(resultWords, resultFreqs, resultBigrams);
            diff = System.currentTimeMillis() - now;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }

        checkWordMap(words, bigrams, resultWords, resultFreqs, resultBigrams);
        return diff;
    }

    private String runReadUnigramsAndBigramsBinary(final ArrayList<String> words,
            final SparseArray<List<Integer>> bigrams, final int bufferType,
            final FormatSpec.FormatOptions formatOptions, final String message) {
        final String dictName = "runReadUnigrams";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = setUpDictionaryFile(dictName, dictVersion);

        // making the dictionary from lists of words.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(dictName, dictVersion));
        addUnigrams(words.size(), dict, words, null /* shortcutMap */);
        addBigrams(dict, words, bigrams);

        timeWritingDictToFile(file, dict, formatOptions);

        long wordMap = timeAndCheckReadUnigramsAndBigramsBinary(file, words, bigrams, bufferType,
                formatOptions, dict.mOptions);
        long fullReading = timeReadingAndCheckDict(file, words, bigrams, null /* shortcutMap */,
                bufferType, formatOptions, dict.mOptions);

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
        final ArrayList<String> results = CollectionUtils.newArrayList();

        runReadUnigramsAndBigramsTests(results, USE_BYTE_BUFFER, VERSION2);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_BUFFER, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_BUFFER, VERSION3_WITH_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_BUFFER, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_BUFFER, VERSION4_WITH_DYNAMIC_UPDATE);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    public void testReadUnigramsAndBigramsBinaryWithByteArray() {
        final ArrayList<String> results = CollectionUtils.newArrayList();

        runReadUnigramsAndBigramsTests(results, USE_BYTE_ARRAY, VERSION2);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_ARRAY, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_ARRAY, VERSION3_WITH_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_ARRAY, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runReadUnigramsAndBigramsTests(results, USE_BYTE_ARRAY, VERSION4_WITH_DYNAMIC_UPDATE);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    // Tests for getTerminalPosition
    private String getWordFromBinary(final DictDecoder dictDecoder, final int address) {
        if (dictDecoder.getPosition() != 0) dictDecoder.setPosition(0);

        FileHeader fileHeader = null;
        try {
            fileHeader = dictDecoder.readHeader();
        } catch (IOException e) {
            return null;
        } catch (UnsupportedFormatException e) {
            return null;
        }
        if (fileHeader == null) return null;
        return BinaryDictDecoderUtils.getWordAtPosition(dictDecoder, fileHeader.mHeaderSize,
                address, fileHeader.mFormatOptions).mWord;
    }

    private long checkGetTerminalPosition(final DictDecoder dictDecoder, final String word,
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
        final File file = setUpDictionaryFile(dictName, dictVersion);

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(dictName, dictVersion));
        addUnigrams(sWords.size(), dict, sWords, null /* shortcutMap */);
        addBigrams(dict, words, bigrams);
        timeWritingDictToFile(file, dict, formatOptions);

        final DictDecoder dictDecoder = getDictDecoder(file, DictDecoder.USE_BYTEARRAY,
                formatOptions, dict.mOptions);
        try {
            dictDecoder.openDictBuffer();
        } catch (IOException e) {
            // ignore
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
        final Random random = new Random((int)System.currentTimeMillis());
        final int[] codePointSet = CodePointUtils.generateCodePointSet(DEFAULT_CODE_POINT_SET_SIZE,
                random);
        for (int i = 0; i < 1000; ++i) {
            final String word = CodePointUtils.generateWord(random, codePointSet);
            if (sWords.indexOf(word) != -1) continue;
            checkGetTerminalPosition(dictDecoder, word, false);
        }
    }

    private void runGetTerminalPositionTests(final int bufferType,
            final FormatOptions formatOptions) {
        runGetTerminalPosition(sWords, sEmptyBigrams, bufferType, formatOptions, "unigram");
    }

    public void testGetTerminalPosition() {
        final ArrayList<String> results = CollectionUtils.newArrayList();

        runGetTerminalPositionTests(USE_BYTE_ARRAY, VERSION2);
        runGetTerminalPositionTests(USE_BYTE_ARRAY, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_ARRAY, VERSION3_WITH_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_ARRAY, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_ARRAY, VERSION4_WITH_DYNAMIC_UPDATE);

        runGetTerminalPositionTests(USE_BYTE_BUFFER, VERSION2);
        runGetTerminalPositionTests(USE_BYTE_BUFFER, VERSION3_WITHOUT_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_BUFFER, VERSION3_WITH_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_BUFFER, VERSION4_WITHOUT_DYNAMIC_UPDATE);
        runGetTerminalPositionTests(USE_BYTE_BUFFER, VERSION4_WITH_DYNAMIC_UPDATE);

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    private void runTestDeleteWord(final FormatOptions formatOptions) {
        final String dictName = "testDeleteWord";
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File file = setUpDictionaryFile(dictName, dictVersion);

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(
                        new HashMap<String, String>(), false, false));
        addUnigrams(sWords.size(), dict, sWords, null /* shortcutMap */);
        timeWritingDictToFile(file, dict, formatOptions);

        final DictUpdater dictUpdater;
        if (formatOptions.mVersion == 3) {
            dictUpdater = new Ver3DictUpdater(file, DictDecoder.USE_WRITABLE_BYTEBUFFER);
        } else if (formatOptions.mVersion == 4) {
            dictUpdater = new Ver4DictUpdater(file, DictDecoder.USE_WRITABLE_BYTEBUFFER);
        } else {
            throw new RuntimeException("DictUpdater for version " + formatOptions.mVersion
                    + " doesn't exist.");
        }

        try {
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD,
                    dictUpdater.getTerminalPosition(sWords.get(0)));
            dictUpdater.deleteWord(sWords.get(0));
            assertEquals(FormatSpec.NOT_VALID_WORD,
                    dictUpdater.getTerminalPosition(sWords.get(0)));

            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD,
                    dictUpdater.getTerminalPosition(sWords.get(5)));
            dictUpdater.deleteWord(sWords.get(5));
            assertEquals(FormatSpec.NOT_VALID_WORD,
                    dictUpdater.getTerminalPosition(sWords.get(5)));
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }
    }

    public void testDeleteWord() {
        runTestDeleteWord(VERSION3_WITH_DYNAMIC_UPDATE);
        runTestDeleteWord(VERSION4_WITH_DYNAMIC_UPDATE);
    }
}
