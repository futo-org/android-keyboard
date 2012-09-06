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

import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.PendingAttribute;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import android.test.AndroidTestCase;
import android.util.Log;
import android.util.SparseArray;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for BinaryDictInputOutput
 */
public class BinaryDictIOTests extends AndroidTestCase {
    private static final String TAG = BinaryDictIOTests.class.getSimpleName();
    private static final int MAX_UNIGRAMS = 1000;
    private static final int UNIGRAM_FREQ = 10;
    private static final int BIGRAM_FREQ = 50;
    private static final int TOLERANCE_OF_BIGRAM_FREQ = 5;

    private static final List<String> sWords = CollectionUtils.newArrayList();
    private static final SparseArray<List<Integer>> sEmptyBigrams =
            CollectionUtils.newSparseArray();
    private static final SparseArray<List<Integer>> sStarBigrams = CollectionUtils.newSparseArray();
    private static final SparseArray<List<Integer>> sChainBigrams =
            CollectionUtils.newSparseArray();

    private static final BinaryDictInputOutput.FormatOptions VERSION2 =
            new BinaryDictInputOutput.FormatOptions(2);

    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };

    public BinaryDictIOTests() {
        super();

        final Random random = new Random(123456);
        sWords.clear();
        generateWords(MAX_UNIGRAMS, random);

        for (int i = 0; i < sWords.size(); ++i) {
            sChainBigrams.put(i, new ArrayList<Integer>());
            if (i > 0) {
                sChainBigrams.get(i-1).add(i);
            }
        }

        sStarBigrams.put(0, new ArrayList<Integer>());
        for (int i = 1; i < sWords.size(); ++i) {
            sStarBigrams.get(0).add(i);
        }
    }

    // Utilities for test
    /**
     * Generates a random word.
     */
    private String generateWord(final int value) {
        final int lengthOfChars = CHARACTERS.length;
        StringBuilder builder = new StringBuilder("a");
        long lvalue = Math.abs((long)value);
        while (lvalue > 0) {
            builder.append(CHARACTERS[(int)(lvalue % lengthOfChars)]);
            lvalue /= lengthOfChars;
        }
        return builder.toString();
    }

    private void generateWords(final int number, final Random random) {
        final Set<String> wordSet = CollectionUtils.newHashSet();
        while (wordSet.size() < number) {
            wordSet.add(generateWord(random.nextInt()));
        }
        sWords.addAll(wordSet);
    }

    /**
     * Adds unigrams to the dictionary.
     */
    private void addUnigrams(final int number, final FusionDictionary dict,
            final List<String> words, final Map<String, List<String>> shortcutMap) {
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

    private long timeWritingDictToFile(final File file, final FusionDictionary dict) {

        long now = -1, diff = -1;

        try {
            final FileOutputStream out = new FileOutputStream(file);

            now = System.currentTimeMillis();
            BinaryDictInputOutput.writeDictionaryBinary(out, dict, VERSION2);
            diff = System.currentTimeMillis() - now;

            out.flush();
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file: " + e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException: " + e);
        }

        return diff;
    }

    private void checkDictionary(final FusionDictionary dict, final List<String> words,
            final SparseArray<List<Integer>> bigrams, final Map<String, List<String>> shortcutMap) {
        assertNotNull(dict);

        // check unigram
        for (final String word : words) {
            final CharGroup cg = FusionDictionary.findWordInTree(dict.mRoot, word);
            assertNotNull(cg);
        }

        // check bigram
        for (int i = 0; i < bigrams.size(); ++i) {
            final int w1 = bigrams.keyAt(i);
            for (final int w2 : bigrams.valueAt(i)) {
                final CharGroup cg = FusionDictionary.findWordInTree(dict.mRoot, words.get(w1));
                assertNotNull(words.get(w1) + "," + words.get(w2), cg.getBigram(words.get(w2)));
            }
        }

        // check shortcut
        if (shortcutMap != null) {
            for (final Map.Entry<String, List<String>> entry : shortcutMap.entrySet()) {
                final CharGroup group = FusionDictionary.findWordInTree(dict.mRoot, entry.getKey());
                for (final String word : entry.getValue()) {
                    assertNotNull("shortcut not found: " + entry.getKey() + ", " + word,
                            group.getShortcut(word));
                }
            }
        }
    }

    // Tests for readDictionaryBinary and writeDictionaryBinary

    private long timeReadingAndCheckDict(final File file, final List<String> words,
            final SparseArray<List<Integer>> bigrams, final Map<String, List<String>> shortcutMap) {

        long now, diff = -1;

        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            final ByteBuffer buffer = inStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length());

            now = System.currentTimeMillis();
            final FusionDictionary dict =
                    BinaryDictInputOutput.readDictionaryBinary(buffer, null);
            diff = System.currentTimeMillis() - now;

            checkDictionary(dict, words, bigrams, shortcutMap);
            return diff;

        } catch (IOException e) {
            Log.e(TAG, "raise IOException while reading file " + e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format: " + e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }

        return diff;
    }

    // Tests for readDictionaryBinary and writeDictionaryBinary
    private String runReadAndWrite(final List<String> words,
            final SparseArray<List<Integer>> bigrams, final Map<String, List<String>> shortcuts,
            final String message) {
        File file = null;
        try {
            file = File.createTempFile("runReadAndWrite", ".dict");
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        }
        assertNotNull(file);

        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(
                        new HashMap<String,String>(), false, false));
        addUnigrams(words.size(), dict, words, shortcuts);
        addBigrams(dict, words, bigrams);
        checkDictionary(dict, words, bigrams, shortcuts);

        final long write = timeWritingDictToFile(file, dict);
        final long read = timeReadingAndCheckDict(file, words, bigrams, shortcuts);

        return "PROF: read=" + read + "ms, write=" + write + "ms    :" + message;
    }

    public void testReadAndWrite() {
        final List<String> results = new ArrayList<String>();

        results.add(runReadAndWrite(sWords, sEmptyBigrams, null /* shortcuts */ , "unigram"));
        results.add(runReadAndWrite(sWords, sChainBigrams, null /* shortcuts */ , "chain"));
        results.add(runReadAndWrite(sWords, sStarBigrams, null /* shortcuts */ , "star"));

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }

    // Tests for readUnigramsAndBigramsBinary

    private void checkWordMap(final List<String> expectedWords,
            final SparseArray<List<Integer>> expectedBigrams,
            final Map<Integer, String> resultWords,
            final Map<Integer, Integer> resultFrequencies,
            final Map<Integer, ArrayList<PendingAttribute>> resultBigrams) {
        // check unigrams
        final Set<String> actualWordsSet = new HashSet<String>(resultWords.values());
        final Set<String> expectedWordsSet = new HashSet<String>(expectedWords);
        assertEquals(actualWordsSet, expectedWordsSet);

        for (int freq : resultFrequencies.values()) {
            assertEquals(freq, UNIGRAM_FREQ);
        }

        // check bigrams
        final Map<String, List<String>> expBigrams = new HashMap<String, List<String>>();
        for (int i = 0; i < expectedBigrams.size(); ++i) {
            final String word1 = expectedWords.get(expectedBigrams.keyAt(i));
            for (int w2 : expectedBigrams.valueAt(i)) {
                if (expBigrams.get(word1) == null) {
                    expBigrams.put(word1, new ArrayList<String>());
                }
                expBigrams.get(word1).add(expectedWords.get(w2));
            }
        }

        final Map<String, List<String>> actBigrams = new HashMap<String, List<String>>();
        for (Entry<Integer, ArrayList<PendingAttribute>> entry : resultBigrams.entrySet()) {
            final String word1 = resultWords.get(entry.getKey());
            final int unigramFreq = resultFrequencies.get(entry.getKey());
            for (PendingAttribute attr : entry.getValue()) {
                final String word2 = resultWords.get(attr.mAddress);
                if (actBigrams.get(word1) == null) {
                    actBigrams.put(word1, new ArrayList<String>());
                }
                actBigrams.get(word1).add(word2);

                final int bigramFreq = BinaryDictInputOutput.reconstructBigramFrequency(
                        unigramFreq, attr.mFrequency);
                assertTrue(Math.abs(bigramFreq - BIGRAM_FREQ) < TOLERANCE_OF_BIGRAM_FREQ);
            }
        }

        assertEquals(actBigrams, expBigrams);
    }

    private long timeAndCheckReadUnigramsAndBigramsBinary(final File file, final List<String> words,
            final SparseArray<List<Integer>> bigrams) {
        FileInputStream inStream = null;

        final Map<Integer, String> resultWords = CollectionUtils.newTreeMap();
        final Map<Integer, ArrayList<PendingAttribute>> resultBigrams =
                CollectionUtils.newTreeMap();
        final Map<Integer, Integer> resultFreqs = CollectionUtils.newTreeMap();

        long now = -1, diff = -1;
        try {
            inStream = new FileInputStream(file);
            final ByteBuffer buffer = inStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length());

            now = System.currentTimeMillis();
            BinaryDictInputOutput.readUnigramsAndBigramsBinary(
                    new BinaryDictInputOutput.ByteBufferWrapper(buffer), resultWords, resultFreqs,
                    resultBigrams);
            diff = System.currentTimeMillis() - now;
            checkWordMap(words, bigrams, resultWords, resultFreqs, resultBigrams);
        } catch (IOException e) {
            Log.e(TAG, "IOException " + e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "UnsupportedFormatException: " + e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }

        return diff;
    }

    private String runReadUnigramsAndBigramsBinary(final List<String> words,
            final SparseArray<List<Integer>> bigrams, final String message) {
        File file = null;
        try {
            file = File.createTempFile("runReadUnigrams", ".dict");
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        }
        assertNotNull(file);

        // making the dictionary from lists of words.
        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(
                        new HashMap<String, String>(), false, false));
        addUnigrams(words.size(), dict, words, null /* shortcutMap */);
        addBigrams(dict, words, bigrams);

        timeWritingDictToFile(file, dict);

        long wordMap = timeAndCheckReadUnigramsAndBigramsBinary(file, words, bigrams);
        long fullReading = timeReadingAndCheckDict(file, words, bigrams, null /* shortcutMap */);

        return "readDictionaryBinary=" + fullReading + ", readUnigramsAndBigramsBinary=" + wordMap
                + " : " + message;
    }

    public void testReadUnigramsAndBigramsBinary() {
        final List<String> results = new ArrayList<String>();

        results.add(runReadUnigramsAndBigramsBinary(sWords, sEmptyBigrams, "unigram"));
        results.add(runReadUnigramsAndBigramsBinary(sWords, sChainBigrams, "chain"));
        results.add(runReadUnigramsAndBigramsBinary(sWords, sStarBigrams, "star"));

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }
}
