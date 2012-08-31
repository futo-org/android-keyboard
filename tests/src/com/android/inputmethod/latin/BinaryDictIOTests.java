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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
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
import java.util.List;
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

    private static final String[] CHARACTERS =
        {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        };

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

    private List<String> generateWords(final int number, final Random random) {
        final Set<String> wordSet = CollectionUtils.newHashSet();
        while (wordSet.size() < number) {
            wordSet.add(generateWord(random.nextInt()));
        }
        return new ArrayList<String>(wordSet);
    }

    private void addUnigrams(final int number,
            final FusionDictionary dict,
            final List<String> words) {
        for (int i = 0; i < number; ++i) {
            final String word = words.get(i);
            dict.add(word, UNIGRAM_FREQ, null, false /* isNotAWord */);
        }
    }

    private void addBigrams(final FusionDictionary dict,
            final List<String> words,
            final SparseArray<List<Integer>> sparseArray) {
        for (int i = 0; i < sparseArray.size(); ++i) {
            final int w1 = sparseArray.keyAt(i);
            for (int w2 : sparseArray.valueAt(i)) {
                dict.setBigram(words.get(w1), words.get(w2), BIGRAM_FREQ);
            }
        }
    }

    private long timeWritingDictToFile(final String fileName,
            final FusionDictionary dict) {

        final File file = new File(getContext().getFilesDir(), fileName);
        long now = -1, diff = -1;

        try {
            final FileOutputStream out = new FileOutputStream(file);

            now = System.currentTimeMillis();
            BinaryDictInputOutput.writeDictionaryBinary(out, dict, 2);
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

    private void checkDictionary(final FusionDictionary dict,
            final List<String> words,
            final SparseArray<List<Integer>> bigrams) {
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
    }

    private long timeReadingAndCheckDict(final String fileName,
            final List<String> words,
            final SparseArray<List<Integer>> bigrams) {

        long now, diff = -1;

        try {
            final File file = new File(getContext().getFilesDir(), fileName);
            final FileInputStream inStream = new FileInputStream(file);
            final ByteBuffer buffer = inStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length());

            now = System.currentTimeMillis();

            final FusionDictionary dict =
                    BinaryDictInputOutput.readDictionaryBinary(buffer, null);

            diff = System.currentTimeMillis() - now;

            checkDictionary(dict, words, bigrams);
            return diff;

        } catch (IOException e) {
            Log.e(TAG, "raise IOException while reading file " + e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format: " + e);
        }

        return diff;
    }

    private String runReadAndWrite(final List<String> words,
            final SparseArray<List<Integer>> bigrams,
            final String message) {
        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(
                        new HashMap<String,String>(), false, false));

        final String fileName = generateWord((int)System.currentTimeMillis()) + ".dict";

        addUnigrams(words.size(), dict, words);
        addBigrams(dict, words, bigrams);
        // check original dictionary
        checkDictionary(dict, words, bigrams);

        final long write = timeWritingDictToFile(fileName, dict);
        final long read = timeReadingAndCheckDict(fileName, words, bigrams);
        deleteFile(fileName);

        return "PROF: read=" + read + "ms, write=" + write + "ms    :" + message;
    }

    private void deleteFile(final String fileName) {
        final File file = new File(getContext().getFilesDir(), fileName);
        file.delete();
    }

    public void testReadAndWrite() {
        final List<String> results = new ArrayList<String>();

        final Random random = new Random(123456);
        final List<String> words = generateWords(MAX_UNIGRAMS, random);
        final SparseArray<List<Integer>> emptyArray = CollectionUtils.newSparseArray();

        final SparseArray<List<Integer>> chain = CollectionUtils.newSparseArray();
        for (int i = 0; i < words.size(); ++i) chain.put(i, new ArrayList<Integer>());
        for (int i = 1; i < words.size(); ++i) chain.get(i-1).add(i);

        final SparseArray<List<Integer>> star = CollectionUtils.newSparseArray();
        final List<Integer> list0 = CollectionUtils.newArrayList();
        star.put(0, list0);
        for (int i = 1; i < words.size(); ++i) star.get(0).add(i);

        results.add(runReadAndWrite(words, emptyArray, "only unigram"));
        results.add(runReadAndWrite(words, chain, "chain"));
        results.add(runReadAndWrite(words, star, "star"));

        for (final String result : results) {
            Log.d(TAG, result);
        }
    }
}
