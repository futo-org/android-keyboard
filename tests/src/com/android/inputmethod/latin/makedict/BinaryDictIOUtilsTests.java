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

import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.ByteBufferWrapper;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.FusionDictionaryBufferInterface;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

@LargeTest
public class BinaryDictIOUtilsTests  extends AndroidTestCase {
    private static final String TAG = BinaryDictIOUtilsTests.class.getSimpleName();
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(3, true);
    private static final int MAX_UNIGRAMS = 1500;

    private static final ArrayList<String> sWords = CollectionUtils.newArrayList();

    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "\u00FC" /* ü */, "\u00E2" /* â */, "\u00F1" /* ñ */, // accented characters
        "\u4E9C" /* 亜 */, "\u4F0A" /* 伊 */, "\u5B87" /* 宇 */, // kanji
        "\uD841\uDE28" /* 𠘨 */, "\uD840\uDC0B" /* 𠀋 */, "\uD861\uDeD7" /* 𨛗 */ // surrogate pair
    };

    public BinaryDictIOUtilsTests() {
        super();
        final Random random = new Random(123456);
        sWords.clear();
        for (int i = 0; i < MAX_UNIGRAMS; ++i) {
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

    private static void printCharGroup(final CharGroupInfo info) {
        Log.d(TAG, "    CharGroup at " + info.mOriginalAddress);
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

    private static void printNode(final FusionDictionaryBufferInterface buffer,
            final FormatSpec.FormatOptions formatOptions) {
        Log.d(TAG, "Node at " + buffer.position());
        final int count = BinaryDictInputOutput.readCharGroupCount(buffer);
        Log.d(TAG, "    charGroupCount = " + count);
        for (int i = 0; i < count; ++i) {
            final CharGroupInfo currentInfo = BinaryDictInputOutput.readCharGroup(buffer,
                    buffer.position(), formatOptions);
            printCharGroup(currentInfo);
        }
        if (formatOptions.mSupportsDynamicUpdate) {
            final int forwardLinkAddress = buffer.readUnsignedInt24();
            Log.d(TAG, "    forwardLinkAddress = " + forwardLinkAddress);
        }
    }

    private static void printBinaryFile(final FusionDictionaryBufferInterface buffer)
            throws IOException, UnsupportedFormatException {
        FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        while (buffer.position() < buffer.limit()) {
            printNode(buffer, header.mFormatOptions);
        }
    }

    private int getWordPosition(final File file, final String word) {
        int position = FormatSpec.NOT_VALID_WORD;
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            final FusionDictionaryBufferInterface buffer = new ByteBufferWrapper(
                    inStream.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length()));
            position = BinaryDictIOUtils.getTerminalPosition(buffer, word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
        return position;
    }

    private CharGroupInfo findWordFromFile(final File file, final String word) {
        FileInputStream inStream = null;
        CharGroupInfo info = null;
        try {
            inStream = new FileInputStream(file);
            final FusionDictionaryBufferInterface buffer = new ByteBufferWrapper(
                    inStream.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length()));
            info = BinaryDictIOUtils.findWordFromBuffer(buffer, word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
        return info;
    }

    // return amount of time to insert a word
    private long insertAndCheckWord(final File file, final String word, final int frequency,
            final boolean exist, final ArrayList<WeightedString> bigrams,
            final ArrayList<WeightedString> shortcuts) {
        RandomAccessFile raFile = null;
        BufferedOutputStream outStream = null;
        FusionDictionaryBufferInterface buffer = null;
        long amountOfTime = -1;
        try {
            raFile = new RandomAccessFile(file, "rw");
            buffer = new ByteBufferWrapper(raFile.getChannel().map(
                    FileChannel.MapMode.READ_WRITE, 0, file.length()));
            outStream = new BufferedOutputStream(new FileOutputStream(file, true));

            if (!exist) {
                assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
            }
            final long now = System.nanoTime();
            BinaryDictIOUtils.insertWord(buffer, outStream, word, frequency, bigrams, shortcuts,
                    false, false);
            amountOfTime = System.nanoTime() - now;
            outStream.flush();
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
            outStream.close();
            raFile.close();
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
            if (raFile != null) {
                try {
                    raFile.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
        return amountOfTime;
    }

    private void deleteWord(final File file, final String word) {
        RandomAccessFile raFile = null;
        FusionDictionaryBufferInterface buffer = null;
        try {
            raFile = new RandomAccessFile(file, "rw");
            buffer = new ByteBufferWrapper(raFile.getChannel().map(
                    FileChannel.MapMode.READ_WRITE, 0, file.length()));
            BinaryDictIOUtils.deleteWord(buffer, word);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        } finally {
            if (raFile != null) {
                try {
                    raFile.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }

    private void checkReverseLookup(final File file, final String word, final int position) {
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            final FusionDictionaryBufferInterface buffer = new ByteBufferWrapper(
                    inStream.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length()));
            final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
            assertEquals(word, BinaryDictInputOutput.getWordAtAddress(buffer, header.mHeaderSize,
                    position - header.mHeaderSize, header.mFormatOptions).mWord);
        } catch (IOException e) {
        } catch (UnsupportedFormatException e) {
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }

    public void testInsertWord() {
        File file = null;
        try {
            file = File.createTempFile("testInsertWord", ".dict", getContext().getCacheDir());
        } catch (IOException e) {
            fail("IOException while creating temporary file: " + e);
        }

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(new HashMap<String,String>(), false, false));
        dict.add("abcd", 10, null, false);

        try {
            final FileOutputStream out = new FileOutputStream(file);
            BinaryDictInputOutput.writeDictionaryBinary(out, dict, FORMAT_OPTIONS);
            out.close();
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
        insertAndCheckWord(file, "abcde", 10, false, null, null);

        insertAndCheckWord(file, "abcdefghijklmn", 10, false, null, null);
        checkReverseLookup(file, "abcdefghijklmn", getWordPosition(file, "abcdefghijklmn"));

        insertAndCheckWord(file, "abcdabcd", 10, false, null, null);
        checkReverseLookup(file, "abcdabcd", getWordPosition(file, "abcdabcd"));

        // update the existing word.
        insertAndCheckWord(file, "abcdabcd", 15, true, null, null);

        // split 1
        insertAndCheckWord(file, "ab", 20, false, null, null);

        // split 2
        insertAndCheckWord(file, "ami", 30, false, null, null);

        deleteWord(file, "ami");
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "ami"));

        insertAndCheckWord(file, "abcdabfg", 30, false, null, null);

        deleteWord(file, "abcd");
        assertEquals(FormatSpec.NOT_VALID_WORD, getWordPosition(file, "abcd"));
    }

    public void testInsertWordWithBigrams() {
        File file = null;
        try {
            file = File.createTempFile("testInsertWordWithBigrams", ".dict",
                    getContext().getCacheDir());
        } catch (IOException e) {
            fail("IOException while creating temporary file: " + e);
        }

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(new HashMap<String,String>(), false, false));
        dict.add("abcd", 10, null, false);
        dict.add("efgh", 15, null, false);

        try {
            final FileOutputStream out = new FileOutputStream(file);
            BinaryDictInputOutput.writeDictionaryBinary(out, dict, FORMAT_OPTIONS);
            out.close();
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        } catch (UnsupportedFormatException e) {
            fail("UnsupportedFormatException while writing an initial dictionary : " + e);
        }

        final ArrayList<WeightedString> banana = new ArrayList<WeightedString>();
        banana.add(new WeightedString("banana", 10));

        insertAndCheckWord(file, "banana", 0, false, null, null);
        insertAndCheckWord(file, "recursive", 60, true, banana, null);

        final CharGroupInfo info = findWordFromFile(file, "recursive");
        int bananaPos = getWordPosition(file, "banana");
        assertNotNull(info.mBigrams);
        assertEquals(info.mBigrams.size(), 1);
        assertEquals(info.mBigrams.get(0).mAddress, bananaPos);
    }

    public void testRandomWords() {
        File file = null;
        try {
            file = File.createTempFile("testRandomWord", ".dict", getContext().getCacheDir());
        } catch (IOException e) {
        }
        assertNotNull(file);

        // set an initial dictionary.
        final FusionDictionary dict = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(new HashMap<String, String>(), false,
                        false));
        dict.add("initial", 10, null, false);

        try {
            final FileOutputStream out = new FileOutputStream(file);
            BinaryDictInputOutput.writeDictionaryBinary(out, dict, FORMAT_OPTIONS);
            out.close();
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
                    cnt % FormatSpec.MAX_TERMINAL_FREQUENCY, false, null, null);
            maxTimeToInsert = Math.max(maxTimeToInsert, diff);
            minTimeToInsert = Math.min(minTimeToInsert, diff);
            sum += diff;
            cnt++;
        }
        cnt = 0;
        for (final String word : sWords) {
            MoreAsserts.assertNotEqual(FormatSpec.NOT_VALID_WORD, getWordPosition(file, word));
        }

        Log.d(TAG, "max = " + ((double)maxTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "min = " + ((double)minTimeToInsert/1000000) + " ms.");
        Log.d(TAG, "avg = " + ((double)sum/MAX_UNIGRAMS/1000000) + " ms.");
    }
}
