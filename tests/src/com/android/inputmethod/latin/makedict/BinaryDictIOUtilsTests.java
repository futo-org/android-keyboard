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

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

@LargeTest
public class BinaryDictIOUtilsTests extends AndroidTestCase {
    private static final String TAG = BinaryDictIOUtilsTests.class.getSimpleName();
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(3, true);

    private static final ArrayList<String> sWords = CollectionUtils.newArrayList();
    public static final int DEFAULT_MAX_UNIGRAMS = 1500;
    private final int mMaxUnigrams;

    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";

    private static final int VERSION3 = 3;
    private static final int VERSION4 = 4;

    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "\u00FC" /* ü */, "\u00E2" /* â */, "\u00F1" /* ñ */, // accented characters
        "\u4E9C" /* 亜 */, "\u4F0A" /* 伊 */, "\u5B87" /* 宇 */, // kanji
        "\uD841\uDE28" /* 𠘨 */, "\uD840\uDC0B" /* 𠀋 */, "\uD861\uDED7" /* 𨛗 */ // surrogate pair
    };

    public BinaryDictIOUtilsTests() {
        // 1500 is the default max unigrams
        this(System.currentTimeMillis(), DEFAULT_MAX_UNIGRAMS);
    }

    public BinaryDictIOUtilsTests(final long seed, final int maxUnigrams) {
        super();
        Log.d(TAG, "Seed for test is " + seed + ", maxUnigrams is " + maxUnigrams);
        mMaxUnigrams = maxUnigrams;
        final Random random = new Random(seed);
        sWords.clear();
        for (int i = 0; i < maxUnigrams; ++i) {
            sWords.add(generateWord(random.nextInt()));
        }
    }

    // Utilities for test
    private String generateWord(final int value) {
        final int lengthOfChars = CHARACTERS.length;
        StringBuilder builder = new StringBuilder("");
        long lvalue = Math.abs((long)value);
        while (lvalue > 0) {
            builder.append(CHARACTERS[(int)(lvalue % lengthOfChars)]);
            lvalue /= lengthOfChars;
        }
        if (builder.toString().equals("")) return "a";
        return builder.toString();
    }

    private static void printPtNode(final PtNodeInfo info) {
        Log.d(TAG, "    PtNode at " + info.mOriginalAddress);
        Log.d(TAG, "        flags = " + info.mFlags);
        Log.d(TAG, "        parentAddress = " + info.mParentAddress);
        Log.d(TAG, "        characters = " + new String(info.mCharacters, 0,
                info.mCharacters.length));
        if (info.mFrequency != -1) Log.d(TAG, "        frequency = " + info.mFrequency);
        if (info.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS) {
            Log.d(TAG, "        children address = no children address");
        } else {
            Log.d(TAG, "        children address = " + info.mChildrenAddress);
        }
        if (info.mShortcutTargets != null) {
            for (final WeightedString ws : info.mShortcutTargets) {
                Log.d(TAG, "        shortcuts = " + ws.mWord);
            }
        }
        if (info.mBigrams != null) {
            for (final PendingAttribute attr : info.mBigrams) {
                Log.d(TAG, "        bigram = " + attr.mAddress);
            }
        }
        Log.d(TAG, "    end address = " + info.mEndAddress);
    }

    private static void printNode(final Ver3DictDecoder dictDecoder,
            final FormatSpec.FormatOptions formatOptions) {
        final DictBuffer dictBuffer = dictDecoder.getDictBuffer();
        Log.d(TAG, "Node at " + dictBuffer.position());
        final int count = BinaryDictDecoderUtils.readPtNodeCount(dictBuffer);
        Log.d(TAG, "    ptNodeCount = " + count);
        for (int i = 0; i < count; ++i) {
            final PtNodeInfo currentInfo = dictDecoder.readPtNode(dictBuffer.position(),
                    formatOptions);
            printPtNode(currentInfo);
        }
        if (formatOptions.mSupportsDynamicUpdate) {
            final int forwardLinkAddress = dictBuffer.readUnsignedInt24();
            Log.d(TAG, "    forwardLinkAddress = " + forwardLinkAddress);
        }
    }

    @SuppressWarnings("unused")
    private static void printBinaryFile(final Ver3DictDecoder dictDecoder)
            throws IOException, UnsupportedFormatException {
        final FileHeader fileHeader = dictDecoder.readHeader();
        final DictBuffer dictBuffer = dictDecoder.getDictBuffer();
        while (dictBuffer.position() < dictBuffer.limit()) {
            printNode(dictDecoder, fileHeader.mFormatOptions);
        }
    }

    private int getWordPosition(final File file, final String word) {
        int position = FormatSpec.NOT_VALID_WORD;

        try {
            final Ver3DictDecoder dictDecoder = new Ver3DictDecoder(file,
                    DictDecoder.USE_READONLY_BYTEBUFFER);
            position = dictDecoder.getTerminalPosition(word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }
        return position;
    }

    /**
     * Find a word using the DictDecoder.
     *
     * @param dictDecoder the dict decoder
     * @param word the word searched
     * @return the found ptNodeInfo
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    private static PtNodeInfo findWordByBinaryDictReader(final DictDecoder dictDecoder,
            final String word) throws IOException, UnsupportedFormatException {
        int position = dictDecoder.getTerminalPosition(word);
        if (position != FormatSpec.NOT_VALID_WORD) {
            dictDecoder.setPosition(0);
            final FileHeader header = dictDecoder.readHeader();
            dictDecoder.setPosition(position);
            return dictDecoder.readPtNode(position, header.mFormatOptions);
        }
        return null;
    }

    private PtNodeInfo findWordFromFile(final File file, final String word) {
        final DictDecoder dictDecoder = FormatSpec.getDictDecoder(file);
        PtNodeInfo info = null;
        try {
            dictDecoder.openDictBuffer();
            info = findWordByBinaryDictReader(dictDecoder, word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }
        return info;
    }

    // return amount of time to insert a word
    private long insertAndCheckWord(final File file, final String word, final int frequency,
            final boolean exist, final ArrayList<WeightedString> bigrams,
            final ArrayList<WeightedString> shortcuts, final int formatVersion) {
        long amountOfTime = -1;
        try {
            final DictUpdater dictUpdater;
            if (formatVersion == VERSION3) {
                dictUpdater = new Ver3DictUpdater(file, DictDecoder.USE_WRITABLE_BYTEBUFFER);
            } else {
                throw new RuntimeException("DictUpdater for version " + formatVersion + " doesn't"
                        + " exist.");
            }

            if (!exist) {
                assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
            }
            final long now = System.nanoTime();
            dictUpdater.insertWord(word, frequency, bigrams, shortcuts, false, false);
            amountOfTime = System.nanoTime() - now;
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
        } catch (IOException e) {
            Log.e(TAG, "Raised an IOException while inserting a word", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Raised an UnsupportedFormatException error while inserting a word", e);
        }
        return amountOfTime;
    }

    private void deleteWord(final File file, final String word, final int formatVersion) {
        try {
            final DictUpdater dictUpdater;
            if (formatVersion == VERSION3) {
                dictUpdater = new Ver3DictUpdater(file, DictDecoder.USE_WRITABLE_BYTEBUFFER);
            } else {
                throw new RuntimeException("DictUpdater for version " + formatVersion + " doesn't"
                        + " exist.");
            }
            dictUpdater.deleteWord(word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }
    }

    private void checkReverseLookup(final File file, final String word, final int position) {

        try {
            final DictDecoder dictDecoder = FormatSpec.getDictDecoder(file);
            final FileHeader fileHeader = dictDecoder.readHeader();
            assertEquals(word,
                    BinaryDictDecoderUtils.getWordAtPosition(dictDecoder, fileHeader.mHeaderSize,
                            position, fileHeader.mFormatOptions).mWord);
        } catch (IOException e) {
            Log.e(TAG, "Raised an IOException while looking up a word", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Raised an UnsupportedFormatException error while looking up a word", e);
        }
    }

    private void runTestInsertWord(final int formatVersion) {
        File file = null;
        try {
            file = File.createTempFile("testInsertWord", TEST_DICT_FILE_EXTENSION,
                    getContext().getCacheDir());
        } catch (IOException e) {
            fail("IOException while creating temporary file: " + e);
        }

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(new HashMap<String,String>(), false, false));
        dict.add("abcd", 10, null, false);

        try {
            final DictEncoder dictEncoder = new Ver3DictEncoder(file);
            dictEncoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
        insertAndCheckWord(file, "abcde", 10, false, null, null, formatVersion);

        insertAndCheckWord(file, "abcdefghijklmn", 10, false, null, null, formatVersion);
        checkReverseLookup(file, "abcdefghijklmn", getWordPosition(file, "abcdefghijklmn"));

        insertAndCheckWord(file, "abcdabcd", 10, false, null, null, formatVersion);
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));

        // update the existing word.
        insertAndCheckWord(file, "abcdabcd", 15, true, null, null, formatVersion);

        // split 1
        insertAndCheckWord(file, "ab", 20, false, null, null, formatVersion);

        // split 2
        insertAndCheckWord(file, "ami", 30, false, null, null, formatVersion);

        deleteWord(file, "ami", formatVersion);
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "ami"));

        insertAndCheckWord(file, "abcdabfg", 30, false, null, null, formatVersion);

        deleteWord(file, "abcd", formatVersion);
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
    }

    public void testInsertWord() {
        runTestInsertWord(VERSION3);
    }

    private void runTestInsertWordWithBigrams(final int formatVersion) {
        File file = null;
        try {
            file = File.createTempFile("testInsertWordWithBigrams", TEST_DICT_FILE_EXTENSION,
                    getContext().getCacheDir());
        } catch (IOException e) {
            fail("IOException while creating temporary file: " + e);
        }

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(new HashMap<String,String>(), false, false));
        dict.add("abcd", 10, null, false);
        dict.add("efgh", 15, null, false);

        try {
            final DictEncoder dictEncoder = new Ver3DictEncoder(file); 
            dictEncoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        final ArrayList<WeightedString> banana = new ArrayList<WeightedString>();
        banana.add(new WeightedString("banana", 10));

        insertAndCheckWord(file, "banana", 0, false, null, null, formatVersion);
        insertAndCheckWord(file, "recursive", 60, true, banana, null, formatVersion);

        final PtNodeInfo info = findWordFromFile(file, "recursive");
        int bananaPos = getWordPosition(file, "banana");
        assertNotNull(info.mBigrams);
        assertEquals(info.mBigrams.size(), 1);
        assertEquals(info.mBigrams.get(0).mAddress, bananaPos);
    }

    public void testInsertWordWithBigrams() {
        runTestInsertWordWithBigrams(VERSION3);
    }

    private void runTestRandomWords(final int formatVersion) {
        File file = null;
        try {
            file = File.createTempFile("testRandomWord", TEST_DICT_FILE_EXTENSION,
                    getContext().getCacheDir());
        } catch (IOException e) {
        }
        assertNotNull(file);

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(new HashMap<String, String>(), false,
                        false));
        dict.add("initial", 10, null, false);

        try {
            final DictEncoder dictEncoder = new Ver3DictEncoder(file);
            dictEncoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            assertTrue(false);
        } catch (UnsupportedFormatException e) {
            assertTrue(false);
        }

        long maxTimeToInsert = 0, sum = 0;
        long minTimeToInsert = 100000000; // 1000000000 is an upper bound for minTimeToInsert.
        int cnt = 0;
        for (final String word : sWords) {
            final long diff = insertAndCheckWord(file, word,
                    cnt % FormatSpec.MAX_TERMINAL_FREQUENCY, false, null, null, formatVersion);
            maxTimeToInsert = Math.max(maxTimeToInsert, diff);
            minTimeToInsert = Math.min(minTimeToInsert, diff);
            sum += diff;
            cnt++;
        }
        cnt = 0;
        for (final String word : sWords) {
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
        }

        Log.d(TAG, "Test version " + formatVersion);
        Log.d(TAG, "max = " + ((double)maxTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "min = " + ((double)minTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "avg = " + ((double)sum/mMaxUnigrams/1000000) + " ms.");
    }

    public void testRandomWords() {
        runTestRandomWords(VERSION3);
    }
}
