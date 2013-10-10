/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils.ForgettingCurveParams;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Class for an in-memory dictionary that can grow dynamically and can
 * be searched for suggestions and valid words.
 */
// TODO: Remove after binary dictionary supports dynamic update.
public class ExpandableDictionary extends Dictionary {
    private static final String TAG = ExpandableDictionary.class.getSimpleName();
    /**
     * The weight to give to a word if it's length is the same as the number of typed characters.
     */
    private static final int FULL_WORD_SCORE_MULTIPLIER = 2;

    private char[] mWordBuilder = new char[Constants.DICTIONARY_MAX_WORD_LENGTH];
    private int mMaxDepth;
    private int mInputLength;

    private static final class Node {
        char mCode;
        int mFrequency;
        boolean mTerminal;
        Node mParent;
        NodeArray mChildren;
        ArrayList<char[]> mShortcutTargets;
        boolean mShortcutOnly;
        LinkedList<NextWord> mNGrams; // Supports ngram
    }

    private static final class NodeArray {
        Node[] mData;
        int mLength = 0;
        private static final int INCREMENT = 2;

        NodeArray() {
            mData = new Node[INCREMENT];
        }

        void add(final Node n) {
            if (mLength + 1 > mData.length) {
                Node[] tempData = new Node[mLength + INCREMENT];
                if (mLength > 0) {
                    System.arraycopy(mData, 0, tempData, 0, mLength);
                }
                mData = tempData;
            }
            mData[mLength++] = n;
        }
    }

    public interface NextWord {
        public Node getWordNode();
        public int getFrequency();
        public ForgettingCurveParams getFcParams();
        public int notifyTypedAgainAndGetFrequency();
    }

    private static final class NextStaticWord implements NextWord {
        public final Node mWord;
        private final int mFrequency;
        public NextStaticWord(Node word, int frequency) {
            mWord = word;
            mFrequency = frequency;
        }

        @Override
        public Node getWordNode() {
            return mWord;
        }

        @Override
        public int getFrequency() {
            return mFrequency;
        }

        @Override
        public ForgettingCurveParams getFcParams() {
            return null;
        }

        @Override
        public int notifyTypedAgainAndGetFrequency() {
            return mFrequency;
        }
    }

    private static final class NextHistoryWord implements NextWord {
        public final Node mWord;
        public final ForgettingCurveParams mFcp;

        public NextHistoryWord(Node word, ForgettingCurveParams fcp) {
            mWord = word;
            mFcp = fcp;
        }

        @Override
        public Node getWordNode() {
            return mWord;
        }

        @Override
        public int getFrequency() {
            return mFcp.getFrequency();
        }

        @Override
        public ForgettingCurveParams getFcParams() {
            return mFcp;
        }

        @Override
        public int notifyTypedAgainAndGetFrequency() {
            return mFcp.notifyTypedAgainAndGetFrequency();
        }
    }

    private NodeArray mRoots;

    private int[][] mCodes;

    public ExpandableDictionary(final String dictType) {
        super(dictType);
        clearDictionary();
        mCodes = new int[Constants.DICTIONARY_MAX_WORD_LENGTH][];
    }

    public int getMaxWordLength() {
        return Constants.DICTIONARY_MAX_WORD_LENGTH;
    }

    /**
     * Add a word with an optional shortcut to the dictionary.
     * @param word The word to add.
     * @param shortcutTarget A shortcut target for this word, or null if none.
     * @param frequency The frequency for this unigram.
     * @param shortcutFreq The frequency of the shortcut (0~15, with 15 = whitelist). Ignored
     *   if shortcutTarget is null.
     */
    public void addWord(final String word, final String shortcutTarget, final int frequency,
            final int shortcutFreq) {
        if (word.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH) {
            return;
        }
        addWordRec(mRoots, word, 0, shortcutTarget, frequency, shortcutFreq, null);
    }

    /**
     * Add a word, recursively searching for its correct place in the trie tree.
     * @param children The node to recursively search for addition. Initially, the root of the tree.
     * @param word The word to add.
     * @param depth The current depth in the tree.
     * @param shortcutTarget A shortcut target for this word, or null if none.
     * @param frequency The frequency for this unigram.
     * @param shortcutFreq The frequency of the shortcut (0~15, with 15 = whitelist). Ignored
     *   if shortcutTarget is null.
     * @param parentNode The parent node, for up linking. Initially null, as the root has no parent.
     */
    private void addWordRec(final NodeArray children, final String word, final int depth,
            final String shortcutTarget, final int frequency, final int shortcutFreq,
            final Node parentNode) {
        final int wordLength = word.length();
        if (wordLength <= depth) return;
        final char c = word.charAt(depth);
        // Does children have the current character?
        final int childrenLength = children.mLength;
        Node childNode = null;
        for (int i = 0; i < childrenLength; i++) {
            final Node node = children.mData[i];
            if (node.mCode == c) {
                childNode = node;
                break;
            }
        }
        final boolean isShortcutOnly = (null != shortcutTarget);
        if (childNode == null) {
            childNode = new Node();
            childNode.mCode = c;
            childNode.mParent = parentNode;
            childNode.mShortcutOnly = isShortcutOnly;
            children.add(childNode);
        }
        if (wordLength == depth + 1) {
            // Terminate this word
            childNode.mTerminal = true;
            if (isShortcutOnly) {
                if (null == childNode.mShortcutTargets) {
                    childNode.mShortcutTargets = CollectionUtils.newArrayList();
                }
                childNode.mShortcutTargets.add(shortcutTarget.toCharArray());
            } else {
                childNode.mShortcutOnly = false;
            }
            childNode.mFrequency = Math.max(frequency, childNode.mFrequency);
            if (childNode.mFrequency > 255) childNode.mFrequency = 255;
            return;
        }
        if (childNode.mChildren == null) {
            childNode.mChildren = new NodeArray();
        }
        addWordRec(childNode.mChildren, word, depth + 1, shortcutTarget, frequency, shortcutFreq,
                childNode);
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        if (composer.size() > 1) {
            if (composer.size() >= Constants.DICTIONARY_MAX_WORD_LENGTH) {
                return null;
            }
            final ArrayList<SuggestedWordInfo> suggestions =
                    getWordsInner(composer, prevWord, proximityInfo);
            return suggestions;
        } else {
            if (TextUtils.isEmpty(prevWord)) return null;
            final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
            runBigramReverseLookUp(prevWord, suggestions);
            return suggestions;
        }
    }

