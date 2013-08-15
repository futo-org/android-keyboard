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
import com.android.inputmethod.latin.makedict.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads binary files for a FusionDictionary.
 *
 * All the methods in this class are static.
 */
public final class BinaryDictInputUtils {

    private BinaryDictInputUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final boolean DBG = MakedictLog.DBG;

    private static final int MAX_JUMPS = 12;

    @UsedForTesting
    public interface FusionDictionaryBufferInterface {
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

    public static final class ByteBufferWrapper implements FusionDictionaryBufferInterface {
        private ByteBuffer mBuffer;

        public ByteBufferWrapper(final ByteBuffer buffer) {
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
            buffer[index++] = FormatSpec.GROUP_CHARACTERS_TERMINATOR;
            return index - origin;
        }

        /**
         * Writes a string with our character format to a ByteArrayOutputStream.
         *
         * This will also write the terminator byte.
         *
         * @param buffer the ByteArrayOutputStream to write to.
         * @param word the string to write.
         */
        static void writeString(final ByteArrayOutputStream buffer, final String word) {
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
            buffer.write(FormatSpec.GROUP_CHARACTERS_TERMINATOR);
        }

        /**
         * Reads a string from a buffer. This is the converse of the above method.
         */
        private static String readString(final FusionDictionaryBufferInterface buffer) {
            final StringBuilder s = new StringBuilder();
            int character = readChar(buffer);
            while (character != FormatSpec.INVALID_CHARACTER) {
                s.appendCodePoint(character);
                character = readChar(buffer);
            }
            return s.toString();
        }

        /**
         * Reads a character from the buffer.
         *
         * This follows the character format documented earlier in this source file.
         *
         * @param buffer the buffer, positioned over an encoded character.
         * @return the character code.
         */
        static int readChar(final FusionDictionaryBufferInterface buffer) {
            int character = buffer.readUnsignedByte();
            if (!fitsOnOneByte(character)) {
                if (FormatSpec.GROUP_CHARACTERS_TERMINATOR == character) {
                    return FormatSpec.INVALID_CHARACTER;
                }
                character <<= 16;
                character += buffer.readUnsignedShort();
            }
            return character;
        }
    }

    // Input methods: Read a binary dictionary to memory.
    // readDictionaryBinary is the public entry point for them.

    static int readChildrenAddress(final FusionDictionaryBufferInterface buffer,
            final int optionFlags, final FormatOptions options) {
        if (options.mSupportsDynamicUpdate) {
            final int address = buffer.readUnsignedInt24();
            if (address == 0) return FormatSpec.NO_CHILDREN_ADDRESS;
            if ((address & FormatSpec.MSB24) != 0) {
                return -(address & FormatSpec.SINT24_MAX);
            } else {
                return address;
            }
        }
        int address;
        switch (optionFlags & FormatSpec.MASK_GROUP_ADDRESS_TYPE) {
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_ONEBYTE:
                return buffer.readUnsignedByte();
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_TWOBYTES:
                return buffer.readUnsignedShort();
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_THREEBYTES:
                return buffer.readUnsignedInt24();
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_NOADDRESS:
            default:
                return FormatSpec.NO_CHILDREN_ADDRESS;
        }
    }

    static int readParentAddress(final FusionDictionaryBufferInterface buffer,
            final FormatOptions formatOptions) {
        if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
            final int parentAddress = buffer.readUnsignedInt24();
            final int sign = ((parentAddress & FormatSpec.MSB24) != 0) ? -1 : 1;
            return sign * (parentAddress & FormatSpec.SINT24_MAX);
        } else {
            return FormatSpec.NO_PARENT_ADDRESS;
        }
    }

