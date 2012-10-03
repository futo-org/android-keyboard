/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.FusionDictionaryBufferInterface;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

public final class BinaryDictIOUtils {
    private static final boolean DBG = false;
    private static final int MAX_JUMPS = 10000;

    private BinaryDictIOUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final class Position {
        public static final int NOT_READ_GROUPCOUNT = -1;

        public int mAddress;
        public int mNumOfCharGroup;
        public int mPosition;
        public int mLength;

        public Position(int address, int length) {
            mAddress = address;
            mLength = length;
            mNumOfCharGroup = NOT_READ_GROUPCOUNT;
        }
    }

    /**
     * Tours all node without recursive call.
     */
    private static void readUnigramsAndBigramsBinaryInner(
            final FusionDictionaryBufferInterface buffer, final int headerSize,
            final Map<Integer, String> words, final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams,
            final FormatOptions formatOptions) {
        int[] pushedChars = new int[FormatSpec.MAX_WORD_LENGTH + 1];

        Stack<Position> stack = new Stack<Position>();
        int index = 0;

        Position initPos = new Position(headerSize, 0);
        stack.push(initPos);

        while (!stack.empty()) {
            Position p = stack.peek();

            if (DBG) {
                MakedictLog.d("read: address=" + p.mAddress + ", numOfCharGroup=" +
                        p.mNumOfCharGroup + ", position=" + p.mPosition + ", length=" + p.mLength);
            }

            if (buffer.position() != p.mAddress) buffer.position(p.mAddress);
            if (index != p.mLength) index = p.mLength;

            if (p.mNumOfCharGroup == Position.NOT_READ_GROUPCOUNT) {
                p.mNumOfCharGroup = BinaryDictInputOutput.readCharGroupCount(buffer);
                p.mAddress += BinaryDictInputOutput.getGroupCountSize(p.mNumOfCharGroup);
                p.mPosition = 0;
            }
            if (p.mNumOfCharGroup == 0) {
                stack.pop();
                continue;
            }
            CharGroupInfo info = BinaryDictInputOutput.readCharGroup(buffer,
                    p.mAddress - headerSize, formatOptions);
            for (int i = 0; i < info.mCharacters.length; ++i) {
                pushedChars[index++] = info.mCharacters[i];
            }
            p.mPosition++;

            final boolean isMovedGroup = BinaryDictInputOutput.isMovedGroup(info.mFlags,
                    formatOptions);
            final boolean isDeletedGroup = BinaryDictInputOutput.isDeletedGroup(info.mFlags,
                    formatOptions);
            if (!isMovedGroup && !isDeletedGroup
                    && info.mFrequency != FusionDictionary.CharGroup.NOT_A_TERMINAL) {// found word
                words.put(info.mOriginalAddress, new String(pushedChars, 0, index));
                frequencies.put(info.mOriginalAddress, info.mFrequency);
                if (info.mBigrams != null) bigrams.put(info.mOriginalAddress, info.mBigrams);
            }

            if (p.mPosition == p.mNumOfCharGroup) {
                if (formatOptions.mSupportsDynamicUpdate) {
                    final int forwardLinkAddress = buffer.readUnsignedInt24();
                    if (forwardLinkAddress != FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                        // the node has a forward link.
                        p.mNumOfCharGroup = Position.NOT_READ_GROUPCOUNT;
                        p.mAddress = forwardLinkAddress;
                    } else {
                        stack.pop();
                    }
                } else {
                    stack.pop();
                }
            } else {
                // the node has more groups.
                p.mAddress = buffer.position();
            }

            if (!isMovedGroup && BinaryDictInputOutput.hasChildrenAddress(info.mChildrenAddress)) {
                Position childrenPos = new Position(info.mChildrenAddress + headerSize, index);
                stack.push(childrenPos);
            }
        }
    }

