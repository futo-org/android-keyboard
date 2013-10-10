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

import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.latin.makedict.CodePointUtils;

import java.util.Random;

@LargeTest
public class LatinImeStressTests extends InputTestsBase {
    public void testSwitchLanguagesAndInputLatinRandomCodePoints() {
        final String[] locales = {"en_US", "de", "el", "es", "fi", "it", "nl", "pt", "ru"};
        final int switchCount = 50;
        final int maxWordCountToTypeInEachIteration = 20;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int codePointSetSize = 30;
        final int[] codePointSet = CodePointUtils.LATIN_ALPHABETS_LOWER;
        for (int i = 0; i < switchCount; ++i) {
            changeLanguageWithoutWait(locales[random.nextInt(locales.length)]);
            final int wordCount = random.nextInt(maxWordCountToTypeInEachIteration);
            for (int j = 0; j < wordCount; ++j) {
                final String word = CodePointUtils.generateWord(random, codePointSet);
                type(word);
            }
        }
    }
    public void testSwitchLanguagesAndInputRandamCodePoints() {
        final String[] locales = {"en_US", "de", "el", "es", "fi", "it", "nl", "pt", "ru"};
        final int switchCount = 50;
        final int maxWordCountToTypeInEachIteration = 20;
        final long seed = System.currentTimeMillis();
        final Random random = new Random(seed);
        final int codePointSetSize = 30;
        final int[] codePointSet = CodePointUtils.generateCodePointSet(codePointSetSize, random);
        for (int i = 0; i < switchCount; ++i) {
            changeLanguageWithoutWait(locales[random.nextInt(locales.length)]);
            final int wordCount = random.nextInt(maxWordCountToTypeInEachIteration);
            for (int j = 0; j < wordCount; ++j) {
                final String word = CodePointUtils.generateWord(random, codePointSet);
                type(word);
            }
        }
    }
}