    private ArrayList<SuggestedWordInfo> getWordsInner(final WordComposer codes,
            final String prevWordForBigrams, final ProximityInfo proximityInfo) {
        final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
        mInputLength = codes.size();
        if (mCodes.length < mInputLength) mCodes = new int[mInputLength][];
        final InputPointers ips = codes.getInputPointers();
        final int[] xCoordinates = ips.getXCoordinates();
        final int[] yCoordinates = ips.getYCoordinates();
        // Cache the codes so that we don't have to lookup an array list
        for (int i = 0; i < mInputLength; i++) {
            // TODO: Calculate proximity info here.
            if (mCodes[i] == null || mCodes[i].length < 1) {
                mCodes[i] = new int[ProximityInfo.MAX_PROXIMITY_CHARS_SIZE];
            }
            final int x = xCoordinates != null && i < xCoordinates.length ?
                    xCoordinates[i] : Constants.NOT_A_COORDINATE;
            final int y = xCoordinates != null && i < yCoordinates.length ?
                    yCoordinates[i] : Constants.NOT_A_COORDINATE;
            proximityInfo.fillArrayWithNearestKeyCodes(x, y, codes.getCodeAt(i), mCodes[i]);
        }
        mMaxDepth = mInputLength * 3;
        getWordsRec(mRoots, codes, mWordBuilder, 0, false, 1, 0, -1, suggestions);
        for (int i = 0; i < mInputLength; i++) {
            getWordsRec(mRoots, codes, mWordBuilder, 0, false, 1, 0, i, suggestions);
        }
        return suggestions;
    }

    @Override
    public synchronized boolean isValidWord(final String word) {
        final Node node = searchNode(mRoots, word, 0, word.length());
        // If node is null, we didn't find the word, so it's not valid.
        // If node.mShortcutOnly is true, then it exists as a shortcut but not as a word,
        // so that means it's not a valid word.
        // If node.mShortcutOnly is false, then it exists as a word (it may also exist as
        // a shortcut, but this does not matter), so it's a valid word.
        return (node == null) ? false : !node.mShortcutOnly;
    }

    public boolean removeBigram(final String word0, final String word1) {
        // Refer to addOrSetBigram() about word1.toLowerCase()
        final Node firstWord = searchWord(mRoots, word0.toLowerCase(), 0, null);
        final Node secondWord = searchWord(mRoots, word1, 0, null);
        LinkedList<NextWord> bigrams = firstWord.mNGrams;
        NextWord bigramNode = null;
        if (bigrams == null || bigrams.size() == 0) {
            return false;
        } else {
            for (NextWord nw : bigrams) {
                if (nw.getWordNode() == secondWord) {
                    bigramNode = nw;
                    break;
                }
            }
        }
        if (bigramNode == null) {
            return false;
        }
        return bigrams.remove(bigramNode);
    }

    /**
     * Returns the word's frequency or -1 if not found
     */
    @UsedForTesting
    public int getWordFrequency(final String word) {
        // Case-sensitive search
        final Node node = searchNode(mRoots, word, 0, word.length());
        return (node == null) ? -1 : node.mFrequency;
    }

    public NextWord getBigramWord(final String word0, final String word1) {
        // Refer to addOrSetBigram() about word0.toLowerCase()
        final Node firstWord = searchWord(mRoots, word0.toLowerCase(), 0, null);
        final Node secondWord = searchWord(mRoots, word1, 0, null);
        LinkedList<NextWord> bigrams = firstWord.mNGrams;
        if (bigrams == null || bigrams.size() == 0) {
            return null;
        } else {
            for (NextWord nw : bigrams) {
                if (nw.getWordNode() == secondWord) {
                    return nw;
                }
            }
        }
        return null;
    }

    private static int computeSkippedWordFinalFreq(final int freq, final int snr,
            final int inputLength) {
        // The computation itself makes sense for >= 2, but the == 2 case returns 0
        // anyway so we may as well test against 3 instead and return the constant
        if (inputLength >= 3) {
            return (freq * snr * (inputLength - 2)) / (inputLength - 1);
        } else {
            return 0;
        }
    }

