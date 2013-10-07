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
import com.android.inputmethod.latin.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A dictionary that can fusion heads and tails of words for more compression.
 */
@UsedForTesting
public final class FusionDictionary implements Iterable<Word> {
    private static final boolean DBG = MakedictLog.DBG;

    private static int CHARACTER_NOT_FOUND_INDEX = -1;

    /**
     * A node array of the dictionary, containing several PtNodes.
     *
     * A PtNodeArray is but an ordered array of PtNodes, which essentially contain all the
     * real information.
     * This class also contains fields to cache size and address, to help with binary
     * generation.
     */
    public static final class PtNodeArray {
        ArrayList<PtNode> mData;
        // To help with binary generation
        int mCachedSize = Integer.MIN_VALUE;
        // mCachedAddressBefore/AfterUpdate are helpers for binary dictionary generation. They
        // always hold the same value except between dictionary address compression, during which
        // the update process needs to know about both values at the same time. Updating will
        // update the AfterUpdate value, and the code will move them to BeforeUpdate before
        // the next update pass.
        int mCachedAddressBeforeUpdate = Integer.MIN_VALUE;
        int mCachedAddressAfterUpdate = Integer.MIN_VALUE;
        int mCachedParentAddress = 0;

        public PtNodeArray() {
            mData = new ArrayList<PtNode>();
        }
        public PtNodeArray(ArrayList<PtNode> data) {
            mData = data;
        }
    }

