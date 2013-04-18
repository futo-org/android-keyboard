/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes XML files for a FusionDictionary.
 *
 * All the methods in this class are static.
 */
public final class BinaryDictInputOutput {

    private static final boolean DBG = MakedictLog.DBG;

    // Arbitrary limit to how much passes we consider address size compression should
    // terminate in. At the time of this writing, our largest dictionary completes
    // compression in five passes.
    // If the number of passes exceeds this number, makedict bails with an exception on
    // suspicion that a bug might be causing an infinite loop.
    private static final int MAX_PASSES = 24;
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
        private static int getCharArraySize(final int[] chars) {
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
        private static int writeCharArray(final int[] codePoints, final byte[] buffer, int index) {
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
        private static int writeString(final byte[] buffer, final int origin,
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
        private static void writeString(final ByteArrayOutputStream buffer, final String word) {
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

    /**
     * Compute the binary size of the character array.
     *
     * If only one character, this is the size of this character. If many, it's the sum of their
     * sizes + 1 byte for the terminator.
     *
     * @param characters the character array
     * @return the size of the char array, including the terminator if any
     */
    static int getGroupCharactersSize(final int[] characters) {
        int size = CharEncoding.getCharArraySize(characters);
        if (characters.length > 1) size += FormatSpec.GROUP_TERMINATOR_SIZE;
        return size;
    }

    /**
     * Compute the binary size of the character array in a group
     *
     * If only one character, this is the size of this character. If many, it's the sum of their
     * sizes + 1 byte for the terminator.
     *
     * @param group the group
     * @return the size of the char array, including the terminator if any
     */
    private static int getGroupCharactersSize(final CharGroup group) {
        return getGroupCharactersSize(group.mChars);
    }

    /**
     * Compute the binary size of the group count
     * @param count the group count
     * @return the size of the group count, either 1 or 2 bytes.
     */
    public static int getGroupCountSize(final int count) {
        if (FormatSpec.MAX_CHARGROUPS_FOR_ONE_BYTE_CHARGROUP_COUNT >= count) {
            return 1;
        } else if (FormatSpec.MAX_CHARGROUPS_IN_A_NODE >= count) {
            return 2;
        } else {
            throw new RuntimeException("Can't have more than "
                    + FormatSpec.MAX_CHARGROUPS_IN_A_NODE + " groups in a node (found " + count
                    + ")");
        }
    }

    /**
     * Compute the binary size of the group count for a node
     * @param node the node
     * @return the size of the group count, either 1 or 2 bytes.
     */
    private static int getGroupCountSize(final Node node) {
        return getGroupCountSize(node.mData.size());
    }

    /**
     * Compute the size of a shortcut in bytes.
     */
    private static int getShortcutSize(final WeightedString shortcut) {
        int size = FormatSpec.GROUP_ATTRIBUTE_FLAGS_SIZE;
        final String word = shortcut.mWord;
        final int length = word.length();
        for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
            final int codePoint = word.codePointAt(i);
            size += CharEncoding.getCharSize(codePoint);
        }
        size += FormatSpec.GROUP_TERMINATOR_SIZE;
        return size;
    }

    /**
     * Compute the size of a shortcut list in bytes.
     *
     * This is known in advance and does not change according to position in the file
     * like address lists do.
     */
    static int getShortcutListSize(final ArrayList<WeightedString> shortcutList) {
        if (null == shortcutList) return 0;
        int size = FormatSpec.GROUP_SHORTCUT_LIST_SIZE_SIZE;
        for (final WeightedString shortcut : shortcutList) {
            size += getShortcutSize(shortcut);
        }
        return size;
    }

    /**
     * Compute the maximum size of a CharGroup, assuming 3-byte addresses for everything.
     *
     * @param group the CharGroup to compute the size of.
     * @param options file format options.
     * @return the maximum size of the group.
     */
    private static int getCharGroupMaximumSize(final CharGroup group, final FormatOptions options) {
        int size = getGroupHeaderSize(group, options);
        // If terminal, one byte for the frequency
        if (group.isTerminal()) size += FormatSpec.GROUP_FREQUENCY_SIZE;
        size += FormatSpec.GROUP_MAX_ADDRESS_SIZE; // For children address
        size += getShortcutListSize(group.mShortcutTargets);
        if (null != group.mBigrams) {
            size += (FormatSpec.GROUP_ATTRIBUTE_FLAGS_SIZE
                    + FormatSpec.GROUP_ATTRIBUTE_MAX_ADDRESS_SIZE)
                    * group.mBigrams.size();
        }
        return size;
    }

    /**
     * Compute the maximum size of a node, assuming 3-byte addresses for everything, and caches
     * it in the 'actualSize' member of the node.
     *
     * @param node the node to compute the maximum size of.
     * @param options file format options.
     */
    private static void setNodeMaximumSize(final Node node, final FormatOptions options) {
        int size = getGroupCountSize(node);
        for (CharGroup g : node.mData) {
            final int groupSize = getCharGroupMaximumSize(g, options);
            g.mCachedSize = groupSize;
            size += groupSize;
        }
        if (options.mSupportsDynamicUpdate) {
            size += FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
        }
        node.mCachedSize = size;
    }

    /**
     * Helper method to hide the actual value of the no children address.
     */
    public static boolean hasChildrenAddress(final int address) {
        return FormatSpec.NO_CHILDREN_ADDRESS != address;
    }

    /**
     * Helper method to check whether the group is moved.
     */
    public static boolean isMovedGroup(final int flags, final FormatOptions options) {
        return options.mSupportsDynamicUpdate
                && ((flags & FormatSpec.MASK_GROUP_ADDRESS_TYPE) == FormatSpec.FLAG_IS_MOVED);
    }

    /**
     * Helper method to check whether the group is deleted.
     */
    public static boolean isDeletedGroup(final int flags, final FormatOptions formatOptions) {
        return formatOptions.mSupportsDynamicUpdate
                && ((flags & FormatSpec.MASK_GROUP_ADDRESS_TYPE) == FormatSpec.FLAG_IS_DELETED);
    }

    /**
     * Helper method to check whether the dictionary can be updated dynamically.
     */
    public static boolean supportsDynamicUpdate(final FormatOptions options) {
        return options.mVersion >= FormatSpec.FIRST_VERSION_WITH_DYNAMIC_UPDATE
                && options.mSupportsDynamicUpdate;
    }

    /**
     * Compute the size of the header (flag + [parent address] + characters size) of a CharGroup.
     *
     * @param group the group of which to compute the size of the header
     * @param options file format options.
     */
    private static int getGroupHeaderSize(final CharGroup group, final FormatOptions options) {
        if (supportsDynamicUpdate(options)) {
            return FormatSpec.GROUP_FLAGS_SIZE + FormatSpec.PARENT_ADDRESS_SIZE
                    + getGroupCharactersSize(group);
        } else {
            return FormatSpec.GROUP_FLAGS_SIZE + getGroupCharactersSize(group);
        }
    }

    private static final int UINT8_MAX = 0xFF;
    private static final int UINT16_MAX = 0xFFFF;
    private static final int UINT24_MAX = 0xFFFFFF;

    /**
     * Compute the size, in bytes, that an address will occupy.
     *
     * This can be used either for children addresses (which are always positive) or for
     * attribute, which may be positive or negative but
     * store their sign bit separately.
     *
     * @param address the address
     * @return the byte size.
     */
    static int getByteSize(final int address) {
        assert(address <= UINT24_MAX);
        if (!hasChildrenAddress(address)) {
            return 0;
        } else if (Math.abs(address) <= UINT8_MAX) {
            return 1;
        } else if (Math.abs(address) <= UINT16_MAX) {
            return 2;
        } else {
            return 3;
        }
    }

    private static final int SINT24_MAX = 0x7FFFFF;
    private static final int MSB8 = 0x80;
    private static final int MSB24 = 0x800000;

    // End utility methods.

    // This method is responsible for finding a nice ordering of the nodes that favors run-time
    // cache performance and dictionary size.
    /* package for tests */ static ArrayList<Node> flattenTree(final Node root) {
        final int treeSize = FusionDictionary.countCharGroups(root);
        MakedictLog.i("Counted nodes : " + treeSize);
        final ArrayList<Node> flatTree = new ArrayList<Node>(treeSize);
        return flattenTreeInner(flatTree, root);
    }

    private static ArrayList<Node> flattenTreeInner(final ArrayList<Node> list, final Node node) {
        // Removing the node is necessary if the tails are merged, because we would then
        // add the same node several times when we only want it once. A number of places in
        // the code also depends on any node being only once in the list.
        // Merging tails can only be done if there are no attributes. Searching for attributes
        // in LatinIME code depends on a total breadth-first ordering, which merging tails
        // breaks. If there are no attributes, it should be fine (and reduce the file size)
        // to merge tails, and removing the node from the list would be necessary. However,
        // we don't merge tails because breaking the breadth-first ordering would result in
        // extreme overhead at bigram lookup time (it would make the search function O(n) instead
        // of the current O(log(n)), where n=number of nodes in the dictionary which is pretty
        // high).
        // If no nodes are ever merged, we can't have the same node twice in the list, hence
        // searching for duplicates in unnecessary. It is also very performance consuming,
        // since `list' is an ArrayList so it's an O(n) operation that runs on all nodes, making
        // this simple list.remove operation O(n*n) overall. On Android this overhead is very
        // high.
        // For future reference, the code to remove duplicate is a simple : list.remove(node);
        list.add(node);
        final ArrayList<CharGroup> branches = node.mData;
        final int nodeSize = branches.size();
        for (CharGroup group : branches) {
            if (null != group.mChildren) flattenTreeInner(list, group.mChildren);
        }
        return list;
    }

    /**
     * Finds the absolute address of a word in the dictionary.
     *
     * @param dict the dictionary in which to search.
     * @param word the word we are searching for.
     * @return the word address. If it is not found, an exception is thrown.
     */
    private static int findAddressOfWord(final FusionDictionary dict, final String word) {
        return FusionDictionary.findWordInTree(dict.mRoot, word).mCachedAddress;
    }

    /**
     * Computes the actual node size, based on the cached addresses of the children nodes.
     *
     * Each node stores its tentative address. During dictionary address computing, these
     * are not final, but they can be used to compute the node size (the node size depends
     * on the address of the children because the number of bytes necessary to store an
     * address depends on its numeric value. The return value indicates whether the node
     * contents (as in, any of the addresses stored in the cache fields) have changed with
     * respect to their previous value.
     *
     * @param node the node to compute the size of.
     * @param dict the dictionary in which the word/attributes are to be found.
     * @param formatOptions file format options.
     * @return false if none of the cached addresses inside the node changed, true otherwise.
     */
    private static boolean computeActualNodeSize(final Node node, final FusionDictionary dict,
            final FormatOptions formatOptions) {
        boolean changed = false;
        int size = getGroupCountSize(node);
        for (CharGroup group : node.mData) {
            if (group.mCachedAddress != node.mCachedAddress + size) {
                changed = true;
                group.mCachedAddress = node.mCachedAddress + size;
            }
            int groupSize = getGroupHeaderSize(group, formatOptions);
            if (group.isTerminal()) groupSize += FormatSpec.GROUP_FREQUENCY_SIZE;
            if (null == group.mChildren && formatOptions.mSupportsDynamicUpdate) {
                groupSize += FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;
            } else if (null != group.mChildren) {
                final int offsetBasePoint = groupSize + node.mCachedAddress + size;
                final int offset = group.mChildren.mCachedAddress - offsetBasePoint;
                // assign my address to children's parent address
                group.mChildren.mCachedParentAddress = group.mCachedAddress
                        - group.mChildren.mCachedAddress;
                if (formatOptions.mSupportsDynamicUpdate) {
                    groupSize += FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;
                } else {
                    groupSize += getByteSize(offset);
                }
            }
            groupSize += getShortcutListSize(group.mShortcutTargets);
            if (null != group.mBigrams) {
                for (WeightedString bigram : group.mBigrams) {
                    final int offsetBasePoint = groupSize + node.mCachedAddress + size
                            + FormatSpec.GROUP_FLAGS_SIZE;
                    final int addressOfBigram = findAddressOfWord(dict, bigram.mWord);
                    final int offset = addressOfBigram - offsetBasePoint;
                    groupSize += getByteSize(offset) + FormatSpec.GROUP_FLAGS_SIZE;
                }
            }
            group.mCachedSize = groupSize;
            size += groupSize;
        }
        if (formatOptions.mSupportsDynamicUpdate) {
            size += FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
        }
        if (node.mCachedSize != size) {
            node.mCachedSize = size;
            changed = true;
        }
        return changed;
    }

    /**
     * Computes the byte size of a list of nodes and updates each node cached position.
     *
     * @param flatNodes the array of nodes.
     * @param formatOptions file format options.
     * @return the byte size of the entire stack.
     */
    private static int stackNodes(final ArrayList<Node> flatNodes,
            final FormatOptions formatOptions) {
        int nodeOffset = 0;
        for (Node n : flatNodes) {
            n.mCachedAddress = nodeOffset;
            int groupCountSize = getGroupCountSize(n);
            int groupOffset = 0;
            for (CharGroup g : n.mData) {
                g.mCachedAddress = groupCountSize + nodeOffset + groupOffset;
                groupOffset += g.mCachedSize;
            }
            final int nodeSize = groupCountSize + groupOffset
                    + (formatOptions.mSupportsDynamicUpdate
                            ? FormatSpec.FORWARD_LINK_ADDRESS_SIZE : 0);
            if (nodeSize != n.mCachedSize) {
                throw new RuntimeException("Bug : Stored and computed node size differ");
            }
            nodeOffset += n.mCachedSize;
        }
        return nodeOffset;
    }

    /**
     * Compute the addresses and sizes of an ordered node array.
     *
     * This method takes a node array and will update its cached address and size values
     * so that they can be written into a file. It determines the smallest size each of the
     * nodes can be given the addresses of its children and attributes, and store that into
     * each node.
     * The order of the node is given by the order of the array. This method makes no effort
     * to find a good order; it only mechanically computes the size this order results in.
     *
     * @param dict the dictionary
     * @param flatNodes the ordered array of nodes
     * @param formatOptions file format options.
     * @return the same array it was passed. The nodes have been updated for address and size.
     */
    private static ArrayList<Node> computeAddresses(final FusionDictionary dict,
            final ArrayList<Node> flatNodes, final FormatOptions formatOptions) {
        // First get the worst sizes and offsets
        for (Node n : flatNodes) setNodeMaximumSize(n, formatOptions);
        final int offset = stackNodes(flatNodes, formatOptions);

        MakedictLog.i("Compressing the array addresses. Original size : " + offset);
        MakedictLog.i("(Recursively seen size : " + offset + ")");

        int passes = 0;
        boolean changesDone = false;
        do {
            changesDone = false;
            for (Node n : flatNodes) {
                final int oldNodeSize = n.mCachedSize;
                final boolean changed = computeActualNodeSize(n, dict, formatOptions);
                final int newNodeSize = n.mCachedSize;
                if (oldNodeSize < newNodeSize) throw new RuntimeException("Increased size ?!");
                changesDone |= changed;
            }
            stackNodes(flatNodes, formatOptions);
            ++passes;
            if (passes > MAX_PASSES) throw new RuntimeException("Too many passes - probably a bug");
        } while (changesDone);

        final Node lastNode = flatNodes.get(flatNodes.size() - 1);
        MakedictLog.i("Compression complete in " + passes + " passes.");
        MakedictLog.i("After address compression : "
                + (lastNode.mCachedAddress + lastNode.mCachedSize));

        return flatNodes;
    }

    /**
     * Sanity-checking method.
     *
     * This method checks an array of node for juxtaposition, that is, it will do
     * nothing if each node's cached address is actually the previous node's address
     * plus the previous node's size.
     * If this is not the case, it will throw an exception.
     *
     * @param array the array node to check
     */
    private static void checkFlatNodeArray(final ArrayList<Node> array) {
        int offset = 0;
        int index = 0;
        for (Node n : array) {
            if (n.mCachedAddress != offset) {
                throw new RuntimeException("Wrong address for node " + index
                        + " : expected " + offset + ", got " + n.mCachedAddress);
            }
            ++index;
            offset += n.mCachedSize;
        }
    }

    /**
     * Helper method to write a variable-size address to a file.
     *
     * @param buffer the buffer to write to.
     * @param index the index in the buffer to write the address to.
     * @param address the address to write.
     * @return the size in bytes the address actually took.
     */
    private static int writeVariableAddress(final byte[] buffer, int index, final int address) {
        switch (getByteSize(address)) {
        case 1:
            buffer[index++] = (byte)address;
            return 1;
        case 2:
            buffer[index++] = (byte)(0xFF & (address >> 8));
            buffer[index++] = (byte)(0xFF & address);
            return 2;
        case 3:
            buffer[index++] = (byte)(0xFF & (address >> 16));
            buffer[index++] = (byte)(0xFF & (address >> 8));
            buffer[index++] = (byte)(0xFF & address);
            return 3;
        case 0:
            return 0;
        default:
            throw new RuntimeException("Address " + address + " has a strange size");
        }
    }

    /**
     * Helper method to write a variable-size signed address to a file.
     *
     * @param buffer the buffer to write to.
     * @param index the index in the buffer to write the address to.
     * @param address the address to write.
     * @return the size in bytes the address actually took.
     */
    private static int writeVariableSignedAddress(final byte[] buffer, int index,
            final int address) {
        if (!hasChildrenAddress(address)) {
            buffer[index] = buffer[index + 1] = buffer[index + 2] = 0;
        } else {
            final int absAddress = Math.abs(address);
            buffer[index++] = (byte)((address < 0 ? MSB8 : 0) | (0xFF & (absAddress >> 16)));
            buffer[index++] = (byte)(0xFF & (absAddress >> 8));
            buffer[index++] = (byte)(0xFF & absAddress);
        }
        return 3;
    }

    /**
     * Makes the flag value for a char group.
     *
     * @param hasMultipleChars whether the group has multiple chars.
     * @param isTerminal whether the group is terminal.
     * @param childrenAddressSize the size of a children address.
     * @param hasShortcuts whether the group has shortcuts.
     * @param hasBigrams whether the group has bigrams.
     * @param isNotAWord whether the group is not a word.
     * @param isBlackListEntry whether the group is a blacklist entry.
     * @param formatOptions file format options.
     * @return the flags
     */
    static int makeCharGroupFlags(final boolean hasMultipleChars, final boolean isTerminal,
            final int childrenAddressSize, final boolean hasShortcuts, final boolean hasBigrams,
            final boolean isNotAWord, final boolean isBlackListEntry,
            final FormatOptions formatOptions) {
        byte flags = 0;
        if (hasMultipleChars) flags |= FormatSpec.FLAG_HAS_MULTIPLE_CHARS;
        if (isTerminal) flags |= FormatSpec.FLAG_IS_TERMINAL;
        if (formatOptions.mSupportsDynamicUpdate) {
            flags |= FormatSpec.FLAG_IS_NOT_MOVED;
        } else if (true) {
            switch (childrenAddressSize) {
                case 1:
                    flags |= FormatSpec.FLAG_GROUP_ADDRESS_TYPE_ONEBYTE;
                    break;
                case 2:
                    flags |= FormatSpec.FLAG_GROUP_ADDRESS_TYPE_TWOBYTES;
                    break;
                case 3:
                    flags |= FormatSpec.FLAG_GROUP_ADDRESS_TYPE_THREEBYTES;
                    break;
                case 0:
                    flags |= FormatSpec.FLAG_GROUP_ADDRESS_TYPE_NOADDRESS;
                    break;
                default:
                    throw new RuntimeException("Node with a strange address");
            }
        }
        if (hasShortcuts) flags |= FormatSpec.FLAG_HAS_SHORTCUT_TARGETS;
        if (hasBigrams) flags |= FormatSpec.FLAG_HAS_BIGRAMS;
        if (isNotAWord) flags |= FormatSpec.FLAG_IS_NOT_A_WORD;
        if (isBlackListEntry) flags |= FormatSpec.FLAG_IS_BLACKLISTED;
        return flags;
    }

    private static byte makeCharGroupFlags(final CharGroup group, final int groupAddress,
            final int childrenOffset, final FormatOptions formatOptions) {
        return (byte) makeCharGroupFlags(group.mChars.length > 1, group.mFrequency >= 0,
                getByteSize(childrenOffset), group.mShortcutTargets != null, group.mBigrams != null,
                group.mIsNotAWord, group.mIsBlacklistEntry, formatOptions);
    }

    /**
     * Makes the flag value for a bigram.
     *
     * @param more whether there are more bigrams after this one.
     * @param offset the offset of the bigram.
     * @param bigramFrequency the frequency of the bigram, 0..255.
     * @param unigramFrequency the unigram frequency of the same word, 0..255.
     * @param word the second bigram, for debugging purposes
     * @return the flags
     */
    private static final int makeBigramFlags(final boolean more, final int offset,
            int bigramFrequency, final int unigramFrequency, final String word) {
        int bigramFlags = (more ? FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT : 0)
                + (offset < 0 ? FormatSpec.FLAG_ATTRIBUTE_OFFSET_NEGATIVE : 0);
        switch (getByteSize(offset)) {
        case 1:
            bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
            break;
        case 2:
            bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
            break;
        case 3:
            bigramFlags |= FormatSpec.FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
            break;
        default:
            throw new RuntimeException("Strange offset size");
        }
        if (unigramFrequency > bigramFrequency) {
            MakedictLog.e("Unigram freq is superior to bigram freq for \"" + word
                    + "\". Bigram freq is " + bigramFrequency + ", unigram freq for "
                    + word + " is " + unigramFrequency);
            bigramFrequency = unigramFrequency;
        }
        // We compute the difference between 255 (which means probability = 1) and the
        // unigram score. We split this into a number of discrete steps.
        // Now, the steps are numbered 0~15; 0 represents an increase of 1 step while 15
        // represents an increase of 16 steps: a value of 15 will be interpreted as the median
        // value of the 16th step. In all justice, if the bigram frequency is low enough to be
        // rounded below the first step (which means it is less than half a step higher than the
        // unigram frequency) then the unigram frequency itself is the best approximation of the
        // bigram freq that we could possibly supply, hence we should *not* include this bigram
        // in the file at all.
        // until this is done, we'll write 0 and slightly overestimate this case.
        // In other words, 0 means "between 0.5 step and 1.5 step", 1 means "between 1.5 step
        // and 2.5 steps", and 15 means "between 15.5 steps and 16.5 steps". So we want to
        // divide our range [unigramFreq..MAX_TERMINAL_FREQUENCY] in 16.5 steps to get the
        // step size. Then we compute the start of the first step (the one where value 0 starts)
        // by adding half-a-step to the unigramFrequency. From there, we compute the integer
        // number of steps to the bigramFrequency. One last thing: we want our steps to include
        // their lower bound and exclude their higher bound so we need to have the first step
        // start at exactly 1 unit higher than floor(unigramFreq + half a step).
        // Note : to reconstruct the score, the dictionary reader will need to divide
        // MAX_TERMINAL_FREQUENCY - unigramFreq by 16.5 likewise to get the value of the step,
        // and add (discretizedFrequency + 0.5 + 0.5) times this value to get the best
        // approximation. (0.5 to get the first step start, and 0.5 to get the middle of the
        // step pointed by the discretized frequency.
        final float stepSize =
                (FormatSpec.MAX_TERMINAL_FREQUENCY - unigramFrequency)
                / (1.5f + FormatSpec.MAX_BIGRAM_FREQUENCY);
        final float firstStepStart = 1 + unigramFrequency + (stepSize / 2.0f);
        final int discretizedFrequency = (int)((bigramFrequency - firstStepStart) / stepSize);
        // If the bigram freq is less than half-a-step higher than the unigram freq, we get -1
        // here. The best approximation would be the unigram freq itself, so we should not
        // include this bigram in the dictionary. For now, register as 0, and live with the
        // small over-estimation that we get in this case. TODO: actually remove this bigram
        // if discretizedFrequency < 0.
        final int finalBigramFrequency = discretizedFrequency > 0 ? discretizedFrequency : 0;
        bigramFlags += finalBigramFrequency & FormatSpec.FLAG_ATTRIBUTE_FREQUENCY;
        return bigramFlags;
    }

    /**
     * Makes the 2-byte value for options flags.
     */
    private static final int makeOptionsValue(final FusionDictionary dictionary,
            final FormatOptions formatOptions) {
        final DictionaryOptions options = dictionary.mOptions;
        final boolean hasBigrams = dictionary.hasBigrams();
        return (options.mFrenchLigatureProcessing ? FormatSpec.FRENCH_LIGATURE_PROCESSING_FLAG : 0)
                + (options.mGermanUmlautProcessing ? FormatSpec.GERMAN_UMLAUT_PROCESSING_FLAG : 0)
                + (hasBigrams ? FormatSpec.CONTAINS_BIGRAMS_FLAG : 0)
                + (formatOptions.mSupportsDynamicUpdate ? FormatSpec.SUPPORTS_DYNAMIC_UPDATE : 0);
    }

    /**
     * Makes the flag value for a shortcut.
     *
     * @param more whether there are more attributes after this one.
     * @param frequency the frequency of the attribute, 0..15
     * @return the flags
     */
    static final int makeShortcutFlags(final boolean more, final int frequency) {
        return (more ? FormatSpec.FLAG_ATTRIBUTE_HAS_NEXT : 0)
                + (frequency & FormatSpec.FLAG_ATTRIBUTE_FREQUENCY);
    }

    private static final int writeParentAddress(final byte[] buffer, final int index,
            final int address, final FormatOptions formatOptions) {
        if (supportsDynamicUpdate(formatOptions)) {
            if (address == FormatSpec.NO_PARENT_ADDRESS) {
                buffer[index] = buffer[index + 1] = buffer[index + 2] = 0;
            } else {
                final int absAddress = Math.abs(address);
                assert(absAddress <= SINT24_MAX);
                buffer[index] = (byte)((address < 0 ? MSB8 : 0)
                        | ((absAddress >> 16) & 0xFF));
                buffer[index + 1] = (byte)((absAddress >> 8) & 0xFF);
                buffer[index + 2] = (byte)(absAddress & 0xFF);
            }
            return index + 3;
        } else {
            return index;
        }
    }

    /**
     * Write a node to memory. The node is expected to have its final position cached.
     *
     * This can be an empty map, but the more is inside the faster the lookups will be. It can
     * be carried on as long as nodes do not move.
     *
     * @param dict the dictionary the node is a part of (for relative offsets).
     * @param buffer the memory buffer to write to.
     * @param node the node to write.
     * @param formatOptions file format options.
     * @return the address of the END of the node.
     */
    @SuppressWarnings("unused")
    private static int writePlacedNode(final FusionDictionary dict, byte[] buffer,
            final Node node, final FormatOptions formatOptions) {
        // TODO: Make the code in common with BinaryDictIOUtils#writeCharGroup
        int index = node.mCachedAddress;

        final int groupCount = node.mData.size();
        final int countSize = getGroupCountSize(node);
        final int parentAddress = node.mCachedParentAddress;
        if (1 == countSize) {
            buffer[index++] = (byte)groupCount;
        } else if (2 == countSize) {
            // We need to signal 2-byte size by setting the top bit of the MSB to 1, so
            // we | 0x80 to do this.
            buffer[index++] = (byte)((groupCount >> 8) | 0x80);
            buffer[index++] = (byte)(groupCount & 0xFF);
        } else {
            throw new RuntimeException("Strange size from getGroupCountSize : " + countSize);
        }
        int groupAddress = index;
        for (int i = 0; i < groupCount; ++i) {
            CharGroup group = node.mData.get(i);
            if (index != group.mCachedAddress) throw new RuntimeException("Bug: write index is not "
                    + "the same as the cached address of the group : "
                    + index + " <> " + group.mCachedAddress);
            groupAddress += getGroupHeaderSize(group, formatOptions);
            // Sanity checks.
            if (DBG && group.mFrequency > FormatSpec.MAX_TERMINAL_FREQUENCY) {
                throw new RuntimeException("A node has a frequency > "
                        + FormatSpec.MAX_TERMINAL_FREQUENCY
                        + " : " + group.mFrequency);
            }
            if (group.mFrequency >= 0) groupAddress += FormatSpec.GROUP_FREQUENCY_SIZE;
            final int childrenOffset = null == group.mChildren
                    ? FormatSpec.NO_CHILDREN_ADDRESS
                            : group.mChildren.mCachedAddress - groupAddress;
            byte flags = makeCharGroupFlags(group, groupAddress, childrenOffset, formatOptions);
            buffer[index++] = flags;

            if (parentAddress == FormatSpec.NO_PARENT_ADDRESS) {
                index = writeParentAddress(buffer, index, parentAddress, formatOptions);
            } else {
                index = writeParentAddress(buffer, index,
                        parentAddress + (node.mCachedAddress - group.mCachedAddress),
                        formatOptions);
            }

            index = CharEncoding.writeCharArray(group.mChars, buffer, index);
            if (group.hasSeveralChars()) {
                buffer[index++] = FormatSpec.GROUP_CHARACTERS_TERMINATOR;
            }
            if (group.mFrequency >= 0) {
                buffer[index++] = (byte) group.mFrequency;
            }

            final int shift;
            if (formatOptions.mSupportsDynamicUpdate) {
                shift = writeVariableSignedAddress(buffer, index, childrenOffset);
            } else {
                shift = writeVariableAddress(buffer, index, childrenOffset);
            }
            index += shift;
            groupAddress += shift;

            // Write shortcuts
            if (null != group.mShortcutTargets) {
                final int indexOfShortcutByteSize = index;
                index += FormatSpec.GROUP_SHORTCUT_LIST_SIZE_SIZE;
                groupAddress += FormatSpec.GROUP_SHORTCUT_LIST_SIZE_SIZE;
                final Iterator<WeightedString> shortcutIterator = group.mShortcutTargets.iterator();
                while (shortcutIterator.hasNext()) {
                    final WeightedString target = shortcutIterator.next();
                    ++groupAddress;
                    int shortcutFlags = makeShortcutFlags(shortcutIterator.hasNext(),
                            target.mFrequency);
                    buffer[index++] = (byte)shortcutFlags;
                    final int shortcutShift = CharEncoding.writeString(buffer, index, target.mWord);
                    index += shortcutShift;
                    groupAddress += shortcutShift;
                }
                final int shortcutByteSize = index - indexOfShortcutByteSize;
                if (shortcutByteSize > 0xFFFF) {
                    throw new RuntimeException("Shortcut list too large");
                }
                buffer[indexOfShortcutByteSize] = (byte)(shortcutByteSize >> 8);
                buffer[indexOfShortcutByteSize + 1] = (byte)(shortcutByteSize & 0xFF);
            }
            // Write bigrams
            if (null != group.mBigrams) {
                final Iterator<WeightedString> bigramIterator = group.mBigrams.iterator();
                while (bigramIterator.hasNext()) {
                    final WeightedString bigram = bigramIterator.next();
                    final CharGroup target =
                            FusionDictionary.findWordInTree(dict.mRoot, bigram.mWord);
                    final int addressOfBigram = target.mCachedAddress;
                    final int unigramFrequencyForThisWord = target.mFrequency;
                    ++groupAddress;
                    final int offset = addressOfBigram - groupAddress;
                    int bigramFlags = makeBigramFlags(bigramIterator.hasNext(), offset,
                            bigram.mFrequency, unigramFrequencyForThisWord, bigram.mWord);
                    buffer[index++] = (byte)bigramFlags;
                    final int bigramShift = writeVariableAddress(buffer, index, Math.abs(offset));
                    index += bigramShift;
                    groupAddress += bigramShift;
                }
            }

        }
        if (formatOptions.mSupportsDynamicUpdate) {
            buffer[index] = buffer[index + 1] = buffer[index + 2]
                    = FormatSpec.NO_FORWARD_LINK_ADDRESS;
            index += FormatSpec.FORWARD_LINK_ADDRESS_SIZE;
        }
        if (index != node.mCachedAddress + node.mCachedSize) throw new RuntimeException(
                "Not the same size : written "
                + (index - node.mCachedAddress) + " bytes out of a node that should have "
                + node.mCachedSize + " bytes");
        return index;
    }

    /**
     * Dumps a collection of useful statistics about a node array.
     *
     * This prints purely informative stuff, like the total estimated file size, the
     * number of nodes, of character groups, the repartition of each address size, etc
     *
     * @param nodes the node array.
     */
    private static void showStatistics(ArrayList<Node> nodes) {
        int firstTerminalAddress = Integer.MAX_VALUE;
        int lastTerminalAddress = Integer.MIN_VALUE;
        int size = 0;
        int charGroups = 0;
        int maxGroups = 0;
        int maxRuns = 0;
        for (Node n : nodes) {
            if (maxGroups < n.mData.size()) maxGroups = n.mData.size();
            for (CharGroup cg : n.mData) {
                ++charGroups;
                if (cg.mChars.length > maxRuns) maxRuns = cg.mChars.length;
                if (cg.mFrequency >= 0) {
                    if (n.mCachedAddress < firstTerminalAddress)
                        firstTerminalAddress = n.mCachedAddress;
                    if (n.mCachedAddress > lastTerminalAddress)
                        lastTerminalAddress = n.mCachedAddress;
                }
            }
            if (n.mCachedAddress + n.mCachedSize > size) size = n.mCachedAddress + n.mCachedSize;
        }
        final int[] groupCounts = new int[maxGroups + 1];
        final int[] runCounts = new int[maxRuns + 1];
        for (Node n : nodes) {
            ++groupCounts[n.mData.size()];
            for (CharGroup cg : n.mData) {
                ++runCounts[cg.mChars.length];
            }
        }

        MakedictLog.i("Statistics:\n"
                + "  total file size " + size + "\n"
                + "  " + nodes.size() + " nodes\n"
                + "  " + charGroups + " groups (" + ((float)charGroups / nodes.size())
                        + " groups per node)\n"
                + "  first terminal at " + firstTerminalAddress + "\n"
                + "  last terminal at " + lastTerminalAddress + "\n"
                + "  Group stats : max = " + maxGroups);
        for (int i = 0; i < groupCounts.length; ++i) {
            MakedictLog.i("    " + i + " : " + groupCounts[i]);
        }
        MakedictLog.i("  Character run stats : max = " + maxRuns);
        for (int i = 0; i < runCounts.length; ++i) {
            MakedictLog.i("    " + i + " : " + runCounts[i]);
        }
    }

    /**
     * Dumps a FusionDictionary to a file.
     *
     * This is the public entry point to write a dictionary to a file.
     *
     * @param destination the stream to write the binary data to.
     * @param dict the dictionary to write.
     * @param formatOptions file format options.
     */
    public static void writeDictionaryBinary(final OutputStream destination,
            final FusionDictionary dict, final FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {

        // Addresses are limited to 3 bytes, but since addresses can be relative to each node, the
        // structure itself is not limited to 16MB. However, if it is over 16MB deciding the order
        // of the nodes becomes a quite complicated problem, because though the dictionary itself
        // does not have a size limit, each node must still be within 16MB of all its children and
        // parents. As long as this is ensured, the dictionary file may grow to any size.

        final int version = formatOptions.mVersion;
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
            throw new UnsupportedFormatException("Requested file format version " + version
                    + ", but this implementation only supports versions "
                    + FormatSpec.MINIMUM_SUPPORTED_VERSION + " through "
                    + FormatSpec.MAXIMUM_SUPPORTED_VERSION);
        }

        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream(256);

        // The magic number in big-endian order.
        if (version >= FormatSpec.FIRST_VERSION_WITH_HEADER_SIZE) {
            // Magic number for version 2+.
            headerBuffer.write((byte) (0xFF & (FormatSpec.VERSION_2_MAGIC_NUMBER >> 24)));
            headerBuffer.write((byte) (0xFF & (FormatSpec.VERSION_2_MAGIC_NUMBER >> 16)));
            headerBuffer.write((byte) (0xFF & (FormatSpec.VERSION_2_MAGIC_NUMBER >> 8)));
            headerBuffer.write((byte) (0xFF & FormatSpec.VERSION_2_MAGIC_NUMBER));
            // Dictionary version.
            headerBuffer.write((byte) (0xFF & (version >> 8)));
            headerBuffer.write((byte) (0xFF & version));
        } else {
            // Magic number for version 1.
            headerBuffer.write((byte) (0xFF & (FormatSpec.VERSION_1_MAGIC_NUMBER >> 8)));
            headerBuffer.write((byte) (0xFF & FormatSpec.VERSION_1_MAGIC_NUMBER));
            // Dictionary version.
            headerBuffer.write((byte) (0xFF & version));
        }
        // Options flags
        final int options = makeOptionsValue(dict, formatOptions);
        headerBuffer.write((byte) (0xFF & (options >> 8)));
        headerBuffer.write((byte) (0xFF & options));
        if (version >= FormatSpec.FIRST_VERSION_WITH_HEADER_SIZE) {
            final int headerSizeOffset = headerBuffer.size();
            // Placeholder to be written later with header size.
            for (int i = 0; i < 4; ++i) {
                headerBuffer.write(0);
            }
            // Write out the options.
            for (final String key : dict.mOptions.mAttributes.keySet()) {
                final String value = dict.mOptions.mAttributes.get(key);
                CharEncoding.writeString(headerBuffer, key);
                CharEncoding.writeString(headerBuffer, value);
            }
            final int size = headerBuffer.size();
            final byte[] bytes = headerBuffer.toByteArray();
            // Write out the header size.
            bytes[headerSizeOffset] = (byte) (0xFF & (size >> 24));
            bytes[headerSizeOffset + 1] = (byte) (0xFF & (size >> 16));
            bytes[headerSizeOffset + 2] = (byte) (0xFF & (size >> 8));
            bytes[headerSizeOffset + 3] = (byte) (0xFF & (size >> 0));
            destination.write(bytes);
        } else {
            headerBuffer.writeTo(destination);
        }

        headerBuffer.close();

        // Leave the choice of the optimal node order to the flattenTree function.
        MakedictLog.i("Flattening the tree...");
        ArrayList<Node> flatNodes = flattenTree(dict.mRoot);

        MakedictLog.i("Computing addresses...");
        computeAddresses(dict, flatNodes, formatOptions);
        MakedictLog.i("Checking array...");
        if (DBG) checkFlatNodeArray(flatNodes);

        // Create a buffer that matches the final dictionary size.
        final Node lastNode = flatNodes.get(flatNodes.size() - 1);
        final int bufferSize = lastNode.mCachedAddress + lastNode.mCachedSize;
        final byte[] buffer = new byte[bufferSize];
        int index = 0;

        MakedictLog.i("Writing file...");
        int dataEndOffset = 0;
        for (Node n : flatNodes) {
            dataEndOffset = writePlacedNode(dict, buffer, n, formatOptions);
        }

        if (DBG) showStatistics(flatNodes);

        destination.write(buffer, 0, dataEndOffset);

        destination.close();
        MakedictLog.i("Done");
    }


    // Input methods: Read a binary dictionary to memory.
    // readDictionaryBinary is the public entry point for them.

    static int getChildrenAddressSize(final int optionFlags,
            final FormatOptions formatOptions) {
        if (formatOptions.mSupportsDynamicUpdate) return FormatSpec.SIGNED_CHILDREN_ADDRESS_SIZE;
        switch (optionFlags & FormatSpec.MASK_GROUP_ADDRESS_TYPE) {
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_ONEBYTE:
                return 1;
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_TWOBYTES:
                return 2;
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_THREEBYTES:
                return 3;
            case FormatSpec.FLAG_GROUP_ADDRESS_TYPE_NOADDRESS:
            default:
                return 0;
        }
    }

    static int readChildrenAddress(final FusionDictionaryBufferInterface buffer,
            final int optionFlags, final FormatOptions options) {
        if (options.mSupportsDynamicUpdate) {
            final int address = buffer.readUnsignedInt24();
            if (address == 0) return FormatSpec.NO_CHILDREN_ADDRESS;
            if ((address & MSB24) != 0) {
                return -(address & SINT24_MAX);
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
        if (supportsDynamicUpdate(formatOptions)) {
            final int parentAddress = buffer.readUnsignedInt24();
            final int sign = ((parentAddress & MSB24) != 0) ? -1 : 1;
            return sign * (parentAddress & SINT24_MAX);
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
        if (supportsDynamicUpdate(options)) {
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
        addressPointer += getChildrenAddressSize(flags, options);
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

        if (supportsDynamicUpdate(formatOptions)) {
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
                if (isMovedGroup(currentInfo.mFlags, options)) {
                    currentAddress = currentInfo.mParentAddress + currentInfo.mOriginalAddress;
                }
                if (DBG && loopCounter++ > MAX_JUMPS) {
                    MakedictLog.d("Too many jumps - probably a bug");
                }
            } while (isMovedGroup(currentInfo.mFlags, options));
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
        int groupOffset = getGroupCountSize(count);
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
            if (hasChildrenAddress(info.mChildrenAddress)) {
                if (info.mChildrenAddress > address) {
                    if (null == last) continue;
                    builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                    buffer.position(last.mChildrenAddress + headerSize);
                    i = readCharGroupCount(buffer);
                    groupOffset = last.mChildrenAddress + getGroupCountSize(i);
                    last = null;
                    continue;
                }
                last = info;
            }
            if (0 == i && hasChildrenAddress(last.mChildrenAddress)) {
                builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                buffer.position(last.mChildrenAddress + headerSize);
                i = readCharGroupCount(buffer);
                groupOffset = last.mChildrenAddress + getGroupCountSize(i);
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
            int groupOffset = nodeHeadPosition + getGroupCountSize(count);
            for (int i = count; i > 0; --i) { // Scan the array of CharGroup.
                CharGroupInfo info = readCharGroup(buffer, groupOffset, options);
                if (isMovedGroup(info.mFlags, options)) continue;
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
                if (hasChildrenAddress(info.mChildrenAddress)) {
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
        node.mCachedAddress = nodeOrigin;
        reverseNodeMap.put(node.mCachedAddress, node);
        return node;
    }

    /**
     * Helper function to get the binary format version from the header.
     * @throws IOException
     */
    private static int getFormatVersion(final FusionDictionaryBufferInterface buffer)
            throws IOException {
        final int magic_v1 = buffer.readUnsignedShort();
        if (FormatSpec.VERSION_1_MAGIC_NUMBER == magic_v1) return buffer.readUnsignedByte();
        final int magic_v2 = (magic_v1 << 16) + buffer.readUnsignedShort();
        if (FormatSpec.VERSION_2_MAGIC_NUMBER == magic_v2) return buffer.readUnsignedShort();
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
        if (version < FormatSpec.FIRST_VERSION_WITH_HEADER_SIZE) {
            headerSize = buffer.position();
        } else {
            headerSize = buffer.readInt();
            populateOptions(buffer, headerSize, attributes);
            buffer.position(headerSize);
        }

        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

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
     * @param buffer the buffer to read.
     * @param dict an optional dictionary to add words to, or null.
     * @return the created (or merged) dictionary.
     */
    @UsedForTesting
    public static FusionDictionary readDictionaryBinary(
            final FusionDictionaryBufferInterface buffer, final FusionDictionary dict)
                    throws IOException, UnsupportedFormatException {
        // clear cache
        wordCache.clear();

        // Read header
        final FileHeader header = readHeader(buffer);

        Map<Integer, Node> reverseNodeMapping = new TreeMap<Integer, Node>();
        Map<Integer, CharGroup> reverseGroupMapping = new TreeMap<Integer, CharGroup>();
        final Node root = readNode(buffer, header.mHeaderSize, reverseNodeMapping,
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
     * @see #makeBigramFlags
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
