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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * An interface of binary dictionary decoders.
 */
// TODO: Straighten out responsibility for the buffer's file pointer.
public interface DictDecoder {

    /**
     * Reads and returns the file header.
     */
    public DictionaryHeader readHeader() throws IOException, UnsupportedFormatException;

    /**
     * Reads PtNode from ptNodePos.
     * @param ptNodePos the position of PtNode.
     * @return PtNodeInfo.
     */
    public PtNodeInfo readPtNode(final int ptNodePos);

    /**
     * Reads a buffer and returns the memory representation of the dictionary.
     *
     * This high-level method takes a buffer and reads its contents, populating a
     * FusionDictionary structure.
     *
     * @param deleteDictIfBroken a flag indicating whether this method should remove the broken
     * dictionary or not.
     * @return the created dictionary.
     */
    @UsedForTesting
    public FusionDictionary readDictionaryBinary(final boolean deleteDictIfBroken)
            throws FileNotFoundException, IOException, UnsupportedFormatException;

    /**
     * Gets the address of the last PtNode of the exact matching word in the dictionary.
     * If no match is found, returns NOT_VALID_WORD.
     *
     * @param word the word we search for.
     * @return the address of the terminal node.
     * @throws IOException if the file can't be read.
     * @throws UnsupportedFormatException if the format of the file is not recognized.
     */
    @UsedForTesting
    public int getTerminalPosition(final String word)
            throws IOException, UnsupportedFormatException;

    /**
     * Reads unigrams and bigrams from the binary file.
     * Doesn't store a full memory representation of the dictionary.
     *
     * @param words the map to store the address as a key and the word as a value.
     * @param frequencies the map to store the address as a key and the frequency as a value.
     * @param bigrams the map to store the address as a key and the list of address as a value.
     * @throws IOException if the file can't be read.
     * @throws UnsupportedFormatException if the format of the file is not recognized.
     */
    @UsedForTesting
    public void readUnigramsAndBigramsBinary(final TreeMap<Integer, String> words,
            final TreeMap<Integer, Integer> frequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams)
                throws IOException, UnsupportedFormatException;

    /**
     * Sets the position of the buffer to the given value.
     *
     * @param newPos the new position
     */
    public void setPosition(final int newPos);

    /**
     * Gets the position of the buffer.
     *
     * @return the position
     */
    public int getPosition();

    /**
     * Reads and returns the PtNode count out of a buffer and forwards the pointer.
     */
    public int readPtNodeCount();

    /**
     * Opens the dictionary file and makes DictBuffer.
     */
    @UsedForTesting
    public void openDictBuffer() throws FileNotFoundException, IOException,
            UnsupportedFormatException;
    @UsedForTesting
    public boolean isDictBufferOpen();

    // Constants for DictionaryBufferFactory.
    public static final int USE_READONLY_BYTEBUFFER = 0x01000000;
    public static final int USE_BYTEARRAY = 0x02000000;
    public static final int USE_WRITABLE_BYTEBUFFER = 0x03000000;
    public static final int MASK_DICTBUFFER = 0x0F000000;

    public interface DictionaryBufferFactory {
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException;
    }

    /**
     * Creates DictionaryBuffer using a ByteBuffer
     *
     * This class uses less memory than DictionaryBufferFromByteArrayFactory,
     * but doesn't perform as fast.
     * When operating on a big dictionary, this class is preferred.
     */
    public static final class DictionaryBufferFromReadOnlyByteBufferFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            FileInputStream inStream = null;
            ByteBuffer buffer = null;
            try {
                inStream = new FileInputStream(file);
                buffer = inStream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                        0, file.length());
            } finally {
                if (inStream != null) {
                    inStream.close();
                }
            }
            if (buffer != null) {
                return new BinaryDictDecoderUtils.ByteBufferDictBuffer(buffer);
            }
            return null;
        }
    }

    /**
     * Creates DictionaryBuffer using a byte array
     *
     * This class performs faster than other classes, but consumes more memory.
     * When operating on a small dictionary, this class is preferred.
     */
    public static final class DictionaryBufferFromByteArrayFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            FileInputStream inStream = null;
            try {
                inStream = new FileInputStream(file);
                final byte[] array = new byte[(int) file.length()];
                inStream.read(array);
                return new ByteArrayDictBuffer(array);
            } finally {
                if (inStream != null) {
                    inStream.close();
                }
            }
        }
    }

    /**
     * Creates DictionaryBuffer using a writable ByteBuffer and a RandomAccessFile.
     *
     * This class doesn't perform as fast as other classes,
     * but this class is the only option available for destructive operations (insert or delete)
     * on a dictionary.
     */
    @UsedForTesting
    public static final class DictionaryBufferFromWritableByteBufferFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            RandomAccessFile raFile = null;
            ByteBuffer buffer = null;
            try {
                raFile = new RandomAccessFile(file, "rw");
                buffer = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, file.length());
            } finally {
                if (raFile != null) {
                    raFile.close();
                }
            }
            if (buffer != null) {
                return new BinaryDictDecoderUtils.ByteBufferDictBuffer(buffer);
            }
            return null;
        }
    }

    /**
     * @return whether this decoder has a valid binary dictionary that it can decode.
     */
    public boolean hasValidRawBinaryDictionary();
}
