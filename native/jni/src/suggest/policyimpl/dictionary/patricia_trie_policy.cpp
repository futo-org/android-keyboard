/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#include "suggest/policyimpl/dictionary/patricia_trie_policy.h"

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

void PatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPos();
    const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
            mDictRoot, &nextPos);
    for (int i = 0; i < childCount; i++) {
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, childDicNodes);
    }
}

// This retrieves code points and the probability of the word by its terminal position.
// Due to the fact that words are ordered in the dictionary in a strict breadth-first order,
// it is possible to check for this with advantageous complexity. For each node, we search
// for groups with children and compare the children position with the position we look for.
// When we shoot the position we look for, it means the word we look for is in the children
// of the previous group. The only tricky part is the fact that if we arrive at the end of a
// node with the last group's children position still less than what we are searching for, we
// must descend the last group's children (for example, if the word we are searching for starts
// with a z, it's the last group of the root node, so all children addresses will be smaller
// than the position we look for, and we have to descend the z node).
/* Parameters :
 * nodePos: the byte position of the terminal chargroup of the word we are searching for (this is
 *   what is stored as the "bigram position" in each bigram)
 * outCodePoints: an array to write the found word, with MAX_WORD_LENGTH size.
 * outUnigramProbability: a pointer to an int to write the probability into.
 * Return value : the code point count, of 0 if the word was not found.
 */
