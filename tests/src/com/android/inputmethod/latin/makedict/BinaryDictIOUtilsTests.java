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
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

@LargeTest
public class BinaryDictIOUtilsTests extends AndroidTestCase {
    private static final String TAG = BinaryDictIOUtilsTests.class.getSimpleName();

    private static final ArrayList<String> sWords = CollectionUtils.newArrayList();
    public static final int DEFAULT_MAX_UNIGRAMS = 1500;
    private final int mMaxUnigrams;

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
            final DictDecoder dictDecoder = FormatSpec.getDictDecoder(file,
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
    private static PtNodeInfo findWordByDictDecoder(final DictDecoder dictDecoder,
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
            info = findWordByDictDecoder(dictDecoder, word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        }
        return info;
    }

    // return amount of time to insert a word
    private long insertAndCheckWord(final File file, final String word, final int frequency,
            final boolean exist, final ArrayList<WeightedString> bigrams,
            final ArrayList<WeightedString> shortcuts, final FormatOptions formatOptions) {
        long amountOfTime = -1;
        try {
            final DictUpdater dictUpdater = BinaryDictUtils.getDictUpdater(file, formatOptions);

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

    private void deleteWord(final File file, final String word, final FormatOptions formatOptions) {
        try {
            final DictUpdater dictUpdater = BinaryDictUtils.getDictUpdater(file, formatOptions);
            dictUpdater.deleteWord(word);
        } catch (IOException e) {
            Log.e(TAG, "Raised an IOException while deleting a word", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Raised an UnsupportedFormatException while deleting a word", e);
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

    private void runTestInsertWord(final FormatOptions formatOptions) {
        final String testName = "testInsertWord";
        final String version = Long.toString(System.currentTimeMillis());
        final File file = BinaryDictUtils.getDictFile(testName, version, formatOptions,
                getContext().getCacheDir());

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.getDictionaryOptions(testName, version));
        dict.add("abcd", 10, null, false);

        try {
            final DictEncoder dictEncoder = BinaryDictUtils.getDictEncoder(file, formatOptions,
                    getContext().getCacheDir());
            dictEncoder.writeDictionary(dict, formatOptions);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
        insertAndCheckWord(file, "abcde", 10, false, null, null, formatOptions);
        checkReverseLookup(file, "abcde", getWordPosition(file, "abcde"));

        insertAndCheckWord(file, "abcdefghijklmn", 10, false, null, null, formatOptions);
        checkReverseLookup(file, "abcdefghijklmn", getWordPosition(file, "abcdefghijklmn"));

        insertAndCheckWord(file, "abcdabcd", 10, false, null, null, formatOptions);
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));

        // update the existing word.
        insertAndCheckWord(file, "abcdabcd", 15, true, null, null, formatOptions);
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));

        // Testing splitOnly
        insertAndCheckWord(file, "ab", 20, false, null, null, formatOptions);
        checkReverseLookup(file, "ab", getWordPosition(file, "ab"));
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));
        checkReverseLookup(file, "abcde", getWordPosition(file, "abcde"));
        checkReverseLookup(file, "abcdefghijklmn", getWordPosition(file, "abcdefghijklmn"));

        // Testing splitAndBranch
        insertAndCheckWord(file, "ami", 30, false, null, null, formatOptions);
        checkReverseLookup(file, "ami", getWordPosition(file, "ami"));
        checkReverseLookup(file, "ab", getWordPosition(file, "ab"));
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));
        checkReverseLookup(file, "abcde", getWordPosition(file, "abcde"));
        checkReverseLookup(file, "abcdefghijklmn", getWordPosition(file, "abcdefghijklmn"));
        checkReverseLookup(file, "ami", getWordPosition(file, "ami"));

        insertAndCheckWord(file, "abcdefzzzz", 40, false, null, null, formatOptions);
        checkReverseLookup(file, "abcdefzzzz", getWordPosition(file, "abcdefzzzz"));

        deleteWord(file, "ami", formatOptions);
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "ami"));

        insertAndCheckWord(file, "abcdabfg", 30, false, null, null, formatOptions);

        deleteWord(file, "abcd", formatOptions);
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
    }

    public void testInsertWord() {
        runTestInsertWord(BinaryDictUtils.VERSION3_WITH_DYNAMIC_UPDATE);
        runTestInsertWord(BinaryDictUtils.VERSION4_WITH_DYNAMIC_UPDATE);
    }

    private void runTestInsertWordWithBigrams(final FormatOptions formatOptions) {
        final String testName = "testInsertWordWithBigrams";
        final String version = Long.toString(System.currentTimeMillis());
        File file = BinaryDictUtils.getDictFile(testName, version, formatOptions,
                getContext().getCacheDir());

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.getDictionaryOptions(testName, version));
        dict.add("abcd", 10, null, false);
        dict.add("efgh", 15, null, false);

        try {
            final DictEncoder dictEncoder = BinaryDictUtils.getDictEncoder(file, formatOptions,
                    getContext().getCacheDir());
            dictEncoder.writeDictionary(dict, formatOptions);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        final ArrayList<WeightedString> banana = new ArrayList<WeightedString>();
        banana.add(new WeightedString("banana", 10));

        insertAndCheckWord(file, "banana", 0, false, null, null, formatOptions);
        insertAndCheckWord(file, "recursive", 60, true, banana, null, formatOptions);

        final PtNodeInfo info = findWordFromFile(file, "recursive");
        int bananaPos = getWordPosition(file, "banana");
        assertNotNull(info.mBigrams);
        assertEquals(info.mBigrams.size(), 1);
        assertEquals(info.mBigrams.get(0).mAddress, bananaPos);
    }

    public void testInsertWordWithBigrams() {
        runTestInsertWordWithBigrams(BinaryDictUtils.VERSION3_WITH_DYNAMIC_UPDATE);
        runTestInsertWordWithBigrams(BinaryDictUtils.VERSION4_WITH_DYNAMIC_UPDATE);
    }

    private void runTestRandomWords(final FormatOptions formatOptions) {
        final String testName = "testRandomWord";
        final String version = Long.toString(System.currentTimeMillis());
        final File file = BinaryDictUtils.getDictFile(testName, version, formatOptions,
                getContext().getCacheDir());

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                BinaryDictUtils.getDictionaryOptions(testName, version));
        dict.add("initial", 10, null, false);

        try {
            final DictEncoder dictEncoder = BinaryDictUtils.getDictEncoder(file, formatOptions,
                    getContext().getCacheDir());
            dictEncoder.writeDictionary(dict, formatOptions);
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
                    cnt % FormatSpec.MAX_TERMINAL_FREQUENCY, false, null, null, formatOptions);
            maxTimeToInsert = Math.max(maxTimeToInsert, diff);
            minTimeToInsert = Math.min(minTimeToInsert, diff);
            sum += diff;
            cnt++;
        }
        cnt = 0;
        for (final String word : sWords) {
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
        }

        Log.d(TAG, "Test version " + formatOptions.mVersion);
        Log.d(TAG, "max = " + ((double)maxTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "min = " + ((double)minTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "avg = " + ((double)sum/mMaxUnigrams/1000000) + " ms.");
    }

    public void testRandomWords() {
        runTestRandomWords(BinaryDictUtils.VERSION3_WITH_DYNAMIC_UPDATE);
        runTestRandomWords(BinaryDictUtils.VERSION4_WITH_DYNAMIC_UPDATE);
    }
}
