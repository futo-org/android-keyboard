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
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decodes binary files for a FusionDictionary.
 *
 * All the methods in this class are static.
 *
 * TODO: Remove calls from classes except Ver3DictDecoder
 * TODO: Move this file to makedict/internal.
 * TODO: Rename this class to DictDecoderUtils.
 */
public final class BinaryDictDecoderUtils {

    private static final boolean DBG = MakedictLog.DBG;

    private BinaryDictDecoderUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final int MAX_JUMPS = 12;

    @UsedForTesting
    public interface DictBuffer {
        public int readUnsignedByte();
        public int readUnsignedShort();
        public int readUnsignedInt24();
        public int readInt();
        public int position();
        public void position(int newPosition);
        public void put(final byte b);
        public int limit();
        @UsedForTesting
        public int capacity();
    }

    public static final class ByteBufferDictBuffer implements DictBuffer {
        private ByteBuffer mBuffer;

        public ByteBufferDictBuffer(final ByteBuffer buffer) {
            mBuffer = buffer;
        }

        @Override
        public int readUnsignedByte() {
            return mBuffer.get() & 0xFF;
        }

        @Override
        public int readUnsignedShort() {
            return mBuffer.getShort() & 0xFFFF;
        }

        @Override
        public int readUnsignedInt24() {
            final int retval = readUnsignedByte();
            return (retval << 16) + readUnsignedShort();
        }

        @Override
        public int readInt() {
            return mBuffer.getInt();
        }

        @Override
        public int position() {
            return mBuffer.position();
        }

        @Override
        public void position(int newPos) {
            mBuffer.position(newPos);
        }

        @Override
        public void put(final byte b) {
            mBuffer.put(b);
        }

        @Override
        public int limit() {
            return mBuffer.limit();
        }

        @Override
        public int capacity() {
            return mBuffer.capacity();
        }
    }

    /**
     * A class grouping utility function for our specific character encoding.
     */
    static final class CharEncoding {
        private static final int MINIMAL_ONE_BYTE_CHARACTER_VALUE = 0x20;
        private static final int MAXIMAL_ONE_BYTE_CHARACTER_VALUE = 0xFF;

        /**
         * Helper method to find out whether this code fits on one byte
         */
        private static boolean fitsOnOneByte(final int character) {
            return character >= MINIMAL_ONE_BYTE_CHARACTER_VALUE
                    && character <= MAXIMAL_ONE_BYTE_CHARACTER_VALUE;
        }

        /**
         * Compute the size of a character given its character code.
         *
         * Char format is:
         * 1 byte = bbbbbbbb match
         * case 000xxxxx: xxxxx << 16 + next byte << 8 + next byte
         * else: if 00011111 (= 0x1F) : this is the terminator. This is a relevant choice because
         *       unicode code points range from 0 to 0x10FFFF, so any 3-byte value starting with
         *       00011111 would be outside unicode.
         * else: iso-latin-1 code
         * This allows for the whole unicode range to be encoded, including chars outside of
         * the BMP. Also everything in the iso-latin-1 charset is only 1 byte, except control
         * characters which should never happen anyway (and still work, but take 3 bytes).
         *
         * @param character the character code.
         * @return the size in binary encoded-form, either 1 or 3 bytes.
         */
        static int getCharSize(final int character) {
            // See char encoding in FusionDictionary.java
            if (fitsOnOneByte(character)) return 1;
            if (FormatSpec.INVALID_CHARACTER == character) return 1;
            return 3;
        }

        /**
         * Compute the byte size of a character array.
         */
        static int getCharArraySize(final int[] chars) {
            int size = 0;
            for (int character : chars) size += getCharSize(character);
            return size;
        }

        /**
         * Writes a char array to a byte buffer.
         *
         * @param codePoints the code point array to write.
         * @param buffer the byte buffer to write to.
         * @param index the index in buffer to write the character array to.
         * @return the index after the last character.
         */
        static int writeCharArray(final int[] codePoints, final byte[] buffer, int index) {
            for (int codePoint : codePoints) {
                if (1 == getCharSize(codePoint)) {
                    buffer[index++] = (byte)codePoint;
                } else {
                    buffer[index++] = (byte)(0xFF & (codePoint >> 16));
                    buffer[index++] = (byte)(0xFF & (codePoint >> 8));
                    buffer[index++] = (byte)(0xFF & codePoint);
                }
            }
            return index;
        }

