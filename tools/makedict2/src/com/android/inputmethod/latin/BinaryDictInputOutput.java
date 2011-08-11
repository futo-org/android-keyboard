/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.FusionDictionary.Node;
import com.android.inputmethod.latin.FusionDictionary.WeightedString;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes XML files for a FusionDictionary.
 *
 * All the methods in this class are static.
 */
public class BinaryDictInputOutput {

    /* Node layout is as follows:
     *   | addressType                         xx     : mask with MASK_GROUP_ADDRESS_TYPE
     *                                 2 bits, 00 = no children : FLAG_GROUP_ADDRESS_TYPE_NOADDRESS
     * f |                                     01 = 1 byte      : FLAG_GROUP_ADDRESS_TYPE_ONEBYTE
     * l |                                     10 = 2 bytes     : FLAG_GROUP_ADDRESS_TYPE_TWOBYTES
     * a |                                     11 = 3 bytes     : FLAG_GROUP_ADDRESS_TYPE_THREEBYTES
     * g | has several chars ?         1 bit, 1 = yes, 0 = no   : FLAG_HAS_MULTIPLE_CHARS
     * s | has a terminal ?            1 bit, 1 = yes, 0 = no   : FLAG_IS_TERMINAL
     *   | reserved                    1 bit, 1 = yes, 0 = no
     *   | has bigrams ?               1 bit, 1 = yes, 0 = no   : FLAG_HAS_BIGRAMS
     *
     * c | IF FLAG_HAS_MULTIPLE_CHARS
     * h |   char, char, char, char    n * (1 or 3 bytes) : use CharGroupInfo for i/o helpers
     * a |   end                       1 byte, = 0
     * r | ELSE
     * s |   char                      1 or 3 bytes
     *   | END
     *
     * f |
     * r | IF FLAG_IS_TERMINAL
     * e |   frequency                 1 byte
     * q |
     *
     * c | IF 00 = FLAG_GROUP_ADDRESS_TYPE_NOADDRESS = addressType
     * h |   // nothing
     * i | ELSIF 01 = FLAG_GROUP_ADDRESS_TYPE_ONEBYTE == addressType
     * l |   children address, 1 byte
     * d | ELSIF 10 = FLAG_GROUP_ADDRESS_TYPE_TWOBYTES == addressType
     * r |   children address, 2 bytes
     * e | ELSE // 11 = FLAG_GROUP_ADDRESS_TYPE_THREEBYTES = addressType
     * n |   children address, 3 bytes
     * A | END
     * d
     * dress
     *
     *   | IF FLAG_IS_TERMINAL && FLAG_HAS_BIGRAMS
     *   | bigrams address list
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
     * bigram and shortcut address list is:
     * <flags> = | hasNext = 1 bit, 1 = yes, 0 = no     : FLAG_ATTRIBUTE_HAS_NEXT
     *           | addressSign = 1 bit,                 : FLAG_ATTRIBUTE_OFFSET_NEGATIVE
     *           |                      1 = must take -address, 0 = must take +address
     *           |                         xx : mask with MASK_ATTRIBUTE_ADDRESS_TYPE
     *           | addressFormat = 2 bits, 00 = unused  : FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE
     *           |                         01 = 1 byte  : FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE
     *           |                         10 = 2 bytes : FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES
     *           |                         11 = 3 bytes : FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES
     *           | 4 bits : frequency         : mask with FLAG_ATTRIBUTE_FREQUENCY
     * <address> | IF (01 == FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE == addressFormat)
     *           |   read 1 byte, add top 4 bits
     *           | ELSIF (10 == FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES == addressFormat)
     *           |   read 2 bytes, add top 4 bits
     *           | ELSE // 11 == FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES == addressFormat
     *           |   read 3 bytes, add top 4 bits
     *           | END
     *           | if (FLAG_ATTRIBUTE_OFFSET_NEGATIVE) then address = -address
     * if (FLAG_ATTRIBUTE_HAS_NET) goto bigram_and_shortcut_address_list_is
     *
     */

    private static final int MAGIC_NUMBER = 0x78B1;
    private static final int VERSION = 1;
    private static final int MAXIMUM_SUPPORTED_VERSION = VERSION;
    // No options yet, reserved for future use.
    private static final int OPTIONS = 0;

    // TODO: Make this value adaptative to content data, store it in the header, and
    // use it in the reading code.
    private static final int MAX_WORD_LENGTH = 48;

