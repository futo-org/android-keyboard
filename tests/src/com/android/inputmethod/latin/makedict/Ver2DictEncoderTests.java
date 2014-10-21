/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.android.inputmethod.latin.makedict.BinaryDictEncoderUtils.CodePointTable;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

/**
 * Unit tests for Ver2DictEncoder
 */
@LargeTest
public class Ver2DictEncoderTests extends AndroidTestCase {
    private static final String TAG = Ver2DictEncoderTests.class.getSimpleName();
    private static final int UNIGRAM_FREQ = 10;

    public void testCodePointTable() {
        final String[] wordSource = {"words", "used", "for", "testing", "a", "code point", "table"};
        final List<String> words = Arrays.asList(wordSource);
        final String correctCodePointTable = "eotdsanirfg bclwup";
        final String correctCodePointOccurrenceArrayString =
                "11641114101411531003114211021052972119111711121108110311021991981321";
        final String correctCodePointExpectedMapString = "343332363540383937464549484744414243";
        final String dictName = "codePointTableTest";
        final String dictVersion = Long.toString(System.currentTimeMillis());

        final FormatSpec.FormatOptions formatOptions =
                new FormatSpec.FormatOptions(FormatSpec.VERSION2);
        final FusionDictionary sourcedict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.makeDictionaryOptions(dictName, dictVersion, formatOptions));
        addUnigrams(sourcedict, words, null /* shortcutMap */);
        final CodePointTable codePointTable = Ver2DictEncoder.makeCodePointTable(sourcedict);

        // Check if mCodePointOccurrenceArray is correct
        final StringBuilder codePointOccurrenceArrayString = new StringBuilder();
        for (Entry<Integer, Integer> entry : codePointTable.mCodePointOccurrenceArray) {
            codePointOccurrenceArrayString.append(entry.getKey());
            codePointOccurrenceArrayString.append(entry.getValue());
        }
        assertEquals(correctCodePointOccurrenceArrayString,
                codePointOccurrenceArrayString.toString());

        // Check if mCodePointToOneByteCodeMap is correct
        final StringBuilder codePointExpectedMapString = new StringBuilder();
        for (int i = 0; i < correctCodePointTable.length(); ++i) {
            codePointExpectedMapString.append(codePointTable.mCodePointToOneByteCodeMap.get(
                    correctCodePointTable.codePointAt(i)));
        }
        assertEquals(correctCodePointExpectedMapString, codePointExpectedMapString.toString());
    }

    /**
     * Adds unigrams to the dictionary.
     */
    private void addUnigrams(final FusionDictionary dict, final List<String> words,
            final HashMap<String, List<String>> shortcutMap) {
        for (final String word : words) {
            final ArrayList<WeightedString> shortcuts = new ArrayList<>();
            if (shortcutMap != null && shortcutMap.containsKey(word)) {
                for (final String shortcut : shortcutMap.get(word)) {
                    shortcuts.add(new WeightedString(shortcut, UNIGRAM_FREQ));
                }
            }
            dict.add(word, new ProbabilityInfo(UNIGRAM_FREQ),
                    (shortcutMap == null) ? null : shortcuts, false /* isNotAWord */,
                    false /* isPossiblyOffensive */);
        }
    }
}