    private static final int[] CHARACTER_BUFFER = new int[FormatSpec.MAX_WORD_LENGTH];
    public static CharGroupInfo readCharGroup(final FusionDictionaryBufferInterface buffer,
            final int originalGroupAddress, final FormatOptions options) {
        int addressPointer = originalGroupAddress;
        final int flags = buffer.readUnsignedByte();
        ++addressPointer;

        final int parentAddress = readParentAddress(buffer, options);
        if (BinaryDictIOUtils.supportsDynamicUpdate(options)) {
            addressPointer += 3;
        }

        final int characters[];
        if (0 != (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS)) {
            int index = 0;
            int character = CharEncoding.readChar(buffer);
            addressPointer += CharEncoding.getCharSize(character);
            while (-1 != character) {
                // FusionDictionary is making sure that the length of the word is smaller than
                // MAX_WORD_LENGTH.
                // So we'll never write past the end of CHARACTER_BUFFER.
                CHARACTER_BUFFER[index++] = character;
                character = CharEncoding.readChar(buffer);
                addressPointer += CharEncoding.getCharSize(character);
            }
            characters = Arrays.copyOfRange(CHARACTER_BUFFER, 0, index);
        } else {
            final int character = CharEncoding.readChar(buffer);
            addressPointer += CharEncoding.getCharSize(character);
            characters = new int[] { character };
        }
        final int frequency;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            ++addressPointer;
            frequency = buffer.readUnsignedByte();
        } else {
            frequency = CharGroup.NOT_A_TERMINAL;
        }
        int childrenAddress = readChildrenAddress(buffer, flags, options);
        if (childrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
            childrenAddress += addressPointer;
        }
        addressPointer += BinaryDictIOUtils.getChildrenAddressSize(flags, options);
        ArrayList<WeightedString> shortcutTargets = null;
        if (0 != (flags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS)) {
            final int pointerBefore = buffer.position();
            shortcutTargets = new ArrayList<WeightedString>();
            buffer.readUnsignedShort(); // Skip the size
            while (true) {
                final int targetFlags = buffer.readUnsignedByte();
                final String word = CharEncoding.readString(buffer);
                shortcutTargets.add(new WeightedString(word,
                        targetFlags & FormatSpec.FLAG_ATTRIBUTE_FREQUENCY));
                if (0 == (targetFlags & FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT)) break;
            }
            addressPointer += buffer.position() - pointerBefore;
        }
        ArrayList<PendingAttribute> bigrams = null;
        if (0 != (flags & FormatSpec.FLAG_HAS_BIGRAMS)) {
            bigrams = new ArrayList<PendingAttribute>();
            int bigramCount = 0;
            while (bigramCount++ < FormatSpec.MAX_BIGRAMS_IN_A_GROUP) {
                final int bigramFlags = buffer.readUnsignedByte();
                ++addressPointer;
                final int sign = 0 == (bigramFlags & FormatSpec.FLAG_ATTRIBUTE_OFFSET_NEGATIVE)
                        ? 1 : -1;
                int bigramAddress = addressPointer;
                switch (bigramFlags & FormatSpec.MASK_ATTRIBUTE_ADDRESS_TYPE) {
                case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE:
                    bigramAddress += sign * buffer.readUnsignedByte();
                    addressPointer += 1;
                    break;
                case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES:
                    bigramAddress += sign * buffer.readUnsignedShort();
                    addressPointer += 2;
                    break;
                case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES:
                    final int offset = (buffer.readUnsignedByte() << 16)
                            + buffer.readUnsignedShort();
                    bigramAddress += sign * offset;
                    addressPointer += 3;
                    break;
                default:
                    throw new RuntimeException("Has bigrams with no address");
                }
                bigrams.add(new PendingAttribute(bigramFlags & FormatSpec.FLAG_ATTRIBUTE_FREQUENCY,
                        bigramAddress));
                if (0 == (bigramFlags & FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT)) break;
            }
            if (bigramCount >= FormatSpec.MAX_BIGRAMS_IN_A_GROUP) {
                MakedictLog.d("too many bigrams in a group.");
            }
        }
        return new CharGroupInfo(originalGroupAddress, addressPointer, flags, characters, frequency,
                parentAddress, childrenAddress, shortcutTargets, bigrams);
    }

    /**
     * Reads and returns the char group count out of a buffer and forwards the pointer.
     */
    public static int readCharGroupCount(final FusionDictionaryBufferInterface buffer) {
        final int msb = buffer.readUnsignedByte();
        if (FormatSpec.MAX_CHARGROUPS_FOR_ONE_BYTE_CHARGROUP_COUNT >= msb) {
            return msb;
        } else {
            return ((FormatSpec.MAX_CHARGROUPS_FOR_ONE_BYTE_CHARGROUP_COUNT & msb) << 8)
                    + buffer.readUnsignedByte();
        }
    }

    // The word cache here is a stopgap bandaid to help the catastrophic performance
    // of this method. Since it performs direct, unbuffered random access to the file and
    // may be called hundreds of thousands of times, the resulting performance is not
    // reasonable without some kind of cache. Thus:
    private static TreeMap<Integer, WeightedString> wordCache =
            new TreeMap<Integer, WeightedString>();
    /**
     * Finds, as a string, the word at the address passed as an argument.
     *
     * @param buffer the buffer to read from.
     * @param headerSize the size of the header.
     * @param address the address to seek.
     * @param formatOptions file format options.
     * @return the word with its frequency, as a weighted string.
     */
    /* package for tests */ static WeightedString getWordAtAddress(
            final FusionDictionaryBufferInterface buffer, final int headerSize, final int address,
            final FormatOptions formatOptions) {
        final WeightedString cachedString = wordCache.get(address);
        if (null != cachedString) return cachedString;

        final WeightedString result;
        final int originalPointer = buffer.position();
        buffer.position(address);

        if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
            result = getWordAtAddressWithParentAddress(buffer, headerSize, address, formatOptions);
        } else {
            result = getWordAtAddressWithoutParentAddress(buffer, headerSize, address,
                    formatOptions);
        }

        wordCache.put(address, result);
        buffer.position(originalPointer);
        return result;
    }

    // TODO: static!? This will behave erratically when used in multi-threaded code.
    // We need to fix this
    private static int[] sGetWordBuffer = new int[FormatSpec.MAX_WORD_LENGTH];
    @SuppressWarnings("unused")
    private static WeightedString getWordAtAddressWithParentAddress(
            final FusionDictionaryBufferInterface buffer, final int headerSize, final int address,
            final FormatOptions options) {
        int currentAddress = address;
        int index = FormatSpec.MAX_WORD_LENGTH - 1;
        int frequency = Integer.MIN_VALUE;
        // the length of the path from the root to the leaf is limited by MAX_WORD_LENGTH
        for (int count = 0; count < FormatSpec.MAX_WORD_LENGTH; ++count) {
            CharGroupInfo currentInfo;
            int loopCounter = 0;
            do {
                buffer.position(currentAddress + headerSize);
                currentInfo = readCharGroup(buffer, currentAddress, options);
                if (BinaryDictIOUtils.isMovedGroup(currentInfo.mFlags, options)) {
                    currentAddress = currentInfo.mParentAddress + currentInfo.mOriginalAddress;
                }
                if (DBG && loopCounter++ > MAX_JUMPS) {
                    MakedictLog.d("Too many jumps - probably a bug");
                }
            } while (BinaryDictIOUtils.isMovedGroup(currentInfo.mFlags, options));
            if (Integer.MIN_VALUE == frequency) frequency = currentInfo.mFrequency;
            for (int i = 0; i < currentInfo.mCharacters.length; ++i) {
                sGetWordBuffer[index--] =
                        currentInfo.mCharacters[currentInfo.mCharacters.length - i - 1];
            }
            if (currentInfo.mParentAddress == FormatSpec.NO_PARENT_ADDRESS) break;
            currentAddress = currentInfo.mParentAddress + currentInfo.mOriginalAddress;
        }

        return new WeightedString(
                new String(sGetWordBuffer, index + 1, FormatSpec.MAX_WORD_LENGTH - index - 1),
                        frequency);
    }

    private static WeightedString getWordAtAddressWithoutParentAddress(
            final FusionDictionaryBufferInterface buffer, final int headerSize, final int address,
            final FormatOptions options) {
        buffer.position(headerSize);
        final int count = readCharGroupCount(buffer);
        int groupOffset = BinaryDictIOUtils.getGroupCountSize(count);
        final StringBuilder builder = new StringBuilder();
        WeightedString result = null;

        CharGroupInfo last = null;
        for (int i = count - 1; i >= 0; --i) {
            CharGroupInfo info = readCharGroup(buffer, groupOffset, options);
            groupOffset = info.mEndAddress;
            if (info.mOriginalAddress == address) {
                builder.append(new String(info.mCharacters, 0, info.mCharacters.length));
                result = new WeightedString(builder.toString(), info.mFrequency);
                break; // and return
            }
            if (BinaryDictIOUtils.hasChildrenAddress(info.mChildrenAddress)) {
                if (info.mChildrenAddress > address) {
                    if (null == last) continue;
                    builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                    buffer.position(last.mChildrenAddress + headerSize);
                    i = readCharGroupCount(buffer);
                    groupOffset = last.mChildrenAddress + BinaryDictIOUtils.getGroupCountSize(i);
                    last = null;
                    continue;
                }
                last = info;
            }
            if (0 == i && BinaryDictIOUtils.hasChildrenAddress(last.mChildrenAddress)) {
                builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                buffer.position(last.mChildrenAddress + headerSize);
                i = readCharGroupCount(buffer);
                groupOffset = last.mChildrenAddress + BinaryDictIOUtils.getGroupCountSize(i);
                last = null;
                continue;
            }
        }
        return result;
    }

    /**
     * Reads a single node from a buffer.
     *
     * This methods reads the file at the current position. A node is fully expected to start at
     * the current position.
     * This will recursively read other nodes into the structure, populating the reverse
     * maps on the fly and using them to keep track of already read nodes.
     *
     * @param buffer the buffer, correctly positioned at the start of a node.
     * @param headerSize the size, in bytes, of the file header.
     * @param reverseNodeMap a mapping from addresses to already read nodes.
     * @param reverseGroupMap a mapping from addresses to already read character groups.
     * @param options file format options.
     * @return the read node with all his children already read.
     */
    private static Node readNode(final FusionDictionaryBufferInterface buffer, final int headerSize,
            final Map<Integer, Node> reverseNodeMap, final Map<Integer, CharGroup> reverseGroupMap,
            final FormatOptions options)
            throws IOException {
        final ArrayList<CharGroup> nodeContents = new ArrayList<CharGroup>();
        final int nodeOrigin = buffer.position() - headerSize;

        do { // Scan the linked-list node.
            final int nodeHeadPosition = buffer.position() - headerSize;
            final int count = readCharGroupCount(buffer);
            int groupOffset = nodeHeadPosition + BinaryDictIOUtils.getGroupCountSize(count);
            for (int i = count; i > 0; --i) { // Scan the array of CharGroup.
                CharGroupInfo info = readCharGroup(buffer, groupOffset, options);
                if (BinaryDictIOUtils.isMovedGroup(info.mFlags, options)) continue;
                ArrayList<WeightedString> shortcutTargets = info.mShortcutTargets;
                ArrayList<WeightedString> bigrams = null;
                if (null != info.mBigrams) {
                    bigrams = new ArrayList<WeightedString>();
                    for (PendingAttribute bigram : info.mBigrams) {
                        final WeightedString word = getWordAtAddress(
                                buffer, headerSize, bigram.mAddress, options);
                        final int reconstructedFrequency =
                                reconstructBigramFrequency(word.mFrequency, bigram.mFrequency);
                        bigrams.add(new WeightedString(word.mWord, reconstructedFrequency));
                    }
                }
                if (BinaryDictIOUtils.hasChildrenAddress(info.mChildrenAddress)) {
                    Node children = reverseNodeMap.get(info.mChildrenAddress);
                    if (null == children) {
                        final int currentPosition = buffer.position();
                        buffer.position(info.mChildrenAddress + headerSize);
                        children = readNode(
                                buffer, headerSize, reverseNodeMap, reverseGroupMap, options);
                        buffer.position(currentPosition);
                    }
                    nodeContents.add(
                            new CharGroup(info.mCharacters, shortcutTargets, bigrams,
                                    info.mFrequency,
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_NOT_A_WORD),
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_BLACKLISTED), children));
                } else {
                    nodeContents.add(
                            new CharGroup(info.mCharacters, shortcutTargets, bigrams,
                                    info.mFrequency,
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_NOT_A_WORD),
                                    0 != (info.mFlags & FormatSpec.FLAG_IS_BLACKLISTED)));
                }
                groupOffset = info.mEndAddress;
            }

            // reach the end of the array.
            if (options.mSupportsDynamicUpdate) {
                final int nextAddress = buffer.readUnsignedInt24();
                if (nextAddress >= 0 && nextAddress < buffer.limit()) {
                    buffer.position(nextAddress);
                } else {
                    break;
                }
            }
        } while (options.mSupportsDynamicUpdate &&
                buffer.position() != FormatSpec.NO_FORWARD_LINK_ADDRESS);

        final Node node = new Node(nodeContents);
        node.mCachedAddressBeforeUpdate = nodeOrigin;
        node.mCachedAddressAfterUpdate = nodeOrigin;
        reverseNodeMap.put(node.mCachedAddressAfterUpdate, node);
        return node;
    }

    /**
     * Helper function to get the binary format version from the header.
     * @throws IOException
     */
    private static int getFormatVersion(final FusionDictionaryBufferInterface buffer)
            throws IOException {
        final int magic = buffer.readInt();
        if (FormatSpec.MAGIC_NUMBER == magic) return buffer.readUnsignedShort();
        return FormatSpec.NOT_A_VERSION_NUMBER;
    }

    /**
     * Helper function to get and validate the binary format version.
     * @throws UnsupportedFormatException
     * @throws IOException
     */
    private static int checkFormatVersion(final FusionDictionaryBufferInterface buffer)
            throws IOException, UnsupportedFormatException {
        final int version = getFormatVersion(buffer);
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
            throw new UnsupportedFormatException("This file has version " + version
                    + ", but this implementation does not support versions above "
                    + FormatSpec.MAXIMUM_SUPPORTED_VERSION);
        }
        return version;
    }

    /**
     * Reads a header from a buffer.
     * @param buffer the buffer to read.
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    public static FileHeader readHeader(final FusionDictionaryBufferInterface buffer)
            throws IOException, UnsupportedFormatException {
        final int version = checkFormatVersion(buffer);
        final int optionsFlags = buffer.readUnsignedShort();

        final HashMap<String, String> attributes = new HashMap<String, String>();
        final int headerSize;
        headerSize = buffer.readInt();

        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

        populateOptions(buffer, headerSize, attributes);
        buffer.position(headerSize);

        final FileHeader header = new FileHeader(headerSize,
                new FusionDictionary.DictionaryOptions(attributes,
                        0 != (optionsFlags & FormatSpec.GERMAN_UMLAUT_PROCESSING_FLAG),
                        0 != (optionsFlags & FormatSpec.FRENCH_LIGATURE_PROCESSING_FLAG)),
                new FormatOptions(version,
                        0 != (optionsFlags & FormatSpec.SUPPORTS_DYNAMIC_UPDATE)));
        return header;
    }

    /**
     * Reads options from a buffer and populate a map with their contents.
     *
     * The buffer is read at the current position, so the caller must take care the pointer
     * is in the right place before calling this.
     */
    public static void populateOptions(final FusionDictionaryBufferInterface buffer,
            final int headerSize, final HashMap<String, String> options) {
        while (buffer.position() < headerSize) {
            final String key = CharEncoding.readString(buffer);
            final String value = CharEncoding.readString(buffer);
            options.put(key, value);
        }
    }

    /**
     * Reads a buffer and returns the memory representation of the dictionary.
     *
     * This high-level method takes a buffer and reads its contents, populating a
     * FusionDictionary structure. The optional dict argument is an existing dictionary to
     * which words from the buffer should be added. If it is null, a new dictionary is created.
     *
     * @param reader the reader.
     * @param dict an optional dictionary to add words to, or null.
     * @return the created (or merged) dictionary.
     */
    @UsedForTesting
    public static FusionDictionary readDictionaryBinary(final BinaryDictReader reader,
            final FusionDictionary dict) throws FileNotFoundException, IOException,
            UnsupportedFormatException {
        // clear cache
        wordCache.clear();

        // if the buffer has not been opened, open the buffer with bytebuffer.
        if (reader.getBuffer() == null) reader.openBuffer(
                new BinaryDictReader.FusionDictionaryBufferFromByteBufferFactory());
        if (reader.getBuffer() == null) {
            MakedictLog.e("Cannot open the buffer");
        }

        // Read header
        final FileHeader header = readHeader(reader.getBuffer());

        Map<Integer, Node> reverseNodeMapping = new TreeMap<Integer, Node>();
        Map<Integer, CharGroup> reverseGroupMapping = new TreeMap<Integer, CharGroup>();
        final Node root = readNode(reader.getBuffer(), header.mHeaderSize, reverseNodeMapping,
                reverseGroupMapping, header.mFormatOptions);

        FusionDictionary newDict = new FusionDictionary(root, header.mDictionaryOptions);
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
            final int version = getFormatVersion(new ByteBufferWrapper(buffer));
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

    /**
     * Calculate bigram frequency from compressed value
     *
     * @see #BinaryDictOutput.makeBigramFlags
     *
     * @param unigramFrequency
     * @param bigramFrequency compressed frequency
     * @return approximate bigram frequency
     */
    public static int reconstructBigramFrequency(final int unigramFrequency,
            final int bigramFrequency) {
        final float stepSize = (FormatSpec.MAX_TERMINAL_FREQUENCY - unigramFrequency)
                / (1.5f + FormatSpec.MAX_BIGRAM_FREQUENCY);
        final float resultFreqFloat = unigramFrequency + stepSize * (bigramFrequency + 1.0f);
        return (int)resultFreqFloat;
    }
}
