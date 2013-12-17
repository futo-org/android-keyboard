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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

public final class BinaryDictIOUtils {
    private static final boolean DBG = false;

    private BinaryDictIOUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final class Position {
        public static final int NOT_READ_PTNODE_COUNT = -1;

        public int mAddress;
        public int mNumOfPtNode;
        public int mPosition;
        public int mLength;

        public Position(int address, int length) {
            mAddress = address;
            mLength = length;
            mNumOfPtNode = NOT_READ_PTNODE_COUNT;
        }
    }

    /**
     * Retrieves all node arrays without recursive call.
     */
    private static void readUnigramsAndBigramsBinaryInner(final DictDecoder dictDecoder,
            final int bodyOffset, final Map<Integer, String> words,
            final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams,
            final FormatOptions formatOptions) {
        int[] pushedChars = new int[FormatSpec.MAX_WORD_LENGTH + 1];

        Stack<Position> stack = new Stack<Position>();
        int index = 0;

        Position initPos = new Position(bodyOffset, 0);
        stack.push(initPos);

        while (!stack.empty()) {
            Position p = stack.peek();

            if (DBG) {
                MakedictLog.d("read: address=" + p.mAddress + ", numOfPtNode=" +
                        p.mNumOfPtNode + ", position=" + p.mPosition + ", length=" + p.mLength);
            }

            if (dictDecoder.getPosition() != p.mAddress) dictDecoder.setPosition(p.mAddress);
            if (index != p.mLength) index = p.mLength;

            if (p.mNumOfPtNode == Position.NOT_READ_PTNODE_COUNT) {
                p.mNumOfPtNode = dictDecoder.readPtNodeCount();
                p.mAddress += getPtNodeCountSize(p.mNumOfPtNode);
                p.mPosition = 0;
            }
            if (p.mNumOfPtNode == 0) {
                stack.pop();
                continue;
            }
            PtNodeInfo info = dictDecoder.readPtNode(p.mAddress, formatOptions);
            for (int i = 0; i < info.mCharacters.length; ++i) {
                pushedChars[index++] = info.mCharacters[i];
            }
            p.mPosition++;

            final boolean isMovedPtNode = isMovedPtNode(info.mFlags,
                    formatOptions);
            final boolean isDeletedPtNode = isDeletedPtNode(info.mFlags,
                    formatOptions);
            if (!isMovedPtNode && !isDeletedPtNode
                    && info.mFrequency != FusionDictionary.PtNode.NOT_A_TERMINAL) {// found word
                words.put(info.mOriginalAddress, new String(pushedChars, 0, index));
                frequencies.put(info.mOriginalAddress, info.mFrequency);
                if (info.mBigrams != null) bigrams.put(info.mOriginalAddress, info.mBigrams);
            }

            if (p.mPosition == p.mNumOfPtNode) {
                if (formatOptions.supportsDynamicUpdate()) {
                    final boolean hasValidForwardLinkAddress =
                            dictDecoder.readAndFollowForwardLink();
                    if (hasValidForwardLinkAddress && dictDecoder.hasNextPtNodeArray()) {
                        // The node array has a forward link.
                        p.mNumOfPtNode = Position.NOT_READ_PTNODE_COUNT;
                        p.mAddress = dictDecoder.getPosition();
                    } else {
                        stack.pop();
                    }
                } else {
                    stack.pop();
                }
            } else {
                // The Ptnode array has more PtNodes.
                p.mAddress = dictDecoder.getPosition();
            }

            if (!isMovedPtNode && hasChildrenAddress(info.mChildrenAddress)) {
                final Position childrenPos = new Position(info.mChildrenAddress, index);
                stack.push(childrenPos);
            }
        }
    }

    /**
     * Reads unigrams and bigrams from the binary file.
     * Doesn't store a full memory representation of the dictionary.
     *
     * @param dictDecoder the dict decoder.
     * @param words the map to store the address as a key and the word as a value.
     * @param frequencies the map to store the address as a key and the frequency as a value.
     * @param bigrams the map to store the address as a key and the list of address as a value.
     * @throws IOException if the file can't be read.
     * @throws UnsupportedFormatException if the format of the file is not recognized.
     */
    /* package */ static void readUnigramsAndBigramsBinary(final DictDecoder dictDecoder,
            final Map<Integer, String> words, final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams) throws IOException,
            UnsupportedFormatException {
        // Read header
        final FileHeader header = dictDecoder.readHeader();
        readUnigramsAndBigramsBinaryInner(dictDecoder, header.mBodyOffset, words,
                frequencies, bigrams, header.mFormatOptions);
    }