    /**
     * Reads unigrams and bigrams from the binary file.
     * Doesn't make the memory representation of the dictionary.
     *
     * @param buffer the buffer to read.
     * @param words the map to store the address as a key and the word as a value.
     * @param frequencies the map to store the address as a key and the frequency as a value.
     * @param bigrams the map to store the address as a key and the list of address as a value.
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    public static void readUnigramsAndBigramsBinary(final FusionDictionaryBufferInterface buffer,
            final Map<Integer, String> words, final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams) throws IOException,
            UnsupportedFormatException {
        // Read header
        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        readUnigramsAndBigramsBinaryInner(buffer, header.mHeaderSize, words, frequencies, bigrams,
                header.mFormatOptions);
    }

    /**
     * Gets the address of the last CharGroup of the exact matching word in the dictionary.
     * If no match is found, returns NOT_VALID_WORD.
     *
     * @param buffer the buffer to read.
     * @param word the word we search for.
     * @return the address of the terminal node.
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    public static int getTerminalPosition(final FusionDictionaryBufferInterface buffer,
            final String word) throws IOException, UnsupportedFormatException {
        if (word == null) return FormatSpec.NOT_VALID_WORD;
        if (buffer.position() != 0) buffer.position(0);

        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        int wordPos = 0;
        final int wordLen = word.codePointCount(0, word.length());
        for (int depth = 0; depth < Constants.Dictionary.MAX_WORD_LENGTH; ++depth) {
            if (wordPos >= wordLen) return FormatSpec.NOT_VALID_WORD;

            do {
                final int charGroupCount = BinaryDictInputOutput.readCharGroupCount(buffer);
                boolean foundNextCharGroup = false;
                for (int i = 0; i < charGroupCount; ++i) {
                    final int charGroupPos = buffer.position();
                    final CharGroupInfo currentInfo = BinaryDictInputOutput.readCharGroup(buffer,
                            buffer.position(), header.mFormatOptions);
                    final boolean isMovedGroup =
                            BinaryDictInputOutput.isMovedGroup(currentInfo.mFlags,
                                    header.mFormatOptions);
                    final boolean isDeletedGroup =
                            BinaryDictInputOutput.isDeletedGroup(currentInfo.mFlags,
                                    header.mFormatOptions);
                    if (isMovedGroup) continue;
                    boolean same = true;
                    for (int p = 0, j = word.offsetByCodePoints(0, wordPos);
                            p < currentInfo.mCharacters.length;
                            ++p, j = word.offsetByCodePoints(j, 1)) {
                        if (wordPos + p >= wordLen
                                || word.codePointAt(j) != currentInfo.mCharacters[p]) {
                            same = false;
                            break;
                        }
                    }

                    if (same) {
                        // found the group matches the word.
                        if (wordPos + currentInfo.mCharacters.length == wordLen) {
                            if (currentInfo.mFrequency == CharGroup.NOT_A_TERMINAL
                                    || isDeletedGroup) {
                                return FormatSpec.NOT_VALID_WORD;
                            } else {
                                return charGroupPos;
                            }
                        }
                        wordPos += currentInfo.mCharacters.length;
                        if (currentInfo.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS) {
                            return FormatSpec.NOT_VALID_WORD;
                        }
                        foundNextCharGroup = true;
                        buffer.position(currentInfo.mChildrenAddress);
                        break;
                    }
                }

                // If we found the next char group, it is under the file pointer.
                // But if not, we are at the end of this node so we expect to have
                // a forward link address that we need to consult and possibly resume
                // search on the next node in the linked list.
                if (foundNextCharGroup) break;
                if (!header.mFormatOptions.mSupportsDynamicUpdate) {
                    return FormatSpec.NOT_VALID_WORD;
                }

                final int forwardLinkAddress = buffer.readUnsignedInt24();
                if (forwardLinkAddress == FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                    return FormatSpec.NOT_VALID_WORD;
                }
                buffer.position(forwardLinkAddress);
            } while(true);
        }
        return FormatSpec.NOT_VALID_WORD;
    }

    private static int markAsDeleted(final int flags) {
        return (flags & (~FormatSpec.MASK_GROUP_ADDRESS_TYPE)) | FormatSpec.FLAG_IS_DELETED;
    }

    /**
     * Delete the word from the binary file.
     *
     * @param buffer the buffer to write.
     * @param word the word we delete
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    public static void deleteWord(final FusionDictionaryBufferInterface buffer,
            final String word) throws IOException, UnsupportedFormatException {
        buffer.position(0);
        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        final int wordPosition = getTerminalPosition(buffer, word);
        if (wordPosition == FormatSpec.NOT_VALID_WORD) return;

        buffer.position(wordPosition);
        final int flags = buffer.readUnsignedByte();
        buffer.position(wordPosition);
        buffer.put((byte)markAsDeleted(flags));
    }

    /**
     * @return the size written, in bytes. Always 3 bytes.
     */
    private static int writeSInt24ToBuffer(final FusionDictionaryBufferInterface buffer,
            final int value) {
        final int absValue = Math.abs(value);
        buffer.put((byte)(((value < 0 ? 0x80 : 0) | (absValue >> 16)) & 0xFF));
        buffer.put((byte)((absValue >> 8) & 0xFF));
        buffer.put((byte)(absValue & 0xFF));
        return 3;
    }