    /**
     * Helper method to add a word and its shortcuts.
     *
     * @param node the terminal node
     * @param word the word to insert, as an array of code points
     * @param depth the depth of the node in the tree
     * @param finalFreq the frequency for this word
     * @param suggestions the suggestion collection to add the suggestions to
     * @return whether there is still space for more words.
     */
    private boolean addWordAndShortcutsFromNode(final Node node, final char[] word, final int depth,
            final int finalFreq, final ArrayList<SuggestedWordInfo> suggestions) {
        if (finalFreq > 0 && !node.mShortcutOnly) {
            // Use KIND_CORRECTION always. This dictionary does not really have a notion of
            // COMPLETION against CORRECTION; we could artificially add one by looking at
            // the respective size of the typed word and the suggestion if it matters sometime
            // in the future.
            suggestions.add(new SuggestedWordInfo(new String(word, 0, depth + 1), finalFreq,
                    SuggestedWordInfo.KIND_CORRECTION, this /* sourceDict */,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
            if (suggestions.size() >= Suggest.MAX_SUGGESTIONS) return false;
        }
        if (null != node.mShortcutTargets) {
            final int length = node.mShortcutTargets.size();
            for (int shortcutIndex = 0; shortcutIndex < length; ++shortcutIndex) {
                final char[] shortcut = node.mShortcutTargets.get(shortcutIndex);
                suggestions.add(new SuggestedWordInfo(new String(shortcut, 0, shortcut.length),
                        finalFreq, SuggestedWordInfo.KIND_SHORTCUT, this /* sourceDict */,
                        SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                        SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
                if (suggestions.size() > Suggest.MAX_SUGGESTIONS) return false;
            }
        }
        return true;
    }

    /**
     * Recursively traverse the tree for words that match the input. Input consists of
     * a list of arrays. Each item in the list is one input character position. An input
     * character is actually an array of multiple possible candidates. This function is not
     * optimized for speed, assuming that the user dictionary will only be a few hundred words in
     * size.
     * @param roots node whose children have to be search for matches
     * @param codes the input character codes
     * @param word the word being composed as a possible match
     * @param depth the depth of traversal - the length of the word being composed thus far
     * @param completion whether the traversal is now in completion mode - meaning that we've
     * exhausted the input and we're looking for all possible suffixes.
     * @param snr current weight of the word being formed
     * @param inputIndex position in the input characters. This can be off from the depth in
     * case we skip over some punctuations such as apostrophe in the traversal. That is, if you type
     * "wouldve", it could be matching "would've", so the depth will be one more than the
     * inputIndex
     * @param suggestions the list in which to add suggestions
     */
    // TODO: Share this routine with the native code for BinaryDictionary
    private void getWordsRec(final NodeArray roots, final WordComposer codes, final char[] word,
            final int depth, final boolean completion, final int snr, final int inputIndex,
            final int skipPos, final ArrayList<SuggestedWordInfo> suggestions) {
        final int count = roots.mLength;
        final int codeSize = mInputLength;
        // Optimization: Prune out words that are too long compared to how much was typed.
        if (depth > mMaxDepth) {
            return;
        }
        final int[] currentChars;
        if (codeSize <= inputIndex) {
            currentChars = null;
        } else {
            currentChars = mCodes[inputIndex];
        }

        for (int i = 0; i < count; i++) {
            final Node node = roots.mData[i];
            final char c = node.mCode;
            final char lowerC = toLowerCase(c);
            final boolean terminal = node.mTerminal;
            final NodeArray children = node.mChildren;
            final int freq = node.mFrequency;
            if (completion || currentChars == null) {
                word[depth] = c;
                if (terminal) {
                    final int finalFreq;
                    if (skipPos < 0) {
                        finalFreq = freq * snr;
                    } else {
                        finalFreq = computeSkippedWordFinalFreq(freq, snr, mInputLength);
                    }
                    if (!addWordAndShortcutsFromNode(node, word, depth, finalFreq, suggestions)) {
                        // No space left in the queue, bail out
                        return;
                    }
                }
                if (children != null) {
                    getWordsRec(children, codes, word, depth + 1, true, snr, inputIndex,
                            skipPos, suggestions);
                }
            } else if ((c == Constants.CODE_SINGLE_QUOTE
                    && currentChars[0] != Constants.CODE_SINGLE_QUOTE) || depth == skipPos) {
                // Skip the ' and continue deeper
                word[depth] = c;
                if (children != null) {
                    getWordsRec(children, codes, word, depth + 1, completion, snr, inputIndex,
                            skipPos, suggestions);
                }
            } else {
                // Don't use alternatives if we're looking for missing characters
                final int alternativesSize = skipPos >= 0 ? 1 : currentChars.length;
                for (int j = 0; j < alternativesSize; j++) {
                    final int addedAttenuation = (j > 0 ? 1 : 2);
                    final int currentChar = currentChars[j];
                    if (currentChar == Constants.NOT_A_CODE) {
                        break;
                    }
                    if (currentChar == lowerC || currentChar == c) {
                        word[depth] = c;

                        if (codeSize == inputIndex + 1) {
                            if (terminal) {
                                final int finalFreq;
                                if (skipPos < 0) {
                                    finalFreq = freq * snr * addedAttenuation
                                            * FULL_WORD_SCORE_MULTIPLIER;
                                } else {
                                    finalFreq = computeSkippedWordFinalFreq(freq,
                                            snr * addedAttenuation, mInputLength);
                                }
                                if (!addWordAndShortcutsFromNode(node, word, depth, finalFreq,
                                        suggestions)) {
                                    // No space left in the queue, bail out
                                    return;
                                }
                            }
                            if (children != null) {
                                getWordsRec(children, codes, word, depth + 1,
                                        true, snr * addedAttenuation, inputIndex + 1,
                                        skipPos, suggestions);
                            }
                        } else if (children != null) {
                            getWordsRec(children, codes, word, depth + 1,
                                    false, snr * addedAttenuation, inputIndex + 1,
                                    skipPos, suggestions);
                        }
                    }
                }
            }
        }
    }

    public int setBigramAndGetFrequency(final String word0, final String word1,
            final int frequency) {
        return setBigramAndGetFrequency(word0, word1, frequency, null /* unused */);
    }

    public int setBigramAndGetFrequency(final String word0, final String word1,
            final ForgettingCurveParams fcp) {
        return setBigramAndGetFrequency(word0, word1, 0 /* unused */, fcp);
    }

    /**
     * Adds bigrams to the in-memory trie structure that is being used to retrieve any word
     * @param word0 the first word of this bigram
     * @param word1 the second word of this bigram
     * @param frequency frequency for this bigram
     * @param fcp an instance of ForgettingCurveParams to use for decay policy
     * @return returns the final bigram frequency
     */
    private int setBigramAndGetFrequency(final String word0, final String word1,
            final int frequency, final ForgettingCurveParams fcp) {
        if (TextUtils.isEmpty(word0)) {
            Log.e(TAG, "Invalid bigram previous word: " + word0);
            return frequency;
        }
        // We don't want results to be different according to case of the looked up left hand side
        // word. We do want however to return the correct case for the right hand side.
        // So we want to squash the case of the left hand side, and preserve that of the right
        // hand side word.
        final String word0Lower = word0.toLowerCase();
        if (TextUtils.isEmpty(word0Lower) || TextUtils.isEmpty(word1)) {
            Log.e(TAG, "Invalid bigram pair: " + word0 + ", " + word0Lower + ", " + word1);
            return frequency;
        }
        final Node firstWord = searchWord(mRoots, word0Lower, 0, null);
        final Node secondWord = searchWord(mRoots, word1, 0, null);
        LinkedList<NextWord> bigrams = firstWord.mNGrams;
        if (bigrams == null || bigrams.size() == 0) {
            firstWord.mNGrams = CollectionUtils.newLinkedList();
            bigrams = firstWord.mNGrams;
        } else {
            for (NextWord nw : bigrams) {
                if (nw.getWordNode() == secondWord) {
                    return nw.notifyTypedAgainAndGetFrequency();
                }
            }
        }
        if (fcp != null) {
            // history
            firstWord.mNGrams.add(new NextHistoryWord(secondWord, fcp));
        } else {
            firstWord.mNGrams.add(new NextStaticWord(secondWord, frequency));
        }
        return frequency;
    }

    /**
     * Searches for the word and add the word if it does not exist.
     * @return Returns the terminal node of the word we are searching for.
     */
    private Node searchWord(final NodeArray children, final String word, final int depth,
            final Node parentNode) {
        final int wordLength = word.length();
        final char c = word.charAt(depth);
        // Does children have the current character?
        final int childrenLength = children.mLength;
        Node childNode = null;
        for (int i = 0; i < childrenLength; i++) {
            final Node node = children.mData[i];
            if (node.mCode == c) {
                childNode = node;
                break;
            }
        }
        if (childNode == null) {
            childNode = new Node();
            childNode.mCode = c;
            childNode.mParent = parentNode;
            children.add(childNode);
        }
        if (wordLength == depth + 1) {
            // Terminate this word
            childNode.mTerminal = true;
            return childNode;
        }
        if (childNode.mChildren == null) {
            childNode.mChildren = new NodeArray();
        }
        return searchWord(childNode.mChildren, word, depth + 1, childNode);
    }

    private void runBigramReverseLookUp(final String previousWord,
            final ArrayList<SuggestedWordInfo> suggestions) {
        // Search for the lowercase version of the word only, because that's where bigrams
        // store their sons.
        final Node prevWord = searchNode(mRoots, previousWord.toLowerCase(), 0,
                previousWord.length());
        if (prevWord != null && prevWord.mNGrams != null) {
            reverseLookUp(prevWord.mNGrams, suggestions);
        }
    }

    // Local to reverseLookUp, but do not allocate each time.
    private final char[] mLookedUpString = new char[Constants.DICTIONARY_MAX_WORD_LENGTH];

    /**
     * reverseLookUp retrieves the full word given a list of terminal nodes and adds those words
     * to the suggestions list passed as an argument.
     * @param terminalNodes list of terminal nodes we want to add
     * @param suggestions the suggestion collection to add the word to
     */
    private void reverseLookUp(final LinkedList<NextWord> terminalNodes,
            final ArrayList<SuggestedWordInfo> suggestions) {
        Node node;
        int freq;
        for (NextWord nextWord : terminalNodes) {
            node = nextWord.getWordNode();
            freq = nextWord.getFrequency();
            int index = Constants.DICTIONARY_MAX_WORD_LENGTH;
            do {
                --index;
                mLookedUpString[index] = node.mCode;
                node = node.mParent;
            } while (node != null && index > 0);

            // If node is null, we have a word longer than MAX_WORD_LENGTH in the dictionary.
            // It's a little unclear how this can happen, but just in case it does it's safer
            // to ignore the word in this case.
            if (freq >= 0 && node == null) {
                suggestions.add(new SuggestedWordInfo(new String(mLookedUpString, index,
                        Constants.DICTIONARY_MAX_WORD_LENGTH - index),
                        freq, SuggestedWordInfo.KIND_CORRECTION, this /* sourceDict */,
                        SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                        SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
            }
        }
    }

    /**
     * Recursively search for the terminal node of the word.
     *
     * One iteration takes the full word to search for and the current index of the recursion.
     *
     * @param children the node of the trie to search under.
     * @param word the word to search for. Only read [offset..length] so there may be trailing chars
     * @param offset the index in {@code word} this recursion should operate on.
     * @param length the length of the input word.
     * @return Returns the terminal node of the word if the word exists
     */
    private Node searchNode(final NodeArray children, final CharSequence word, final int offset,
            final int length) {
        final int count = children.mLength;
        final char currentChar = word.charAt(offset);
        for (int j = 0; j < count; j++) {
            final Node node = children.mData[j];
            if (node.mCode == currentChar) {
                if (offset == length - 1) {
                    if (node.mTerminal) {
                        return node;
                    }
                } else {
                    if (node.mChildren != null) {
                        Node returnNode = searchNode(node.mChildren, word, offset + 1, length);
                        if (returnNode != null) return returnNode;
                    }
                }
            }
        }
        return null;
    }

    public void clearDictionary() {
        mRoots = new NodeArray();
    }

    private static char toLowerCase(final char c) {
        char baseChar = c;
        if (c < BASE_CHARS.length) {
            baseChar = BASE_CHARS[c];
        }
        if (baseChar >= 'A' && baseChar <= 'Z') {
            return (char)(baseChar | 32);
        } else if (baseChar > 127) {
            return Character.toLowerCase(baseChar);
        }
        return baseChar;
    }

    /**
     * Table mapping most combined Latin, Greek, and Cyrillic characters
     * to their base characters.  If c is in range, BASE_CHARS[c] == c
     * if c is not a combined character, or the base character if it
     * is combined.
     *
     * cf. native/jni/src/utils/char_utils.cpp
     */
    private static final char BASE_CHARS[] = {
        /* U+0000 */ 0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007,
        /* U+0008 */ 0x0008, 0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F,
        /* U+0010 */ 0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017,
        /* U+0018 */ 0x0018, 0x0019, 0x001A, 0x001B, 0x001C, 0x001D, 0x001E, 0x001F,
        /* U+0020 */ 0x0020, 0x0021, 0x0022, 0x0023, 0x0024, 0x0025, 0x0026, 0x0027,
        /* U+0028 */ 0x0028, 0x0029, 0x002A, 0x002B, 0x002C, 0x002D, 0x002E, 0x002F,
        /* U+0030 */ 0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037,
        /* U+0038 */ 0x0038, 0x0039, 0x003A, 0x003B, 0x003C, 0x003D, 0x003E, 0x003F,
        /* U+0040 */ 0x0040, 0x0041, 0x0042, 0x0043, 0x0044, 0x0045, 0x0046, 0x0047,
        /* U+0048 */ 0x0048, 0x0049, 0x004A, 0x004B, 0x004C, 0x004D, 0x004E, 0x004F,
        /* U+0050 */ 0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057,
        /* U+0058 */ 0x0058, 0x0059, 0x005A, 0x005B, 0x005C, 0x005D, 0x005E, 0x005F,
        /* U+0060 */ 0x0060, 0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067,
        /* U+0068 */ 0x0068, 0x0069, 0x006A, 0x006B, 0x006C, 0x006D, 0x006E, 0x006F,
        /* U+0070 */ 0x0070, 0x0071, 0x0072, 0x0073, 0x0074, 0x0075, 0x0076, 0x0077,
        /* U+0078 */ 0x0078, 0x0079, 0x007A, 0x007B, 0x007C, 0x007D, 0x007E, 0x007F,
        /* U+0080 */ 0x0080, 0x0081, 0x0082, 0x0083, 0x0084, 0x0085, 0x0086, 0x0087,
        /* U+0088 */ 0x0088, 0x0089, 0x008A, 0x008B, 0x008C, 0x008D, 0x008E, 0x008F,
        /* U+0090 */ 0x0090, 0x0091, 0x0092, 0x0093, 0x0094, 0x0095, 0x0096, 0x0097,
        /* U+0098 */ 0x0098, 0x0099, 0x009A, 0x009B, 0x009C, 0x009D, 0x009E, 0x009F,
        /* U+00A0 */ 0x0020, 0x00A1, 0x00A2, 0x00A3, 0x00A4, 0x00A5, 0x00A6, 0x00A7,
        /* U+00A8 */ 0x0020, 0x00A9, 0x0061, 0x00AB, 0x00AC, 0x00AD, 0x00AE, 0x0020,
        /* U+00B0 */ 0x00B0, 0x00B1, 0x0032, 0x0033, 0x0020, 0x03BC, 0x00B6, 0x00B7,
        /* U+00B8 */ 0x0020, 0x0031, 0x006F, 0x00BB, 0x0031, 0x0031, 0x0033, 0x00BF,
        /* U+00C0 */ 0x0041, 0x0041, 0x0041, 0x0041, 0x0041, 0x0041, 0x00C6, 0x0043,
        /* U+00C8 */ 0x0045, 0x0045, 0x0045, 0x0045, 0x0049, 0x0049, 0x0049, 0x0049,
        /* U+00D0 */ 0x00D0, 0x004E, 0x004F, 0x004F, 0x004F, 0x004F, 0x004F, 0x00D7,
        /* U+00D8 */ 0x004F, 0x0055, 0x0055, 0x0055, 0x0055, 0x0059, 0x00DE, 0x0073,
            // U+00D8: Manually changed from 00D8 to 004F
              // TODO: Check if it's really acceptable to consider Ø a diacritical variant of O
            // U+00DF: Manually changed from 00DF to 0073
        /* U+00E0 */ 0x0061, 0x0061, 0x0061, 0x0061, 0x0061, 0x0061, 0x00E6, 0x0063,
        /* U+00E8 */ 0x0065, 0x0065, 0x0065, 0x0065, 0x0069, 0x0069, 0x0069, 0x0069,
        /* U+00F0 */ 0x00F0, 0x006E, 0x006F, 0x006F, 0x006F, 0x006F, 0x006F, 0x00F7,
        /* U+00F8 */ 0x006F, 0x0075, 0x0075, 0x0075, 0x0075, 0x0079, 0x00FE, 0x0079,
            // U+00F8: Manually changed from 00F8 to 006F
              // TODO: Check if it's really acceptable to consider ø a diacritical variant of o
        /* U+0100 */ 0x0041, 0x0061, 0x0041, 0x0061, 0x0041, 0x0061, 0x0043, 0x0063,
        /* U+0108 */ 0x0043, 0x0063, 0x0043, 0x0063, 0x0043, 0x0063, 0x0044, 0x0064,
        /* U+0110 */ 0x0110, 0x0111, 0x0045, 0x0065, 0x0045, 0x0065, 0x0045, 0x0065,
        /* U+0118 */ 0x0045, 0x0065, 0x0045, 0x0065, 0x0047, 0x0067, 0x0047, 0x0067,
        /* U+0120 */ 0x0047, 0x0067, 0x0047, 0x0067, 0x0048, 0x0068, 0x0126, 0x0127,
        /* U+0128 */ 0x0049, 0x0069, 0x0049, 0x0069, 0x0049, 0x0069, 0x0049, 0x0069,
        /* U+0130 */ 0x0049, 0x0131, 0x0049, 0x0069, 0x004A, 0x006A, 0x004B, 0x006B,
        /* U+0138 */ 0x0138, 0x004C, 0x006C, 0x004C, 0x006C, 0x004C, 0x006C, 0x004C,
        /* U+0140 */ 0x006C, 0x004C, 0x006C, 0x004E, 0x006E, 0x004E, 0x006E, 0x004E,
            // U+0141: Manually changed from 0141 to 004C
            // U+0142: Manually changed from 0142 to 006C
        /* U+0148 */ 0x006E, 0x02BC, 0x014A, 0x014B, 0x004F, 0x006F, 0x004F, 0x006F,
        /* U+0150 */ 0x004F, 0x006F, 0x0152, 0x0153, 0x0052, 0x0072, 0x0052, 0x0072,
        /* U+0158 */ 0x0052, 0x0072, 0x0053, 0x0073, 0x0053, 0x0073, 0x0053, 0x0073,
        /* U+0160 */ 0x0053, 0x0073, 0x0054, 0x0074, 0x0054, 0x0074, 0x0166, 0x0167,
        /* U+0168 */ 0x0055, 0x0075, 0x0055, 0x0075, 0x0055, 0x0075, 0x0055, 0x0075,
        /* U+0170 */ 0x0055, 0x0075, 0x0055, 0x0075, 0x0057, 0x0077, 0x0059, 0x0079,
        /* U+0178 */ 0x0059, 0x005A, 0x007A, 0x005A, 0x007A, 0x005A, 0x007A, 0x0073,
        /* U+0180 */ 0x0180, 0x0181, 0x0182, 0x0183, 0x0184, 0x0185, 0x0186, 0x0187,
        /* U+0188 */ 0x0188, 0x0189, 0x018A, 0x018B, 0x018C, 0x018D, 0x018E, 0x018F,
        /* U+0190 */ 0x0190, 0x0191, 0x0192, 0x0193, 0x0194, 0x0195, 0x0196, 0x0197,
        /* U+0198 */ 0x0198, 0x0199, 0x019A, 0x019B, 0x019C, 0x019D, 0x019E, 0x019F,
        /* U+01A0 */ 0x004F, 0x006F, 0x01A2, 0x01A3, 0x01A4, 0x01A5, 0x01A6, 0x01A7,
        /* U+01A8 */ 0x01A8, 0x01A9, 0x01AA, 0x01AB, 0x01AC, 0x01AD, 0x01AE, 0x0055,
        /* U+01B0 */ 0x0075, 0x01B1, 0x01B2, 0x01B3, 0x01B4, 0x01B5, 0x01B6, 0x01B7,
        /* U+01B8 */ 0x01B8, 0x01B9, 0x01BA, 0x01BB, 0x01BC, 0x01BD, 0x01BE, 0x01BF,
        /* U+01C0 */ 0x01C0, 0x01C1, 0x01C2, 0x01C3, 0x0044, 0x0044, 0x0064, 0x004C,
        /* U+01C8 */ 0x004C, 0x006C, 0x004E, 0x004E, 0x006E, 0x0041, 0x0061, 0x0049,
        /* U+01D0 */ 0x0069, 0x004F, 0x006F, 0x0055, 0x0075, 0x0055, 0x0075, 0x0055,
            // U+01D5: Manually changed from 00DC to 0055
            // U+01D6: Manually changed from 00FC to 0075
            // U+01D7: Manually changed from 00DC to 0055
        /* U+01D8 */ 0x0075, 0x0055, 0x0075, 0x0055, 0x0075, 0x01DD, 0x0041, 0x0061,
            // U+01D8: Manually changed from 00FC to 0075
            // U+01D9: Manually changed from 00DC to 0055
            // U+01DA: Manually changed from 00FC to 0075
            // U+01DB: Manually changed from 00DC to 0055
            // U+01DC: Manually changed from 00FC to 0075
            // U+01DE: Manually changed from 00C4 to 0041
            // U+01DF: Manually changed from 00E4 to 0061
        /* U+01E0 */ 0x0041, 0x0061, 0x00C6, 0x00E6, 0x01E4, 0x01E5, 0x0047, 0x0067,
            // U+01E0: Manually changed from 0226 to 0041
            // U+01E1: Manually changed from 0227 to 0061
        /* U+01E8 */ 0x004B, 0x006B, 0x004F, 0x006F, 0x004F, 0x006F, 0x01B7, 0x0292,
            // U+01EC: Manually changed from 01EA to 004F
            // U+01ED: Manually changed from 01EB to 006F
        /* U+01F0 */ 0x006A, 0x0044, 0x0044, 0x0064, 0x0047, 0x0067, 0x01F6, 0x01F7,
        /* U+01F8 */ 0x004E, 0x006E, 0x0041, 0x0061, 0x00C6, 0x00E6, 0x004F, 0x006F,
            // U+01FA: Manually changed from 00C5 to 0041
            // U+01FB: Manually changed from 00E5 to 0061
            // U+01FE: Manually changed from 00D8 to 004F
              // TODO: Check if it's really acceptable to consider Ø a diacritical variant of O
            // U+01FF: Manually changed from 00F8 to 006F
              // TODO: Check if it's really acceptable to consider ø a diacritical variant of o
        /* U+0200 */ 0x0041, 0x0061, 0x0041, 0x0061, 0x0045, 0x0065, 0x0045, 0x0065,
        /* U+0208 */ 0x0049, 0x0069, 0x0049, 0x0069, 0x004F, 0x006F, 0x004F, 0x006F,
        /* U+0210 */ 0x0052, 0x0072, 0x0052, 0x0072, 0x0055, 0x0075, 0x0055, 0x0075,
        /* U+0218 */ 0x0053, 0x0073, 0x0054, 0x0074, 0x021C, 0x021D, 0x0048, 0x0068,
        /* U+0220 */ 0x0220, 0x0221, 0x0222, 0x0223, 0x0224, 0x0225, 0x0041, 0x0061,
        /* U+0228 */ 0x0045, 0x0065, 0x004F, 0x006F, 0x004F, 0x006F, 0x004F, 0x006F,
            // U+022A: Manually changed from 00D6 to 004F
            // U+022B: Manually changed from 00F6 to 006F
            // U+022C: Manually changed from 00D5 to 004F
            // U+022D: Manually changed from 00F5 to 006F
        /* U+0230 */ 0x004F, 0x006F, 0x0059, 0x0079, 0x0234, 0x0235, 0x0236, 0x0237,
            // U+0230: Manually changed from 022E to 004F
            // U+0231: Manually changed from 022F to 006F
        /* U+0238 */ 0x0238, 0x0239, 0x023A, 0x023B, 0x023C, 0x023D, 0x023E, 0x023F,
        /* U+0240 */ 0x0240, 0x0241, 0x0242, 0x0243, 0x0244, 0x0245, 0x0246, 0x0247,
        /* U+0248 */ 0x0248, 0x0249, 0x024A, 0x024B, 0x024C, 0x024D, 0x024E, 0x024F,
        /* U+0250 */ 0x0250, 0x0251, 0x0252, 0x0253, 0x0254, 0x0255, 0x0256, 0x0257,
        /* U+0258 */ 0x0258, 0x0259, 0x025A, 0x025B, 0x025C, 0x025D, 0x025E, 0x025F,
        /* U+0260 */ 0x0260, 0x0261, 0x0262, 0x0263, 0x0264, 0x0265, 0x0266, 0x0267,
        /* U+0268 */ 0x0268, 0x0269, 0x026A, 0x026B, 0x026C, 0x026D, 0x026E, 0x026F,
        /* U+0270 */ 0x0270, 0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0276, 0x0277,
        /* U+0278 */ 0x0278, 0x0279, 0x027A, 0x027B, 0x027C, 0x027D, 0x027E, 0x027F,
        /* U+0280 */ 0x0280, 0x0281, 0x0282, 0x0283, 0x0284, 0x0285, 0x0286, 0x0287,
        /* U+0288 */ 0x0288, 0x0289, 0x028A, 0x028B, 0x028C, 0x028D, 0x028E, 0x028F,
        /* U+0290 */ 0x0290, 0x0291, 0x0292, 0x0293, 0x0294, 0x0295, 0x0296, 0x0297,
        /* U+0298 */ 0x0298, 0x0299, 0x029A, 0x029B, 0x029C, 0x029D, 0x029E, 0x029F,
        /* U+02A0 */ 0x02A0, 0x02A1, 0x02A2, 0x02A3, 0x02A4, 0x02A5, 0x02A6, 0x02A7,
        /* U+02A8 */ 0x02A8, 0x02A9, 0x02AA, 0x02AB, 0x02AC, 0x02AD, 0x02AE, 0x02AF,
        /* U+02B0 */ 0x0068, 0x0266, 0x006A, 0x0072, 0x0279, 0x027B, 0x0281, 0x0077,
        /* U+02B8 */ 0x0079, 0x02B9, 0x02BA, 0x02BB, 0x02BC, 0x02BD, 0x02BE, 0x02BF,
        /* U+02C0 */ 0x02C0, 0x02C1, 0x02C2, 0x02C3, 0x02C4, 0x02C5, 0x02C6, 0x02C7,
        /* U+02C8 */ 0x02C8, 0x02C9, 0x02CA, 0x02CB, 0x02CC, 0x02CD, 0x02CE, 0x02CF,
        /* U+02D0 */ 0x02D0, 0x02D1, 0x02D2, 0x02D3, 0x02D4, 0x02D5, 0x02D6, 0x02D7,
        /* U+02D8 */ 0x0020, 0x0020, 0x0020, 0x0020, 0x0020, 0x0020, 0x02DE, 0x02DF,
        /* U+02E0 */ 0x0263, 0x006C, 0x0073, 0x0078, 0x0295, 0x02E5, 0x02E6, 0x02E7,
        /* U+02E8 */ 0x02E8, 0x02E9, 0x02EA, 0x02EB, 0x02EC, 0x02ED, 0x02EE, 0x02EF,
        /* U+02F0 */ 0x02F0, 0x02F1, 0x02F2, 0x02F3, 0x02F4, 0x02F5, 0x02F6, 0x02F7,
        /* U+02F8 */ 0x02F8, 0x02F9, 0x02FA, 0x02FB, 0x02FC, 0x02FD, 0x02FE, 0x02FF,
        /* U+0300 */ 0x0300, 0x0301, 0x0302, 0x0303, 0x0304, 0x0305, 0x0306, 0x0307,
        /* U+0308 */ 0x0308, 0x0309, 0x030A, 0x030B, 0x030C, 0x030D, 0x030E, 0x030F,
        /* U+0310 */ 0x0310, 0x0311, 0x0312, 0x0313, 0x0314, 0x0315, 0x0316, 0x0317,
        /* U+0318 */ 0x0318, 0x0319, 0x031A, 0x031B, 0x031C, 0x031D, 0x031E, 0x031F,
        /* U+0320 */ 0x0320, 0x0321, 0x0322, 0x0323, 0x0324, 0x0325, 0x0326, 0x0327,
        /* U+0328 */ 0x0328, 0x0329, 0x032A, 0x032B, 0x032C, 0x032D, 0x032E, 0x032F,
        /* U+0330 */ 0x0330, 0x0331, 0x0332, 0x0333, 0x0334, 0x0335, 0x0336, 0x0337,
        /* U+0338 */ 0x0338, 0x0339, 0x033A, 0x033B, 0x033C, 0x033D, 0x033E, 0x033F,
        /* U+0340 */ 0x0300, 0x0301, 0x0342, 0x0313, 0x0308, 0x0345, 0x0346, 0x0347,
        /* U+0348 */ 0x0348, 0x0349, 0x034A, 0x034B, 0x034C, 0x034D, 0x034E, 0x034F,
        /* U+0350 */ 0x0350, 0x0351, 0x0352, 0x0353, 0x0354, 0x0355, 0x0356, 0x0357,
        /* U+0358 */ 0x0358, 0x0359, 0x035A, 0x035B, 0x035C, 0x035D, 0x035E, 0x035F,
        /* U+0360 */ 0x0360, 0x0361, 0x0362, 0x0363, 0x0364, 0x0365, 0x0366, 0x0367,
        /* U+0368 */ 0x0368, 0x0369, 0x036A, 0x036B, 0x036C, 0x036D, 0x036E, 0x036F,
        /* U+0370 */ 0x0370, 0x0371, 0x0372, 0x0373, 0x02B9, 0x0375, 0x0376, 0x0377,
        /* U+0378 */ 0x0378, 0x0379, 0x0020, 0x037B, 0x037C, 0x037D, 0x003B, 0x037F,
        /* U+0380 */ 0x0380, 0x0381, 0x0382, 0x0383, 0x0020, 0x00A8, 0x0391, 0x00B7,
        /* U+0388 */ 0x0395, 0x0397, 0x0399, 0x038B, 0x039F, 0x038D, 0x03A5, 0x03A9,
        /* U+0390 */ 0x03CA, 0x0391, 0x0392, 0x0393, 0x0394, 0x0395, 0x0396, 0x0397,
        /* U+0398 */ 0x0398, 0x0399, 0x039A, 0x039B, 0x039C, 0x039D, 0x039E, 0x039F,
        /* U+03A0 */ 0x03A0, 0x03A1, 0x03A2, 0x03A3, 0x03A4, 0x03A5, 0x03A6, 0x03A7,
        /* U+03A8 */ 0x03A8, 0x03A9, 0x0399, 0x03A5, 0x03B1, 0x03B5, 0x03B7, 0x03B9,
        /* U+03B0 */ 0x03CB, 0x03B1, 0x03B2, 0x03B3, 0x03B4, 0x03B5, 0x03B6, 0x03B7,
        /* U+03B8 */ 0x03B8, 0x03B9, 0x03BA, 0x03BB, 0x03BC, 0x03BD, 0x03BE, 0x03BF,
        /* U+03C0 */ 0x03C0, 0x03C1, 0x03C2, 0x03C3, 0x03C4, 0x03C5, 0x03C6, 0x03C7,
        /* U+03C8 */ 0x03C8, 0x03C9, 0x03B9, 0x03C5, 0x03BF, 0x03C5, 0x03C9, 0x03CF,
        /* U+03D0 */ 0x03B2, 0x03B8, 0x03A5, 0x03D2, 0x03D2, 0x03C6, 0x03C0, 0x03D7,
        /* U+03D8 */ 0x03D8, 0x03D9, 0x03DA, 0x03DB, 0x03DC, 0x03DD, 0x03DE, 0x03DF,
        /* U+03E0 */ 0x03E0, 0x03E1, 0x03E2, 0x03E3, 0x03E4, 0x03E5, 0x03E6, 0x03E7,
        /* U+03E8 */ 0x03E8, 0x03E9, 0x03EA, 0x03EB, 0x03EC, 0x03ED, 0x03EE, 0x03EF,
        /* U+03F0 */ 0x03BA, 0x03C1, 0x03C2, 0x03F3, 0x0398, 0x03B5, 0x03F6, 0x03F7,
        /* U+03F8 */ 0x03F8, 0x03A3, 0x03FA, 0x03FB, 0x03FC, 0x03FD, 0x03FE, 0x03FF,
        /* U+0400 */ 0x0415, 0x0415, 0x0402, 0x0413, 0x0404, 0x0405, 0x0406, 0x0406,
        /* U+0408 */ 0x0408, 0x0409, 0x040A, 0x040B, 0x041A, 0x0418, 0x0423, 0x040F,
        /* U+0410 */ 0x0410, 0x0411, 0x0412, 0x0413, 0x0414, 0x0415, 0x0416, 0x0417,
        /* U+0418 */ 0x0418, 0x0419, 0x041A, 0x041B, 0x041C, 0x041D, 0x041E, 0x041F,
            // U+0419: Manually changed from 0418 to 0419
        /* U+0420 */ 0x0420, 0x0421, 0x0422, 0x0423, 0x0424, 0x0425, 0x0426, 0x0427,
        /* U+0428 */ 0x0428, 0x0429, 0x042C, 0x042B, 0x042C, 0x042D, 0x042E, 0x042F,
            // U+042A: Manually changed from 042A to 042C
        /* U+0430 */ 0x0430, 0x0431, 0x0432, 0x0433, 0x0434, 0x0435, 0x0436, 0x0437,
        /* U+0438 */ 0x0438, 0x0439, 0x043A, 0x043B, 0x043C, 0x043D, 0x043E, 0x043F,
            // U+0439: Manually changed from 0438 to 0439
        /* U+0440 */ 0x0440, 0x0441, 0x0442, 0x0443, 0x0444, 0x0445, 0x0446, 0x0447,
        /* U+0448 */ 0x0448, 0x0449, 0x044C, 0x044B, 0x044C, 0x044D, 0x044E, 0x044F,
            // U+044A: Manually changed from 044A to 044C
        /* U+0450 */ 0x0435, 0x0435, 0x0452, 0x0433, 0x0454, 0x0455, 0x0456, 0x0456,
        /* U+0458 */ 0x0458, 0x0459, 0x045A, 0x045B, 0x043A, 0x0438, 0x0443, 0x045F,
        /* U+0460 */ 0x0460, 0x0461, 0x0462, 0x0463, 0x0464, 0x0465, 0x0466, 0x0467,
        /* U+0468 */ 0x0468, 0x0469, 0x046A, 0x046B, 0x046C, 0x046D, 0x046E, 0x046F,
        /* U+0470 */ 0x0470, 0x0471, 0x0472, 0x0473, 0x0474, 0x0475, 0x0474, 0x0475,
        /* U+0478 */ 0x0478, 0x0479, 0x047A, 0x047B, 0x047C, 0x047D, 0x047E, 0x047F,
        /* U+0480 */ 0x0480, 0x0481, 0x0482, 0x0483, 0x0484, 0x0485, 0x0486, 0x0487,
        /* U+0488 */ 0x0488, 0x0489, 0x048A, 0x048B, 0x048C, 0x048D, 0x048E, 0x048F,
        /* U+0490 */ 0x0490, 0x0491, 0x0492, 0x0493, 0x0494, 0x0495, 0x0496, 0x0497,
        /* U+0498 */ 0x0498, 0x0499, 0x049A, 0x049B, 0x049C, 0x049D, 0x049E, 0x049F,
        /* U+04A0 */ 0x04A0, 0x04A1, 0x04A2, 0x04A3, 0x04A4, 0x04A5, 0x04A6, 0x04A7,
        /* U+04A8 */ 0x04A8, 0x04A9, 0x04AA, 0x04AB, 0x04AC, 0x04AD, 0x04AE, 0x04AF,
        /* U+04B0 */ 0x04B0, 0x04B1, 0x04B2, 0x04B3, 0x04B4, 0x04B5, 0x04B6, 0x04B7,
        /* U+04B8 */ 0x04B8, 0x04B9, 0x04BA, 0x04BB, 0x04BC, 0x04BD, 0x04BE, 0x04BF,
        /* U+04C0 */ 0x04C0, 0x0416, 0x0436, 0x04C3, 0x04C4, 0x04C5, 0x04C6, 0x04C7,
        /* U+04C8 */ 0x04C8, 0x04C9, 0x04CA, 0x04CB, 0x04CC, 0x04CD, 0x04CE, 0x04CF,
        /* U+04D0 */ 0x0410, 0x0430, 0x0410, 0x0430, 0x04D4, 0x04D5, 0x0415, 0x0435,
        /* U+04D8 */ 0x04D8, 0x04D9, 0x04D8, 0x04D9, 0x0416, 0x0436, 0x0417, 0x0437,
        /* U+04E0 */ 0x04E0, 0x04E1, 0x0418, 0x0438, 0x0418, 0x0438, 0x041E, 0x043E,
        /* U+04E8 */ 0x04E8, 0x04E9, 0x04E8, 0x04E9, 0x042D, 0x044D, 0x0423, 0x0443,
        /* U+04F0 */ 0x0423, 0x0443, 0x0423, 0x0443, 0x0427, 0x0447, 0x04F6, 0x04F7,
        /* U+04F8 */ 0x042B, 0x044B, 0x04FA, 0x04FB, 0x04FC, 0x04FD, 0x04FE, 0x04FF,
    };
}
