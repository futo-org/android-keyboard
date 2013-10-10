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
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * The base class of binary dictionary decoders.
 */
public abstract class DictDecoder {

    protected FileHeader readHeader(final DictBuffer dictBuffer)
            throws IOException, UnsupportedFormatException {
        if (dictBuffer == null) {
            openDictBuffer();
        }

        final int version = HeaderReader.readVersion(dictBuffer);
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
            throw new UnsupportedFormatException("Unsupported version : " + version);
        }
        // TODO: Remove this field.
        final int optionsFlags = HeaderReader.readOptionFlags(dictBuffer);

        final int headerSize = HeaderReader.readHeaderSize(dictBuffer);

        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

        final HashMap<String, String> attributes = HeaderReader.readAttributes(dictBuffer,
                headerSize);

        final FileHeader header = new FileHeader(headerSize,
                new FusionDictionary.DictionaryOptions(attributes,
                        0 != (optionsFlags & FormatSpec.GERMAN_UMLAUT_PROCESSING_FLAG),
                        0 != (optionsFlags & FormatSpec.FRENCH_LIGATURE_PROCESSING_FLAG)),
                        new FormatOptions(version,
                                0 != (optionsFlags & FormatSpec.SUPPORTS_DYNAMIC_UPDATE)));
        return header;
    }

    /**
     * Reads and returns the file header.
     */
    public abstract FileHeader readHeader() throws IOException, UnsupportedFormatException;

    /**
     * Reads PtNode from nodeAddress.
     * @param ptNodePos the position of PtNode.
     * @param formatOptions the format options.
     * @return PtNodeInfo.
     */
    public abstract PtNodeInfo readPtNode(final int ptNodePos, final FormatOptions formatOptions);

    /**
     * Reads a buffer and returns the memory representation of the dictionary.
     *
     * This high-level method takes a buffer and reads its contents, populating a
     * FusionDictionary structure. The optional dict argument is an existing dictionary to
     * which words from the buffer should be added. If it is null, a new dictionary is created.
     *
     * @param dict an optional dictionary to add words to, or null.
     * @param deleteDictIfBroken a flag indicating whether this method should remove the broken
     * dictionary or not.
     * @return the created (or merged) dictionary.
     */
    @UsedForTesting
    public abstract FusionDictionary readDictionaryBinary(final FusionDictionary dict,
            final boolean deleteDictIfBroken)
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
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        return BinaryDictIOUtils.getTerminalPosition(this, word);
    }

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
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        BinaryDictIOUtils.readUnigramsAndBigramsBinary(this, words, frequencies, bigrams);
    }

    /**
     * Sets the position of the buffer to the given value.
     *
     * @param newPos the new position
     */
    public abstract void setPosition(final int newPos);

    /**
     * Gets the position of the buffer.
     *
     * @return the position
     */
    public abstract int getPosition();

    /**
     * Reads and returns the PtNode count out of a buffer and forwards the pointer.
     */
    public abstract int readPtNodeCount();

    /**
     * Reads the forward link and advances the position.
     *
     * @return true if this method moves the file pointer, false otherwise.
     */
    public abstract boolean readAndFollowForwardLink();
    public abstract boolean hasNextPtNodeArray();

    /**
     * Opens the dictionary file and makes DictBuffer.
     */
    @UsedForTesting
    public abstract void openDictBuffer() throws FileNotFoundException, IOException;
    @UsedForTesting
    public abstract boolean isDictBufferOpen();

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
     * A utility class for reading a file header.
     */
    protected static class HeaderReader {
        protected static int readVersion(final DictBuffer dictBuffer)
                throws IOException, UnsupportedFormatException {
            return BinaryDictDecoderUtils.checkFormatVersion(dictBuffer);
        }

        protected static int readOptionFlags(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedShort();
        }

        protected static int readHeaderSize(final DictBuffer dictBuffer) {
            return dictBuffer.readInt();
        }

        protected static HashMap<String, String> readAttributes(final DictBuffer dictBuffer,
                final int headerSize) {
            final HashMap<String, String> attributes = new HashMap<String, String>();
            while (dictBuffer.position() < headerSize) {
                // We can avoid an infinite loop here since dictBuffer.position() is always
                // increased by calling CharEncoding.readString.
                final String key = CharEncoding.readString(dictBuffer);
                final String value = CharEncoding.readString(dictBuffer);
                attributes.put(key, value);
            }
            dictBuffer.position(headerSize);
            return attributes;
        }
    }

    /**
     * A utility class for reading a PtNode.
     */
    protected static class PtNodeReader {
        protected static int readPtNodeOptionFlags(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedByte();
        }

        protected static int readParentAddress(final DictBuffer dictBuffer,
                final FormatOptions formatOptions) {
            if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
                return BinaryDictDecoderUtils.readSInt24(dictBuffer);
            } else {
                return FormatSpec.NO_PARENT_ADDRESS;
            }
        }

        protected static int readChildrenAddress(final DictBuffer dictBuffer, final int optionFlags,
                final FormatOptions formatOptions) {
            if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
                final int address = BinaryDictDecoderUtils.readSInt24(dictBuffer);
                if (address == 0) return FormatSpec.NO_CHILDREN_ADDRESS;
                return address;
            } else {
                switch (optionFlags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE:
                        return dictBuffer.readUnsignedByte();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES:
                        return dictBuffer.readUnsignedShort();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES:
                        return dictBuffer.readUnsignedInt24();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS:
                    default:
                        return FormatSpec.NO_CHILDREN_ADDRESS;
                }
            }
        }

        // Reads shortcuts and returns the read length.
        protected static int readShortcut(final DictBuffer dictBuffer,
                final ArrayList<WeightedString> shortcutTargets) {
            final int pointerBefore = dictBuffer.position();
            dictBuffer.readUnsignedShort(); // skip the size
            while (true) {
                final int targetFlags = dictBuffer.readUnsignedByte();
                final String word = CharEncoding.readString(dictBuffer);
                shortcutTargets.add(new WeightedString(word,
                        targetFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY));
                if (0 == (targetFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) break;
            }
            return dictBuffer.position() - pointerBefore;
        }

        protected static int readBigramAddresses(final DictBuffer dictBuffer,
                final ArrayList<PendingAttribute> bigrams, final int baseAddress) {
            int readLength = 0;
            int bigramCount = 0;
            while (bigramCount++ < FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                final int bigramFlags = dictBuffer.readUnsignedByte();
                ++readLength;
                final int sign = 0 == (bigramFlags & FormatSpec.FLAG_BIGRAM_ATTR_OFFSET_NEGATIVE)
                        ? 1 : -1;
                int bigramAddress = baseAddress + readLength;
                switch (bigramFlags & FormatSpec.MASK_BIGRAM_ATTR_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_ONEBYTE:
                        bigramAddress += sign * dictBuffer.readUnsignedByte();
                        readLength += 1;
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_TWOBYTES:
                        bigramAddress += sign * dictBuffer.readUnsignedShort();
                        readLength += 2;
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_THREEBYTES:
                        bigramAddress += sign * dictBuffer.readUnsignedInt24();
                        readLength += 3;
                        break;
                    default:
                        throw new RuntimeException("Has bigrams with no address");
                }
                bigrams.add(new PendingAttribute(
                        bigramFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY,
                        bigramAddress));
                if (0 == (bigramFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) break;
            }
            return readLength;
        }
    }

    public abstract void skipPtNode(final FormatOptions formatOptions);
}
