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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.WordProperty;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Unit tests for FusionDictionary.
 */
public class FusionDictionaryTest extends TestCase {
    private static final ArrayList<String> sWords = new ArrayList<>();
    private static final int MAX_UNIGRAMS = 1000;

    private void prepare(final long seed) {
        System.out.println("Seed is " + seed);
        final Random random = new Random(seed);
        sWords.clear();
        generateWords(MAX_UNIGRAMS, random);
    }

    /**
     * Generates a random word.
     */
    private String generateWord(final Random random) {
        StringBuilder builder = new StringBuilder("a");
        int count = random.nextInt() % 30;
        while (count > 0) {
            final long r = Math.abs(random.nextInt());
            if (r < 0) continue;
            // Don't insert 0~20, but insert any other code point.
            // Code points are in the range 0~0x10FFFF.
            if (builder.length() < 7)
                builder.appendCodePoint((int)(20 +r % (0x10FFFF - 20)));
            --count;
        }
        if (builder.length() == 1) return generateWord(random);
        return builder.toString();
    }

    private void generateWords(final int number, final Random random) {
        while (sWords.size() < number) {
            sWords.add(generateWord(random));
        }
    }

    private static void checkDictionary(final FusionDictionary dict, final ArrayList<String> words,
            final int limit) {
        assertNotNull(dict);
        int count = limit;
        for (final String word : words) {
            if (--count < 0) return;
            final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, word);
            assertNotNull(ptNode);
        }
    }

    private static String dumpWord(final String word) {
        final StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            sb.append(word.codePointAt(i));
            sb.append(" ");
        }
        return sb.toString();
    }

    private static void dumpDict(final FusionDictionary dict) {
        for (WordProperty wordProperty : dict) {
            System.out.println("Word " + dumpWord(wordProperty.mWord));
        }
    }

    // Test the flattened array contains the expected number of nodes, and
    // that it does not contain any duplicates.
    public void testFusion() {
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new DictionaryOptions(new HashMap<String, String>()));
        final long time = System.currentTimeMillis();
        prepare(time);
        for (int i = 0; i < sWords.size(); ++i) {
            System.out.println("Adding in pos " + i + " : " + dumpWord(sWords.get(i)));
            dict.add(sWords.get(i), new ProbabilityInfo(180), false,
                    false /* isPossiblyOffensive */);
            dumpDict(dict);
            checkDictionary(dict, sWords, i);
        }
    }
}
