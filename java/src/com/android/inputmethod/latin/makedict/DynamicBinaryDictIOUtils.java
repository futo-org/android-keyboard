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
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The utility class to help dynamic updates on the binary dictionary.
 *
 * All the methods in this class are static.
 */
@UsedForTesting
public final class DynamicBinaryDictIOUtils {
    private static final boolean DBG = false;
    private static final int MAX_JUMPS = 10000;

    private DynamicBinaryDictIOUtils() {
        // This utility class is not publicly instantiable.
    }

    /* package */ static int markAsDeleted(final int flags) {
        return (flags & (~FormatSpec.MASK_CHILDREN_ADDRESS_TYPE)) | FormatSpec.FLAG_IS_DELETED;
    }

    /**
     * Update a parent address in a PtNode that is referred to by ptNodeOriginAddress.
     *
     * @param dictUpdater the DictUpdater to write.
     * @param ptNodeOriginAddress the address of the PtNode.
     * @param newParentAddress the absolute address of the parent.
     * @param formatOptions file format options.
     */
    private static void updateParentAddress(final Ver3DictUpdater dictUpdater,
            final int ptNodeOriginAddress, final int newParentAddress,
            final FormatOptions formatOptions) {
        final DictBuffer dictBuffer = dictUpdater.getDictBuffer();
        final int originalPosition = dictBuffer.position();
        dictBuffer.position(ptNodeOriginAddress);
        if (!formatOptions.mSupportsDynamicUpdate) {
            throw new RuntimeException("this file format does not support parent addresses");
        }
        final int flags = dictBuffer.readUnsignedByte();
        if (BinaryDictIOUtils.isMovedPtNode(flags, formatOptions)) {
            // If the node is moved, the parent address is stored in the destination node.
            // We are guaranteed to process the destination node later, so there is no need to
            // update anything here.
            dictBuffer.position(originalPosition);
            return;
        }
        if (DBG) {
            MakedictLog.d("update parent address flags=" + flags + ", " + ptNodeOriginAddress);
        }
        final int parentOffset = newParentAddress - ptNodeOriginAddress;
        BinaryDictIOUtils.writeSInt24ToBuffer(dictBuffer, parentOffset);
        dictBuffer.position(originalPosition);
    }

    /**
     * Update parent addresses in a node array stored at ptNodeOriginAddress.
     *
     * @param dictUpdater the DictUpdater to be modified.
     * @param ptNodeOriginAddress the address of the node array to update.
     * @param newParentAddress the address to be written.
     * @param formatOptions file format options.
     */
    private static void updateParentAddresses(final Ver3DictUpdater dictUpdater,
            final int ptNodeOriginAddress, final int newParentAddress,
            final FormatOptions formatOptions) {
        final int originalPosition = dictUpdater.getPosition();
        dictUpdater.setPosition(ptNodeOriginAddress);
        do {
            final int count = dictUpdater.readPtNodeCount();
            for (int i = 0; i < count; ++i) {
                updateParentAddress(dictUpdater, dictUpdater.getPosition(), newParentAddress,
                        formatOptions);
                dictUpdater.skipPtNode(formatOptions);
            }
            if (!dictUpdater.readAndFollowForwardLink()) break;
            if (dictUpdater.getPosition() == FormatSpec.NO_FORWARD_LINK_ADDRESS) break;
        } while (formatOptions.mSupportsDynamicUpdate);
        dictUpdater.setPosition(originalPosition);
    }

