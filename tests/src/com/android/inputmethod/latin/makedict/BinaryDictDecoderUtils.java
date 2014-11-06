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
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;

import javax.annotation.Nonnull;

/**
 * Decodes binary files for a FusionDictionary.
 *
 * All the methods in this class are static.
 *
 * TODO: Move this file to makedict/internal.
 * TODO: Rename this class to DictDecoderUtils.
 */
public final class BinaryDictDecoderUtils {
    private BinaryDictDecoderUtils() {
        // This utility class is not publicly instantiable.
    }

    @UsedForTesting
    public interface DictBuffer {
        public int readUnsignedByte();
        public int readUnsignedShort();
        public int readUnsignedInt24();
        public int readInt();
        public int position();
        public void position(int newPosition);
        @UsedForTesting
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

        /**
         * Helper method to find out whether this code fits on one byte
         */
        private static boolean fitsOnOneByte(final int character,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
            int codePoint = character;
            if (codePointToOneByteCodeMap != null) {
                if (codePointToOneByteCodeMap.containsKey(character)) {
                    codePoint = codePointToOneByteCodeMap.get(character);
                }
            }
            return codePoint >= FormatSpec.MINIMAL_ONE_BYTE_CHARACTER_VALUE
                    && codePoint <= FormatSpec.MAXIMAL_ONE_BYTE_CHARACTER_VALUE;
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
        static int getCharSize(final int character,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
            // See char encoding in FusionDictionary.java
            if (fitsOnOneByte(character, codePointToOneByteCodeMap)) return 1;
            if (FormatSpec.INVALID_CHARACTER == character) return 1;
            return 3;
        }

        /**
         * Compute the byte size of a character array.
         */
        static int getCharArraySize(final int[] chars,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
            int size = 0;
            for (int character : chars) size += getCharSize(character, codePointToOneByteCodeMap);
            return size;
        }

        /**
         * Writes a char array to a byte buffer.
         *
         * @param codePoints the code point array to write.
         * @param buffer the byte buffer to write to.
         * @param fromIndex the index in buffer to write the character array to.
         * @param codePointToOneByteCodeMap the map to convert the code point.
         * @return the index after the last character.
         */
        static int writeCharArray(final int[] codePoints, final byte[] buffer, final int fromIndex,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
            int index = fromIndex;
            for (int codePoint : codePoints) {
                if (codePointToOneByteCodeMap != null) {
                    if (codePointToOneByteCodeMap.containsKey(codePoint)) {
                        // Convert code points
                        codePoint = codePointToOneByteCodeMap.get(codePoint);
                    }
                }
                if (1 == getCharSize(codePoint, codePointToOneByteCodeMap)) {
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
        static int writeString(final byte[] buffer, final int origin, final String word,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
            final int length = word.length();
            int index = origin;
            for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
                int codePoint = word.codePointAt(i);
                if (codePointToOneByteCodeMap != null) {
                    if (codePointToOneByteCodeMap.containsKey(codePoint)) {
                        // Convert code points
                        codePoint = codePointToOneByteCodeMap.get(codePoint);
                    }
                }
                if (1 == getCharSize(codePoint, codePointToOneByteCodeMap)) {
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
         * @param stream the OutputStream to write to.
         * @param word the string to write.
         * @return the size written, in bytes.
         */
        static int writeString(final OutputStream stream, final String word,
                final HashMap<Integer, Integer> codePointToOneByteCodeMap) throws IOException {
            final int length = word.length();
            int written = 0;
            for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
                final int codePoint = word.codePointAt(i);
                final int charSize = getCharSize(codePoint, codePointToOneByteCodeMap);
                if (1 == charSize) {
                    stream.write((byte) codePoint);
                } else {
                    stream.write((byte) (0xFF & (codePoint >> 16)));
                    stream.write((byte) (0xFF & (codePoint >> 8)));
                    stream.write((byte) (0xFF & codePoint));
                }
                written += charSize;
            }
            stream.write(FormatSpec.PTNODE_CHARACTERS_TERMINATOR);
            written += FormatSpec.PTNODE_TERMINATOR_SIZE;
            return written;
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
            if (!fitsOnOneByte(character, null)) {
                if (FormatSpec.PTNODE_CHARACTERS_TERMINATOR == character) {
                    return FormatSpec.INVALID_CHARACTER;
                }
                character <<= 16;
                character += dictBuffer.readUnsignedShort();
            }
            return character;
        }
    }

    /**
     * Reads and returns the PtNode count out of a buffer and forwards the pointer.
     */
    /* package */ static int readPtNodeCount(final DictBuffer dictBuffer) {
        final int msb = dictBuffer.readUnsignedByte();
        if (FormatSpec.MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT >= msb) {
            return msb;
        }
        return ((FormatSpec.MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT & msb) << 8)
                + dictBuffer.readUnsignedByte();
    }

    /**
     * Finds, as a string, the word at the position passed as an argument.
     *
     * @param dictDecoder the dict decoder.
     * @param headerSize the size of the header.
     * @param pos the position to seek.
     * @return the word with its frequency, as a weighted string.
     */
    @UsedForTesting
    /* package for tests */ static WeightedString getWordAtPosition(final DictDecoder dictDecoder,
            final int headerSize, final int pos) {
        final WeightedString result;
        final int originalPos = dictDecoder.getPosition();
        dictDecoder.setPosition(pos);
        result = getWordAtPositionWithoutParentAddress(dictDecoder, headerSize, pos);
        dictDecoder.setPosition(originalPos);
        return result;
    }

    private static WeightedString getWordAtPositionWithoutParentAddress(
            final DictDecoder dictDecoder, final int headerSize, final int pos) {
        dictDecoder.setPosition(headerSize);
        final int count = dictDecoder.readPtNodeCount();
        int groupPos = dictDecoder.getPosition();
        final StringBuilder builder = new StringBuilder();
        WeightedString result = null;

        PtNodeInfo last = null;
        for (int i = count - 1; i >= 0; --i) {
            PtNodeInfo info = dictDecoder.readPtNode(groupPos);
            groupPos = info.mEndAddress;
            if (info.mOriginalAddress == pos) {
                builder.append(new String(info.mCharacters, 0, info.mCharacters.length));
                result = new WeightedString(builder.toString(), info.mProbabilityInfo);
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
     * Helper method that brutally decodes a header from a byte array.
     *
     * @param headerBuffer a buffer containing the bytes of the header.
     * @return a hashmap of the attributes stored in the header
     */
    @Nonnull
    public static HashMap<String, String> decodeHeaderAttributes(@Nonnull final byte[] headerBuffer)
            throws UnsupportedFormatException {
        final StringBuilder sb = new StringBuilder();
        final LinkedList<String> keyValues = new LinkedList<>();
        int index = 0;
        while (index < headerBuffer.length) {
            if (headerBuffer[index] == FormatSpec.PTNODE_CHARACTERS_TERMINATOR) {
                keyValues.add(sb.toString());
                sb.setLength(0);
            } else if (CharEncoding.fitsOnOneByte(headerBuffer[index] & 0xFF,
                    null /* codePointTable */)) {
                sb.appendCodePoint(headerBuffer[index] & 0xFF);
            } else {
                sb.appendCodePoint(((headerBuffer[index] & 0xFF) << 16)
                        + ((headerBuffer[index + 1] & 0xFF) << 8)
                        + (headerBuffer[index + 2] & 0xFF));
                index += 2;
            }
            index += 1;
        }
        if ((keyValues.size() & 1) != 0) {
            throw new UnsupportedFormatException("Odd number of attributes");
        }
        final HashMap<String, String> attributes = new HashMap<>();
        for (int i = 0; i < keyValues.size(); i += 2) {
            attributes.put(keyValues.get(i), keyValues.get(i + 1));
        }
        return attributes;
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
     * @param file The file to test.
     * @return true if it's a binary dictionary, false otherwise
     */
    public static boolean isBinaryDictionary(final File file) {
        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length());
        if (dictDecoder == null) {
            return false;
        }
        return dictDecoder.hasValidRawBinaryDictionary();
    }
}