    /**
     * Gets the address of the last PtNode of the exact matching word in the dictionary.
     * If no match is found, returns NOT_VALID_WORD.
     *
     * @param dictDecoder the dict decoder.
     * @param word the word we search for.
     * @return the address of the terminal node.
     * @throws IOException if the file can't be read.
     * @throws UnsupportedFormatException if the format of the file is not recognized.
     */
    @UsedForTesting
    /* package */ static int getTerminalPosition(final DictDecoder dictDecoder,
            final String word) throws IOException, UnsupportedFormatException {
        if (word == null) return FormatSpec.NOT_VALID_WORD;
        dictDecoder.setPosition(0);

        final FileHeader header = dictDecoder.readHeader();
        int wordPos = 0;
        final int wordLen = word.codePointCount(0, word.length());
        for (int depth = 0; depth < Constants.DICTIONARY_MAX_WORD_LENGTH; ++depth) {
            if (wordPos >= wordLen) return FormatSpec.NOT_VALID_WORD;

            do {
                final int ptNodeCount = dictDecoder.readPtNodeCount();
                boolean foundNextPtNode = false;
                for (int i = 0; i < ptNodeCount; ++i) {
                    final int ptNodePos = dictDecoder.getPosition();
                    final PtNodeInfo currentInfo = dictDecoder.readPtNode(ptNodePos,
                            header.mFormatOptions);
                    final boolean isMovedNode = isMovedPtNode(currentInfo.mFlags,
                            header.mFormatOptions);
                    final boolean isDeletedNode = isDeletedPtNode(currentInfo.mFlags,
                            header.mFormatOptions);
                    if (isMovedNode) continue;
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
                        // found the PtNode matches the word.
                        if (wordPos + currentInfo.mCharacters.length == wordLen) {
                            if (currentInfo.mFrequency == PtNode.NOT_A_TERMINAL
                                    || isDeletedNode) {
                                return FormatSpec.NOT_VALID_WORD;
                            } else {
                                return ptNodePos;
                            }
                        }
                        wordPos += currentInfo.mCharacters.length;
                        if (currentInfo.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS) {
                            return FormatSpec.NOT_VALID_WORD;
                        }
                        foundNextPtNode = true;
                        dictDecoder.setPosition(currentInfo.mChildrenAddress);
                        break;
                    }
                }

                // If we found the next PtNode, it is under the file pointer.
                // But if not, we are at the end of this node array so we expect to have
                // a forward link address that we need to consult and possibly resume
                // search on the next node array in the linked list.
                if (foundNextPtNode) break;
                if (!header.mFormatOptions.supportsDynamicUpdate()) {
                    return FormatSpec.NOT_VALID_WORD;
                }

                final boolean hasValidForwardLinkAddress =
                        dictDecoder.readAndFollowForwardLink();
                if (!hasValidForwardLinkAddress || !dictDecoder.hasNextPtNodeArray()) {
                    return FormatSpec.NOT_VALID_WORD;
                }
            } while(true);
        }
        return FormatSpec.NOT_VALID_WORD;
    }

    /**
     * @return the size written, in bytes. Always 3 bytes.
     */
    @UsedForTesting
    static int writeSInt24ToBuffer(final DictBuffer dictBuffer, final int value) {
        final int absValue = Math.abs(value);
        dictBuffer.put((byte)(((value < 0 ? 0x80 : 0) | (absValue >> 16)) & 0xFF));
        dictBuffer.put((byte)((absValue >> 8) & 0xFF));
        dictBuffer.put((byte)(absValue & 0xFF));
        return 3;
    }

    /**
     * @return the size written, in bytes. Always 3 bytes.
     */
    @UsedForTesting
    static int writeSInt24ToStream(final OutputStream destination, final int value)
            throws IOException {
        final int absValue = Math.abs(value);
        destination.write((byte)(((value < 0 ? 0x80 : 0) | (absValue >> 16)) & 0xFF));
        destination.write((byte)((absValue >> 8) & 0xFF));
        destination.write((byte)(absValue & 0xFF));
        return 3;
    }

    @UsedForTesting
    static void skipString(final DictBuffer dictBuffer,
            final boolean hasMultipleChars) {
        if (hasMultipleChars) {
            int character = CharEncoding.readChar(dictBuffer);
            while (character != FormatSpec.INVALID_CHARACTER) {
                character = CharEncoding.readChar(dictBuffer);
            }
        } else {
            CharEncoding.readChar(dictBuffer);
        }
    }

    /**
     * Writes a PtNodeCount to the stream.
     *
     * @param destination the stream to write.
     * @param ptNodeCount the count.
     * @return the size written in bytes.
     */
    @UsedForTesting
    static int writePtNodeCount(final OutputStream destination, final int ptNodeCount)
            throws IOException {
        final int countSize = BinaryDictIOUtils.getPtNodeCountSize(ptNodeCount);
        // the count must fit on one byte or two bytes.
        // Please see comments in FormatSpec.
        if (countSize != 1 && countSize != 2) {
            throw new RuntimeException("Strange size from getPtNodeCountSize : " + countSize);
        }
        final int encodedPtNodeCount = (countSize == 2) ?
                (ptNodeCount | FormatSpec.LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG) : ptNodeCount;
        BinaryDictEncoderUtils.writeUIntToStream(destination, encodedPtNodeCount, countSize);
        return countSize;
    }

    private static final int HEADER_READING_BUFFER_SIZE = 16384;
    /**
     * Convenience method to read the header of a binary file.
     *
     * This is quite resource intensive - don't call when performance is critical.
     *
     * @param file The file to read.
     * @param offset The offset in the file where to start reading the data.
     * @param length The length of the data file.
     */
    private static FileHeader getDictionaryFileHeader(
            final File file, final long offset, final long length)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        final byte[] buffer = new byte[HEADER_READING_BUFFER_SIZE];
        final DictDecoder dictDecoder = FormatSpec.getDictDecoder(file,
                new DictDecoder.DictionaryBufferFactory() {
                    @Override
                    public DictBuffer getDictionaryBuffer(File file)
                            throws FileNotFoundException, IOException {
                        final FileInputStream inStream = new FileInputStream(file);
                        try {
                            inStream.skip(offset);
                            inStream.read(buffer);
                            return new ByteArrayDictBuffer(buffer);
                        } finally {
                            inStream.close();
                        }
                    }
                }
        );
        return dictDecoder.readHeader();
    }

    public static FileHeader getDictionaryFileHeaderOrNull(final File file, final long offset,
            final long length) {
        try {
            final FileHeader header = getDictionaryFileHeader(file, offset, length);
            return header;
        } catch (UnsupportedFormatException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Helper method to hide the actual value of the no children address.
     */
    public static boolean hasChildrenAddress(final int address) {
        return FormatSpec.NO_CHILDREN_ADDRESS != address;
    }

    /**
     * Helper method to check whether the node is moved.
     */
    public static boolean isMovedPtNode(final int flags, final FormatOptions options) {
        return options.supportsDynamicUpdate()
                && ((flags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) == FormatSpec.FLAG_IS_MOVED);
    }

    /**
     * Helper method to check whether the dictionary can be updated dynamically.
     */
    public static boolean supportsDynamicUpdate(final FormatOptions options) {
        return options.mVersion >= FormatSpec.FIRST_VERSION_WITH_DYNAMIC_UPDATE
                && options.supportsDynamicUpdate();
    }

    /**
     * Helper method to check whether the node is deleted.
     */
    public static boolean isDeletedPtNode(final int flags, final FormatOptions formatOptions) {
        return formatOptions.supportsDynamicUpdate()
                && ((flags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) == FormatSpec.FLAG_IS_DELETED);
    }

    /**
     * Compute the binary size of the node count
     * @param count the node count
     * @return the size of the node count, either 1 or 2 bytes.
     */
    public static int getPtNodeCountSize(final int count) {
        if (FormatSpec.MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT >= count) {
            return 1;
        } else if (FormatSpec.MAX_PTNODES_IN_A_PT_NODE_ARRAY >= count) {
            return 2;
        } else {
            throw new RuntimeException("Can't have more than "
                    + FormatSpec.MAX_PTNODES_IN_A_PT_NODE_ARRAY + " PtNode in a PtNodeArray (found "
                    + count + ")");
        }
    }

    static int getChildrenAddressSize(final int optionFlags,
            final FormatOptions formatOptions) {
        if (formatOptions.supportsDynamicUpdate()) return FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;
        switch (optionFlags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) {
            case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE:
                return 1;
            case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES:
                return 2;
            case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES:
                return 3;
            case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS:
            default:
                return 0;
        }
    }

    /**
     * Calculate bigram frequency from compressed value
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