    /**
     * Update a children address in a PtNode that is addressed by ptNodeOriginAddress.
     *
     * @param dictUpdater the DictUpdater to write.
     * @param ptNodeOriginAddress the address of the PtNode.
     * @param newChildrenAddress the absolute address of the child.
     * @param formatOptions file format options.
     */
    private static void updateChildrenAddress(final Ver3DictUpdater dictUpdater,
            final int ptNodeOriginAddress, final int newChildrenAddress,
            final FormatOptions formatOptions) {
        final DictBuffer dictBuffer = dictUpdater.getDictBuffer();
        final int originalPosition = dictBuffer.position();
        dictBuffer.position(ptNodeOriginAddress);
        final int flags = dictBuffer.readUnsignedByte();
        BinaryDictDecoderUtils.readParentAddress(dictBuffer, formatOptions);
        BinaryDictIOUtils.skipString(dictBuffer, (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) dictBuffer.readUnsignedByte();
        final int childrenOffset = newChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS
                ? FormatSpec.NO_CHILDREN_ADDRESS : newChildrenAddress - dictBuffer.position();
        BinaryDictIOUtils.writeSInt24ToBuffer(dictBuffer, childrenOffset);
        dictBuffer.position(originalPosition);
    }

    /**
     * Helper method to move a PtNode to the tail of the file.
     */
    private static int movePtNode(final OutputStream destination,
            final Ver3DictUpdater dictUpdater, final PtNodeInfo info,
            final int nodeArrayOriginAddress, final int oldNodeAddress,
            final FormatOptions formatOptions) throws IOException {
        final DictBuffer dictBuffer = dictUpdater.getDictBuffer();
        updateParentAddress(dictUpdater, oldNodeAddress, dictBuffer.limit() + 1, formatOptions);
        dictBuffer.position(oldNodeAddress);
        final int currentFlags = dictBuffer.readUnsignedByte();
        dictBuffer.position(oldNodeAddress);
        dictBuffer.put((byte)(FormatSpec.FLAG_IS_MOVED | (currentFlags
                & (~FormatSpec.MASK_MOVE_AND_DELETE_FLAG))));
        int size = FormatSpec.PTNODE_FLAGS_SIZE;
        updateForwardLink(dictUpdater, nodeArrayOriginAddress, dictBuffer.limit(), formatOptions);
        size += BinaryDictIOUtils.writeNodes(destination, new PtNodeInfo[] { info });
        return size;
    }

    @SuppressWarnings("unused")
    private static void updateForwardLink(final Ver3DictUpdater dictUpdater,
            final int nodeArrayOriginAddress, final int newNodeArrayAddress,
            final FormatOptions formatOptions) {
        final DictBuffer dictBuffer = dictUpdater.getDictBuffer();
        dictUpdater.setPosition(nodeArrayOriginAddress);
        int jumpCount = 0;
        while (jumpCount++ < MAX_JUMPS) {
            final int count = dictUpdater.readPtNodeCount();
            for (int i = 0; i < count; ++i) {
                dictUpdater.readPtNode(dictUpdater.getPosition(), formatOptions);
            }
            final int forwardLinkAddress = dictBuffer.readUnsignedInt24();
            if (forwardLinkAddress == FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                dictBuffer.position(dictBuffer.position() - FormatSpec.FORWARD_LINK_ADDRESS_SIZE);
                BinaryDictIOUtils.writeSInt24ToBuffer(dictBuffer, newNodeArrayAddress);
                return;
            }
            dictBuffer.position(forwardLinkAddress);
        }
        if (DBG && jumpCount >= MAX_JUMPS) {
            throw new RuntimeException("too many jumps, probably a bug.");
        }
    }

    /**
     * Move a PtNode that is referred to by oldPtNodeOrigin to the tail of the file, and set the
     * children address to the byte after the PtNode.
     *
     * @param fileEndAddress the address of the tail of the file.
     * @param codePoints the characters to put inside the PtNode.
     * @param length how many code points to read from codePoints.
     * @param flags the flags for this PtNode.
     * @param frequency the frequency of this terminal.
     * @param parentAddress the address of the parent PtNode of this PtNode.
     * @param shortcutTargets the shortcut targets for this PtNode.
     * @param bigrams the bigrams for this PtNode.
     * @param destination the stream representing the tail of the file.
     * @param dictUpdater the DictUpdater.
     * @param oldPtNodeArrayOrigin the origin of the old PtNode array this PtNode was a part of.
     * @param oldPtNodeOrigin the old origin where this PtNode used to be stored.
     * @param formatOptions format options for this dictionary.
     * @return the size written, in bytes.
     * @throws IOException if the file can't be accessed
     */
    private static int movePtNode(final int fileEndAddress, final int[] codePoints,
            final int length, final int flags, final int frequency, final int parentAddress,
            final ArrayList<WeightedString> shortcutTargets,
            final ArrayList<PendingAttribute> bigrams, final OutputStream destination,
            final Ver3DictUpdater dictUpdater, final int oldPtNodeArrayOrigin,
            final int oldPtNodeOrigin, final FormatOptions formatOptions) throws IOException {
        int size = 0;
        final int newPtNodeOrigin = fileEndAddress + 1;
        final int[] writtenCharacters = Arrays.copyOfRange(codePoints, 0, length);
        final PtNodeInfo tmpInfo = new PtNodeInfo(newPtNodeOrigin, -1 /* endAddress */,
                flags, writtenCharacters, frequency, parentAddress, FormatSpec.NO_CHILDREN_ADDRESS,
                shortcutTargets, bigrams);
        size = BinaryDictIOUtils.computePtNodeSize(tmpInfo, formatOptions);
        final PtNodeInfo newInfo = new PtNodeInfo(newPtNodeOrigin, newPtNodeOrigin + size,
                flags, writtenCharacters, frequency, parentAddress,
                fileEndAddress + 1 + size + FormatSpec.FORWARD_LINK_ADDRESS_SIZE, shortcutTargets,
                bigrams);
        movePtNode(destination, dictUpdater, newInfo, oldPtNodeArrayOrigin, oldPtNodeOrigin,
                formatOptions);
        return 1 + size + FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
    }

    /**
     * Insert a word into a binary dictionary.
     *
     * @param dictUpdater the dict updater.
     * @param destination a stream to the underlying file, with the pointer at the end of the file.
     * @param word the word to insert.
     * @param frequency the frequency of the new word.
     * @param bigramStrings bigram list, or null if none.
     * @param shortcuts shortcut list, or null if none.
     * @param isBlackListEntry whether this should be a blacklist entry.
     * @throws IOException if the file can't be accessed.
     * @throws UnsupportedFormatException if the existing dictionary is in an unexpected format.
     */
    // TODO: Support batch insertion.
    // TODO: Remove @UsedForTesting once UserHistoryDictionary is implemented by BinaryDictionary.
    @UsedForTesting
    public static void insertWord(final Ver3DictUpdater dictUpdater,
            final OutputStream destination, final String word, final int frequency,
            final ArrayList<WeightedString> bigramStrings,
            final ArrayList<WeightedString> shortcuts, final boolean isNotAWord,
            final boolean isBlackListEntry)
                    throws IOException, UnsupportedFormatException {
        final ArrayList<PendingAttribute> bigrams = new ArrayList<PendingAttribute>();
        final DictBuffer dictBuffer = dictUpdater.getDictBuffer();
        if (bigramStrings != null) {
            for (final WeightedString bigram : bigramStrings) {
                int position = dictUpdater.getTerminalPosition(bigram.mWord);
                if (position == FormatSpec.NOT_VALID_WORD) {
                    // TODO: figure out what is the correct thing to do here.
                } else {
                    bigrams.add(new PendingAttribute(bigram.mFrequency, position));
                }
            }
        }

        final boolean isTerminal = true;
        final boolean hasBigrams = !bigrams.isEmpty();
        final boolean hasShortcuts = shortcuts != null && !shortcuts.isEmpty();

        // find the insert position of the word.
        if (dictBuffer.position() != 0) dictBuffer.position(0);
        final FileHeader fileHeader = dictUpdater.readHeader();

        int wordPos = 0, address = dictBuffer.position(), nodeOriginAddress = dictBuffer.position();
        final int[] codePoints = FusionDictionary.getCodePoints(word);
        final int wordLen = codePoints.length;

        for (int depth = 0; depth < Constants.DICTIONARY_MAX_WORD_LENGTH; ++depth) {
            if (wordPos >= wordLen) break;
            nodeOriginAddress = dictBuffer.position();
            int nodeParentAddress = -1;
            final int ptNodeCount = BinaryDictDecoderUtils.readPtNodeCount(dictBuffer);
            boolean foundNextNode = false;

            for (int i = 0; i < ptNodeCount; ++i) {
                address = dictBuffer.position();
                final PtNodeInfo currentInfo = dictUpdater.readPtNode(address,
                        fileHeader.mFormatOptions);
                final boolean isMovedNode = BinaryDictIOUtils.isMovedPtNode(currentInfo.mFlags,
                        fileHeader.mFormatOptions);
                if (isMovedNode) continue;
                nodeParentAddress = (currentInfo.mParentAddress == FormatSpec.NO_PARENT_ADDRESS)
                        ? FormatSpec.NO_PARENT_ADDRESS : currentInfo.mParentAddress + address;
                boolean matched = true;
                for (int p = 0; p < currentInfo.mCharacters.length; ++p) {
                    if (wordPos + p >= wordLen) {
                        /*
                         * splitting
                         * before
                         *  abcd - ef
                         *
                         * insert "abc"
                         *
                         * after
                         *  abc - d - ef
                         */
                        final int newNodeAddress = dictBuffer.limit();
                        final int flags = BinaryDictEncoderUtils.makePtNodeFlags(p > 1,
                                isTerminal, 0, hasShortcuts, hasBigrams, false /* isNotAWord */,
                                false /* isBlackListEntry */, fileHeader.mFormatOptions);
                        int written = movePtNode(newNodeAddress, currentInfo.mCharacters, p, flags,
                                frequency, nodeParentAddress, shortcuts, bigrams, destination,
                                dictUpdater, nodeOriginAddress, address, fileHeader.mFormatOptions);

                        final int[] characters2 = Arrays.copyOfRange(currentInfo.mCharacters, p,
                                currentInfo.mCharacters.length);
                        if (currentInfo.mChildrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
                            updateParentAddresses(dictUpdater, currentInfo.mChildrenAddress,
                                    newNodeAddress + written + 1, fileHeader.mFormatOptions);
                        }
                        final PtNodeInfo newInfo2 = new PtNodeInfo(
                                newNodeAddress + written + 1, -1 /* endAddress */,
                                currentInfo.mFlags, characters2, currentInfo.mFrequency,
                                newNodeAddress + 1, currentInfo.mChildrenAddress,
                                currentInfo.mShortcutTargets, currentInfo.mBigrams);
                        BinaryDictIOUtils.writeNodes(destination, new PtNodeInfo[] { newInfo2 });
                        return;
                    } else if (codePoints[wordPos + p] != currentInfo.mCharacters[p]) {
                        if (p > 0) {
                            /*
                             * splitting
                             * before
                             *   ab - cd
                             *
                             * insert "ac"
                             *
                             * after
                             *   a - b - cd
                             *     |
                             *     - c
                             */

                            final int newNodeAddress = dictBuffer.limit();
                            final int childrenAddress = currentInfo.mChildrenAddress;

                            // move prefix
                            final int prefixFlags = BinaryDictEncoderUtils.makePtNodeFlags(p > 1,
                                    false /* isTerminal */, 0 /* childrenAddressSize*/,
                                    false /* hasShortcut */, false /* hasBigrams */,
                                    false /* isNotAWord */, false /* isBlackListEntry */,
                                    fileHeader.mFormatOptions);
                            int written = movePtNode(newNodeAddress, currentInfo.mCharacters, p,
                                    prefixFlags, -1 /* frequency */, nodeParentAddress, null, null,
                                    destination, dictUpdater, nodeOriginAddress, address,
                                    fileHeader.mFormatOptions);

                            final int[] suffixCharacters = Arrays.copyOfRange(
                                    currentInfo.mCharacters, p, currentInfo.mCharacters.length);
                            if (currentInfo.mChildrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
                                updateParentAddresses(dictUpdater, currentInfo.mChildrenAddress,
                                        newNodeAddress + written + 1, fileHeader.mFormatOptions);
                            }
                            final int suffixFlags = BinaryDictEncoderUtils.makePtNodeFlags(
                                    suffixCharacters.length > 1,
                                    (currentInfo.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0,
                                    0 /* childrenAddressSize */,
                                    (currentInfo.mFlags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS)
                                            != 0,
                                    (currentInfo.mFlags & FormatSpec.FLAG_HAS_BIGRAMS) != 0,
                                    isNotAWord, isBlackListEntry, fileHeader.mFormatOptions);
                            final PtNodeInfo suffixInfo = new PtNodeInfo(
                                    newNodeAddress + written + 1, -1 /* endAddress */, suffixFlags,
                                    suffixCharacters, currentInfo.mFrequency, newNodeAddress + 1,
                                    currentInfo.mChildrenAddress, currentInfo.mShortcutTargets,
                                    currentInfo.mBigrams);
                            written += BinaryDictIOUtils.computePtNodeSize(suffixInfo,
                                    fileHeader.mFormatOptions) + 1;

                            final int[] newCharacters = Arrays.copyOfRange(codePoints, wordPos + p,
                                    codePoints.length);
                            final int flags = BinaryDictEncoderUtils.makePtNodeFlags(
                                    newCharacters.length > 1, isTerminal,
                                    0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                    isNotAWord, isBlackListEntry, fileHeader.mFormatOptions);
                            final PtNodeInfo newInfo = new PtNodeInfo(
                                    newNodeAddress + written, -1 /* endAddress */, flags,
                                    newCharacters, frequency, newNodeAddress + 1,
                                    FormatSpec.NO_CHILDREN_ADDRESS, shortcuts, bigrams);
                            BinaryDictIOUtils.writeNodes(destination,
                                    new PtNodeInfo[] { suffixInfo, newInfo });
                            return;
                        }
                        matched = false;
                        break;
                    }
                }

                if (matched) {
                    if (wordPos + currentInfo.mCharacters.length == wordLen) {
                        // the word exists in the dictionary.
                        // only update the PtNode.
                        final int newNodeAddress = dictBuffer.limit();
                        final boolean hasMultipleChars = currentInfo.mCharacters.length > 1;
                        final int flags = BinaryDictEncoderUtils.makePtNodeFlags(hasMultipleChars,
                                isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                isNotAWord, isBlackListEntry, fileHeader.mFormatOptions);
                        final PtNodeInfo newInfo = new PtNodeInfo(newNodeAddress + 1,
                                -1 /* endAddress */, flags, currentInfo.mCharacters, frequency,
                                nodeParentAddress, currentInfo.mChildrenAddress, shortcuts,
                                bigrams);
                        movePtNode(destination, dictUpdater, newInfo, nodeOriginAddress, address,
                                fileHeader.mFormatOptions);
                        return;
                    }
                    wordPos += currentInfo.mCharacters.length;
                    if (currentInfo.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS) {
                        /*
                         * found the prefix of the word.
                         * make new PtNode and link to the PtNode from this PtNode.
                         *
                         * before
                         * ab - cd
                         *
                         * insert "abcde"
                         *
                         * after
                         * ab - cd - e
                         */
                        final int newNodeArrayAddress = dictBuffer.limit();
                        updateChildrenAddress(dictUpdater, address, newNodeArrayAddress,
                                fileHeader.mFormatOptions);
                        final int newNodeAddress = newNodeArrayAddress + 1;
                        final boolean hasMultipleChars = (wordLen - wordPos) > 1;
                        final int flags = BinaryDictEncoderUtils.makePtNodeFlags(hasMultipleChars,
                                isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                isNotAWord, isBlackListEntry, fileHeader.mFormatOptions);
                        final int[] characters = Arrays.copyOfRange(codePoints, wordPos, wordLen);
                        final PtNodeInfo newInfo = new PtNodeInfo(newNodeAddress, -1, flags,
                                characters, frequency, address, FormatSpec.NO_CHILDREN_ADDRESS,
                                shortcuts, bigrams);
                        BinaryDictIOUtils.writeNodes(destination, new PtNodeInfo[] { newInfo });
                        return;
                    }
                    dictBuffer.position(currentInfo.mChildrenAddress);
                    foundNextNode = true;
                    break;
                }
            }

            if (foundNextNode) continue;

            // reached the end of the array.
            final int linkAddressPosition = dictBuffer.position();
            int nextLink = dictBuffer.readUnsignedInt24();
            if ((nextLink & FormatSpec.MSB24) != 0) {
                nextLink = -(nextLink & FormatSpec.SINT24_MAX);
            }
            if (nextLink == FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                /*
                 * expand this node.
                 *
                 * before
                 * ab - cd
                 *
                 * insert "abef"
                 *
                 * after
                 * ab - cd
                 *    |
                 *    - ef
                 */

                // change the forward link address.
                final int newNodeAddress = dictBuffer.limit();
                dictBuffer.position(linkAddressPosition);
                BinaryDictIOUtils.writeSInt24ToBuffer(dictBuffer, newNodeAddress);

                final int[] characters = Arrays.copyOfRange(codePoints, wordPos, wordLen);
                final int flags = BinaryDictEncoderUtils.makePtNodeFlags(characters.length > 1,
                        isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                        isNotAWord, isBlackListEntry, fileHeader.mFormatOptions);
                final PtNodeInfo newInfo = new PtNodeInfo(newNodeAddress + 1,
                        -1 /* endAddress */, flags, characters, frequency, nodeParentAddress,
                        FormatSpec.NO_CHILDREN_ADDRESS, shortcuts, bigrams);
                BinaryDictIOUtils.writeNodes(destination, new PtNodeInfo[]{ newInfo });
                return;
            } else {
                depth--;
                dictBuffer.position(nextLink);
            }
        }
    }
}
