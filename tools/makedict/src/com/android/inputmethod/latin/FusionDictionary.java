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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A dictionary that can fusion heads and tails of words for more compression.
 */
public class FusionDictionary implements Iterable<Word> {

    /**
     * A node of the dictionary, containing several CharGroups.
     *
     * A node is but an ordered array of CharGroups, which essentially contain all the
     * real information.
     * This class also contains fields to cache size and address, to help with binary
     * generation.
     */
    public static class Node {
        ArrayList<CharGroup> mData;
        // To help with binary generation
        int mCachedSize;
        int mCachedAddress;
        public Node() {
            mData = new ArrayList<CharGroup>();
            mCachedSize = Integer.MIN_VALUE;
            mCachedAddress = Integer.MIN_VALUE;
        }
        public Node(ArrayList<CharGroup> data) {
            mData = data;
            mCachedSize = Integer.MIN_VALUE;
            mCachedAddress = Integer.MIN_VALUE;
        }
    }

    /**
     * A string with a frequency.
     *
     * This represents an "attribute", that is either a bigram or a shortcut.
     */
    public static class WeightedString {
        final String mWord;
        final int mFrequency;
        public WeightedString(String word, int frequency) {
            mWord = word;
            mFrequency = frequency;
        }
    }

    /**
     * A group of characters, with a frequency, shortcuts, bigrams, and children.
     *
     * This is the central class of the in-memory representation. A CharGroup is what can
     * be seen as a traditional "trie node", except it can hold several characters at the
     * same time. A CharGroup essentially represents one or several characters in the middle
     * of the trie trie; as such, it can be a terminal, and it can have children.
     * In this in-memory representation, whether the CharGroup is a terminal or not is represented
     * in the frequency, where NOT_A_TERMINAL (= -1) means this is not a terminal and any other
     * value is the frequency of this terminal. A terminal may have non-null shortcuts and/or
     * bigrams, but a non-terminal may not. Moreover, children, if present, are null.
     */
    public static class CharGroup {
        public static final int NOT_A_TERMINAL = -1;
        final int mChars[];
        final ArrayList<WeightedString> mBigrams;
        final int mFrequency; // NOT_A_TERMINAL == mFrequency indicates this is not a terminal.
        Node mChildren;
        // The two following members to help with binary generation
        int mCachedSize;
        int mCachedAddress;

        public CharGroup(final int[] chars,
                final ArrayList<WeightedString> bigrams, final int frequency) {
            mChars = chars;
            mFrequency = frequency;
            mBigrams = bigrams;
            mChildren = null;
        }

        public CharGroup(final int[] chars,
                final ArrayList<WeightedString> bigrams, final int frequency, final Node children) {
            mChars = chars;
            mFrequency = frequency;
            mBigrams = bigrams;
            mChildren = children;
        }

        public void addChild(CharGroup n) {
            if (null == mChildren) {
                mChildren = new Node();
            }
            mChildren.mData.add(n);
        }

        public boolean isTerminal() {
            return NOT_A_TERMINAL != mFrequency;
        }

        public boolean hasSeveralChars() {
            assert(mChars.length > 0);
            return 1 < mChars.length;
        }
    }

    /**
     * Options global to the dictionary.
     *
     * There are no options at the moment, so this class is empty.
     */
    public static class DictionaryOptions {
    }


    public final DictionaryOptions mOptions;
    public final Node mRoot;

    public FusionDictionary() {
        mOptions = new DictionaryOptions();
        mRoot = new Node();
    }

    public FusionDictionary(final Node root, final DictionaryOptions options) {
        mRoot = root;
        mOptions = options;
    }

    /**
     * Helper method to convert a String to an int array.
     */
    static private int[] getCodePoints(String word) {
        final int wordLength = word.length();
        int[] array = new int[word.codePointCount(0, wordLength)];
        for (int i = 0; i < wordLength; ++i) {
            array[i] = word.codePointAt(i);
        }
        return array;
    }