    /**
     * @return the size written, in bytes. Always 3 bytes.
     */
    private static int writeSInt24ToStream(final OutputStream destination, final int value)
            throws IOException {
        final int absValue = Math.abs(value);
        destination.write((byte)(((value < 0 ? 0x80 : 0) | (absValue >> 16)) & 0xFF));
        destination.write((byte)((absValue >> 8) & 0xFF));
        destination.write((byte)(absValue & 0xFF));
        return 3;
    }

    /**
     * @return the size written, in bytes. 1, 2, or 3 bytes.
     */
    private static int writeVariableAddress(final OutputStream destination, final int value)
            throws IOException {
        switch (BinaryDictInputOutput.getByteSize(value)) {
        case 1:
            destination.write((byte)value);
            break;
        case 2:
            destination.write((byte)(0xFF & (value >> 8)));
            destination.write((byte)(0xFF & value));
            break;
        case 3:
            destination.write((byte)(0xFF & (value >> 16)));
            destination.write((byte)(0xFF & (value >> 8)));
            destination.write((byte)(0xFF & value));
            break;
        }
        return BinaryDictInputOutput.getByteSize(value);
    }

    /**
     * Update a parent address in a CharGroup that is referred to by groupOriginAddress.
     *
     * @param buffer the buffer to write.
     * @param groupOriginAddress the address of the group.
     * @param newParentAddress the absolute address of the parent.
     * @param formatOptions file format options.
     */
    public static void updateParentAddress(final FusionDictionaryBufferInterface buffer,
            final int groupOriginAddress, final int newParentAddress,
            final FormatOptions formatOptions) {
        final int originalPosition = buffer.position();
        buffer.position(groupOriginAddress);
        if (!formatOptions.mSupportsDynamicUpdate) {
            throw new RuntimeException("this file format does not support parent addresses");
        }
        final int flags = buffer.readUnsignedByte();
        if (BinaryDictInputOutput.isMovedGroup(flags, formatOptions)) {
            // if the group is moved, the parent address is stored in the destination group.
            // We are guaranteed to process the destination group later, so there is no need to
            // update anything here.
            buffer.position(originalPosition);
            return;
        }
        if (DBG) {
            MakedictLog.d("update parent address flags=" + flags + ", " + groupOriginAddress);
        }
        final int parentOffset = newParentAddress - groupOriginAddress;
        writeSInt24ToBuffer(buffer, parentOffset);
        buffer.position(originalPosition);
    }

