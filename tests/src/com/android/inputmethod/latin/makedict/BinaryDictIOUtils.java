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
import com.android.inputmethod.latin.define.DecoderSpecificConstants;
import com.android.inputmethod.latin.makedict.DictDecoder.DictionaryBufferFactory;

import java.io.File;
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

    /**
     * Returns new dictionary decoder.
     *
     * @param dictFile the dictionary file.
     * @param bufferType The type of buffer, as one of USE_* in DictDecoder.
     * @return new dictionary decoder if the dictionary file exists, otherwise null.
     */
    public static DictDecoder getDictDecoder(final File dictFile, final long offset,
            final long length, final int bufferType) {
        return new Ver4DictDecoder(dictFile);
    }

    public static DictDecoder getDictDecoder(final File dictFile, final long offset,
            final long length, final DictionaryBufferFactory factory) {
        return new Ver4DictDecoder(dictFile);
    }

    public static DictDecoder getDictDecoder(final File dictFile, final long offset,
            final long length) {
        return getDictDecoder(dictFile, offset, length, DictDecoder.USE_READONLY_BYTEBUFFER);
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
            final Map<Integer, ArrayList<PendingAttribute>> bigrams) {
        int[] pushedChars = new int[FormatSpec.MAX_WORD_LENGTH + 1];

        Stack<Position> stack = new Stack<>();
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
                p.mAddress = dictDecoder.getPosition();
                p.mPosition = 0;
            }
            if (p.mNumOfPtNode == 0) {
                stack.pop();
                continue;
            }
            final PtNodeInfo ptNodeInfo = dictDecoder.readPtNode(p.mAddress);
            for (int i = 0; i < ptNodeInfo.mCharacters.length; ++i) {
                pushedChars[index++] = ptNodeInfo.mCharacters[i];
            }
            p.mPosition++;
            if (ptNodeInfo.isTerminal()) {// found word
                words.put(ptNodeInfo.mOriginalAddress, new String(pushedChars, 0, index));
                frequencies.put(
                        ptNodeInfo.mOriginalAddress, ptNodeInfo.mProbabilityInfo.mProbability);
                if (ptNodeInfo.mBigrams != null) {
                    bigrams.put(ptNodeInfo.mOriginalAddress, ptNodeInfo.mBigrams);
                }
            }

            if (p.mPosition == p.mNumOfPtNode) {
                stack.pop();
            } else {
                // The PtNode array has more PtNodes.
                p.mAddress = dictDecoder.getPosition();
            }

            if (hasChildrenAddress(ptNodeInfo.mChildrenAddress)) {
                final Position childrenPos = new Position(ptNodeInfo.mChildrenAddress, index);
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
        final DictionaryHeader header = dictDecoder.readHeader();
        readUnigramsAndBigramsBinaryInner(dictDecoder, header.mBodyOffset, words,
            frequencies, bigrams);
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
        dictDecoder.readHeader();
        int wordPos = 0;
        final int wordLen = word.codePointCount(0, word.length());
        for (int depth = 0; depth < DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH; ++depth) {
            if (wordPos >= wordLen) return FormatSpec.NOT_VALID_WORD;

            do {
                final int ptNodeCount = dictDecoder.readPtNodeCount();
                boolean foundNextPtNode = false;
                for (int i = 0; i < ptNodeCount; ++i) {
                    final int ptNodePos = dictDecoder.getPosition();
                    final PtNodeInfo currentInfo = dictDecoder.readPtNode(ptNodePos);
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
                            return currentInfo.isTerminal() ? ptNodePos : FormatSpec.NOT_VALID_WORD;
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
                if (foundNextPtNode) break;
                return FormatSpec.NOT_VALID_WORD;
            } while(true);
        }
        return FormatSpec.NOT_VALID_WORD;
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

    /**
     * Helper method to hide the actual value of the no children address.
     */
    public static boolean hasChildrenAddress(final int address) {
        return FormatSpec.NO_CHILDREN_ADDRESS != address;
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

    static int getChildrenAddressSize(final int optionFlags) {
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
    @UsedForTesting
    public static int reconstructBigramFrequency(final int unigramFrequency,
            final int bigramFrequency) {
        final float stepSize = (FormatSpec.MAX_TERMINAL_FREQUENCY - unigramFrequency)
                / (1.5f + FormatSpec.MAX_BIGRAM_FREQUENCY);
        final float resultFreqFloat = unigramFrequency + stepSize * (bigramFrequency + 1.0f);
        return (int)resultFreqFloat;
    }
}