    private static final int MASK_GROUP_ADDRESS_TYPE = 0xC0;
    private static final int FLAG_GROUP_ADDRESS_TYPE_NOADDRESS = 0x00;
    private static final int FLAG_GROUP_ADDRESS_TYPE_ONEBYTE = 0x40;
    private static final int FLAG_GROUP_ADDRESS_TYPE_TWOBYTES = 0x80;
    private static final int FLAG_GROUP_ADDRESS_TYPE_THREEBYTES = 0xC0;

    private static final int FLAG_HAS_MULTIPLE_CHARS = 0x20;

    private static final int FLAG_IS_TERMINAL = 0x10;
    private static final int FLAG_HAS_BIGRAMS = 0x04;

    private static final int FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
    private static final int FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;
    private static final int MASK_ATTRIBUTE_ADDRESS_TYPE = 0x30;
    private static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
    private static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
    private static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;
    private static final int FLAG_ATTRIBUTE_FREQUENCY = 0x0F;

    private static final int GROUP_CHARACTERS_TERMINATOR = 0x1F;

    private static final int GROUP_COUNT_SIZE = 1;
    private static final int GROUP_TERMINATOR_SIZE = 1;
    private static final int GROUP_FLAGS_SIZE = 1;
    private static final int GROUP_FREQUENCY_SIZE = 1;
    private static final int GROUP_MAX_ADDRESS_SIZE = 3;
    private static final int GROUP_ATTRIBUTE_FLAGS_SIZE = 1;
    private static final int GROUP_ATTRIBUTE_MAX_ADDRESS_SIZE = 3;

    private static final int NO_CHILDREN_ADDRESS = Integer.MIN_VALUE;
    private static final int INVALID_CHARACTER = -1;

    // Limiting to 127 for upward compatibility
    // TODO: implement a scheme to be able to shoot 256 chargroups in a node
    private static final int MAX_CHARGROUPS_IN_A_NODE = 127;

    private static final int MAX_TERMINAL_FREQUENCY = 255;

    /**
     * A class grouping utility function for our specific character encoding.
     */
    private static class CharEncoding {

        private static final int MINIMAL_ONE_BYTE_CHARACTER_VALUE = 0x20;
        private static final int MAXIMAL_ONE_BYTE_CHARACTER_VALUE = 0xFF;