    private static void skipCharGroup(final FusionDictionaryBufferInterface buffer,
            final FormatOptions formatOptions) {
        final int flags = buffer.readUnsignedByte();
        BinaryDictInputOutput.readParentAddress(buffer, formatOptions);
        skipString(buffer, (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        BinaryDictInputOutput.readChildrenAddress(buffer, flags, formatOptions);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) buffer.readUnsignedByte();
        if ((flags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS) != 0) {
            final int shortcutsSize = buffer.readUnsignedShort();
            buffer.position(buffer.position() + shortcutsSize
                    - FormatSpec.GROUP_SHORTCUT_LIST_SIZE_SIZE);
        }
        if ((flags & FormatSpec.FLAG_HAS_BIGRAMS) != 0) {
            int bigramCount = 0;
            while (bigramCount++ < FormatSpec.MAX_BIGRAMS_IN_A_GROUP) {
                final int bigramFlags = buffer.readUnsignedByte();
                switch (bigramFlags & FormatSpec.MASK_ATTRIBUTE_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE:
                        buffer.readUnsignedByte();
                        break;
                    case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES:
                        buffer.readUnsignedShort();
                        break;
                    case FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES:
                        buffer.readUnsignedInt24();
                        break;
                }
                if ((bigramFlags & FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT) == 0) break;
            }
            if (bigramCount >= FormatSpec.MAX_BIGRAMS_IN_A_GROUP) {
                throw new RuntimeException("Too many bigrams in a group.");
            }
        }
    }

    /**
     * Update parent addresses in a Node that is referred to by nodeOriginAddress.
     *
     * @param buffer the buffer to be modified.
     * @param nodeOriginAddress the address of a modified Node.
     * @param newParentAddress the address to be written.
     * @param formatOptions file format options.
     */
    public static void updateParentAddresses(final FusionDictionaryBufferInterface buffer,
            final int nodeOriginAddress, final int newParentAddress,
            final FormatOptions formatOptions) {
        final int originalPosition = buffer.position();
        buffer.position(nodeOriginAddress);
        do {
            final int count = BinaryDictInputOutput.readCharGroupCount(buffer);
            for (int i = 0; i < count; ++i) {
                updateParentAddress(buffer, buffer.position(), newParentAddress, formatOptions);
                skipCharGroup(buffer, formatOptions);
            }
            final int forwardLinkAddress = buffer.readUnsignedInt24();
            buffer.position(forwardLinkAddress);
        } while (formatOptions.mSupportsDynamicUpdate
                && buffer.position() != FormatSpec.NO_FORWARD_LINK_ADDRESS);
        buffer.position(originalPosition);
    }

    private static void skipString(final FusionDictionaryBufferInterface buffer,
            final boolean hasMultipleChars) {
        if (hasMultipleChars) {
            int character = CharEncoding.readChar(buffer);
            while (character != FormatSpec.INVALID_CHARACTER) {
                character = CharEncoding.readChar(buffer);
            }
        } else {
            CharEncoding.readChar(buffer);
        }
    }

    /**
     * Write a string to a stream.
     *
     * @param destination the stream to write.
     * @param word the string to be written.
     * @return the size written, in bytes.
     * @throws IOException
     */
    private static int writeString(final OutputStream destination, final String word)
            throws IOException {
        int size = 0;
        final int length = word.length();
        for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
            final int codePoint = word.codePointAt(i);
            if (CharEncoding.getCharSize(codePoint) == 1) {
                destination.write((byte)codePoint);
                size++;
            } else {
                destination.write((byte)(0xFF & (codePoint >> 16)));
                destination.write((byte)(0xFF & (codePoint >> 8)));
                destination.write((byte)(0xFF & codePoint));
                size += 3;
            }
        }
        destination.write((byte)FormatSpec.GROUP_CHARACTERS_TERMINATOR);
        size += FormatSpec.GROUP_TERMINATOR_SIZE;
        return size;
    }