// TODO: Split this function to be more readable
int PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int nodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    int pos = getRootPosition();
    int wordPos = 0;
    // One iteration of the outer loop iterates through nodes. As stated above, we will only
    // traverse nodes that are actually a part of the terminal we are searching, so each time
    // we enter this loop we are one depth level further than last time.
    // The only reason we count nodes is because we want to reduce the probability of infinite
    // looping in case there is a bug. Since we know there is an upper bound to the depth we are
    // supposed to traverse, it does not hurt to count iterations.
    for (int loopCount = maxCodePointCount; loopCount > 0; --loopCount) {
        int lastCandidateGroupPos = 0;
        // Let's loop through char groups in this node searching for either the terminal
        // or one of its ascendants.
        for (int charGroupCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
                mDictRoot, &pos); charGroupCount > 0; --charGroupCount) {
            const int startPos = pos;
            const PatriciaTrieReadingUtils::NodeFlags flags =
                    PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
            const int character = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                    mDictRoot, &pos);
            if (nodePos == startPos) {
                // We found the position. Copy the rest of the code points in the buffer and return
                // the length.
                outCodePoints[wordPos] = character;
                if (PatriciaTrieReadingUtils::hasMultipleChars(flags)) {
                    int nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                            mDictRoot, &pos);
                    // We count code points in order to avoid infinite loops if the file is broken
                    // or if there is some other bug
                    int charCount = maxCodePointCount;
                    while (NOT_A_CODE_POINT != nextChar && --charCount > 0) {
                        outCodePoints[++wordPos] = nextChar;
                        nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                mDictRoot, &pos);
                    }
                }
                *outUnigramProbability =
                        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot,
                                &pos);
                return ++wordPos;
            }
            // We need to skip past this char group, so skip any remaining code points after the
            // first and possibly the probability.
            if (PatriciaTrieReadingUtils::hasMultipleChars(flags)) {
                PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
            }
            if (PatriciaTrieReadingUtils::isTerminal(flags)) {
                PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
            }
            // The fact that this group has children is very important. Since we already know
            // that this group does not match, if it has no children we know it is irrelevant
            // to what we are searching for.
            const bool hasChildren = PatriciaTrieReadingUtils::hasChildrenInFlags(flags);
            // We will write in `found' whether we have passed the children position we are
            // searching for. For example if we search for "beer", the children of b are less
            // than the address we are searching for and the children of c are greater. When we
            // come here for c, we realize this is too big, and that we should descend b.
            bool found;
            if (hasChildren) {
                int currentPos = pos;
                // Here comes the tricky part. First, read the children position.
                const int childrenPos = PatriciaTrieReadingUtils
                        ::readChildrenPositionAndAdvancePosition(mDictRoot, flags, &currentPos);
                if (childrenPos > nodePos) {
                    // If the children pos is greater than the position, it means the previous
                    // chargroup, which position is stored in lastCandidateGroupPos, was the right
                    // one.
                    found = true;
                } else if (1 >= charGroupCount) {
                    // However if we are on the LAST group of this node, and we have NOT shot the
                    // position we should descend THIS node. So we trick the lastCandidateGroupPos
                    // so that we will descend this node, not the previous one.
                    lastCandidateGroupPos = startPos;
                    found = true;
                } else {
                    // Else, we should continue looking.
                    found = false;
                }
            } else {
                // Even if we don't have children here, we could still be on the last group of this
                // node. If this is the case, we should descend the last group that had children,
                // and their position is already in lastCandidateGroup.
                found = (1 >= charGroupCount);
            }

            if (found) {
                // Okay, we found the group we should descend. Its position is in
                // the lastCandidateGroupPos variable, so we just re-read it.
                if (0 != lastCandidateGroupPos) {
                    const PatriciaTrieReadingUtils::NodeFlags lastFlags =
                            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(
                                    mDictRoot, &lastCandidateGroupPos);
                    const int lastChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                            mDictRoot, &lastCandidateGroupPos);
                    // We copy all the characters in this group to the buffer
                    outCodePoints[wordPos] = lastChar;
                    if (PatriciaTrieReadingUtils::hasMultipleChars(lastFlags)) {
                        int nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                mDictRoot, &lastCandidateGroupPos);
                        int charCount = maxCodePointCount;
                        while (-1 != nextChar && --charCount > 0) {
                            outCodePoints[++wordPos] = nextChar;
                            nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                    mDictRoot, &lastCandidateGroupPos);
                        }
                    }
                    ++wordPos;
                    // Now we only need to branch to the children address. Skip the probability if
                    // it's there, read pos, and break to resume the search at pos.
                    if (PatriciaTrieReadingUtils::isTerminal(lastFlags)) {
                        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot,
                                &lastCandidateGroupPos);
                    }
                    pos = PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                            mDictRoot, lastFlags, &lastCandidateGroupPos);
                    break;
                } else {
                    // Here is a little tricky part: we come here if we found out that all children
                    // addresses in this group are bigger than the address we are searching for.
                    // Should we conclude the word is not in the dictionary? No! It could still be
                    // one of the remaining chargroups in this node, so we have to keep looking in
                    // this node until we find it (or we realize it's not there either, in which
                    // case it's actually not in the dictionary). Pass the end of this group, ready
                    // to start the next one.
                    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                                mDictRoot, flags, &pos);
                    }
                    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
                        mShortcutListPolicy.skipAllShortcuts(&pos);
                    }
                    if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
                        mBigramListPolicy.skipAllBigrams(&pos);
                    }
                }
            } else {
                // If we did not find it, we should record the last children address for the next
                // iteration.
                if (hasChildren) lastCandidateGroupPos = startPos;
                // Now skip the end of this group (children pos and the attributes if any) so that
                // our pos is after the end of this char group, at the start of the next one.
                if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                    PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                            mDictRoot, flags, &pos);
                }
                if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
                    mShortcutListPolicy.skipAllShortcuts(&pos);
                }
                if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
                    mBigramListPolicy.skipAllBigrams(&pos);
                }
            }

        }
    }
    // If we have looked through all the chargroups and found no match, the nodePos is
    // not the position of a terminal in this dictionary.
    return 0;
}