        /**
         * Writes a string with our character format to a byte buffer.
         *
         * This will also write the terminator byte.
         *
         * @param buffer the byte buffer to write to.
         * @param origin the offset to write from.
         * @param word the string to write.
         * @return the size written, in bytes.
         */
        static int writeString(final byte[] buffer, final int origin,
                final String word) {
            final int length = word.length();
            int index = origin;
            for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
                final int codePoint = word.codePointAt(i);
                if (1 == getCharSize(codePoint)) {
                    buffer[index++] = (byte)codePoint;
                } else {
                    buffer[index++] = (byte)(0xFF & (codePoint >> 16));
                    buffer[index++] = (byte)(0xFF & (codePoint >> 8));
                    buffer[index++] = (byte)(0xFF & codePoint);
                }
            }
            buffer[index++] = FormatSpec.PTNODE_CHARACTERS_TERMINATOR;
            return index - origin;
        }

        /**
         * Writes a string with our character format to an OutputStream.
         *
         * This will also write the terminator byte.
         *
         * @param buffer the OutputStream to write to.
         * @param word the string to write.
         */
        static void writeString(final OutputStream buffer, final String word) throws IOException {
            final int length = word.length();
            for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
                final int codePoint = word.codePointAt(i);
                if (1 == getCharSize(codePoint)) {
                    buffer.write((byte) codePoint);
                } else {
                    buffer.write((byte) (0xFF & (codePoint >> 16)));
                    buffer.write((byte) (0xFF & (codePoint >> 8)));
                    buffer.write((byte) (0xFF & codePoint));
                }
            }
            buffer.write(FormatSpec.PTNODE_CHARACTERS_TERMINATOR);
        }

        /**
         * Reads a string from a DictBuffer. This is the converse of the above method.
         */
        static String readString(final DictBuffer dictBuffer) {
            final StringBuilder s = new StringBuilder();
            int character = readChar(dictBuffer);
            while (character != FormatSpec.INVALID_CHARACTER) {
                s.appendCodePoint(character);
                character = readChar(dictBuffer);
            }
            return s.toString();
        }

        /**
         * Reads a character from the buffer.
         *
         * This follows the character format documented earlier in this source file.
         *
         * @param dictBuffer the buffer, positioned over an encoded character.
         * @return the character code.
         */
        static int readChar(final DictBuffer dictBuffer) {
            int character = dictBuffer.readUnsignedByte();
            if (!fitsOnOneByte(character)) {
                if (FormatSpec.PTNODE_CHARACTERS_TERMINATOR == character) {
                    return FormatSpec.INVALID_CHARACTER;
                }
                character <<= 16;
                character += dictBuffer.readUnsignedShort();
            }
            return character;
        }
    }

    // Input methods: Read a binary dictionary to memory.
    // readDictionaryBinary is the public entry point for them.

    static int readSInt24(final DictBuffer dictBuffer) {
        final int retval = dictBuffer.readUnsignedInt24();
        final int sign = ((retval & FormatSpec.MSB24) != 0) ? -1 : 1;
        return sign * (retval & FormatSpec.SINT24_MAX);
    }

    static int readChildrenAddress(final DictBuffer dictBuffer,
            final int optionFlags, final FormatOptions options) {
        if (options.mSupportsDynamicUpdate) {
            final int address = dictBuffer.readUnsignedInt24();
            if (address == 0) return FormatSpec.NO_CHILDREN_ADDRESS;
            if ((address & FormatSpec.MSB24) != 0) {
                return -(address & FormatSpec.SINT24_MAX);
            } else {
                return address;
            }
        }
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

    static int readParentAddress(final DictBuffer dictBuffer,
            final FormatOptions formatOptions) {
        if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
            final int parentAddress = dictBuffer.readUnsignedInt24();
            final int sign = ((parentAddress & FormatSpec.MSB24) != 0) ? -1 : 1;
            return sign * (parentAddress & FormatSpec.SINT24_MAX);
        } else {
            return FormatSpec.NO_PARENT_ADDRESS;
        }
    }

    /**
     * Reads and returns the PtNode count out of a buffer and forwards the pointer.
     */
    /* package */ static int readPtNodeCount(final DictBuffer dictBuffer) {
        final int msb = dictBuffer.readUnsignedByte();
        if (FormatSpec.MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT >= msb) {
            return msb;
        } else {
            return ((FormatSpec.MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT & msb) << 8)
                    + dictBuffer.readUnsignedByte();
        }
    }

    /**
     * Finds, as a string, the word at the position passed as an argument.
     *
     * @param dictDecoder the dict decoder.
     * @param headerSize the size of the header.
     * @param pos the position to seek.
     * @param formatOptions file format options.
     * @return the word with its frequency, as a weighted string.
     */
    /* package for tests */ static WeightedString getWordAtPosition(final DictDecoder dictDecoder,
            final int headerSize, final int pos, final FormatOptions formatOptions) {
        final WeightedString result;
        final int originalPos = dictDecoder.getPosition();
        dictDecoder.setPosition(pos);

        if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
            result = getWordAtPositionWithParentAddress(dictDecoder, pos, formatOptions);
        } else {
            result = getWordAtPositionWithoutParentAddress(dictDecoder, headerSize, pos,
                    formatOptions);
        }

        dictDecoder.setPosition(originalPos);
        return result;
    }

    @SuppressWarnings("unused")
    private static WeightedString getWordAtPositionWithParentAddress(final DictDecoder dictDecoder,
            final int pos, final FormatOptions options) {
        int currentPos = pos;
        int frequency = Integer.MIN_VALUE;
        final StringBuilder builder = new StringBuilder();
        // the length of the path from the root to the leaf is limited by MAX_WORD_LENGTH
        for (int count = 0; count < FormatSpec.MAX_WORD_LENGTH; ++count) {
            PtNodeInfo currentInfo;
            int loopCounter = 0;
            do {
                dictDecoder.setPosition(currentPos);
                currentInfo = dictDecoder.readPtNode(currentPos, options);
                if (BinaryDictIOUtils.isMovedPtNode(currentInfo.mFlags, options)) {
                    currentPos = currentInfo.mParentAddress + currentInfo.mOriginalAddress;
                }
                if (DBG && loopCounter++ > MAX_JUMPS) {
                    MakedictLog.d("Too many jumps - probably a bug");
                }
            } while (BinaryDictIOUtils.isMovedPtNode(currentInfo.mFlags, options));
            if (Integer.MIN_VALUE == frequency) frequency = currentInfo.mFrequency;
            builder.insert(0,
                    new String(currentInfo.mCharacters, 0, currentInfo.mCharacters.length));
            if (currentInfo.mParentAddress == FormatSpec.NO_PARENT_ADDRESS) break;
            currentPos = currentInfo.mParentAddress + currentInfo.mOriginalAddress;
        }
        return new WeightedString(builder.toString(), frequency);
    }

    private static WeightedString getWordAtPositionWithoutParentAddress(
            final DictDecoder dictDecoder, final int headerSize, final int pos,
            final FormatOptions options) {
        dictDecoder.setPosition(headerSize);
        final int count = dictDecoder.readPtNodeCount();
        int groupPos = headerSize + BinaryDictIOUtils.getPtNodeCountSize(count);
        final StringBuilder builder = new StringBuilder();
        WeightedString result = null;

        PtNodeInfo last = null;
        for (int i = count - 1; i >= 0; --i) {
            PtNodeInfo info = dictDecoder.readPtNode(groupPos, options);
            groupPos = info.mEndAddress;
            if (info.mOriginalAddress == pos) {
                builder.append(new String(info.mCharacters, 0, info.mCharacters.length));
                result = new WeightedString(builder.toString(), info.mFrequency);
                break; // and return
            }
            if (BinaryDictIOUtils.hasChildrenAddress(info.mChildrenAddress)) {
                if (info.mChildrenAddress > pos) {
                    if (null == last) continue;
                    builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                    dictDecoder.setPosition(last.mChildrenAddress);
                    i = dictDecoder.readPtNodeCount();
                    groupPos = last.mChildrenAddress + BinaryDictIOUtils.getPtNodeCountSize(i);
                    last = null;
                    continue;
                }
                last = info;
            }
            if (0 == i && BinaryDictIOUtils.hasChildrenAddress(last.mChildrenAddress)) {
                builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                dictDecoder.setPosition(last.mChildrenAddress);
                i = dictDecoder.readPtNodeCount();
                groupPos = last.mChildrenAddress + BinaryDictIOUtils.getPtNodeCountSize(i);
                last = null;
                continue;
            }
        }
        return result;
    }

    /**
     * Reads a single node array from a buffer.
     *
     * This methods reads the file at the current position. A node array is fully expected to start
     * at the current position.
     * This will recursively read other node arrays into the structure, populating the reverse
     * maps on the fly and using them to keep track of already read nodes.
     *
     * @param dictDecoder the dict decoder, correctly positioned at the start of a node array.
     * @param headerSize the size, in bytes, of the file header.
     * @param reverseNodeArrayMap a mapping from addresses to already read node arrays.
     * @param reversePtNodeMap a mapping from addresses to already read PtNodes.
     * @param options file format options.
     * @return the read node array with all his children already read.
     */
    private static PtNodeArray readNodeArray(final DictDecoder dictDecoder,
            final int headerSize, final Map<Integer, PtNodeArray> reverseNodeArrayMap,
            final Map<Integer, PtNode> reversePtNodeMap, final FormatOptions options)
            throws IOException {
        final ArrayList<PtNode> nodeArrayContents = new ArrayList<PtNode>();
        final int nodeArrayOriginPos = dictDecoder.getPosition();

        do { // Scan the linked-list node.
            final int nodeArrayHeadPos = dictDecoder.getPosition();
            final int count = dictDecoder.readPtNodeCount();
            int groupOffsetPos = nodeArrayHeadPos + BinaryDictIOUtils.getPtNodeCountSize(count);
            for (int i = count; i > 0; --i) { // Scan the array of PtNode.
                PtNodeInfo info = dictDecoder.readPtNode(groupOffsetPos, options);
                if (BinaryDictIOUtils.isMovedPtNode(info.mFlags, options)) continue;
                ArrayList<WeightedString> shortcutTargets = info.mShortcutTargets;
                ArrayList<WeightedString> bigrams = null;
                if (null != info.mBigrams) {
                    bigrams = new ArrayList<WeightedString>();
                    for (PendingAttribute bigram : info.mBigrams) {
                        final WeightedString word = getWordAtPosition(dictDecoder, headerSize,
                                bigram.mAddress, options);
                        final int reconstructedFrequency =
                                BinaryDictIOUtils.reconstructBigramFrequency(word.mFrequency,
                                        bigram.mFrequency);
                        bigrams.add(new WeightedString(word.mWord, reconstructedFrequency));
                    }
                }
                if (BinaryDictIOUtils.hasChildrenAddress(info.mChildrenAddress)) {
                    PtNodeArray children = reverseNodeArrayMap.get(info.mChildrenAddress);
                    if (null == children) {
                        final int currentPosition = dictDecoder.getPosition();
                        dictDecoder.setPosition(info.mChildrenAddress);
                        children = readNodeArray(dictDecoder, headerSize, reverseNodeArrayMap,
                                reversePtNodeMap, options);
                        dictDecoder.setPosition(currentPosition);
                    }
                    nodeArrayContents.add(
                            new PtNode(info.mCharacters, shortcutTargets, bigrams,
                                    info.mFrequency,
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_NOT_A_WORD),
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_BLACKLISTED), children));
                } else {
                    nodeArrayContents.add(
                            new PtNode(info.mCharacters, shortcutTargets, bigrams,
                                    info.mFrequency,
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_NOT_A_WORD),
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_BLACKLISTED)));
                }
                groupOffsetPos = info.mEndAddress;
            }

            // reach the end of the array.
            if (options.mSupportsDynamicUpdate) {
                final boolean hasValidForwardLink = dictDecoder.readAndFollowForwardLink();
                if (!hasValidForwardLink) break;
            }
        } while (options.mSupportsDynamicUpdate && dictDecoder.hasNextPtNodeArray());

        final PtNodeArray nodeArray = new PtNodeArray(nodeArrayContents);
        nodeArray.mCachedAddressBeforeUpdate = nodeArrayOriginPos;
        nodeArray.mCachedAddressAfterUpdate = nodeArrayOriginPos;
        reverseNodeArrayMap.put(nodeArray.mCachedAddressAfterUpdate, nodeArray);
        return nodeArray;
    }

    /**
     * Helper function to get the binary format version from the header.
     * @throws IOException
     */
    private static int getFormatVersion(final DictBuffer dictBuffer)
            throws IOException {
        final int magic = dictBuffer.readInt();
        if (FormatSpec.MAGIC_NUMBER == magic) return dictBuffer.readUnsignedShort();
        return FormatSpec.NOT_A_VERSION_NUMBER;
    }

    /**
     * Helper function to get and validate the binary format version.
     * @throws UnsupportedFormatException
     * @throws IOException
     */
    static int checkFormatVersion(final DictBuffer dictBuffer)
            throws IOException, UnsupportedFormatException {
        final int version = getFormatVersion(dictBuffer);
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
            throw new UnsupportedFormatException("This file has version " + version
                    + ", but this implementation does not support versions above "
                    + FormatSpec.MAXIMUM_SUPPORTED_VERSION);
        }
        return version;
    }

    /**
     * Reads a buffer and returns the memory representation of the dictionary.
     *
     * This high-level method takes a buffer and reads its contents, populating a
     * FusionDictionary structure. The optional dict argument is an existing dictionary to
     * which words from the buffer should be added. If it is null, a new dictionary is created.
     *
     * @param dictDecoder the dict decoder.
     * @param dict an optional dictionary to add words to, or null.
     * @return the created (or merged) dictionary.
     */
    @UsedForTesting
    /* package */ static FusionDictionary readDictionaryBinary(final DictDecoder dictDecoder,
            final FusionDictionary dict) throws IOException, UnsupportedFormatException {
        // Read header
        final FileHeader fileHeader = dictDecoder.readHeader();

        Map<Integer, PtNodeArray> reverseNodeArrayMapping = new TreeMap<Integer, PtNodeArray>();
        Map<Integer, PtNode> reversePtNodeMapping = new TreeMap<Integer, PtNode>();
        final PtNodeArray root = readNodeArray(dictDecoder, fileHeader.mHeaderSize,
                reverseNodeArrayMapping, reversePtNodeMapping, fileHeader.mFormatOptions);

        FusionDictionary newDict = new FusionDictionary(root, fileHeader.mDictionaryOptions);
        if (null != dict) {
            for (final Word w : dict) {
                if (w.mIsBlacklistEntry) {
                    newDict.addBlacklistEntry(w.mWord, w.mShortcutTargets, w.mIsNotAWord);
                } else {
                    newDict.add(w.mWord, w.mFrequency, w.mShortcutTargets, w.mIsNotAWord);
                }
            }
            for (final Word w : dict) {
                // By construction a binary dictionary may not have bigrams pointing to
                // words that are not also registered as unigrams so we don't have to avoid
                // them explicitly here.
                for (final WeightedString bigram : w.mBigrams) {
                    newDict.setBigram(w.mWord, bigram.mWord, bigram.mFrequency);
                }
            }
        }

        return newDict;
    }

    /**
     * Helper method to pass a file name instead of a File object to isBinaryDictionary.
     */
    public static boolean isBinaryDictionary(final String filename) {
        final File file = new File(filename);
        return isBinaryDictionary(file);
    }

    /**
     * Basic test to find out whether the file is a binary dictionary or not.
     *
     * Concretely this only tests the magic number.
     *
     * @param file The file to test.
     * @return true if it's a binary dictionary, false otherwise
     */
    public static boolean isBinaryDictionary(final File file) {
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            final ByteBuffer buffer = inStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length());
            final int version = getFormatVersion(new ByteBufferDictBuffer(buffer));
            return (version >= FormatSpec.MINIMUM_SUPPORTED_VERSION
                    && version <= FormatSpec.MAXIMUM_SUPPORTED_VERSION);
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
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
}
