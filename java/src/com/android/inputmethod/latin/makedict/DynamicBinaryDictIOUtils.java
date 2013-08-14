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
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.FusionDictionaryBufferInterface;
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
    @UsedForTesting
    public static void deleteWord(final FusionDictionaryBufferInterface buffer,
            final String word) throws IOException, UnsupportedFormatException {
        buffer.position(0);
        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        final int wordPosition = BinaryDictIOUtils.getTerminalPosition(buffer, word);
        if (wordPosition == FormatSpec.NOT_VALID_WORD) return;

        buffer.position(wordPosition);
        final int flags = buffer.readUnsignedByte();
        buffer.position(wordPosition);
        buffer.put((byte)markAsDeleted(flags));
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
        if (BinaryDictIOUtils.isMovedGroup(flags, formatOptions)) {
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
        BinaryDictIOUtils.writeSInt24ToBuffer(buffer, parentOffset);
        buffer.position(originalPosition);
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
                BinaryDictIOUtils.skipCharGroup(buffer, formatOptions);
            }
            final int forwardLinkAddress = buffer.readUnsignedInt24();
            buffer.position(forwardLinkAddress);
        } while (formatOptions.mSupportsDynamicUpdate
                && buffer.position() != FormatSpec.NO_FORWARD_LINK_ADDRESS);
        buffer.position(originalPosition);
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
        BinaryDictIOUtils.skipString(buffer, (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) buffer.readUnsignedByte();
        final int childrenOffset = newChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS
                ? FormatSpec.NO_CHILDREN_ADDRESS : newChildrenAddress - buffer.position();
        BinaryDictIOUtils.writeSInt24ToBuffer(buffer, childrenOffset);
        buffer.position(originalPosition);
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
        size += BinaryDictIOUtils.writeNode(destination, new CharGroupInfo[] { info });
        return size;
    }
    @SuppressWarnings("unused")
    private static void updateForwardLink(final FusionDictionaryBufferInterface buffer,
            final int nodeOriginAddress, final int newNodeAddress,
            final FormatOptions formatOptions) {
        buffer.position(nodeOriginAddress);
        int jumpCount = 0;
        while (jumpCount++ < MAX_JUMPS) {
            final int count = BinaryDictInputOutput.readCharGroupCount(buffer);
            for (int i = 0; i < count; ++i) BinaryDictIOUtils.skipCharGroup(buffer, formatOptions);
            final int forwardLinkAddress = buffer.readUnsignedInt24();
            if (forwardLinkAddress == FormatSpec.NO_FORWARD_LINK_ADDRESS) {
                buffer.position(buffer.position() - FormatSpec.FORWARD_LINK_ADDRESS_SIZE);
                BinaryDictIOUtils.writeSInt24ToBuffer(buffer, newNodeAddress);
                return;
            }
            buffer.position(forwardLinkAddress);
        }
        if (DBG && jumpCount >= MAX_JUMPS) {
            throw new RuntimeException("too many jumps, probably a bug.");
        }
    }

    /**
     * Move a group that is referred to by oldGroupOrigin to the tail of the file.
     * And set the children address to the byte after the group.
     *
     * @param nodeOrigin the address of the tail of the file.
     * @param characters
     * @param length
     * @param flags
     * @param frequency
     * @param parentAddress
     * @param shortcutTargets
     * @param bigrams
     * @param destination the stream representing the tail of the file.
     * @param buffer the buffer representing the (constant-size) body of the file.
     * @param oldNodeOrigin
     * @param oldGroupOrigin
     * @param formatOptions
     * @return the size written, in bytes.
     * @throws IOException
     */
    private static int moveGroup(final int nodeOrigin, final int[] characters, final int length,
            final int flags, final int frequency, final int parentAddress,
            final ArrayList<WeightedString> shortcutTargets,
            final ArrayList<PendingAttribute> bigrams, final OutputStream destination,
            final FusionDictionaryBufferInterface buffer, final int oldNodeOrigin,
            final int oldGroupOrigin, final FormatOptions formatOptions) throws IOException {
        int size = 0;
        final int newGroupOrigin = nodeOrigin + 1;
        final int[] writtenCharacters = Arrays.copyOfRange(characters, 0, length);
        final CharGroupInfo tmpInfo = new CharGroupInfo(newGroupOrigin, -1 /* endAddress */,
                flags, writtenCharacters, frequency, parentAddress, FormatSpec.NO_CHILDREN_ADDRESS,
                shortcutTargets, bigrams);
        size = BinaryDictIOUtils.computeGroupSize(tmpInfo, formatOptions);
        final CharGroupInfo newInfo = new CharGroupInfo(newGroupOrigin, newGroupOrigin + size,
                flags, writtenCharacters, frequency, parentAddress,
                nodeOrigin + 1 + size + FormatSpec.FORWARD_LINK_ADDRESS_SIZE, shortcutTargets,
                bigrams);
        moveCharGroup(destination, buffer, newInfo, oldNodeOrigin, oldGroupOrigin, formatOptions);
        return 1 + size + FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
    }

    /**
     * Insert a word into a binary dictionary.
     *
     * @param buffer
     * @param destination
     * @param word
     * @param frequency
     * @param bigramStrings
     * @param shortcuts
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    // TODO: Support batch insertion.
    // TODO: Remove @UsedForTesting once UserHistoryDictionary is implemented by BinaryDictionary.
    @UsedForTesting
    public static void insertWord(final FusionDictionaryBufferInterface buffer,
            final OutputStream destination, final String word, final int frequency,
            final ArrayList<WeightedString> bigramStrings,
            final ArrayList<WeightedString> shortcuts, final boolean isNotAWord,
            final boolean isBlackListEntry)
                    throws IOException, UnsupportedFormatException {
        final ArrayList<PendingAttribute> bigrams = new ArrayList<PendingAttribute>();
        if (bigramStrings != null) {
            for (final WeightedString bigram : bigramStrings) {
                int position = BinaryDictIOUtils.getTerminalPosition(buffer, bigram.mWord);
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
        if (buffer.position() != 0) buffer.position(0);
        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);

        int wordPos = 0, address = buffer.position(), nodeOriginAddress = buffer.position();
        final int[] codePoints = FusionDictionary.getCodePoints(word);
        final int wordLen = codePoints.length;

        for (int depth = 0; depth < Constants.DICTIONARY_MAX_WORD_LENGTH; ++depth) {
            if (wordPos >= wordLen) break;
            nodeOriginAddress = buffer.position();
            int nodeParentAddress = -1;
            final int charGroupCount = BinaryDictInputOutput.readCharGroupCount(buffer);
            boolean foundNextGroup = false;

            for (int i = 0; i < charGroupCount; ++i) {
                address = buffer.position();
                final CharGroupInfo currentInfo = BinaryDictInputOutput.readCharGroup(buffer,
                        buffer.position(), header.mFormatOptions);
                final boolean isMovedGroup = BinaryDictIOUtils.isMovedGroup(currentInfo.mFlags,
                        header.mFormatOptions);
                if (isMovedGroup) continue;
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
                        final int newNodeAddress = buffer.limit();
                        final int flags = BinaryDictInputOutput.makeCharGroupFlags(p > 1,
                                isTerminal, 0, hasShortcuts, hasBigrams, false /* isNotAWord */,
                                false /* isBlackListEntry */, header.mFormatOptions);
                        int written = moveGroup(newNodeAddress, currentInfo.mCharacters, p, flags,
                                frequency, nodeParentAddress, shortcuts, bigrams, destination,
                                buffer, nodeOriginAddress, address, header.mFormatOptions);

                        final int[] characters2 = Arrays.copyOfRange(currentInfo.mCharacters, p,
                                currentInfo.mCharacters.length);
                        if (currentInfo.mChildrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
                            updateParentAddresses(buffer, currentInfo.mChildrenAddress,
                                    newNodeAddress + written + 1, header.mFormatOptions);
                        }
                        final CharGroupInfo newInfo2 = new CharGroupInfo(
                                newNodeAddress + written + 1, -1 /* endAddress */,
                                currentInfo.mFlags, characters2, currentInfo.mFrequency,
                                newNodeAddress + 1, currentInfo.mChildrenAddress,
                                currentInfo.mShortcutTargets, currentInfo.mBigrams);
                        BinaryDictIOUtils.writeNode(destination, new CharGroupInfo[] { newInfo2 });
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

                            final int newNodeAddress = buffer.limit();
                            final int childrenAddress = currentInfo.mChildrenAddress;

                            // move prefix
                            final int prefixFlags = BinaryDictInputOutput.makeCharGroupFlags(p > 1,
                                    false /* isTerminal */, 0 /* childrenAddressSize*/,
                                    false /* hasShortcut */, false /* hasBigrams */,
                                    false /* isNotAWord */, false /* isBlackListEntry */,
                                    header.mFormatOptions);
                            int written = moveGroup(newNodeAddress, currentInfo.mCharacters, p,
                                    prefixFlags, -1 /* frequency */, nodeParentAddress, null, null,
                                    destination, buffer, nodeOriginAddress, address,
                                    header.mFormatOptions);

                            final int[] suffixCharacters = Arrays.copyOfRange(
                                    currentInfo.mCharacters, p, currentInfo.mCharacters.length);
                            if (currentInfo.mChildrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
                                updateParentAddresses(buffer, currentInfo.mChildrenAddress,
                                        newNodeAddress + written + 1, header.mFormatOptions);
                            }
                            final int suffixFlags = BinaryDictInputOutput.makeCharGroupFlags(
                                    suffixCharacters.length > 1,
                                    (currentInfo.mFlags & FormatSpec.FLAG_IS_TERMINAL) != 0,
                                    0 /* childrenAddressSize */,
                                    (currentInfo.mFlags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS)
                                            != 0,
                                    (currentInfo.mFlags & FormatSpec.FLAG_HAS_BIGRAMS) != 0,
                                    isNotAWord, isBlackListEntry, header.mFormatOptions);
                            final CharGroupInfo suffixInfo = new CharGroupInfo(
                                    newNodeAddress + written + 1, -1 /* endAddress */, suffixFlags,
                                    suffixCharacters, currentInfo.mFrequency, newNodeAddress + 1,
                                    currentInfo.mChildrenAddress, currentInfo.mShortcutTargets,
                                    currentInfo.mBigrams);
                            written += BinaryDictIOUtils.computeGroupSize(suffixInfo,
                                    header.mFormatOptions) + 1;

                            final int[] newCharacters = Arrays.copyOfRange(codePoints, wordPos + p,
                                    codePoints.length);
                            final int flags = BinaryDictInputOutput.makeCharGroupFlags(
                                    newCharacters.length > 1, isTerminal,
                                    0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                    isNotAWord, isBlackListEntry, header.mFormatOptions);
                            final CharGroupInfo newInfo = new CharGroupInfo(
                                    newNodeAddress + written, -1 /* endAddress */, flags,
                                    newCharacters, frequency, newNodeAddress + 1,
                                    FormatSpec.NO_CHILDREN_ADDRESS, shortcuts, bigrams);
                            BinaryDictIOUtils.writeNode(destination,
                                    new CharGroupInfo[] { suffixInfo, newInfo });
                            return;
                        }
                        matched = false;
                        break;
                    }
                }

                if (matched) {
                    if (wordPos + currentInfo.mCharacters.length == wordLen) {
                        // the word exists in the dictionary.
                        // only update group.
                        final int newNodeAddress = buffer.limit();
                        final boolean hasMultipleChars = currentInfo.mCharacters.length > 1;
                        final int flags = BinaryDictInputOutput.makeCharGroupFlags(hasMultipleChars,
                                isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                isNotAWord, isBlackListEntry, header.mFormatOptions);
                        final CharGroupInfo newInfo = new CharGroupInfo(newNodeAddress + 1,
                                -1 /* endAddress */, flags, currentInfo.mCharacters, frequency,
                                nodeParentAddress, currentInfo.mChildrenAddress, shortcuts,
                                bigrams);
                        moveCharGroup(destination, buffer, newInfo, nodeOriginAddress, address,
                                header.mFormatOptions);
                        return;
                    }
                    wordPos += currentInfo.mCharacters.length;
                    if (currentInfo.mChildrenAddress == FormatSpec.NO_CHILDREN_ADDRESS) {
                        /*
                         * found the prefix of the word.
                         * make new node and link to the node from this group.
                         *
                         * before
                         * ab - cd
                         *
                         * insert "abcde"
                         *
                         * after
                         * ab - cd - e
                         */
                        final int newNodeAddress = buffer.limit();
                        updateChildrenAddress(buffer, address, newNodeAddress,
                                header.mFormatOptions);
                        final int newGroupAddress = newNodeAddress + 1;
                        final boolean hasMultipleChars = (wordLen - wordPos) > 1;
                        final int flags = BinaryDictInputOutput.makeCharGroupFlags(hasMultipleChars,
                                isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                                isNotAWord, isBlackListEntry, header.mFormatOptions);
                        final int[] characters = Arrays.copyOfRange(codePoints, wordPos, wordLen);
                        final CharGroupInfo newInfo = new CharGroupInfo(newGroupAddress, -1, flags,
                                characters, frequency, address, FormatSpec.NO_CHILDREN_ADDRESS,
                                shortcuts, bigrams);
                        BinaryDictIOUtils.writeNode(destination, new CharGroupInfo[] { newInfo });
                        return;
                    }
                    buffer.position(currentInfo.mChildrenAddress);
                    foundNextGroup = true;
                    break;
                }
            }

            if (foundNextGroup) continue;

            // reached the end of the array.
            final int linkAddressPosition = buffer.position();
            int nextLink = buffer.readUnsignedInt24();
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
                final int newNodeAddress = buffer.limit();
                buffer.position(linkAddressPosition);
                BinaryDictIOUtils.writeSInt24ToBuffer(buffer, newNodeAddress);

                final int[] characters = Arrays.copyOfRange(codePoints, wordPos, wordLen);
                final int flags = BinaryDictInputOutput.makeCharGroupFlags(characters.length > 1,
                        isTerminal, 0 /* childrenAddressSize */, hasShortcuts, hasBigrams,
                        isNotAWord, isBlackListEntry, header.mFormatOptions);
                final CharGroupInfo newInfo = new CharGroupInfo(newNodeAddress + 1,
                        -1 /* endAddress */, flags, characters, frequency, nodeParentAddress,
                        FormatSpec.NO_CHILDREN_ADDRESS, shortcuts, bigrams);
                BinaryDictIOUtils.writeNode(destination, new CharGroupInfo[]{ newInfo });
                return;
            } else {
                depth--;
                buffer.position(nextLink);
            }
        }
    }
}