// This function gets the position of the terminal node of the exact matching word in the
// dictionary. If no match is found, it returns NOT_A_VALID_WORD_POS.
int PatriciaTriePolicy::getTerminalNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    int pos = getRootPosition();
    int wordPos = 0;

    while (true) {
        // If we already traversed the tree further than the word is long, there means
        // there was no match (or we would have found it).
        if (wordPos >= length) return NOT_A_VALID_WORD_POS;
        int charGroupCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(mDictRoot,
                &pos);
        const int wChar = forceLowerCaseSearch
                ? CharUtils::toLowerCase(inWord[wordPos]) : inWord[wordPos];
        while (true) {
            // If there are no more character groups in this node, it means we could not
            // find a matching character for this depth, therefore there is no match.
            if (0 >= charGroupCount) return NOT_A_VALID_WORD_POS;
            const int charGroupPos = pos;
            const PatriciaTrieReadingUtils::NodeFlags flags =
                    PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
            int character = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(mDictRoot,
                    &pos);
            if (character == wChar) {
                // This is the correct node. Only one character group may start with the same
                // char within a node, so either we found our match in this node, or there is
                // no match and we can return NOT_A_VALID_WORD_POS. So we will check all the
                // characters in this character group indeed does match.
                if (PatriciaTrieReadingUtils::hasMultipleChars(flags)) {
                    character = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(mDictRoot,
                            &pos);
                    while (NOT_A_CODE_POINT != character) {
                        ++wordPos;
                        // If we shoot the length of the word we search for, or if we find a single
                        // character that does not match, as explained above, it means the word is
                        // not in the dictionary (by virtue of this chargroup being the only one to
                        // match the word on the first character, but not matching the whole word).
                        if (wordPos >= length) return NOT_A_VALID_WORD_POS;
                        if (inWord[wordPos] != character) return NOT_A_VALID_WORD_POS;
                        character = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                mDictRoot, &pos);
                    }
                }
                // If we come here we know that so far, we do match. Either we are on a terminal
                // and we match the length, in which case we found it, or we traverse children.
                // If we don't match the length AND don't have children, then a word in the
                // dictionary fully matches a prefix of the searched word but not the full word.
                ++wordPos;
                if (PatriciaTrieReadingUtils::isTerminal(flags)) {
                    if (wordPos == length) {
                        return charGroupPos;
                    }
                    PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
                }
                if (!PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                    return NOT_A_VALID_WORD_POS;
                }
                // We have children and we are still shorter than the word we are searching for, so
                // we need to traverse children. Put the pointer on the children position, and
                // break
                pos = PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot,
                        flags, &pos);
                break;
            } else {
                // This chargroup does not match, so skip the remaining part and go to the next.
                if (PatriciaTrieReadingUtils::hasMultipleChars(flags)) {
                    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH,
                            &pos);
                }
                if (PatriciaTrieReadingUtils::isTerminal(flags)) {
                    PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
                }
                if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                    PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot,
                            flags, &pos);
                }
                if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
                    mShortcutListPolicy.skipAllShortcuts(&pos);
                }
                if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
                    mBigramListPolicy.skipAllBigrams(&pos);
                }
            }
            --charGroupCount;
        }
    }
}

int PatriciaTriePolicy::getUnigramProbability(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_PROBABILITY;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    if (!PatriciaTrieReadingUtils::isTerminal(flags)) {
        return NOT_A_PROBABILITY;
    }
    if (PatriciaTrieReadingUtils::isNotAWord(flags)
            || PatriciaTrieReadingUtils::isBlacklisted(flags)) {
        // If this is not a word, or if it's a blacklisted entry, it should behave as
        // having no probability outside of the suggestion process (where it should be used
        // for shortcuts).
        return NOT_A_PROBABILITY;
    }
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    return PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
}

int PatriciaTriePolicy::getShortcutPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot, flags, &pos);
    }
    return pos;
}

int PatriciaTriePolicy::getBigramsPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasBigrams(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot, flags, &pos);
    }
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        mShortcutListPolicy.skipAllShortcuts(&pos);;
    }
    return pos;
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode,
        const int nodePos, DicNodeVector *childDicNodes) const {
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    const int mergedNodeCodePointCount = PatriciaTrieReadingUtils::getCharsAndAdvancePosition(
            mDictRoot, flags, MAX_WORD_LENGTH, mergedNodeCodePoints, &pos);
    const int probability = (PatriciaTrieReadingUtils::isTerminal(flags))?
            PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos)
                    : NOT_A_PROBABILITY;
    const int childrenPos = PatriciaTrieReadingUtils::hasChildrenInFlags(flags) ?
            PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                    mDictRoot, flags, &pos) : NOT_A_DICT_POS;
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        getShortcutsStructurePolicy()->skipAllShortcuts(&pos);
    }
    if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
        getBigramsStructurePolicy()->skipAllBigrams(&pos);
    }
    childDicNodes->pushLeavingChild(dicNode, nodePos, childrenPos, probability,
            PatriciaTrieReadingUtils::isTerminal(flags),
            PatriciaTrieReadingUtils::hasChildrenInFlags(flags),
            PatriciaTrieReadingUtils::isBlacklisted(flags) ||
                    PatriciaTrieReadingUtils::isNotAWord(flags),
            mergedNodeCodePointCount, mergedNodeCodePoints);
    return pos;
}

} // namespace latinime
