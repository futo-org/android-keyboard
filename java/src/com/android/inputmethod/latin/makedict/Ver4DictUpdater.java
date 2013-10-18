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
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * An implementation of DictUpdater for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictUpdater extends Ver4DictDecoder implements DictUpdater {
    private static final String TAG = Ver4DictUpdater.class.getSimpleName();

    private OutputStream mDictStream;
    private final File mFrequencyFile;

    @UsedForTesting
    public Ver4DictUpdater(final File dictDirectory, final int factoryType) {
        // DictUpdater must have an updatable DictBuffer.
        super(dictDirectory, ((factoryType & MASK_DICTBUFFER) == USE_BYTEARRAY)
                ? USE_BYTEARRAY : USE_WRITABLE_BYTEBUFFER);
        mFrequencyFile = getFile(FILETYPE_FREQUENCY);
    }

    @Override
    public void deleteWord(final String word) throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) openDictBuffer();
        readHeader();
        final int wordPos = getTerminalPosition(word);
        if (wordPos != FormatSpec.NOT_VALID_WORD) {
            mDictBuffer.position(wordPos);
            final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
            mDictBuffer.position(wordPos);
            mDictBuffer.put((byte) DynamicBinaryDictIOUtils.markAsDeleted(flags));
        }
    }

    private int getNewTerminalId() {
        // The size of frequency file is FormatSpec.FREQUENCY_AND_FLAGS_SIZE * number of terminals
        // because each terminal always has a frequency.
        // So we can get a fresh terminal id by this logic.
        // CAVEAT: we are reading the file size from the disk each time: beware of race conditions,
        // even on one thread.
        return (int) (mFrequencyFile.length() / FormatSpec.FREQUENCY_AND_FLAGS_SIZE);
    }

    private void updateParentPosIfNotMoved(final int nodePos, final int newParentPos,
            final FormatOptions formatOptions) {
        final int originalPos = getPosition();
        setPosition(nodePos);
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        if (!BinaryDictIOUtils.isMovedPtNode(flags, formatOptions)) {
            final int parentOffset = newParentPos - nodePos;
            BinaryDictIOUtils.writeSInt24ToBuffer(mDictBuffer, parentOffset);
        }
        setPosition(originalPos);
    }

    private void updateParentPositions(final int nodeArrayPos, final int newParentPos,
            final FormatOptions formatOptions) {
        final int originalPos = mDictBuffer.position();
        mDictBuffer.position(nodeArrayPos);
        int jumpCount = 0;
        do {
            final int count = readPtNodeCount();
            for (int i = 0; i < count; ++i) {
                updateParentPosIfNotMoved(getPosition(), newParentPos, formatOptions);
                skipPtNode(formatOptions);
            }
            if (!readAndFollowForwardLink()) break;
        } while (jumpCount++ < DynamicBinaryDictIOUtils.MAX_JUMPS);
        setPosition(originalPos);
    }

    private void updateChildrenPos(final int nodePos, final int newChildrenPos,
            final FormatOptions options) {
        final int originalPos = getPosition();
        setPosition(nodePos);
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        PtNodeReader.readParentAddress(mDictBuffer, options);
        BinaryDictIOUtils.skipString(mDictBuffer,
                (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) PtNodeReader.readTerminalId(mDictBuffer);
        final int basePos = getPosition();
        BinaryDictIOUtils.writeSInt24ToBuffer(mDictBuffer, newChildrenPos - basePos);
        setPosition(originalPos);
    }

    private void updateTerminalPosition(final int terminalId, final int position) {
        if (terminalId == PtNode.NOT_A_TERMINAL
                || terminalId * FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE
                        >= mTerminalAddressTableBuffer.limit()) return;
        mTerminalAddressTableBuffer.position(terminalId
                * FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE);
        BinaryDictEncoderUtils.writeUIntToDictBuffer(mTerminalAddressTableBuffer, position,
                FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE);
    }

    private void updateForwardLink(final int nodeArrayPos, final int newForwardLink,
            final FormatOptions formatOptions) {
        final int originalPos = getPosition();
        setPosition(nodeArrayPos);
        int jumpCount = 0;
        while (jumpCount++ < DynamicBinaryDictIOUtils.MAX_JUMPS) {
            final int ptNodeCount = readPtNodeCount();
            for (int i = 0; i < ptNodeCount; ++i) {
                skipPtNode(formatOptions);
            }
            final int forwardLinkPos = getPosition();
            if (!readAndFollowForwardLink()) {
                setPosition(forwardLinkPos);
                BinaryDictIOUtils.writeSInt24ToBuffer(mDictBuffer, newForwardLink - forwardLinkPos);
                break;
            }
        }
        setPosition(originalPos);
    }

    private void markPtNodeAsMoved(final int nodePos, final int newNodePos,
            final FormatOptions options) {
        final int originalPos = getPosition();
        updateParentPosIfNotMoved(nodePos, newNodePos, options);
        setPosition(nodePos);
        final int currentFlags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        setPosition(nodePos);
        mDictBuffer.put((byte) (FormatSpec.FLAG_IS_MOVED
                | (currentFlags & (~FormatSpec.MASK_MOVE_AND_DELETE_FLAG))));
        final int offset = newNodePos - nodePos;
        BinaryDictIOUtils.writeSInt24ToBuffer(mDictBuffer, offset);
        setPosition(originalPos);
    }

    /**
     * Writes a PtNode to an output stream from a Ver4PtNodeInfo.
     *
     * @param nodePos the position of the head of the PtNode.
     * @param info the PtNode info to be written.
     * @return the size written, in bytes.
     */
    private int writePtNode(final int nodePos, final Ver4PtNodeInfo info) throws IOException {
        int written = 0;

        // Write flags.
        mDictStream.write((byte) (info.mFlags & 0xFF));
        written += FormatSpec.PTNODE_FLAGS_SIZE;

        // Write the parent position.
        final int parentOffset = info.mParentPos == FormatSpec.NO_PARENT_ADDRESS ?
                FormatSpec.NO_PARENT_ADDRESS : info.mParentPos - nodePos;
        BinaryDictIOUtils.writeSInt24ToStream(mDictStream, parentOffset);
        written += FormatSpec.PARENT_ADDRESS_SIZE;

        // Write a string.
        if (((info.mFlags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0)
                != (info.mEndIndexOfCharacters - info.mStartIndexOfCharacters > 1)) {
            throw new RuntimeException("Inconsistent flags : hasMultipleChars = "
                    + ((info.mFlags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0) + ", length = "
                    + (info.mEndIndexOfCharacters - info.mStartIndexOfCharacters));
        }
        written += CharEncoding.writeCodePoints(mDictStream, info.mCharacters,
                info.mStartIndexOfCharacters, info.mEndIndexOfCharacters);

        // Write the terminal id.
        if ((info.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0) {
            BinaryDictEncoderUtils.writeUIntToStream(mDictStream, info.mTerminalId,
                    FormatSpec.PTNODE_TERMINAL_ID_SIZE);
            written += FormatSpec.PTNODE_TERMINAL_ID_SIZE;
        }

        // Write the children position.
        final int childrenOffset = info.mChildrenPos == FormatSpec.NO_CHILDREN_ADDRESS
                ? 0 : info.mChildrenPos - (nodePos + written);
        BinaryDictIOUtils.writeSInt24ToStream(mDictStream, childrenOffset);
        written += FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;

        return written;
    }

    /**
     * Helper method to split and move PtNode.
     *
     * @param ptNodeArrayPos the position of PtNodeArray which contains the split and moved PtNode.
     * @param splittedPtNodeToMovePos the position of the split and moved PtNode.
     * @param newParent the parent PtNode after splitting.
     * @param newChildren the children PtNodes after splitting.
     * @param newParentStartPos where to write the new parent.
     * @param formatOptions the format options.
     */
    private void writeSplittedPtNodes(final int ptNodeArrayPos, final int splittedPtNodeToMovePos,
            final Ver4PtNodeInfo newParent, final Ver4PtNodeInfo[] newChildren,
            final int newParentStartPos,
            final FormatOptions formatOptions) throws IOException {
        updateTerminalPosition(newParent.mTerminalId,
                newParentStartPos + 1 /* size of PtNodeCount */);
        int written = writePtNodeArray(newParentStartPos, new Ver4PtNodeInfo[] { newParent },
                FormatSpec.NO_FORWARD_LINK_ADDRESS);
        final int childrenStartPos = newParentStartPos + written;
        writePtNodeArray(childrenStartPos, newChildren, FormatSpec.NO_FORWARD_LINK_ADDRESS);
        int childrenNodePos = childrenStartPos + 1 /* size of PtNodeCount */;
        for (final Ver4PtNodeInfo info : newChildren) {
            updateTerminalPosition(info.mTerminalId, childrenNodePos);
            childrenNodePos += computePtNodeSize(info.mCharacters, info.mStartIndexOfCharacters,
                    info.mEndIndexOfCharacters,
                    (info.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0);
        }

        // Mark as moved.
        markPtNodeAsMoved(splittedPtNodeToMovePos, newParentStartPos + 1 /* size of PtNodeCount */,
                formatOptions);
        updateForwardLink(ptNodeArrayPos, newParentStartPos, formatOptions);
    }

    /**
     * Writes a node array to the stream.
     *
     * @param nodeArrayPos the position of the head of the node array.
     * @param infos an array of Ver4PtNodeInfo to be written.
     * @return the written length in bytes.
     */
    private int writePtNodeArray(final int nodeArrayPos, final Ver4PtNodeInfo[] infos,
            final int forwardLink) throws IOException {
        int written = BinaryDictIOUtils.writePtNodeCount(mDictStream, infos.length);
        for (int i = 0; i < infos.length; ++i) {
            written += writePtNode(nodeArrayPos + written, infos[i]);
        }
        BinaryDictIOUtils.writeSInt24ToStream(mDictStream, forwardLink);
        written += FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
        return written;
    }

    private int computePtNodeSize(final int[] codePoints, final int startIndex, final int endIndex,
            final boolean isTerminal) {
        return FormatSpec.PTNODE_FLAGS_SIZE + FormatSpec.PARENT_ADDRESS_SIZE
                + CharEncoding.getCharArraySize(codePoints, startIndex, endIndex)
                + (endIndex - startIndex > 1 ? FormatSpec.PTNODE_TERMINATOR_SIZE : 0)
                + (isTerminal ? FormatSpec.PTNODE_TERMINAL_ID_SIZE : 0)
                + FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;
    }

    private void writeNewSinglePtNodeWithAttributes(final int[] codePoints,
            final boolean hasShortcuts, final int terminalId, final boolean hasBigrams,
            final boolean isNotAWord, final boolean isBlackListEntry, final int parentPos,
            final FormatOptions formatOptions) throws IOException {
        final int newNodeArrayPos = mDictBuffer.limit();
        final int newNodeFlags = BinaryDictEncoderUtils.makePtNodeFlags(codePoints.length > 1,
                terminalId != PtNode.NOT_A_TERMINAL, FormatSpec.FLAG_IS_NOT_MOVED, hasShortcuts,
                hasBigrams, isNotAWord, isBlackListEntry, formatOptions);
        final Ver4PtNodeInfo info = new Ver4PtNodeInfo(newNodeFlags, codePoints, terminalId,
                FormatSpec.NO_CHILDREN_ADDRESS, parentPos, 0 /* nodeSize */);
        writePtNodeArray(newNodeArrayPos, new Ver4PtNodeInfo[] { info },
                FormatSpec.NO_FORWARD_LINK_ADDRESS);
    }

    private int setMultipleCharsInFlags(final int currentFlags, final boolean hasMultipleChars) {
        final int flags;
        if (hasMultipleChars) {
            flags = currentFlags | FormatSpec.FLAG_HAS_MULTIPLE_CHARS;
        } else {
            flags = currentFlags & (~FormatSpec.FLAG_HAS_MULTIPLE_CHARS);
        }
        return flags;
    }

    private int setIsNotAWordInFlags(final int currentFlags, final boolean isNotAWord) {
        final int flags;
        if (isNotAWord) {
            flags = currentFlags | FormatSpec.FLAG_IS_NOT_A_WORD;
        } else {
            flags = currentFlags & (~FormatSpec.FLAG_IS_NOT_A_WORD);
        }
        return flags;
    }

    private int setIsBlackListEntryInFlags(final int currentFlags, final boolean isBlackListEntry) {
        final int flags;
        if (isBlackListEntry) {
            flags = currentFlags | FormatSpec.FLAG_IS_BLACKLISTED;
        } else {
            flags = currentFlags & (~FormatSpec.FLAG_IS_BLACKLISTED);
        }
        return flags;
    }

    /**
     * Splits a PtNode.
     *
     *  abcd - ef
     *
     * -> inserting "abc"
     *
     *  abc - d - ef
     *
     * @param nodeArrayToSplitPos the position of PtNodeArray which contains the PtNode to split.
     * @param nodeToSplitPos the position of the PtNode to split.
     * @param nodeToSplitInfo the information of the PtNode to split.
     * @param indexToSplit the index where to split in the code points array.
     * @param parentOfNodeToSplitPos the absolute position of a parent of the node to split.
     * @param newTerminalId the terminal id of the inserted node (corresponds to "d").
     * @param hasShortcuts whether the inserted word should have shortcuts.
     * @param hasBigrams whether the inserted word should have bigrams.
     * @param isNotAWord whether the inserted word should be not a word.
     * @param isBlackListEntry whether the inserted word should be a black list entry.
     * @param formatOptions the format options.
     */
    private void splitOnly(final int nodeArrayToSplitPos, final int nodeToSplitPos,
            final Ver4PtNodeInfo nodeToSplitInfo, final int indexToSplit,
            final int parentOfNodeToSplitPos, final int newTerminalId, final boolean hasShortcuts,
            final boolean hasBigrams, final boolean isNotAWord, final boolean isBlackListEntry,
            final FormatOptions formatOptions) throws IOException {
        final int parentNodeArrayStartPos = mDictBuffer.limit();
        final int parentNodeStartPos = parentNodeArrayStartPos + 1 /* size of PtNodeCount */;
        final int parentFlags = BinaryDictEncoderUtils.makePtNodeFlags(indexToSplit > 1,
                true /* isTerminal */, FormatSpec.FLAG_IS_NOT_MOVED, hasShortcuts, hasBigrams,
                isNotAWord, isBlackListEntry, formatOptions);
        final Ver4PtNodeInfo parentInfo = new Ver4PtNodeInfo(parentFlags,
                nodeToSplitInfo.mCharacters, newTerminalId, parentNodeStartPos
                        + computePtNodeSize(nodeToSplitInfo.mCharacters, 0, indexToSplit, true)
                        + FormatSpec.FORWARD_LINK_ADDRESS_SIZE,
                parentOfNodeToSplitPos, 0 /* nodeSize */);
        parentInfo.mStartIndexOfCharacters = 0;
        parentInfo.mEndIndexOfCharacters = indexToSplit;

        // Write the child.
        final int childrenFlags = setMultipleCharsInFlags(nodeToSplitInfo.mFlags,
                nodeToSplitInfo.mCharacters.length - indexToSplit > 1);
        final Ver4PtNodeInfo childrenInfo = new Ver4PtNodeInfo(childrenFlags,
                nodeToSplitInfo.mCharacters, nodeToSplitInfo.mTerminalId,
                nodeToSplitInfo.mChildrenPos, parentNodeStartPos, 0 /* nodeSize */);
        childrenInfo.mStartIndexOfCharacters = indexToSplit;
        childrenInfo.mEndIndexOfCharacters = nodeToSplitInfo.mCharacters.length;
        if (nodeToSplitInfo.mChildrenPos != FormatSpec.NO_CHILDREN_ADDRESS) {
            updateParentPositions(nodeToSplitInfo.mChildrenPos,
                    parentInfo.mChildrenPos + 1 /* size of PtNodeCount */, formatOptions);
        }

        writeSplittedPtNodes(nodeArrayToSplitPos, nodeToSplitPos, parentInfo,
                new Ver4PtNodeInfo[] { childrenInfo }, parentNodeArrayStartPos, formatOptions);
    }

    /**
     * Split and branch a PtNode.
     *
     *   ab - cd
     *
     * -> inserting "ac"
     *
     * a - b - cd
     *   |
     *   - c
     *
     * @param nodeArrayToSplitPos the position of PtNodeArray which contains the PtNode to split.
     * @param nodeToSplitPos the position of the PtNode to split.
     * @param nodeToSplitInfo the information of the PtNode to split.
     * @param indexToSplit the index where to split in the code points array.
     * @param parentOfNodeToSplitPos the absolute position of parent of the node to split.
     * @param newWordSuffixCodePoints the suffix of the newly inserted word (corresponds to "c").
     * @param startIndexOfNewWordSuffixCodePoints the start index in newWordSuffixCodePoints where
     * the suffix starts.
     * @param newTerminalId the terminal id of the inserted node (correspond to "c").
     * @param hasShortcuts whether the inserted word should have shortcuts.
     * @param hasBigrams whether the inserted word should have bigrams.
     * @param isNotAWord whether the inserted word should be not a word.
     * @param isBlackListEntry whether the inserted word should be a black list entry.
     * @param formatOptions the format options.
     */
    private void splitAndBranch(final int nodeArrayToSplitPos, final int nodeToSplitPos,
            final Ver4PtNodeInfo nodeToSplitInfo, final int indexToSplit,
            final int parentOfNodeToSplitPos, final int[] newWordSuffixCodePoints,
            final int startIndexOfNewWordSuffixCodePoints,
            final int newTerminalId,
            final boolean hasShortcuts, final boolean hasBigrams, final boolean isNotAWord,
            final boolean isBlackListEntry, final FormatOptions formatOptions) throws IOException {
        final int parentNodeArrayStartPos = mDictBuffer.limit();
        final int parentNodeStartPos = parentNodeArrayStartPos + 1 /* size of PtNodeCount */;
        final int parentFlags = BinaryDictEncoderUtils.makePtNodeFlags(
                indexToSplit > 1,
                false /* isTerminal */, FormatSpec.FLAG_IS_NOT_MOVED,
                false /* hasShortcut */, false /* hasBigrams */,
                false /* isNotAWord */, false /* isBlackListEntry */, formatOptions);
        final Ver4PtNodeInfo parentInfo = new Ver4PtNodeInfo(parentFlags,
                nodeToSplitInfo.mCharacters, PtNode.NOT_A_TERMINAL,
                parentNodeStartPos
                        + computePtNodeSize(nodeToSplitInfo.mCharacters, 0, indexToSplit, false)
                        + FormatSpec.FORWARD_LINK_ADDRESS_SIZE,
                parentOfNodeToSplitPos, 0 /* nodeSize */);
        parentInfo.mStartIndexOfCharacters = 0;
        parentInfo.mEndIndexOfCharacters = indexToSplit;

        final int childrenNodeArrayStartPos = parentNodeStartPos
                + computePtNodeSize(nodeToSplitInfo.mCharacters, 0, indexToSplit, false)
                + FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
        final int firstChildrenFlags = BinaryDictEncoderUtils.makePtNodeFlags(
                newWordSuffixCodePoints.length - startIndexOfNewWordSuffixCodePoints > 1,
                true /* isTerminal */, FormatSpec.FLAG_IS_NOT_MOVED, hasShortcuts, hasBigrams,
                isNotAWord, isBlackListEntry, formatOptions);
        final Ver4PtNodeInfo firstChildrenInfo = new Ver4PtNodeInfo(firstChildrenFlags,
                newWordSuffixCodePoints, newTerminalId,
                FormatSpec.NO_CHILDREN_ADDRESS, parentNodeStartPos,
                0 /* nodeSize */);
        firstChildrenInfo.mStartIndexOfCharacters = startIndexOfNewWordSuffixCodePoints;
        firstChildrenInfo.mEndIndexOfCharacters = newWordSuffixCodePoints.length;

        final int secondChildrenStartPos = childrenNodeArrayStartPos + 1 /* size of ptNodeCount */
                + computePtNodeSize(newWordSuffixCodePoints, startIndexOfNewWordSuffixCodePoints,
                        newWordSuffixCodePoints.length, true /* isTerminal */);
        final int secondChildrenFlags = setMultipleCharsInFlags(nodeToSplitInfo.mFlags,
                nodeToSplitInfo.mCharacters.length - indexToSplit > 1);
        final Ver4PtNodeInfo secondChildrenInfo = new Ver4PtNodeInfo(secondChildrenFlags,
                nodeToSplitInfo.mCharacters, nodeToSplitInfo.mTerminalId,
                nodeToSplitInfo.mChildrenPos, parentNodeStartPos, 0 /* nodeSize */);
        secondChildrenInfo.mStartIndexOfCharacters = indexToSplit;
        secondChildrenInfo.mEndIndexOfCharacters = nodeToSplitInfo.mCharacters.length;
        if (nodeToSplitInfo.mChildrenPos != FormatSpec.NO_CHILDREN_ADDRESS) {
            updateParentPositions(nodeToSplitInfo.mChildrenPos, secondChildrenStartPos,
                    formatOptions);
        }

        writeSplittedPtNodes(nodeArrayToSplitPos, nodeToSplitPos, parentInfo,
                new Ver4PtNodeInfo[] { firstChildrenInfo, secondChildrenInfo },
                parentNodeArrayStartPos, formatOptions);
    }

    /**
     * Inserts a word into the trie file and returns the position of inserted terminal node.
     * If the insertion is failed, returns FormatSpec.NOT_VALID_WORD.
     */
    @UsedForTesting
    private int insertWordToTrie(final String word, final int newTerminalId,
            final boolean isNotAWord, final boolean isBlackListEntry, final boolean hasBigrams,
            final boolean hasShortcuts) throws IOException, UnsupportedFormatException {
        setPosition(0);
        final FileHeader header = readHeader();

        final int[] codePoints = FusionDictionary.getCodePoints(word);
        final int wordLen = codePoints.length;

        int wordPos = 0;
        for (int depth = 0; depth < FormatSpec.MAX_WORD_LENGTH; /* nop */) {
            final int nodeArrayPos = getPosition();
            final int ptNodeCount = readPtNodeCount();
            boolean goToChildren = false;
            int parentPos = FormatSpec.NO_PARENT_ADDRESS;
            for (int i = 0; i < ptNodeCount; ++i) {
                final int nodePos = getPosition();
                final Ver4PtNodeInfo nodeInfo = readVer4PtNodeInfo(nodePos, header.mFormatOptions);
                if (BinaryDictIOUtils.isMovedPtNode(nodeInfo.mFlags, header.mFormatOptions)) {
                    continue;
                }
                if (nodeInfo.mParentPos != FormatSpec.NO_PARENT_ADDRESS) {
                    parentPos = nodePos + nodeInfo.mParentPos;
                }

                final boolean firstCharacterMatched =
                        codePoints[wordPos] == nodeInfo.mCharacters[0];
                boolean allCharactersMatched = true;
                int firstDifferentCharacterIndex = -1;
                for (int p = 0; p < nodeInfo.mCharacters.length; ++p) {
                    if (wordPos + p >= codePoints.length) break;
                    if (codePoints[wordPos + p] != nodeInfo.mCharacters[p]) {
                        if (firstDifferentCharacterIndex == -1) {
                            firstDifferentCharacterIndex = p;
                        }
                        allCharactersMatched = false;
                    }
                }

                if (!firstCharacterMatched) {
                    // Go to the next sibling node.
                    continue;
                }

                if (!allCharactersMatched) {
                    final int parentNodeArrayStartPos = mDictBuffer.limit();
                    splitAndBranch(nodeArrayPos, nodePos, nodeInfo, firstDifferentCharacterIndex,
                            parentPos, codePoints, wordPos + firstDifferentCharacterIndex,
                            newTerminalId, hasShortcuts, hasBigrams, isNotAWord,
                            isBlackListEntry, header.mFormatOptions);

                    return parentNodeArrayStartPos + computePtNodeSize(codePoints, wordPos,
                            wordPos + firstDifferentCharacterIndex, false)
                            + FormatSpec.FORWARD_LINK_ADDRESS_SIZE + 1 /* size of PtNodeCount */;
                }

                if (wordLen - wordPos < nodeInfo.mCharacters.length) {
                    final int parentNodeArrayStartPos = mDictBuffer.limit();
                    splitOnly(nodeArrayPos, nodePos, nodeInfo, wordLen - wordPos, parentPos,
                            newTerminalId, hasShortcuts, hasBigrams, isNotAWord, isBlackListEntry,
                            header.mFormatOptions);

                    // Return the position of the inserted word.
                    return parentNodeArrayStartPos + 1 /* size of PtNodeCount */;
                }

                wordPos += nodeInfo.mCharacters.length;
                if (wordPos == wordLen) {
                    // This dictionary already contains the word.
                    Log.e(TAG, "Something went wrong. If the word is already contained, "
                            + " there is no need to insert new PtNode.");
                    return FormatSpec.NOT_VALID_WORD;
                }
                if (nodeInfo.mChildrenPos == FormatSpec.NO_CHILDREN_ADDRESS) {
                    // There are no children.
                    // We need to add a new node as a child of this node.
                    final int newNodeArrayPos = mDictBuffer.limit();
                    final int[] newNodeCodePoints = Arrays.copyOfRange(codePoints, wordPos,
                            codePoints.length);
                    writeNewSinglePtNodeWithAttributes(newNodeCodePoints, hasShortcuts,
                            newTerminalId, hasBigrams, isNotAWord, isBlackListEntry, nodePos,
                            header.mFormatOptions);
                    updateChildrenPos(nodePos, newNodeArrayPos, header.mFormatOptions);
                    return newNodeArrayPos + 1 /* size of PtNodeCount */;
                } else {
                    // Found the matched node.
                    // Go to the children of this node.
                    setPosition(nodeInfo.mChildrenPos);
                    goToChildren = true;
                    depth++;
                    break;
                }
            }

            if (goToChildren) continue;
            if (!readAndFollowForwardLink()) {
                // Add a new node that contains [wordPos, word.length()-1].
                // and update the forward link.
                final int newNodeArrayPos = mDictBuffer.limit();
                final int[] newCodePoints = Arrays.copyOfRange(codePoints, wordPos,
                        codePoints.length);
                writeNewSinglePtNodeWithAttributes(newCodePoints, hasShortcuts, newTerminalId,
                        hasBigrams, isNotAWord, isBlackListEntry, parentPos, header.mFormatOptions);
                updateForwardLink(nodeArrayPos, newNodeArrayPos, header.mFormatOptions);
                return newNodeArrayPos + 1 /* size of PtNodeCount */;
            }
        }
        return FormatSpec.NOT_VALID_WORD;
    }

    private void updateFrequency(final int terminalId, final int frequency) {
        mFrequencyBuffer.position(terminalId * FormatSpec.FREQUENCY_AND_FLAGS_SIZE);
        BinaryDictEncoderUtils.writeUIntToDictBuffer(mFrequencyBuffer, frequency,
                FormatSpec.FREQUENCY_AND_FLAGS_SIZE);
    }

    private void insertFrequency(final int frequency) throws IOException {
        final OutputStream frequencyStream = new FileOutputStream(mFrequencyFile,
                true /* append */);
        BinaryDictEncoderUtils.writeUIntToStream(frequencyStream, frequency,
                FormatSpec.FREQUENCY_AND_FLAGS_SIZE);
    }

    private void insertTerminalPosition(final int posOfTerminal) throws IOException {
        final OutputStream terminalPosStream = new FileOutputStream(
                getFile(FILETYPE_TERMINAL_ADDRESS_TABLE), true /* append */);
        BinaryDictEncoderUtils.writeUIntToStream(terminalPosStream, posOfTerminal,
                FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE);
    }

    private void insertBigrams(final int terminalId, final ArrayList<PendingAttribute> bigrams) {
        // TODO: Implement.
    }

    private void insertShortcuts(final int terminalId, final ArrayList<WeightedString> shortcuts) {
        // TODO: Implement.
    }

    private void openBuffersAndStream() throws IOException {
        openDictBuffer();
        mDictStream = new FileOutputStream(getFile(FILETYPE_TRIE), true /* append */);
    }

    private void close() throws IOException {
        mDictStream.close();
        mDictBuffer = null;
        mFrequencyBuffer = null;
        mTerminalAddressTableBuffer = null;
    }

    private void updateAttributes(final int posOfWord, final int frequency,
            final ArrayList<WeightedString> bigramStrings,
            final ArrayList<WeightedString> shortcuts, final boolean isNotAWord,
            final boolean isBlackListEntry) throws IOException, UnsupportedFormatException {
        mDictBuffer.position(0);
        final FileHeader header = readHeader();
        mDictBuffer.position(posOfWord);
        final Ver4PtNodeInfo info = readVer4PtNodeInfo(posOfWord, header.mFormatOptions);
        final int terminalId = info.mTerminalId;

        // Update the flags.
        final int newFlags = setIsNotAWordInFlags(
                setIsBlackListEntryInFlags(info.mFlags, isBlackListEntry), isNotAWord);
        mDictBuffer.position(posOfWord);
        mDictBuffer.put((byte) newFlags);

        updateFrequency(terminalId, frequency);
        insertBigrams(terminalId,
                DynamicBinaryDictIOUtils.resolveBigramPositions(this, bigramStrings));
        insertShortcuts(terminalId, shortcuts);
    }

    @Override @UsedForTesting
    public void insertWord(final String word, final int frequency,
        final ArrayList<WeightedString> bigramStrings, final ArrayList<WeightedString> shortcuts,
        final boolean isNotAWord, final boolean isBlackListEntry)
                throws IOException, UnsupportedFormatException {
        final int newTerminalId = getNewTerminalId();

        openBuffersAndStream();
        final int posOfWord = getTerminalPosition(word);
        if (posOfWord != FormatSpec.NOT_VALID_WORD) {
            // The word is already contained in the dictionary.
            updateAttributes(posOfWord, frequency, bigramStrings, shortcuts, isNotAWord,
                    isBlackListEntry);
            close();
            return;
        }

        // Insert new PtNode into trie.
        final int posOfTerminal = insertWordToTrie(word, newTerminalId, isNotAWord,
                isBlackListEntry, bigramStrings != null && !bigramStrings.isEmpty(),
                shortcuts != null && !shortcuts.isEmpty());
        insertFrequency(frequency);
        insertTerminalPosition(posOfTerminal);
        close();

        insertBigrams(newTerminalId,
                DynamicBinaryDictIOUtils.resolveBigramPositions(this, bigramStrings));
        insertShortcuts(newTerminalId, shortcuts);
    }
}
