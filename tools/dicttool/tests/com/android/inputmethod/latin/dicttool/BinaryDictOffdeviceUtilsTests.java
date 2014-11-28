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

import com.android.inputmethod.latin.common.CodePointUtils;
import com.android.inputmethod.latin.dicttool.BinaryDictOffdeviceUtils;
import com.android.inputmethod.latin.dicttool.Compress;
import com.android.inputmethod.latin.dicttool.Crypt;
import com.android.inputmethod.latin.dicttool.BinaryDictOffdeviceUtils.DecoderChainSpec;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.BinaryDictUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for BinaryDictOffdeviceUtils
 */
public class BinaryDictOffdeviceUtilsTests extends TestCase {
    private static final int TEST_FREQ = 37; // Some arbitrary value unlikely to happen by chance
    private static final int CODE_POINT_SET_SIZE = 300;
    final Random mRandom;
    private static final ArrayList<String> sWords = new ArrayList<>();

    public BinaryDictOffdeviceUtilsTests(final long seed, final int maxUnigrams) {
        super();
        mRandom = new Random(seed);
        sWords.clear();
        generateWords(maxUnigrams, mRandom);
    }

    private static void generateWords(final int maxUnigrams, final Random random) {
        final int[] codePointSet = CodePointUtils.generateCodePointSet(
                CODE_POINT_SET_SIZE, random);
        final Set<String> wordSet = new HashSet<>();
        while (wordSet.size() < maxUnigrams) {
            wordSet.add(CodePointUtils.generateWord(random, codePointSet));
        }
        sWords.addAll(wordSet);
    }

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
        dict.add("foo", new ProbabilityInfo(TEST_FREQ), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("fta", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("ftb", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("bar", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("fool", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);

        final File dst = File.createTempFile("testGetRawDict", ".tmp");
        dst.deleteOnExit();
        try (final OutputStream out = Compress.getCompressedStream(
                new BufferedOutputStream(new FileOutputStream(dst)))) {
            final DictEncoder dictEncoder = new Ver2DictEncoder(out);
            dictEncoder.writeDictionary(dict, new FormatOptions(FormatSpec.VERSION202, false));
        }

        // Test for an actually compressed dictionary and its contents
        final BinaryDictOffdeviceUtils.DecoderChainSpec<File> decodeSpec =
                BinaryDictOffdeviceUtils.getRawDictionaryOrNull(dst);
        assertEquals("Wrong decode spec", "raw > compression", decodeSpec.describeChain());
        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(decodeSpec.mResult, 0,
                decodeSpec.mResult.length());
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

    public void runTestHeaderReaderProcessorWithOneSpec(final boolean compress, final boolean crypt)
            throws IOException, UnsupportedFormatException {
        final String dictName = "testHeaderReaderProcessor";
        final FormatOptions formatOptions = BinaryDictUtils.STATIC_OPTIONS;
        final int MAX_NUMBER_OF_OPTIONS_TO_ADD = 5;
        final HashMap<String, String> options = new HashMap<>();
        // Required attributes
        options.put("dictionary", "main:en_US");
        options.put("locale", "en_US");
        options.put("version", Integer.toString(mRandom.nextInt()));
        // Add some random options for test
        final int numberOfOptionsToAdd = mRandom.nextInt() % (MAX_NUMBER_OF_OPTIONS_TO_ADD + 1);
        for (int i = 0; i < numberOfOptionsToAdd; ++i) {
            options.put(sWords.get(2 * i), sWords.get(2 * 1 + 1));
        }
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new DictionaryOptions(options));

        for (int i = 0; i < sWords.size(); ++i) {
            final String word = sWords.get(i);
            dict.add(word, new ProbabilityInfo(TEST_FREQ), null /* shortcuts */,
                    false /* isNotAWord */, false /* isPossiblyOffensive */);
        }

        File file = File.createTempFile(dictName, ".tmp");
        final DictEncoder dictEncoder = BinaryDictUtils.getDictEncoder(file, formatOptions);
        dictEncoder.writeDictionary(dict, formatOptions);

        if (compress) {
            final File rawFile = file;
            file = File.createTempFile(dictName + ".compress", ".tmp");
            final Compress.Compressor compressCommand = new Compress.Compressor();
            compressCommand.setArgs(new String[] { rawFile.getPath(), file.getPath() });
            compressCommand.run();
        }
        if (crypt) {
            final File rawFile = file;
            file = File.createTempFile(dictName + ".crypt", ".tmp");
            final Crypt.Encrypter cryptCommand = new Crypt.Encrypter();
            cryptCommand.setArgs(new String[] { rawFile.getPath(), file.getPath() });
            cryptCommand.run();
        }

        final DecoderChainSpec<DictionaryHeader> spec =
                BinaryDictOffdeviceUtils.decodeDictionaryForProcess(file,
                        new BinaryDictOffdeviceUtils.HeaderReaderProcessor());
        assertNotNull("Can't decode a dictionary we just wrote : " + file, spec);
        final DictionaryHeader header = spec.mResult;
        assertEquals("raw" + (crypt ? " > encryption" : "") + (compress ? " > compression" : ""),
                spec.describeChain());
        assertEquals(header.mDictionaryOptions.mAttributes, options);
    }

    public void testHeaderReaderProcessor() throws IOException, UnsupportedFormatException {
        runTestHeaderReaderProcessorWithOneSpec(false /* compress */, false /* crypt */);
        runTestHeaderReaderProcessorWithOneSpec(true /* compress */, false /* crypt */);
        runTestHeaderReaderProcessorWithOneSpec(true /* compress */, true /* crypt */);
    }
}
