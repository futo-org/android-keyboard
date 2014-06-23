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

package com.android.inputmethod.latin.dicttool;

import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.ProbabilityInfo;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver2DictEncoder;

import junit.framework.TestCase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Unit tests for BinaryDictOffdeviceUtils
 */
public class BinaryDictOffdeviceUtilsTests extends TestCase {
    private static final int TEST_FREQ = 37; // Some arbitrary value unlikely to happen by chance

    public void testGetRawDictWorks() throws IOException, UnsupportedFormatException {
        final String VERSION = "1";
        final String LOCALE = "test";
        final String ID = "main:test";

        // Create a thrice-compressed dictionary file.
        final DictionaryOptions testOptions = new DictionaryOptions(new HashMap<String, String>());
        testOptions.mAttributes.put(DictionaryHeader.DICTIONARY_VERSION_KEY, VERSION);
        testOptions.mAttributes.put(DictionaryHeader.DICTIONARY_LOCALE_KEY, LOCALE);
        testOptions.mAttributes.put(DictionaryHeader.DICTIONARY_ID_KEY, ID);
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(), testOptions);
        dict.add("foo", new ProbabilityInfo(TEST_FREQ), null, false /* isNotAWord */);
        dict.add("fta", new ProbabilityInfo(1), null, false /* isNotAWord */);
        dict.add("ftb", new ProbabilityInfo(1), null, false /* isNotAWord */);
        dict.add("bar", new ProbabilityInfo(1), null, false /* isNotAWord */);
        dict.add("fool", new ProbabilityInfo(1), null, false /* isNotAWord */);

        final File dst = File.createTempFile("testGetRawDict", ".tmp");
        dst.deleteOnExit();
        try (final OutputStream out = Compress.getCompressedStream(
                Compress.getCompressedStream(
                        Compress.getCompressedStream(
                                new BufferedOutputStream(new FileOutputStream(dst)))))) {
            final DictEncoder dictEncoder = new Ver2DictEncoder(out);
            dictEncoder.writeDictionary(dict, new FormatOptions(2, false));
        }

        // Test for an actually compressed dictionary and its contents
        final BinaryDictOffdeviceUtils.DecoderChainSpec decodeSpec =
                BinaryDictOffdeviceUtils.getRawDictionaryOrNull(dst);
        for (final String step : decodeSpec.mDecoderSpec) {
            assertEquals("Wrong decode spec", BinaryDictOffdeviceUtils.COMPRESSION, step);
        }
        assertEquals("Wrong decode spec", 3, decodeSpec.mDecoderSpec.size());
        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(decodeSpec.mFile, 0,
                decodeSpec.mFile.length());
        final FusionDictionary resultDict =
                dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
        assertEquals("Wrong version attribute", VERSION, resultDict.mOptions.mAttributes.get(
                DictionaryHeader.DICTIONARY_VERSION_KEY));
        assertEquals("Wrong locale attribute", LOCALE, resultDict.mOptions.mAttributes.get(
                DictionaryHeader.DICTIONARY_LOCALE_KEY));
        assertEquals("Wrong id attribute", ID, resultDict.mOptions.mAttributes.get(
                DictionaryHeader.DICTIONARY_ID_KEY));
        assertEquals("Dictionary can't be read back correctly",
                FusionDictionary.findWordInTree(resultDict.mRootNodeArray, "foo").getProbability(),
                TEST_FREQ);
    }

    public void testGetRawDictFails() throws IOException {
        // Randomly create some 4k file containing garbage
        final File dst = File.createTempFile("testGetRawDict", ".tmp");
        dst.deleteOnExit();
        try (final OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
            for (int i = 0; i < 1024; ++i) {
                out.write(0x12345678);
            }
        }

        // Test that a random data file actually fails
        assertNull("Wrongly identified data file",
                BinaryDictOffdeviceUtils.getRawDictionaryOrNull(dst));

        final File gzDst = File.createTempFile("testGetRawDict", ".tmp");
        gzDst.deleteOnExit();
        try (final OutputStream gzOut = Compress.getCompressedStream(
                new BufferedOutputStream(new FileOutputStream(gzDst)))) {
            for (int i = 0; i < 1024; ++i) {
                gzOut.write(0x12345678);
            }
        }

        // Test that a compressed random data file actually fails
        assertNull("Wrongly identified data file",
                BinaryDictOffdeviceUtils.getRawDictionaryOrNull(gzDst));
    }
}