    /**
     * Helper method to add a word as a string.
     *
     * This method adds a word to the dictionary with the given frequency. Optional
     * lists of bigrams and shortcuts can be passed here. For each word inside,
     * they will be added to the dictionary as necessary.
     *
     * @param word the word to add.
     * @param frequency the frequency of the word, in the range [0..255].
     * @param bigrams a list of bigrams, or null.
     */
    public void add(String word, int frequency, ArrayList<WeightedString> bigrams) {
        if (null != bigrams) {
            for (WeightedString bigram : bigrams) {
                final CharGroup t = findWordInTree(mRoot, bigram.mWord);
                if (null == t) {
                    add(getCodePoints(bigram.mWord), 0, null);
                }
            }
        }
        add(getCodePoints(word), frequency, bigrams);
    }

    /**
     * Sanity check for a node.
     *
     * This method checks that all CharGroups in a node are ordered as expected.
     * If they are, nothing happens. If they aren't, an exception is thrown.
     */
    private void checkStack(Node node) {
        ArrayList<CharGroup> stack = node.mData;
        int lastValue = -1;
        for (int i = 0; i < stack.size(); ++i) {
            int currentValue = stack.get(i).mChars[0];
            if (currentValue <= lastValue)
                throw new RuntimeException("Invalid stack");
            else
                lastValue = currentValue;
        }
    }

    /**
     * Add a word to this dictionary.
     *
     * The bigrams, if any, have to be in the dictionary already. If they aren't,
     * an exception is thrown.
     *
     * @param word the word, as an int array.
     * @param frequency the frequency of the word, in the range [0..255].
     * @param bigrams an optional list of bigrams for this word (null if none).
     */
    private void add(int[] word, int frequency, ArrayList<WeightedString> bigrams) {
        assert(frequency >= 0 && frequency <= 255);
        Node currentNode = mRoot;
        int charIndex = 0;

        CharGroup currentGroup = null;
        int differentCharIndex = 0; // Set by the loop to the index of the char that differs
        int nodeIndex = findIndexOfChar(mRoot, word[charIndex]);
        while (CHARACTER_NOT_FOUND != nodeIndex) {
            currentGroup = currentNode.mData.get(nodeIndex);
            differentCharIndex = compareArrays(currentGroup.mChars, word, charIndex);
            if (ARRAYS_ARE_EQUAL != differentCharIndex
                    && differentCharIndex < currentGroup.mChars.length) break;
            if (null == currentGroup.mChildren) break;
            charIndex += currentGroup.mChars.length;
            if (charIndex >= word.length) break;
            currentNode = currentGroup.mChildren;
            nodeIndex = findIndexOfChar(currentNode, word[charIndex]);
        }

        if (-1 == nodeIndex) {
            // No node at this point to accept the word. Create one.
            final int insertionIndex = findInsertionIndex(currentNode, word[charIndex]);
            final CharGroup newGroup = new CharGroup(
                    Arrays.copyOfRange(word, charIndex, word.length), bigrams, frequency);
            currentNode.mData.add(insertionIndex, newGroup);
            checkStack(currentNode);
        } else {
            // There is a word with a common prefix.
            if (differentCharIndex == currentGroup.mChars.length) {
                if (charIndex + differentCharIndex >= word.length) {
                    // The new word is a prefix of an existing word, but the node on which it
                    // should end already exists as is.
                    if (currentGroup.mFrequency > 0) {
                        throw new RuntimeException("Such a word already exists in the dictionary : "
                                + new String(word, 0, word.length));
                    } else {
                        final CharGroup newNode = new CharGroup(currentGroup.mChars,
                                bigrams, frequency, currentGroup.mChildren);
                        currentNode.mData.set(nodeIndex, newNode);
                        checkStack(currentNode);
                    }
                } else {
                    // The new word matches the full old word and extends past it.
                    // We only have to create a new node and add it to the end of this.
                    final CharGroup newNode = new CharGroup(
                            Arrays.copyOfRange(word, charIndex + differentCharIndex, word.length),
                                    bigrams, frequency);
                    currentGroup.mChildren = new Node();
                    currentGroup.mChildren.mData.add(newNode);
                }
            } else {
                if (0 == differentCharIndex) {
                    // Exact same word. Check the frequency is 0 or -1, and update.
                    if (0 != frequency) {
                        if (0 < currentGroup.mFrequency) {
                            throw new RuntimeException("This word already exists with frequency "
                                    + currentGroup.mFrequency + " : "
                                    + new String(word, 0, word.length));
                        }
                        final CharGroup newGroup = new CharGroup(word,
                                currentGroup.mBigrams, frequency, currentGroup.mChildren);
                        currentNode.mData.set(nodeIndex, newGroup);
                    }
                } else {
                    // Partial prefix match only. We have to replace the current node with a node
                    // containing the current prefix and create two new ones for the tails.
                    Node newChildren = new Node();
                    final CharGroup newOldWord = new CharGroup(
                            Arrays.copyOfRange(currentGroup.mChars, differentCharIndex,
                                    currentGroup.mChars.length),
                            currentGroup.mBigrams, currentGroup.mFrequency, currentGroup.mChildren);
                    newChildren.mData.add(newOldWord);

                    final CharGroup newParent;
                    if (charIndex + differentCharIndex >= word.length) {
                        newParent = new CharGroup(
                                Arrays.copyOfRange(currentGroup.mChars, 0, differentCharIndex),
                                        bigrams, frequency, newChildren);
                    } else {
                        newParent = new CharGroup(
                                Arrays.copyOfRange(currentGroup.mChars, 0, differentCharIndex),
                                        null, -1, newChildren);
                        final CharGroup newWord = new CharGroup(
                                Arrays.copyOfRange(word, charIndex + differentCharIndex,
                                        word.length), bigrams, frequency);
                        final int addIndex = word[charIndex + differentCharIndex]
                                > currentGroup.mChars[differentCharIndex] ? 1 : 0;
                        newChildren.mData.add(addIndex, newWord);
                    }
                    currentNode.mData.set(nodeIndex, newParent);
                }
                checkStack(currentNode);
            }
        }
    }