    /**
     * A string with a frequency.
     *
     * This represents an "attribute", that is either a bigram or a shortcut.
     */
    public static final class WeightedString {
        public final String mWord;
        public int mFrequency;
        public WeightedString(String word, int frequency) {
            mWord = word;
            mFrequency = frequency;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new Object[] { mWord, mFrequency });
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof WeightedString)) return false;
            WeightedString w = (WeightedString)o;
            return mWord.equals(w.mWord) && mFrequency == w.mFrequency;
        }
    }

    /**
     * PtNode is a group of characters, with a frequency, shortcut targets, bigrams, and children
     * (Pt means Patricia Trie).
     *
     * This is the central class of the in-memory representation. A PtNode is what can
     * be seen as a traditional "trie node", except it can hold several characters at the
     * same time. A PtNode essentially represents one or several characters in the middle
     * of the trie tree; as such, it can be a terminal, and it can have children.
     * In this in-memory representation, whether the PtNode is a terminal or not is represented
     * in the frequency, where NOT_A_TERMINAL (= -1) means this is not a terminal and any other
     * value is the frequency of this terminal. A terminal may have non-null shortcuts and/or
     * bigrams, but a non-terminal may not. Moreover, children, if present, are null.
     */
    public static final class PtNode {
        public static final int NOT_A_TERMINAL = -1;
        final int mChars[];
        ArrayList<WeightedString> mShortcutTargets;
        ArrayList<WeightedString> mBigrams;
        int mFrequency; // NOT_A_TERMINAL == mFrequency indicates this is not a terminal.
        int mTerminalId; // NOT_A_TERMINAL == mTerminalId indicates this is not a terminal.
        PtNodeArray mChildren;
        boolean mIsNotAWord; // Only a shortcut
        boolean mIsBlacklistEntry;
        // mCachedSize and mCachedAddressBefore/AfterUpdate are helpers for binary dictionary
        // generation. Before and After always hold the same value except during dictionary
        // address compression, where the update process needs to know about both values at the
        // same time. Updating will update the AfterUpdate value, and the code will move them
        // to BeforeUpdate before the next update pass.
        // The update process does not need two versions of mCachedSize.
        int mCachedSize; // The size, in bytes, of this PtNode.
        int mCachedAddressBeforeUpdate; // The address of this PtNode (before update)
        int mCachedAddressAfterUpdate; // The address of this PtNode (after update)

        public PtNode(final int[] chars, final ArrayList<WeightedString> shortcutTargets,
                final ArrayList<WeightedString> bigrams, final int frequency,
                final boolean isNotAWord, final boolean isBlacklistEntry) {
            mChars = chars;
            mFrequency = frequency;
            mTerminalId = frequency;
            mShortcutTargets = shortcutTargets;
            mBigrams = bigrams;
            mChildren = null;
            mIsNotAWord = isNotAWord;
            mIsBlacklistEntry = isBlacklistEntry;
        }

        public PtNode(final int[] chars, final ArrayList<WeightedString> shortcutTargets,
                final ArrayList<WeightedString> bigrams, final int frequency,
                final boolean isNotAWord, final boolean isBlacklistEntry,
                final PtNodeArray children) {
            mChars = chars;
            mFrequency = frequency;
            mShortcutTargets = shortcutTargets;
            mBigrams = bigrams;
            mChildren = children;
            mIsNotAWord = isNotAWord;
            mIsBlacklistEntry = isBlacklistEntry;
        }

        public void addChild(PtNode n) {
            if (null == mChildren) {
                mChildren = new PtNodeArray();
            }
            mChildren.mData.add(n);
        }

        public int getTerminalId() {
            return mTerminalId;
        }

        public boolean isTerminal() {
            return NOT_A_TERMINAL != mFrequency;
        }

        public int getFrequency() {
            return mFrequency;
        }

        public boolean getIsNotAWord() {
            return mIsNotAWord;
        }

        public boolean getIsBlacklistEntry() {
            return mIsBlacklistEntry;
        }

        public ArrayList<WeightedString> getShortcutTargets() {
            // We don't want write permission to escape outside the package, so we return a copy
            if (null == mShortcutTargets) return null;
            final ArrayList<WeightedString> copyOfShortcutTargets =
                    new ArrayList<WeightedString>(mShortcutTargets);
            return copyOfShortcutTargets;
        }

        public ArrayList<WeightedString> getBigrams() {
            // We don't want write permission to escape outside the package, so we return a copy
            if (null == mBigrams) return null;
            final ArrayList<WeightedString> copyOfBigrams = new ArrayList<WeightedString>(mBigrams);
            return copyOfBigrams;
        }

        public boolean hasSeveralChars() {
            assert(mChars.length > 0);
            return 1 < mChars.length;
        }

        /**
         * Adds a word to the bigram list. Updates the frequency if the word already
         * exists.
         */
        public void addBigram(final String word, final int frequency) {
            if (mBigrams == null) {
                mBigrams = new ArrayList<WeightedString>();
            }
            WeightedString bigram = getBigram(word);
            if (bigram != null) {
                bigram.mFrequency = frequency;
            } else {
                bigram = new WeightedString(word, frequency);
                mBigrams.add(bigram);
            }
        }

        /**
         * Gets the shortcut target for the given word. Returns null if the word is not in the
         * shortcut list.
         */
        public WeightedString getShortcut(final String word) {
            // TODO: Don't do a linear search
            if (mShortcutTargets != null) {
                final int size = mShortcutTargets.size();
                for (int i = 0; i < size; ++i) {
                    WeightedString shortcut = mShortcutTargets.get(i);
                    if (shortcut.mWord.equals(word)) {
                        return shortcut;
                    }
                }
            }
            return null;
        }

        /**
         * Gets the bigram for the given word.
         * Returns null if the word is not in the bigrams list.
         */
        public WeightedString getBigram(final String word) {
            // TODO: Don't do a linear search
            if (mBigrams != null) {
                final int size = mBigrams.size();
                for (int i = 0; i < size; ++i) {
                    WeightedString bigram = mBigrams.get(i);
                    if (bigram.mWord.equals(word)) {
                        return bigram;
                    }
                }
            }
            return null;
        }

        /**
         * Updates the PtNode with the given properties. Adds the shortcut and bigram lists to
         * the existing ones if any. Note: unigram, bigram, and shortcut frequencies are only
         * updated if they are higher than the existing ones.
         */
        public void update(final int frequency, final ArrayList<WeightedString> shortcutTargets,
                final ArrayList<WeightedString> bigrams,
                final boolean isNotAWord, final boolean isBlacklistEntry) {
            if (frequency > mFrequency) {
                mFrequency = frequency;
            }
            if (shortcutTargets != null) {
                if (mShortcutTargets == null) {
                    mShortcutTargets = shortcutTargets;
                } else {
                    final int size = shortcutTargets.size();
                    for (int i = 0; i < size; ++i) {
                        final WeightedString shortcut = shortcutTargets.get(i);
                        final WeightedString existingShortcut = getShortcut(shortcut.mWord);
                        if (existingShortcut == null) {
                            mShortcutTargets.add(shortcut);
                        } else if (existingShortcut.mFrequency < shortcut.mFrequency) {
                            existingShortcut.mFrequency = shortcut.mFrequency;
                        }
                    }
                }
            }
            if (bigrams != null) {
                if (mBigrams == null) {
                    mBigrams = bigrams;
                } else {
                    final int size = bigrams.size();
                    for (int i = 0; i < size; ++i) {
                        final WeightedString bigram = bigrams.get(i);
                        final WeightedString existingBigram = getBigram(bigram.mWord);
                        if (existingBigram == null) {
                            mBigrams.add(bigram);
                        } else if (existingBigram.mFrequency < bigram.mFrequency) {
                            existingBigram.mFrequency = bigram.mFrequency;
                        }
                    }
                }
            }
            mIsNotAWord = isNotAWord;
            mIsBlacklistEntry = isBlacklistEntry;
        }
    }

    /**
     * Options global to the dictionary.
     */
    public static final class DictionaryOptions {
        public final boolean mGermanUmlautProcessing;
        public final boolean mFrenchLigatureProcessing;
        public final HashMap<String, String> mAttributes;
        public DictionaryOptions(final HashMap<String, String> attributes,
                final boolean germanUmlautProcessing, final boolean frenchLigatureProcessing) {
            mAttributes = attributes;
            mGermanUmlautProcessing = germanUmlautProcessing;
            mFrenchLigatureProcessing = frenchLigatureProcessing;
        }
        @Override
        public String toString() { // Convenience method
            return toString(0, false);
        }
        public String toString(final int indentCount, final boolean plumbing) {
            final StringBuilder indent = new StringBuilder();
            if (plumbing) {
                indent.append("H:");
            } else {
                for (int i = 0; i < indentCount; ++i) {
                    indent.append(" ");
                }
            }
            final StringBuilder s = new StringBuilder();
            for (final String optionKey : mAttributes.keySet()) {
                s.append(indent);
                s.append(optionKey);
                s.append(" = ");
                if ("date".equals(optionKey) && !plumbing) {
                    // Date needs a number of milliseconds, but the dictionary contains seconds
                    s.append(new Date(
                            1000 * Long.parseLong(mAttributes.get(optionKey))).toString());
                } else {
                    s.append(mAttributes.get(optionKey));
                }
                s.append("\n");
            }
            if (mGermanUmlautProcessing) {
                s.append(indent);
                s.append("Needs German umlaut processing\n");
            }
            if (mFrenchLigatureProcessing) {
                s.append(indent);
                s.append("Needs French ligature processing\n");
            }
            return s.toString();
        }
    }

    public final DictionaryOptions mOptions;
    public final PtNodeArray mRootNodeArray;

    public FusionDictionary(final PtNodeArray rootNodeArray, final DictionaryOptions options) {
        mRootNodeArray = rootNodeArray;
        mOptions = options;
    }

    public void addOptionAttribute(final String key, final String value) {
        mOptions.mAttributes.put(key, value);
    }

    /**
     * Helper method to convert a String to an int array.
     */
    static int[] getCodePoints(final String word) {
        // TODO: this is a copy-paste of the old contents of StringUtils.toCodePointArray,
        // which is not visible from the makedict package. Factor this code.
        final int length = word.length();
        if (length <= 0) return new int[] {};
        final char[] characters = word.toCharArray();
        final int[] codePoints = new int[Character.codePointCount(characters, 0, length)];
        int codePoint = Character.codePointAt(characters, 0);
        int dsti = 0;
        for (int srci = Character.charCount(codePoint);
                srci < length; srci += Character.charCount(codePoint), ++dsti) {
            codePoints[dsti] = codePoint;
            codePoint = Character.codePointAt(characters, srci);
        }
        codePoints[dsti] = codePoint;
        return codePoints;
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
     * @param shortcutTargets a list of shortcut targets for this word, or null.
     * @param isNotAWord true if this should not be considered a word (e.g. shortcut only)
     */
    public void add(final String word, final int frequency,
            final ArrayList<WeightedString> shortcutTargets, final boolean isNotAWord) {
        add(getCodePoints(word), frequency, shortcutTargets, isNotAWord,
                false /* isBlacklistEntry */);
    }

    /**
     * Helper method to add a blacklist entry as a string.
     *
     * @param word the word to add as a blacklist entry.
     * @param shortcutTargets a list of shortcut targets for this word, or null.
     * @param isNotAWord true if this is not a word for spellcheking purposes (shortcut only or so)
     */
    public void addBlacklistEntry(final String word,
            final ArrayList<WeightedString> shortcutTargets, final boolean isNotAWord) {
        add(getCodePoints(word), 0, shortcutTargets, isNotAWord, true /* isBlacklistEntry */);
    }

    /**
     * Sanity check for a PtNode array.
     *
     * This method checks that all PtNodes in a node array are ordered as expected.
     * If they are, nothing happens. If they aren't, an exception is thrown.
     */
    private void checkStack(PtNodeArray ptNodeArray) {
        ArrayList<PtNode> stack = ptNodeArray.mData;
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
     * Helper method to add a new bigram to the dictionary.
     *
     * @param word1 the previous word of the context
     * @param word2 the next word of the context
     * @param frequency the bigram frequency
     */
    public void setBigram(final String word1, final String word2, final int frequency) {
        PtNode ptNode = findWordInTree(mRootNodeArray, word1);
        if (ptNode != null) {
            final PtNode ptNode2 = findWordInTree(mRootNodeArray, word2);
            if (ptNode2 == null) {
                add(getCodePoints(word2), 0, null, false /* isNotAWord */,
                        false /* isBlacklistEntry */);
                // The PtNode for the first word may have moved by the above insertion,
                // if word1 and word2 share a common stem that happens not to have been
                // a cutting point until now. In this case, we need to refresh ptNode.
                ptNode = findWordInTree(mRootNodeArray, word1);
            }
            ptNode.addBigram(word2, frequency);
        } else {
            throw new RuntimeException("First word of bigram not found");
        }
    }

    /**
     * Add a word to this dictionary.
     *
     * The shortcuts, if any, have to be in the dictionary already. If they aren't,
     * an exception is thrown.
     *
     * @param word the word, as an int array.
     * @param frequency the frequency of the word, in the range [0..255].
     * @param shortcutTargets an optional list of shortcut targets for this word (null if none).
     * @param isNotAWord true if this is not a word for spellcheking purposes (shortcut only or so)
     * @param isBlacklistEntry true if this is a blacklisted word, false otherwise
     */
    private void add(final int[] word, final int frequency,
            final ArrayList<WeightedString> shortcutTargets,
            final boolean isNotAWord, final boolean isBlacklistEntry) {
        assert(frequency >= 0 && frequency <= 255);
        if (word.length >= Constants.DICTIONARY_MAX_WORD_LENGTH) {
            MakedictLog.w("Ignoring a word that is too long: word.length = " + word.length);
            return;
        }

        PtNodeArray currentNodeArray = mRootNodeArray;
        int charIndex = 0;

        PtNode currentPtNode = null;
        int differentCharIndex = 0; // Set by the loop to the index of the char that differs
        int nodeIndex = findIndexOfChar(mRootNodeArray, word[charIndex]);
        while (CHARACTER_NOT_FOUND_INDEX != nodeIndex) {
            currentPtNode = currentNodeArray.mData.get(nodeIndex);
            differentCharIndex = compareCharArrays(currentPtNode.mChars, word, charIndex);
            if (ARRAYS_ARE_EQUAL != differentCharIndex
                    && differentCharIndex < currentPtNode.mChars.length) break;
            if (null == currentPtNode.mChildren) break;
            charIndex += currentPtNode.mChars.length;
            if (charIndex >= word.length) break;
            currentNodeArray = currentPtNode.mChildren;
            nodeIndex = findIndexOfChar(currentNodeArray, word[charIndex]);
        }

        if (CHARACTER_NOT_FOUND_INDEX == nodeIndex) {
            // No node at this point to accept the word. Create one.
            final int insertionIndex = findInsertionIndex(currentNodeArray, word[charIndex]);
            final PtNode newPtNode = new PtNode(Arrays.copyOfRange(word, charIndex, word.length),
                    shortcutTargets, null /* bigrams */, frequency, isNotAWord, isBlacklistEntry);
            currentNodeArray.mData.add(insertionIndex, newPtNode);
            if (DBG) checkStack(currentNodeArray);
        } else {
            // There is a word with a common prefix.
            if (differentCharIndex == currentPtNode.mChars.length) {
                if (charIndex + differentCharIndex >= word.length) {
                    // The new word is a prefix of an existing word, but the node on which it
                    // should end already exists as is. Since the old PtNode was not a terminal,
                    // make it one by filling in its frequency and other attributes
                    currentPtNode.update(frequency, shortcutTargets, null, isNotAWord,
                            isBlacklistEntry);
                } else {
                    // The new word matches the full old word and extends past it.
                    // We only have to create a new node and add it to the end of this.
                    final PtNode newNode = new PtNode(
                            Arrays.copyOfRange(word, charIndex + differentCharIndex, word.length),
                                    shortcutTargets, null /* bigrams */, frequency, isNotAWord,
                                    isBlacklistEntry);
                    currentPtNode.mChildren = new PtNodeArray();
                    currentPtNode.mChildren.mData.add(newNode);
                }
            } else {
                if (0 == differentCharIndex) {
                    // Exact same word. Update the frequency if higher. This will also add the
                    // new shortcuts to the existing shortcut list if it already exists.
                    currentPtNode.update(frequency, shortcutTargets, null,
                            currentPtNode.mIsNotAWord && isNotAWord,
                            currentPtNode.mIsBlacklistEntry || isBlacklistEntry);
                } else {
                    // Partial prefix match only. We have to replace the current node with a node
                    // containing the current prefix and create two new ones for the tails.
                    PtNodeArray newChildren = new PtNodeArray();
                    final PtNode newOldWord = new PtNode(
                            Arrays.copyOfRange(currentPtNode.mChars, differentCharIndex,
                                    currentPtNode.mChars.length), currentPtNode.mShortcutTargets,
                            currentPtNode.mBigrams, currentPtNode.mFrequency,
                            currentPtNode.mIsNotAWord, currentPtNode.mIsBlacklistEntry,
                            currentPtNode.mChildren);
                    newChildren.mData.add(newOldWord);

                    final PtNode newParent;
                    if (charIndex + differentCharIndex >= word.length) {
                        newParent = new PtNode(
                                Arrays.copyOfRange(currentPtNode.mChars, 0, differentCharIndex),
                                shortcutTargets, null /* bigrams */, frequency,
                                isNotAWord, isBlacklistEntry, newChildren);
                    } else {
                        newParent = new PtNode(
                                Arrays.copyOfRange(currentPtNode.mChars, 0, differentCharIndex),
                                null /* shortcutTargets */, null /* bigrams */, -1,
                                false /* isNotAWord */, false /* isBlacklistEntry */, newChildren);
                        final PtNode newWord = new PtNode(Arrays.copyOfRange(word,
                                charIndex + differentCharIndex, word.length),
                                shortcutTargets, null /* bigrams */, frequency,
                                isNotAWord, isBlacklistEntry);
                        final int addIndex = word[charIndex + differentCharIndex]
                                > currentPtNode.mChars[differentCharIndex] ? 1 : 0;
                        newChildren.mData.add(addIndex, newWord);
                    }
                    currentNodeArray.mData.set(nodeIndex, newParent);
                }
                if (DBG) checkStack(currentNodeArray);
            }
        }
    }

    private static int ARRAYS_ARE_EQUAL = 0;

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
    private static int compareCharArrays(final int[] src, final int[] dst, int dstOffset) {
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
     * Helper class that compares and sorts two PtNodes according to their
     * first element only. I repeat: ONLY the first element is considered, the rest
     * is ignored.
     * This comparator imposes orderings that are inconsistent with equals.
     */
    static private final class PtNodeComparator implements java.util.Comparator<PtNode> {
        @Override
        public int compare(PtNode p1, PtNode p2) {
            if (p1.mChars[0] == p2.mChars[0]) return 0;
            return p1.mChars[0] < p2.mChars[0] ? -1 : 1;
        }
    }
    final static private PtNodeComparator PTNODE_COMPARATOR = new PtNodeComparator();

    /**
     * Finds the insertion index of a character within a node array.
     */
    private static int findInsertionIndex(final PtNodeArray nodeArray, int character) {
        final ArrayList<PtNode> data = nodeArray.mData;
        final PtNode reference = new PtNode(new int[] { character },
                null /* shortcutTargets */, null /* bigrams */, 0, false /* isNotAWord */,
                false /* isBlacklistEntry */);
        int result = Collections.binarySearch(data, reference, PTNODE_COMPARATOR);
        return result >= 0 ? result : -result - 1;
    }

    /**
     * Find the index of a char in a node array, if it exists.
     *
     * @param nodeArray the node array to search in.
     * @param character the character to search for.
     * @return the position of the character if it's there, or CHARACTER_NOT_FOUND_INDEX = -1 else.
     */
    private static int findIndexOfChar(final PtNodeArray nodeArray, int character) {
        final int insertionIndex = findInsertionIndex(nodeArray, character);
        if (nodeArray.mData.size() <= insertionIndex) return CHARACTER_NOT_FOUND_INDEX;
        return character == nodeArray.mData.get(insertionIndex).mChars[0] ? insertionIndex
                : CHARACTER_NOT_FOUND_INDEX;
    }

    /**
     * Helper method to find a word in a given branch.
     */
    @SuppressWarnings("unused")
    public static PtNode findWordInTree(PtNodeArray nodeArray, final String string) {
        int index = 0;
        final StringBuilder checker = DBG ? new StringBuilder() : null;
        final int[] codePoints = getCodePoints(string);

        PtNode currentPtNode;
        do {
            int indexOfGroup = findIndexOfChar(nodeArray, codePoints[index]);
            if (CHARACTER_NOT_FOUND_INDEX == indexOfGroup) return null;
            currentPtNode = nodeArray.mData.get(indexOfGroup);

            if (codePoints.length - index < currentPtNode.mChars.length) return null;
            int newIndex = index;
            while (newIndex < codePoints.length && newIndex - index < currentPtNode.mChars.length) {
                if (currentPtNode.mChars[newIndex - index] != codePoints[newIndex]) return null;
                newIndex++;
            }
            index = newIndex;

            if (DBG) {
                checker.append(new String(currentPtNode.mChars, 0, currentPtNode.mChars.length));
            }
            if (index < codePoints.length) {
                nodeArray = currentPtNode.mChildren;
            }
        } while (null != nodeArray && index < codePoints.length);

        if (index < codePoints.length) return null;
        if (!currentPtNode.isTerminal()) return null;
        if (DBG && !string.equals(checker.toString())) return null;
        return currentPtNode;
    }

    /**
     * Helper method to find out whether a word is in the dict or not.
     */
    public boolean hasWord(final String s) {
        if (null == s || "".equals(s)) {
            throw new RuntimeException("Can't search for a null or empty string");
        }
        return null != findWordInTree(mRootNodeArray, s);
    }

    /**
     * Recursively count the number of PtNodes in a given branch of the trie.
     *
     * @param nodeArray the parent node.
     * @return the number of PtNodes in all the branch under this node.
     */
    public static int countPtNodes(final PtNodeArray nodeArray) {
        final int nodeSize = nodeArray.mData.size();
        int size = nodeSize;
        for (int i = nodeSize - 1; i >= 0; --i) {
            PtNode ptNode = nodeArray.mData.get(i);
            if (null != ptNode.mChildren)
                size += countPtNodes(ptNode.mChildren);
        }
        return size;
    }

    /**
     * Recursively count the number of nodes in a given branch of the trie.
     *
     * @param nodeArray the node array to count.
     * @return the number of nodes in this branch.
     */
    public static int countNodeArrays(final PtNodeArray nodeArray) {
        int size = 1;
        for (int i = nodeArray.mData.size() - 1; i >= 0; --i) {
            PtNode ptNode = nodeArray.mData.get(i);
            if (null != ptNode.mChildren)
                size += countNodeArrays(ptNode.mChildren);
        }
        return size;
    }

    // Recursively find out whether there are any bigrams.
    // This can be pretty expensive especially if there aren't any (we return as soon
    // as we find one, so it's much cheaper if there are bigrams)
    private static boolean hasBigramsInternal(final PtNodeArray nodeArray) {
        if (null == nodeArray) return false;
        for (int i = nodeArray.mData.size() - 1; i >= 0; --i) {
            PtNode ptNode = nodeArray.mData.get(i);
            if (null != ptNode.mBigrams) return true;
            if (hasBigramsInternal(ptNode.mChildren)) return true;
        }
        return false;
    }

    /**
     * Finds out whether there are any bigrams in this dictionary.
     *
     * @return true if there is any bigram, false otherwise.
     */
    // TODO: this is expensive especially for large dictionaries without any bigram.
    // The up side is, this is always accurate and correct and uses no memory. We should
    // find a more efficient way of doing this, without compromising too much on memory
    // and ease of use.
    public boolean hasBigrams() {
        return hasBigramsInternal(mRootNodeArray);
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

//        MakedictLog.i("Merging PtNodes. Number of PtNodes : " + countPtNodes(root));
//        MakedictLog.i("Number of PtNodes : " + countPtNodes(root));
//
//        final HashMap<String, ArrayList<PtNodeArray>> repository =
//                  new HashMap<String, ArrayList<PtNodeArray>>();
//        mergeTailsInner(repository, root);
//
//        MakedictLog.i("Number of different pseudohashes : " + repository.size());
//        int size = 0;
//        for (ArrayList<PtNodeArray> a : repository.values()) {
//            size += a.size();
//        }
//        MakedictLog.i("Number of nodes after merge : " + (1 + size));
//        MakedictLog.i("Recursively seen nodes : " + countNodes(root));
    }

    // The following methods are used by the deactivated mergeTails()
//   private static boolean isEqual(PtNodeArray a, PtNodeArray b) {
//       if (null == a && null == b) return true;
//       if (null == a || null == b) return false;
//       if (a.data.size() != b.data.size()) return false;
//       final int size = a.data.size();
//       for (int i = size - 1; i >= 0; --i) {
//           PtNode aPtNode = a.data.get(i);
//           PtNode bPtNode = b.data.get(i);
//           if (aPtNode.frequency != bPtNode.frequency) return false;
//           if (aPtNode.alternates == null && bPtNode.alternates != null) return false;
//           if (aPtNode.alternates != null && !aPtNode.equals(bPtNode.alternates)) return false;
//           if (!Arrays.equals(aPtNode.chars, bPtNode.chars)) return false;
//           if (!isEqual(aPtNode.children, bPtNode.children)) return false;
//       }
//       return true;
//   }

//   static private HashMap<String, ArrayList<PtNodeArray>> mergeTailsInner(
//           final HashMap<String, ArrayList<PtNodeArray>> map, final PtNodeArray nodeArray) {
//       final ArrayList<PtNode> branches = nodeArray.data;
//       final int nodeSize = branches.size();
//       for (int i = 0; i < nodeSize; ++i) {
//           PtNode ptNode = branches.get(i);
//           if (null != ptNode.children) {
//               String pseudoHash = getPseudoHash(ptNode.children);
//               ArrayList<PtNodeArray> similarList = map.get(pseudoHash);
//               if (null == similarList) {
//                   similarList = new ArrayList<PtNodeArray>();
//                   map.put(pseudoHash, similarList);
//               }
//               boolean merged = false;
//               for (PtNodeArray similar : similarList) {
//                   if (isEqual(ptNode.children, similar)) {
//                       ptNode.children = similar;
//                       merged = true;
//                       break;
//                   }
//               }
//               if (!merged) {
//                   similarList.add(ptNode.children);
//               }
//               mergeTailsInner(map, ptNode.children);
//           }
//       }
//       return map;
//   }

//  private static String getPseudoHash(final PtNodeArray nodeArray) {
//      StringBuilder s = new StringBuilder();
//      for (PtNode ptNode : nodeArray.data) {
//          s.append(ptNode.frequency);
//          for (int ch : ptNode.chars) {
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
    public static final class DictionaryIterator implements Iterator<Word> {
        private static final class Position {
            public Iterator<PtNode> pos;
            public int length;
            public Position(ArrayList<PtNode> ptNodes) {
                pos = ptNodes.iterator();
                length = 0;
            }
        }
        final StringBuilder mCurrentString;
        final LinkedList<Position> mPositions;

        public DictionaryIterator(ArrayList<PtNode> ptRoot) {
            mCurrentString = new StringBuilder();
            mPositions = new LinkedList<Position>();
            final Position rootPos = new Position(ptRoot);
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
            mCurrentString.setLength(currentPos.length);

            do {
                if (currentPos.pos.hasNext()) {
                    final PtNode currentPtNode = currentPos.pos.next();
                    currentPos.length = mCurrentString.length();
                    for (int i : currentPtNode.mChars) {
                        mCurrentString.append(Character.toChars(i));
                    }
                    if (null != currentPtNode.mChildren) {
                        currentPos = new Position(currentPtNode.mChildren.mData);
                        currentPos.length = mCurrentString.length();
                        mPositions.addLast(currentPos);
                    }
                    if (currentPtNode.mFrequency >= 0) {
                        return new Word(mCurrentString.toString(), currentPtNode.mFrequency,
                                currentPtNode.mShortcutTargets, currentPtNode.mBigrams,
                                currentPtNode.mIsNotAWord, currentPtNode.mIsBlacklistEntry);
                    }
                } else {
                    mPositions.removeLast();
                    currentPos = mPositions.getLast();
                    mCurrentString.setLength(mPositions.getLast().length);
                }
            } while (true);
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
        return new DictionaryIterator(mRootNodeArray.mData);
    }
}