        /**
         * Helper method to find out whether this code fits on one byte
         */
        private static boolean fitsOnOneByte(int character) {
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
        private static int getCharSize(int character) {
            // See char encoding in FusionDictionary.java
            if (fitsOnOneByte(character)) return 1;
            if (INVALID_CHARACTER == character) return 1;
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
         * @param characters the character array to write.
         * @param buffer the byte buffer to write to.
         * @param index the index in buffer to write the character array to.
         * @return the index after the last character.
         */
        private static int writeCharArray(int[] characters, byte[] buffer, int index) {
            for (int character : characters) {
                if (1 == getCharSize(character)) {
                    buffer[index++] = (byte)character;
                } else {
                    buffer[index++] = (byte)(0xFF & (character >> 16));
                    buffer[index++] = (byte)(0xFF & (character >> 8));
                    buffer[index++] = (byte)(0xFF & character);
                }
            }
            return index;
        }

        /**
         * Reads a character from the file.
         *
         * This follows the character format documented earlier in this source file.
         *
         * @param source the file, positioned over an encoded character.
         * @return the character code.
         */
        private static int readChar(RandomAccessFile source) throws IOException {
            int character = source.readUnsignedByte();
            if (!fitsOnOneByte(character)) {
                if (GROUP_CHARACTERS_TERMINATOR == character)
                    return INVALID_CHARACTER;
                character <<= 16;
                character += source.readUnsignedShort();
            }
            return character;
        }
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
    private static int getGroupCharactersSize(CharGroup group) {
        int size = CharEncoding.getCharArraySize(group.mChars);
        if (group.hasSeveralChars()) size += GROUP_TERMINATOR_SIZE;
        return size;
    }

    /**
     * Compute the maximum size of a CharGroup, assuming 3-byte addresses for everything.
     *
     * @param group the CharGroup to compute the size of.
     * @return the maximum size of the group.
     */
    private static int getCharGroupMaximumSize(CharGroup group) {
        int size = getGroupCharactersSize(group) + GROUP_FLAGS_SIZE;
        // If terminal, one byte for the frequency
        if (group.isTerminal()) size += GROUP_FREQUENCY_SIZE;
        size += GROUP_MAX_ADDRESS_SIZE; // For children address
        if (null != group.mBigrams) {
            for (WeightedString bigram : group.mBigrams) {
                size += GROUP_ATTRIBUTE_FLAGS_SIZE + GROUP_ATTRIBUTE_MAX_ADDRESS_SIZE;
            }
        }
        return size;
    }

    /**
     * Compute the maximum size of a node, assuming 3-byte addresses for everything, and caches
     * it in the 'actualSize' member of the node.
     *
     * @param node the node to compute the maximum size of.
     */
    private static void setNodeMaximumSize(Node node) {
        int size = GROUP_COUNT_SIZE;
        for (CharGroup g : node.mData) {
            final int groupSize = getCharGroupMaximumSize(g);
            g.mCachedSize = groupSize;
            size += groupSize;
        }
        node.mCachedSize = size;
    }

    /**
     * Helper method to hide the actual value of the no children address.
     */
    private static boolean hasChildrenAddress(int address) {
        return NO_CHILDREN_ADDRESS != address;
    }

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
    private static int getByteSize(int address) {
        assert(address < 0x1000000);
        if (!hasChildrenAddress(address)) {
            return 0;
        } else if (Math.abs(address) < 0x100) {
            return 1;
        } else if (Math.abs(address) < 0x10000) {
            return 2;
        } else {
            return 3;
        }
    }
    // End utility methods.

    // This method is responsible for finding a nice ordering of the nodes that favors run-time
    // cache performance and dictionary size.
    /* package for tests */ static ArrayList<Node> flattenTree(Node root) {
        final int treeSize = FusionDictionary.countCharGroups(root);
        MakedictLog.i("Counted nodes : " + treeSize);
        final ArrayList<Node> flatTree = new ArrayList<Node>(treeSize);
        return flattenTreeInner(flatTree, root);
    }

    private static ArrayList<Node> flattenTreeInner(ArrayList<Node> list, Node node) {
        // Removing the node is necessary if the tails are merged, because we would then
        // add the same node several times when we only want it once. A number of places in
        // the code also depends on any node being only once in the list.
        // Merging tails can only be done if there are no attributes. Searching for attributes
        // in LatinIME code depends on a total breadth-first ordering, which merging tails
        // breaks. If there are no attributes, it should be fine (and reduce the file size)
        // to merge tails, and the following step would be necessary.
        // If eventually the code runs on Android, searching through the whole array each time
        // may be a performance concern.
        list.remove(node);
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
     * address depends on its numeric value.
     *
     * @param node the node to compute the size of.
     * @param dict the dictionary in which the word/attributes are to be found.
     */
    private static void computeActualNodeSize(Node node, FusionDictionary dict) {
        int size = GROUP_COUNT_SIZE;
        for (CharGroup group : node.mData) {
            int groupSize = GROUP_FLAGS_SIZE + getGroupCharactersSize(group);
            if (group.isTerminal()) groupSize += GROUP_FREQUENCY_SIZE;
            if (null != group.mChildren) {
                final int offsetBasePoint= groupSize + node.mCachedAddress + size;
                final int offset = group.mChildren.mCachedAddress - offsetBasePoint;
                groupSize += getByteSize(offset);
            }
            if (null != group.mBigrams) {
                for (WeightedString bigram : group.mBigrams) {
                    final int offsetBasePoint = groupSize + node.mCachedAddress + size
                            + GROUP_FLAGS_SIZE;
                    final int addressOfBigram = findAddressOfWord(dict, bigram.mWord);
                    final int offset = addressOfBigram - offsetBasePoint;
                    groupSize += getByteSize(offset) + GROUP_FLAGS_SIZE;
                }
            }
            group.mCachedSize = groupSize;
            size += groupSize;
        }
        node.mCachedSize = size;
    }

    /**
     * Computes the byte size of a list of nodes and updates each node cached position.
     *
     * @param flatNodes the array of nodes.
     * @return the byte size of the entire stack.
     */
    private static int stackNodes(ArrayList<Node> flatNodes) {
        int nodeOffset = 0;
        for (Node n : flatNodes) {
            n.mCachedAddress = nodeOffset;
            int groupOffset = 0;
            for (CharGroup g : n.mData) {
                g.mCachedAddress = GROUP_COUNT_SIZE + nodeOffset + groupOffset;
                groupOffset += g.mCachedSize;
            }
            if (groupOffset + GROUP_COUNT_SIZE != n.mCachedSize) {
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
     * @return the same array it was passed. The nodes have been updated for address and size.
     */
    private static ArrayList<Node> computeAddresses(FusionDictionary dict,
            ArrayList<Node> flatNodes) {
        // First get the worst sizes and offsets
        for (Node n : flatNodes) setNodeMaximumSize(n);
        final int offset = stackNodes(flatNodes);

        MakedictLog.i("Compressing the array addresses. Original size : " + offset);
        MakedictLog.i("(Recursively seen size : " + offset + ")");

        int passes = 0;
        boolean changesDone = false;
        do {
            changesDone = false;
            for (Node n : flatNodes) {
                final int oldNodeSize = n.mCachedSize;
                computeActualNodeSize(n, dict);
                final int newNodeSize = n.mCachedSize;
                if (oldNodeSize < newNodeSize) throw new RuntimeException("Increased size ?!");
                if (oldNodeSize != newNodeSize) changesDone = true;
            }
            stackNodes(flatNodes);
            ++passes;
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
    private static void checkFlatNodeArray(ArrayList<Node> array) {
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
    private static int writeVariableAddress(byte[] buffer, int index, int address) {
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

    private static byte makeCharGroupFlags(final CharGroup group, final int groupAddress,
            final int childrenOffset) {
        byte flags = 0;
        if (group.mChars.length > 1) flags |= FLAG_HAS_MULTIPLE_CHARS;
        if (group.mFrequency >= 0) {
            flags |= FLAG_IS_TERMINAL;
        }
        if (null != group.mChildren) {
            switch (getByteSize(childrenOffset)) {
             case 1:
                 flags |= FLAG_GROUP_ADDRESS_TYPE_ONEBYTE;
                 break;
             case 2:
                 flags |= FLAG_GROUP_ADDRESS_TYPE_TWOBYTES;
                 break;
             case 3:
                 flags |= FLAG_GROUP_ADDRESS_TYPE_THREEBYTES;
                 break;
             default:
                 throw new RuntimeException("Node with a strange address");
             }
        }
        if (null != group.mBigrams) flags |= FLAG_HAS_BIGRAMS;
        return flags;
    }

    /**
     * Makes the flag value for an attribute.
     *
     * @param more whether there are more attributes after this one.
     * @param offset the offset of the attribute.
     * @param frequency the frequency of the attribute, 0..15
     * @return the flags
     */
    private static final int makeAttributeFlags(final boolean more, final int offset,
            final int frequency) {
        int bigramFlags = (more ? FLAG_ATTRIBUTE_HAS_NEXT : 0)
                + (offset < 0 ? FLAG_ATTRIBUTE_OFFSET_NEGATIVE : 0);
        switch (getByteSize(offset)) {
        case 1:
            bigramFlags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
            break;
        case 2:
            bigramFlags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
            break;
        case 3:
            bigramFlags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
            break;
        default:
            throw new RuntimeException("Strange offset size");
        }
        bigramFlags += frequency & FLAG_ATTRIBUTE_FREQUENCY;
        return bigramFlags;
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
     * @return the address of the END of the node.
     */
    private static int writePlacedNode(FusionDictionary dict, byte[] buffer, Node node) {
        int index = node.mCachedAddress;

        final int size = node.mData.size();
        if (size > MAX_CHARGROUPS_IN_A_NODE)
            throw new RuntimeException("A node has a group count over 127 (" + size + ").");

        buffer[index++] = (byte)size;
        int groupAddress = index;
        for (int i = 0; i < size; ++i) {
            CharGroup group = node.mData.get(i);
            if (index != group.mCachedAddress) throw new RuntimeException("Bug: write index is not "
                    + "the same as the cached address of the group");
            groupAddress += GROUP_FLAGS_SIZE + getGroupCharactersSize(group);
            // Sanity checks.
            if (group.mFrequency > MAX_TERMINAL_FREQUENCY) {
                throw new RuntimeException("A node has a frequency > " + MAX_TERMINAL_FREQUENCY
                        + " : " + group.mFrequency);
            }
            if (group.mFrequency >= 0) groupAddress += GROUP_FREQUENCY_SIZE;
            final int childrenOffset = null == group.mChildren
                    ? NO_CHILDREN_ADDRESS : group.mChildren.mCachedAddress - groupAddress;
            byte flags = makeCharGroupFlags(group, groupAddress, childrenOffset);
            buffer[index++] = flags;
            index = CharEncoding.writeCharArray(group.mChars, buffer, index);
            if (group.hasSeveralChars()) {
                buffer[index++] = GROUP_CHARACTERS_TERMINATOR;
            }
            if (group.mFrequency >= 0) {
                buffer[index++] = (byte) group.mFrequency;
            }
            final int shift = writeVariableAddress(buffer, index, childrenOffset);
            index += shift;
            groupAddress += shift;

            // Write bigrams
            if (null != group.mBigrams) {
                int remainingBigrams = group.mBigrams.size();
                for (WeightedString bigram : group.mBigrams) {
                    boolean more = remainingBigrams > 1;
                    final int addressOfBigram = findAddressOfWord(dict, bigram.mWord);
                    ++groupAddress;
                    final int offset = addressOfBigram - groupAddress;
                    int bigramFlags = makeAttributeFlags(more, offset, bigram.mFrequency);
                    buffer[index++] = (byte)bigramFlags;
                    final int bigramShift = writeVariableAddress(buffer, index, Math.abs(offset));
                    index += bigramShift;
                    groupAddress += bigramShift;
                    --remainingBigrams;
                }
            }

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
     */
    public static void writeDictionaryBinary(OutputStream destination, FusionDictionary dict)
            throws IOException {

        // Addresses are limited to 3 bytes, so we'll just make a 16MB buffer. Since addresses
        // can be relative to each node, the structure itself is not limited to 16MB at all, but
        // I doubt this will ever be shot. If it is, deciding the order of the nodes becomes
        // a quite complicated problem, because though the dictionary itself does not have a
        // size limit, each node must still be within 16MB of all its children and parents.
        // As long as this is ensured, the dictionary file may grow to any size.
        // Anyway, to make a dictionary bigger than 16MB just increase the size of this buffer.
        final byte[] buffer = new byte[1 << 24];
        int index = 0;

        // Magic number in big-endian order.
        buffer[index++] = (byte) (0xFF & (MAGIC_NUMBER >> 8));
        buffer[index++] = (byte) (0xFF & MAGIC_NUMBER);
        // Dictionary version.
        buffer[index++] = (byte) (0xFF & VERSION);
        // Options flags
        buffer[index++] = (byte) (0xFF & (OPTIONS >> 8));
        buffer[index++] = (byte) (0xFF & OPTIONS);

        // Should we include the locale and title of the dictionary ?

        destination.write(buffer, 0, index);
        index = 0;

        // Leave the choice of the optimal node order to the flattenTree function.
        MakedictLog.i("Flattening the tree...");
        ArrayList<Node> flatNodes = flattenTree(dict.mRoot);

        MakedictLog.i("Computing addresses...");
        computeAddresses(dict, flatNodes);
        MakedictLog.i("Checking array...");
        checkFlatNodeArray(flatNodes);

        MakedictLog.i("Writing file...");
        int dataEndOffset = 0;
        for (Node n : flatNodes) {
            dataEndOffset = writePlacedNode(dict, buffer, n);
        }

        showStatistics(flatNodes);

        destination.write(buffer, 0, dataEndOffset);

        destination.close();
        MakedictLog.i("Done");
    }


    // Input methods: Read a binary dictionary to memory.
    // readDictionaryBinary is the public entry point for them.

    static final int[] characterBuffer = new int[MAX_WORD_LENGTH];
    private static CharGroupInfo readCharGroup(RandomAccessFile source,
            final int originalGroupAddress) throws IOException {
        int addressPointer = originalGroupAddress;
        final int flags = source.readUnsignedByte();
        ++addressPointer;
        final int characters[];
        if (0 != (flags & FLAG_HAS_MULTIPLE_CHARS)) {
            int index = 0;
            int character = CharEncoding.readChar(source);
            addressPointer += CharEncoding.getCharSize(character);
            while (-1 != character) {
                characterBuffer[index++] = character;
                character = CharEncoding.readChar(source);
                addressPointer += CharEncoding.getCharSize(character);
            }
            characters = Arrays.copyOfRange(characterBuffer, 0, index);
        } else {
            final int character = CharEncoding.readChar(source);
            addressPointer += CharEncoding.getCharSize(character);
            characters = new int[] { character };
        }
        final int frequency;
        if (0 != (FLAG_IS_TERMINAL & flags)) {
            ++addressPointer;
            frequency = source.readUnsignedByte();
        } else {
            frequency = CharGroup.NOT_A_TERMINAL;
        }
        int childrenAddress = addressPointer;
        switch (flags & MASK_GROUP_ADDRESS_TYPE) {
        case FLAG_GROUP_ADDRESS_TYPE_ONEBYTE:
            childrenAddress += source.readUnsignedByte();
            addressPointer += 1;
            break;
        case FLAG_GROUP_ADDRESS_TYPE_TWOBYTES:
            childrenAddress += source.readUnsignedShort();
            addressPointer += 2;
            break;
        case FLAG_GROUP_ADDRESS_TYPE_THREEBYTES:
            childrenAddress += (source.readUnsignedByte() << 16) + source.readUnsignedShort();
            addressPointer += 3;
            break;
        case FLAG_GROUP_ADDRESS_TYPE_NOADDRESS:
        default:
            childrenAddress = NO_CHILDREN_ADDRESS;
            break;
        }
        ArrayList<PendingAttribute> bigrams = null;
        if (0 != (flags & FLAG_HAS_BIGRAMS)) {
            bigrams = new ArrayList<PendingAttribute>();
            boolean more = true;
            while (more) {
                int bigramFlags = source.readUnsignedByte();
                ++addressPointer;
                more = (0 != (bigramFlags & FLAG_ATTRIBUTE_HAS_NEXT));
                final int sign = 0 == (bigramFlags & FLAG_ATTRIBUTE_OFFSET_NEGATIVE) ? 1 : -1;
                int bigramAddress = addressPointer;
                switch (bigramFlags & MASK_ATTRIBUTE_ADDRESS_TYPE) {
                case FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE:
                    bigramAddress += sign * source.readUnsignedByte();
                    addressPointer += 1;
                    break;
                case FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES:
                    bigramAddress += sign * source.readUnsignedShort();
                    addressPointer += 2;
                    break;
                case FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES:
                    final int offset = ((source.readUnsignedByte() << 16)
                            + source.readUnsignedShort());
                    bigramAddress += sign * offset;
                    addressPointer += 3;
                    break;
                default:
                    throw new RuntimeException("Has attribute with no address");
                }
                bigrams.add(new PendingAttribute(bigramFlags & FLAG_ATTRIBUTE_FREQUENCY,
                        bigramAddress));
            }
        }
        return new CharGroupInfo(originalGroupAddress, addressPointer, flags, characters, frequency,
                childrenAddress, bigrams);
    }

    /**
     * Finds, as a string, the word at the address passed as an argument.
     *
     * @param source the file to read from.
     * @param headerSize the size of the header.
     * @param address the address to seek.
     * @return the word, as a string.
     * @throws IOException if the file can't be read.
     */
    private static String getWordAtAddress(RandomAccessFile source, long headerSize,
            int address) throws IOException {
        final long originalPointer = source.getFilePointer();
        source.seek(headerSize);
        final int count = source.readUnsignedByte();
        int groupOffset = 1; // 1 for the group count
        final StringBuilder builder = new StringBuilder();
        String result = null;

        CharGroupInfo last = null;
        for (int i = count - 1; i >= 0; --i) {
            CharGroupInfo info = readCharGroup(source, groupOffset);
            groupOffset = info.mEndAddress;
            if (info.mOriginalAddress == address) {
                builder.append(new String(info.mCharacters, 0, info.mCharacters.length));
                result = builder.toString();
                break; // and return
            }
            if (hasChildrenAddress(info.mChildrenAddress)) {
                if (info.mChildrenAddress > address) {
                    if (null == last) continue;
                    builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                    source.seek(last.mChildrenAddress + headerSize);
                    groupOffset = last.mChildrenAddress + 1;
                    i = source.readUnsignedByte();
                    last = null;
                    continue;
                }
                last = info;
            }
            if (0 == i && hasChildrenAddress(last.mChildrenAddress)) {
                builder.append(new String(last.mCharacters, 0, last.mCharacters.length));
                source.seek(last.mChildrenAddress + headerSize);
                groupOffset = last.mChildrenAddress + 1;
                i = source.readUnsignedByte();
                last = null;
                continue;
            }
        }
        source.seek(originalPointer);
        return result;
    }

    /**
     * Reads a single node from a binary file.
     *
     * This methods reads the file at the current position of its file pointer. A node is
     * fully expected to start at the current position.
     * This will recursively read other nodes into the structure, populating the reverse
     * maps on the fly and using them to keep track of already read nodes.
     *
     * @param source the data file, correctly positioned at the start of a node.
     * @param headerSize the size, in bytes, of the file header.
     * @param reverseNodeMap a mapping from addresses to already read nodes.
     * @param reverseGroupMap a mapping from addresses to already read character groups.
     * @return the read node with all his children already read.
     */
    private static Node readNode(RandomAccessFile source, long headerSize,
            Map<Integer, Node> reverseNodeMap, Map<Integer, CharGroup> reverseGroupMap)
            throws IOException {
        final int nodeOrigin = (int)(source.getFilePointer() - headerSize);
        final int count = source.readUnsignedByte();
        final ArrayList<CharGroup> nodeContents = new ArrayList<CharGroup>();
        int groupOffset = nodeOrigin + 1; // 1 byte for the group count
        for (int i = count; i > 0; --i) {
            CharGroupInfo info = readCharGroup(source, groupOffset);
            ArrayList<WeightedString> bigrams = null;
            if (null != info.mBigrams) {
                bigrams = new ArrayList<WeightedString>();
                for (PendingAttribute bigram : info.mBigrams) {
                    final String word = getWordAtAddress(source, headerSize, bigram.mAddress);
                    bigrams.add(new WeightedString(word, bigram.mFrequency));
                }
            }
            if (hasChildrenAddress(info.mChildrenAddress)) {
                Node children = reverseNodeMap.get(info.mChildrenAddress);
                if (null == children) {
                    final long currentPosition = source.getFilePointer();
                    source.seek(info.mChildrenAddress + headerSize);
                    children = readNode(source, headerSize, reverseNodeMap, reverseGroupMap);
                    source.seek(currentPosition);
                }
                nodeContents.add(
                        new CharGroup(info.mCharacters, bigrams, info.mFrequency,
                        children));
            } else {
                nodeContents.add(
                        new CharGroup(info.mCharacters, bigrams, info.mFrequency));
            }
            groupOffset = info.mEndAddress;
        }
        final Node node = new Node(nodeContents);
        node.mCachedAddress = nodeOrigin;
        reverseNodeMap.put(node.mCachedAddress, node);
        return node;
    }

    /**
     * Reads a random access file and returns the memory representation of the dictionary.
     *
     * This high-level method takes a binary file and reads its contents, populating a
     * FusionDictionary structure. The optional dict argument is an existing dictionary to
     * which words from the file should be added. If it is null, a new dictionary is created.
     *
     * @param source the file to read.
     * @param dict an optional dictionary to add words to, or null.
     * @return the created (or merged) dictionary.
     */
    public static FusionDictionary readDictionaryBinary(RandomAccessFile source,
            FusionDictionary dict) throws IOException, UnsupportedFormatException {
        // Check magic number
        final int magic = source.readUnsignedShort();
        if (MAGIC_NUMBER != magic) {
            throw new UnsupportedFormatException("The magic number in this file does not match "
                    + "the expected value");
        }

        // Check file version
        final int version = source.readUnsignedByte();
        if (version > MAXIMUM_SUPPORTED_VERSION) {
            throw new UnsupportedFormatException("This file has version " + version
                    + ", but this implementation does not support versions above "
                    + MAXIMUM_SUPPORTED_VERSION);
        }

        // Read options
        source.readUnsignedShort();

        long headerSize = source.getFilePointer();
        Map<Integer, Node> reverseNodeMapping = new TreeMap<Integer, Node>();
        Map<Integer, CharGroup> reverseGroupMapping = new TreeMap<Integer, CharGroup>();
        final Node root = readNode(source, headerSize, reverseNodeMapping, reverseGroupMapping);

        FusionDictionary newDict = new FusionDictionary(root,
                new FusionDictionary.DictionaryOptions());
        if (null != dict) {
            for (Word w : dict) {
                newDict.add(w.mWord, w.mFrequency, w.mBigrams);
            }
        }

        return newDict;
    }

    /**
     * Basic test to find out whether the file is a binary dictionary or not.
     *
     * Concretely this only tests the magic number.
     *
     * @param filename The name of the file to test.
     * @return true if it's a binary dictionary, false otherwise
     */
    public static boolean isBinaryDictionary(String filename) {
        try {
            RandomAccessFile f = new RandomAccessFile(filename, "r");
            return MAGIC_NUMBER == f.readUnsignedShort();
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