    /**
     * Custom comparison of two int arrays taken to contain character codes.
     *
     * This method compares the two arrays passed as an argument in a lexicographic way,
     * with an offset in the dst string.
     * This method does NOT test for the first character. It is taken to be equal.
     * I repeat: this method starts the comparison at 1 <> dstOffset + 1.
     * The index where the strings differ is returned. ARRAYS_ARE_EQUAL = 0 is returned if the
     * strings are equal. This works BECAUSE we don't look at the first character.
     *
     * @param src the left-hand side string of the comparison.
     * @param dst the right-hand side string of the comparison.
     * @param dstOffset the offset in the right-hand side string.
     * @return the index at which the strings differ, or ARRAYS_ARE_EQUAL = 0 if they don't.
     */
    private static int ARRAYS_ARE_EQUAL = 0;
    private static int compareArrays(final int[] src, final int[] dst, int dstOffset) {
        // We do NOT test the first char, because we come from a method that already
        // tested it.
        for (int i = 1; i < src.length; ++i) {
            if (dstOffset + i >= dst.length) return i;
            if (src[i] != dst[dstOffset + i]) return i;
        }
        if (dst.length > src.length) return src.length;
        return ARRAYS_ARE_EQUAL;
    }

    /**
     * Helper class that compares and sorts two chargroups according to their
     * first element only. I repeat: ONLY the first element is considered, the rest
     * is ignored.
     * This comparator imposes orderings that are inconsistent with equals.
     */
    static private class CharGroupComparator implements java.util.Comparator {
        public int compare(Object o1, Object o2) {
            final CharGroup c1 = (CharGroup)o1;
            final CharGroup c2 = (CharGroup)o2;
            if (c1.mChars[0] == c2.mChars[0]) return 0;
            return c1.mChars[0] < c2.mChars[0] ? -1 : 1;
        }
        public boolean equals(Object o) {
            return o instanceof CharGroupComparator;
        }
    }
    final static private CharGroupComparator CHARGROUP_COMPARATOR = new CharGroupComparator();