    /**
     * Update a children address in a CharGroup that is addressed by groupOriginAddress.
     *
     * @param buffer the buffer to write.
     * @param groupOriginAddress the address of the group.
     * @param newChildrenAddress the absolute address of the child.
     * @param formatOptions file format options.
     */
    public static void updateChildrenAddress(final FusionDictionaryBufferInterface buffer,
            final int groupOriginAddress, final int newChildrenAddress,
            final FormatOptions formatOptions) {
        final int originalPosition = buffer.position();
        buffer.position(groupOriginAddress);
        final int flags = buffer.readUnsignedByte();
        final int parentAddress = BinaryDictInputOutput.readParentAddress(buffer, formatOptions);
        skipString(buffer, (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) buffer.readUnsignedByte();
        final int childrenOffset = newChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS
                ? FormatSpec.NO_CHILDREN_ADDRESS : newChildrenAddress - buffer.position();
        writeSInt24ToBuffer(buffer, childrenOffset);
        buffer.position(originalPosition);
    }

    /**
     * Write a char group to an output stream.
     * A char group is an in-memory representation of a node in trie.
     * A char group info is an on-disk representation of a node.
     *
     * @param destination the stream to write.
     * @param info the char group info to be written.
     * @return the size written, in bytes.
     */
    public static int writeCharGroup(final OutputStream destination, final CharGroupInfo info)
            throws IOException {
        int size = FormatSpec.GROUP_FLAGS_SIZE;
        destination.write((byte)info.mFlags);
        final int parentOffset = info.mParentAddress == FormatSpec.NO_PARENT_ADDRESS ?
                FormatSpec.NO_PARENT_ADDRESS : info.mParentAddress - info.mOriginalAddress;
        size += writeSInt24ToStream(destination, parentOffset);

        for (int i = 0; i < info.mCharacters.length; ++i) {
            if (CharEncoding.getCharSize(info.mCharacters[i]) == 1) {
                destination.write((byte)info.mCharacters[i]);
                size++;
            } else {
                size += writeSInt24ToStream(destination, info.mCharacters[i]);
            }
        }
        if (info.mCharacters.length > 1) {
            destination.write((byte)FormatSpec.GROUP_CHARACTERS_TERMINATOR);
            size++;
        }

        if ((info.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0) {
            destination.write((byte)info.mFrequency);
            size++;
        }

        if (DBG) {
            MakedictLog.d("writeCharGroup origin=" + info.mOriginalAddress + ", size=" + size
                    + ", child=" + info.mChildrenAddress + ", characters ="
                    + new String(info.mCharacters, 0, info.mCharacters.length));
        }
        final int childrenOffset = info.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS ?
                0 : info.mChildrenAddress - (info.mOriginalAddress + size);
        writeSInt24ToStream(destination, childrenOffset);
        size += FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;

        if (info.mShortcutTargets != null && info.mShortcutTargets.size() > 0) {
            final int shortcutListSize =
                    BinaryDictInputOutput.getShortcutListSize(info.mShortcutTargets);
            destination.write((byte)(shortcutListSize >> 8));
            destination.write((byte)(shortcutListSize & 0xFF));
            size += 2;
            final Iterator<WeightedString> shortcutIterator = info.mShortcutTargets.iterator();
            while (shortcutIterator.hasNext()) {
                final WeightedString target = shortcutIterator.next();
                destination.write((byte)BinaryDictInputOutput.makeShortcutFlags(
                        shortcutIterator.hasNext(), target.mFrequency));
                size++;
                size += writeString(destination, target.mWord);
            }
        }

        if (info.mBigrams != null) {
            // TODO: Consolidate this code with the code that computes the size of the bigram list
            //        in BinaryDictionaryInputOutput#computeActualNodeSize
            for (int i = 0; i < info.mBigrams.size(); ++i) {
                final int bigramOffset = info.mBigrams.get(i).mAddress - info.mOriginalAddress;
                final int bigramFrequency = info.mBigrams.get(i).mFrequency;
                int bigramFlags = (i < info.mBigrams.size() - 1)
                        ? FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT : 0;
                bigramFlags |= (bigramOffset < 0) ? FormatSpec.FLAG_ATTRIBUTE_OFFSET_NEGATIVE : 0;
                switch (BinaryDictInputOutput.getByteSize(bigramOffset)) {
                case 1:
                    bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
                    break;
                case 2:
                    bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
                    break;
                case 3:
                    bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
                    break;
                }
                bigramFlags |= bigramFrequency & FormatSpec.FLAG_ATTRIBUTE_FREQUENCY;
                destination.write((byte)bigramFlags);
                size++;
                size += writeVariableAddress(destination, Math.abs(bigramOffset));
            }
        }
        return size;
    }

    private static void updateForwardLink(final FusionDictionaryBufferInterface buffer,
            final int nodeOriginAddress, final int newNodeAddress,
            final FormatOptions formatOptions) {
        buffer.position(nodeOriginAddress);
        int jumpCount = 0;
        while (jumpCount++ < MAX_JUMPS) {
            final int count = BinaryDictInputOutput.readCharGroupCount(buffer);
            for (int i = 0; i < count; ++i) skipCharGroup(buffer, formatOptions);
            final int forwardLinkAddress = buffer.readUnsignedInt24();
            if (forwardLinkAddress == FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                buffer.position(buffer.position() - FormatSpec.FORWARD_LINK_ADDRESS_SIZE);
                writeSInt24ToBuffer(buffer, newNodeAddress);
                return;
            }
            buffer.position(forwardLinkAddress);
        }
        if (DBG && jumpCount >= MAX_JUMPS) {
            throw new RuntimeException("too many jumps, probably a bug.");
        }
    }

    /**
     * Helper method to move a char group to the tail of the file.
     */
    private static int moveCharGroup(final OutputStream destination,
            final FusionDictionaryBufferInterface buffer, final CharGroupInfo info,
            final int nodeOriginAddress, final int oldGroupAddress,
            final FormatOptions formatOptions) throws IOException {
        updateParentAddress(buffer, oldGroupAddress, buffer.limit() + 1, formatOptions);
        buffer.position(oldGroupAddress);
        final int currentFlags = buffer.readUnsignedByte();
        buffer.position(oldGroupAddress);
        buffer.put((byte)(FormatSpec.FLAG_IS_MOVED | (currentFlags
                & (~FormatSpec.MASK_MOVE_AND_DELETE_FLAG))));
        int size = FormatSpec.GROUP_FLAGS_SIZE;
        updateForwardLink(buffer, nodeOriginAddress, buffer.limit(), formatOptions);
        size += writeNode(destination, new CharGroupInfo[] { info });
        return size;
    }

    /**
     * Compute the size of the char group.
     */
    private static int computeGroupSize(final CharGroupInfo info,
            final FormatOptions formatOptions) {
        int size = FormatSpec.GROUP_FLAGS_SIZE + FormatSpec.PARENT_ADDRESS_SIZE
                + BinaryDictInputOutput.getGroupCharactersSize(info.mCharacters)
                + BinaryDictInputOutput.getChildrenAddressSize(info.mFlags, formatOptions);
        if ((info.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0) {
            size += FormatSpec.GROUP_FREQUENCY_SIZE;
        }
        if (info.mShortcutTargets != null && !info.mShortcutTargets.isEmpty()) {
            size += BinaryDictInputOutput.getShortcutListSize(info.mShortcutTargets);
        }
        if (info.mBigrams != null) {
            for (final PendingAttribute attr : info.mBigrams) {
                size += FormatSpec.GROUP_FLAGS_SIZE;
                size += BinaryDictInputOutput.getByteSize(attr.mAddress);
            }
        }
        return size;
    }

    /**
     * Write a node to the stream.
     *
     * @param destination the stream to write.
     * @param infos groups to be written.
     * @return the size written, in bytes.
     * @throws IOException
     */
    private static int writeNode(final OutputStream destination, final CharGroupInfo[] infos)
            throws IOException {
        int size = BinaryDictInputOutput.getGroupCountSize(infos.length);
        switch (BinaryDictInputOutput.getGroupCountSize(infos.length)) {
            case 1:
                destination.write((byte)infos.length);
                break;
            case 2:
                destination.write((byte)(infos.length >> 8));
                destination.write((byte)(infos.length & 0xFF));
                break;
            default:
                throw new RuntimeException("Invalid group count size.");
        }
        for (final CharGroupInfo info : infos) size += writeCharGroup(destination, info);
        writeSInt24ToStream(destination, FormatSpec.NO_FORWARD_LINK_ADDRESS);
        return size + FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
    }
}