    /**
     * Finds the insertion index of a character within a node.
     */
    private static int findInsertionIndex(final Node node, int character) {
        final List data = node.mData;
        final CharGroup reference = new CharGroup(new int[] { character }, null, 0);
        int result = Collections.binarySearch(data, reference, CHARGROUP_COMPARATOR);
        return result >= 0 ? result : -result - 1;
    }

    /**
     * Find the index of a char in a node, if it exists.
     *
     * @param node the node to search in.
     * @param character the character to search for.
     * @return the position of the character if it's there, or CHARACTER_NOT_FOUND = -1 else.
     */
    private static int CHARACTER_NOT_FOUND = -1;
    private static int findIndexOfChar(final Node node, int character) {
        final int insertionIndex = findInsertionIndex(node, character);
        if (node.mData.size() <= insertionIndex) return CHARACTER_NOT_FOUND;
        return character == node.mData.get(insertionIndex).mChars[0] ? insertionIndex
                : CHARACTER_NOT_FOUND;
    }

    /**
     * Helper method to find a word in a given branch.
     */
    public static CharGroup findWordInTree(Node node, final String s) {
        int index = 0;
        final StringBuilder checker = new StringBuilder();

        CharGroup currentGroup;
        do {
            int indexOfGroup = findIndexOfChar(node, s.codePointAt(index));
            if (CHARACTER_NOT_FOUND == indexOfGroup) return null;
            currentGroup = node.mData.get(indexOfGroup);
            checker.append(new String(currentGroup.mChars, 0, currentGroup.mChars.length));
            index += currentGroup.mChars.length;
            if (index < s.length()) {
                node = currentGroup.mChildren;
            }
        } while (null != node && index < s.length());

        if (!s.equals(checker.toString())) return null;
        return currentGroup;
    }

    /**
     * Recursively count the number of character groups in a given branch of the trie.
     *
     * @param node the parent node.
     * @return the number of char groups in all the branch under this node.
     */
    public static int countCharGroups(final Node node) {
        final int nodeSize = node.mData.size();
        int size = nodeSize;
        for (int i = nodeSize - 1; i >= 0; --i) {
            CharGroup group = node.mData.get(i);
            if (null != group.mChildren)
                size += countCharGroups(group.mChildren);
        }
        return size;
    }

    /**
     * Recursively count the number of nodes in a given branch of the trie.
     *
     * @param node the node to count.
     * @result the number of nodes in this branch.
     */
    public static int countNodes(final Node node) {
        int size = 1;
        for (int i = node.mData.size() - 1; i >= 0; --i) {
            CharGroup group = node.mData.get(i);
            if (null != group.mChildren)
                size += countNodes(group.mChildren);
        }
        return size;
    }

    // Historically, the tails of the words were going to be merged to save space.
    // However, that would prevent the code to search for a specific address in log(n)
    // time so this was abandoned.
    // The code is still of interest as it does add some compression to any dictionary
    // that has no need for attributes. Implementations that does not read attributes should be
    // able to read a dictionary with merged tails.
    // Also, the following code does support frequencies, as in, it will only merges
    // tails that share the same frequency. Though it would result in the above loss of
    // performance while searching by address, it is still technically possible to merge
    // tails that contain attributes, but this code does not take that into account - it does
    // not compare attributes and will merge terminals with different attributes regardless.
    public void mergeTails() {
        MakedictLog.i("Do not merge tails");
        return;

//        MakedictLog.i("Merging nodes. Number of nodes : " + countNodes(root));
//        MakedictLog.i("Number of groups : " + countCharGroups(root));
//
//        final HashMap<String, ArrayList<Node>> repository =
//                  new HashMap<String, ArrayList<Node>>();
//        mergeTailsInner(repository, root);
//
//        MakedictLog.i("Number of different pseudohashes : " + repository.size());
//        int size = 0;
//        for (ArrayList<Node> a : repository.values()) {
//            size += a.size();
//        }
//        MakedictLog.i("Number of nodes after merge : " + (1 + size));
//        MakedictLog.i("Recursively seen nodes : " + countNodes(root));
    }

    // The following methods are used by the deactivated mergeTails()
//   private static boolean isEqual(Node a, Node b) {
//       if (null == a && null == b) return true;
//       if (null == a || null == b) return false;
//       if (a.data.size() != b.data.size()) return false;
//       final int size = a.data.size();
//       for (int i = size - 1; i >= 0; --i) {
//           CharGroup aGroup = a.data.get(i);
//           CharGroup bGroup = b.data.get(i);
//           if (aGroup.frequency != bGroup.frequency) return false;
//           if (aGroup.alternates == null && bGroup.alternates != null) return false;
//           if (aGroup.alternates != null && !aGroup.equals(bGroup.alternates)) return false;
//           if (!Arrays.equals(aGroup.chars, bGroup.chars)) return false;
//           if (!isEqual(aGroup.children, bGroup.children)) return false;
//       }
//       return true;
//   }

//   static private HashMap<String, ArrayList<Node>> mergeTailsInner(
//           final HashMap<String, ArrayList<Node>> map, final Node node) {
//       final ArrayList<CharGroup> branches = node.data;
//       final int nodeSize = branches.size();
//       for (int i = 0; i < nodeSize; ++i) {
//           CharGroup group = branches.get(i);
//           if (null != group.children) {
//               String pseudoHash = getPseudoHash(group.children);
//               ArrayList<Node> similarList = map.get(pseudoHash);
//               if (null == similarList) {
//                   similarList = new ArrayList<Node>();
//                   map.put(pseudoHash, similarList);
//               }
//               boolean merged = false;
//               for (Node similar : similarList) {
//                   if (isEqual(group.children, similar)) {
//                       group.children = similar;
//                       merged = true;
//                       break;
//                   }
//               }
//               if (!merged) {
//                   similarList.add(group.children);
//               }
//               mergeTailsInner(map, group.children);
//           }
//       }
//       return map;
//   }

//  private static String getPseudoHash(final Node node) {
//      StringBuilder s = new StringBuilder();
//      for (CharGroup g : node.data) {
//          s.append(g.frequency);
//          for (int ch : g.chars){
//              s.append(Character.toChars(ch));
//          }
//      }
//      return s.toString();
//  }

    /**
     * Iterator to walk through a dictionary.
     *
     * This is purely for convenience.
     */
    public static class DictionaryIterator implements Iterator<Word> {

        private static class Position {
            public Iterator<CharGroup> pos;
            public int length;
            public Position(ArrayList<CharGroup> groups) {
                pos = groups.iterator();
                length = 0;
            }
        }
        final StringBuilder mCurrentString;
        final LinkedList<Position> mPositions;

        public DictionaryIterator(ArrayList<CharGroup> root) {
            mCurrentString = new StringBuilder();
            mPositions = new LinkedList<Position>();
            final Position rootPos = new Position(root);
            mPositions.add(rootPos);
        }

        @Override
        public boolean hasNext() {
            for (Position p : mPositions) {
                if (p.pos.hasNext()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Word next() {
            Position currentPos = mPositions.getLast();
            mCurrentString.setLength(mCurrentString.length() - currentPos.length);

            do {
                if (currentPos.pos.hasNext()) {
                    final CharGroup currentGroup = currentPos.pos.next();
                    currentPos.length = currentGroup.mChars.length;
                    for (int i : currentGroup.mChars)
                        mCurrentString.append(Character.toChars(i));
                    if (null != currentGroup.mChildren) {
                        currentPos = new Position(currentGroup.mChildren.mData);
                        mPositions.addLast(currentPos);
                    }
                    if (currentGroup.mFrequency >= 0)
                        return new Word(mCurrentString.toString(), currentGroup.mFrequency,
                                currentGroup.mBigrams);
                } else {
                    mPositions.removeLast();
                    currentPos = mPositions.getLast();
                    mCurrentString.setLength(mCurrentString.length() - mPositions.getLast().length);
                }
            } while(true);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Unsupported yet");
        }

    }

    /**
     * Method to return an iterator.
     *
     * This method enables Java's enhanced for loop. With this you can have a FusionDictionary x
     * and say : for (Word w : x) {}
     */
    @Override
    public Iterator<Word> iterator() {
        return new DictionaryIterator(mRoot.mData);
    }
}
