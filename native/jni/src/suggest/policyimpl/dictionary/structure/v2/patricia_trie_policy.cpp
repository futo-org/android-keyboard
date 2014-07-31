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


#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_policy.h"

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_bigrams_iterator.h"
#include "suggest/core/dictionary/ngram_listener.h"
#include "suggest/core/session/prev_words_info.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/dynamic_pt_reading_helper.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"
#include "utils/char_utils.h"

namespace latinime {

void PatriciaTriePolicy::createAndGetAllChildDicNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPtNodeArrayPos();
    if (nextPos < 0 || nextPos >= mDictBufferSize) {
        AKLOGE("Children PtNode array position is invalid. pos: %d, dict size: %d",
                nextPos, mDictBufferSize);
        mIsCorrupted = true;
        ASSERT(false);
        return;
    }
    const int childCount = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(
            mDictRoot, &nextPos);
    for (int i = 0; i < childCount; i++) {
        if (nextPos < 0 || nextPos >= mDictBufferSize) {
            AKLOGE("Child PtNode position is invalid. pos: %d, dict size: %d, childCount: %d / %d",
                    nextPos, mDictBufferSize, i, childCount);
            mIsCorrupted = true;
            ASSERT(false);
            return;
        }
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, childDicNodes);
    }
}

// This retrieves code points and the probability of the word by its terminal position.
// Due to the fact that words are ordered in the dictionary in a strict breadth-first order,
// it is possible to check for this with advantageous complexity. For each PtNode array, we search
// for PtNodes with children and compare the children position with the position we look for.
// When we shoot the position we look for, it means the word we look for is in the children
// of the previous PtNode. The only tricky part is the fact that if we arrive at the end of a
// PtNode array with the last PtNode's children position still less than what we are searching for,
// we must descend the last PtNode's children (for example, if the word we are searching for starts
// with a z, it's the last PtNode of the root array, so all children addresses will be smaller
// than the position we look for, and we have to descend the z PtNode).
/* Parameters :
 * ptNodePos: the byte position of the terminal PtNode of the word we are searching for (this is
 *   what is stored as the "bigram position" in each bigram)
 * outCodePoints: an array to write the found word, with MAX_WORD_LENGTH size.
 * outUnigramProbability: a pointer to an int to write the probability into.
 * Return value : the code point count, of 0 if the word was not found.
 */
// TODO: Split this function to be more readable
int PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    int pos = getRootPosition();
    int wordPos = 0;
    // One iteration of the outer loop iterates through PtNode arrays. As stated above, we will
    // only traverse PtNodes that are actually a part of the terminal we are searching, so each
    // time we enter this loop we are one depth level further than last time.
    // The only reason we count PtNodes is because we want to reduce the probability of infinite
    // looping in case there is a bug. Since we know there is an upper bound to the depth we are
    // supposed to traverse, it does not hurt to count iterations.
    for (int loopCount = maxCodePointCount; loopCount > 0; --loopCount) {
        int lastCandidatePtNodePos = 0;
        // Let's loop through PtNodes in this PtNode array searching for either the terminal
        // or one of its ascendants.
        if (pos < 0 || pos >= mDictBufferSize) {
            AKLOGE("PtNode array position is invalid. pos: %d, dict size: %d",
                    pos, mDictBufferSize);
            mIsCorrupted = true;
            ASSERT(false);
            *outUnigramProbability = NOT_A_PROBABILITY;
            return 0;
        }
        for (int ptNodeCount = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(
                mDictRoot, &pos); ptNodeCount > 0; --ptNodeCount) {
            const int startPos = pos;
            if (pos < 0 || pos >= mDictBufferSize) {
                AKLOGE("PtNode position is invalid. pos: %d, dict size: %d", pos, mDictBufferSize);
                mIsCorrupted = true;
                ASSERT(false);
                *outUnigramProbability = NOT_A_PROBABILITY;
                return 0;
            }
            const PatriciaTrieReadingUtils::NodeFlags flags =
                    PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
            const int character = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                    mDictRoot, &pos);
            if (ptNodePos == startPos) {
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
            // We need to skip past this PtNode, so skip any remaining code points after the
            // first and possibly the probability.
            if (PatriciaTrieReadingUtils::hasMultipleChars(flags)) {
                PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
            }
            if (PatriciaTrieReadingUtils::isTerminal(flags)) {
                PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
            }
            // The fact that this PtNode has children is very important. Since we already know
            // that this PtNode does not match, if it has no children we know it is irrelevant
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
                if (childrenPos > ptNodePos) {
                    // If the children pos is greater than the position, it means the previous
                    // PtNode, which position is stored in lastCandidatePtNodePos, was the right
                    // one.
                    found = true;
                } else if (1 >= ptNodeCount) {
                    // However if we are on the LAST PtNode of this array, and we have NOT shot the
                    // position we should descend THIS PtNode. So we trick the
                    // lastCandidatePtNodePos so that we will descend this PtNode, not the previous
                    // one.
                    lastCandidatePtNodePos = startPos;
                    found = true;
                } else {
                    // Else, we should continue looking.
                    found = false;
                }
            } else {
                // Even if we don't have children here, we could still be on the last PtNode of
                // this array. If this is the case, we should descend the last PtNode that had
                // children, and their position is already in lastCandidatePtNodePos.
                found = (1 >= ptNodeCount);
            }

            if (found) {
                // Okay, we found the PtNode we should descend. Its position is in
                // the lastCandidatePtNodePos variable, so we just re-read it.
                if (0 != lastCandidatePtNodePos) {
                    const PatriciaTrieReadingUtils::NodeFlags lastFlags =
                            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(
                                    mDictRoot, &lastCandidatePtNodePos);
                    const int lastChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                            mDictRoot, &lastCandidatePtNodePos);
                    // We copy all the characters in this PtNode to the buffer
                    outCodePoints[wordPos] = lastChar;
                    if (PatriciaTrieReadingUtils::hasMultipleChars(lastFlags)) {
                        int nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                mDictRoot, &lastCandidatePtNodePos);
                        int charCount = maxCodePointCount;
                        while (-1 != nextChar && --charCount > 0) {
                            outCodePoints[++wordPos] = nextChar;
                            nextChar = PatriciaTrieReadingUtils::getCodePointAndAdvancePosition(
                                    mDictRoot, &lastCandidatePtNodePos);
                        }
                    }
                    ++wordPos;
                    // Now we only need to branch to the children address. Skip the probability if
                    // it's there, read pos, and break to resume the search at pos.
                    if (PatriciaTrieReadingUtils::isTerminal(lastFlags)) {
                        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot,
                                &lastCandidatePtNodePos);
                    }
                    pos = PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                            mDictRoot, lastFlags, &lastCandidatePtNodePos);
                    break;
                } else {
                    // Here is a little tricky part: we come here if we found out that all children
                    // addresses in this PtNode are bigger than the address we are searching for.
                    // Should we conclude the word is not in the dictionary? No! It could still be
                    // one of the remaining PtNodes in this array, so we have to keep looking in
                    // this array until we find it (or we realize it's not there either, in which
                    // case it's actually not in the dictionary). Pass the end of this PtNode,
                    // ready to start the next one.
                    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                                mDictRoot, flags, &pos);
                    }
                    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
                        mShortcutListPolicy.skipAllShortcuts(&pos);
                    }
                    if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
                        if (!mBigramListPolicy.skipAllBigrams(&pos)) {
                            AKLOGE("Cannot skip bigrams. BufSize: %d, pos: %d.", mDictBufferSize,
                                    pos);
                            mIsCorrupted = true;
                            ASSERT(false);
                            *outUnigramProbability = NOT_A_PROBABILITY;
                            return 0;
                        }
                    }
                }
            } else {
                // If we did not find it, we should record the last children address for the next
                // iteration.
                if (hasChildren) lastCandidatePtNodePos = startPos;
                // Now skip the end of this PtNode (children pos and the attributes if any) so that
                // our pos is after the end of this PtNode, at the start of the next one.
                if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
                    PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                            mDictRoot, flags, &pos);
                }
                if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
                    mShortcutListPolicy.skipAllShortcuts(&pos);
                }
                if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
                    if (!mBigramListPolicy.skipAllBigrams(&pos)) {
                        AKLOGE("Cannot skip bigrams. BufSize: %d, pos: %d.", mDictBufferSize, pos);
                        mIsCorrupted = true;
                        ASSERT(false);
                        *outUnigramProbability = NOT_A_PROBABILITY;
                        return 0;
                    }
                }
            }

        }
    }
    // If we have looked through all the PtNodes and found no match, the ptNodePos is
    // not the position of a terminal in this dictionary.
    return 0;
}

// This function gets the position of the terminal PtNode of the exact matching word in the
// dictionary. If no match is found, it returns NOT_A_DICT_POS.
int PatriciaTriePolicy::getTerminalPtNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    DynamicPtReadingHelper readingHelper(&mPtNodeReader, &mPtNodeArrayReader);
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    const int ptNodePos =
            readingHelper.getTerminalPtNodePositionOfWord(inWord, length, forceLowerCaseSearch);
    if (readingHelper.isError()) {
        mIsCorrupted = true;
        AKLOGE("Dictionary reading error in createAndGetAllChildDicNodes().");
    }
    return ptNodePos;
}

int PatriciaTriePolicy::getProbability(const int unigramProbability,
        const int bigramProbability) const {
    // Due to space constraints, the probability for bigrams is approximate - the lower the unigram
    // probability, the worse the precision. The theoritical maximum error in resulting probability
    // is 8 - although in the practice it's never bigger than 3 or 4 in very bad cases. This means
    // that sometimes, we'll see some bigrams interverted here, but it can't get too bad.
    if (unigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else if (bigramProbability == NOT_A_PROBABILITY) {
        return ProbabilityUtils::backoff(unigramProbability);
    } else {
        return ProbabilityUtils::computeProbabilityForBigram(unigramProbability,
                bigramProbability);
    }
}

int PatriciaTriePolicy::getProbabilityOfPtNode(const int *const prevWordsPtNodePos,
        const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_PROBABILITY;
    }
    const PtNodeParams ptNodeParams =
            mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    if (ptNodeParams.isNotAWord() || ptNodeParams.isBlacklisted()) {
        // If this is not a word, or if it's a blacklisted entry, it should behave as
        // having no probability outside of the suggestion process (where it should be used
        // for shortcuts).
        return NOT_A_PROBABILITY;
    }
    if (prevWordsPtNodePos) {
        const int bigramsPosition = getBigramsPositionOfPtNode(prevWordsPtNodePos[0]);
        BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramsPosition);
        while (bigramsIt.hasNext()) {
            bigramsIt.next();
            if (bigramsIt.getBigramPos() == ptNodePos
                    && bigramsIt.getProbability() != NOT_A_PROBABILITY) {
                return getProbability(ptNodeParams.getProbability(), bigramsIt.getProbability());
            }
        }
        return NOT_A_PROBABILITY;
    }
    return getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY);
}

void PatriciaTriePolicy::iterateNgramEntries(const int *const prevWordsPtNodePos,
        NgramListener *const listener) const {
    if (!prevWordsPtNodePos) {
        return;
    }
    const int bigramsPosition = getBigramsPositionOfPtNode(prevWordsPtNodePos[0]);
    BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramsPosition);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        listener->onVisitEntry(bigramsIt.getProbability(), bigramsIt.getBigramPos());
    }
}

int PatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    return mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos).getShortcutPos();
}

int PatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    return mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos).getBigramsPos();
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode,
        const int ptNodePos, DicNodeVector *childDicNodes) const {
    PatriciaTrieReadingUtils::NodeFlags flags;
    int mergedNodeCodePointCount = 0;
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    int probability = NOT_A_PROBABILITY;
    int childrenPos = NOT_A_DICT_POS;
    int shortcutPos = NOT_A_DICT_POS;
    int bigramPos = NOT_A_DICT_POS;
    int siblingPos = NOT_A_DICT_POS;
    PatriciaTrieReadingUtils::readPtNodeInfo(mDictRoot, ptNodePos, getShortcutsStructurePolicy(),
            &mBigramListPolicy, &flags, &mergedNodeCodePointCount, mergedNodeCodePoints,
            &probability, &childrenPos, &shortcutPos, &bigramPos, &siblingPos);
    // Skip PtNodes don't start with Unicode code point because they represent non-word information.
    if (CharUtils::isInUnicodeSpace(mergedNodeCodePoints[0])) {
        childDicNodes->pushLeavingChild(dicNode, ptNodePos, childrenPos, probability,
                PatriciaTrieReadingUtils::isTerminal(flags),
                PatriciaTrieReadingUtils::hasChildrenInFlags(flags),
                PatriciaTrieReadingUtils::isBlacklisted(flags)
                        || PatriciaTrieReadingUtils::isNotAWord(flags),
                mergedNodeCodePointCount, mergedNodeCodePoints);
    }
    return siblingPos;
}

const WordProperty PatriciaTriePolicy::getWordProperty(const int *const codePoints,
        const int codePointCount) const {
    const int ptNodePos = getTerminalPtNodePositionOfWord(codePoints, codePointCount,
            false /* forceLowerCaseSearch */);
    if (ptNodePos == NOT_A_DICT_POS) {
        AKLOGE("getWordProperty was called for invalid word.");
        return WordProperty();
    }
    const PtNodeParams ptNodeParams =
            mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    std::vector<int> codePointVector(ptNodeParams.getCodePoints(),
            ptNodeParams.getCodePoints() + ptNodeParams.getCodePointCount());
    // Fetch bigram information.
    std::vector<BigramProperty> bigrams;
    const int bigramListPos = getBigramsPositionOfPtNode(ptNodePos);
    int bigramWord1CodePoints[MAX_WORD_LENGTH];
    BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramListPos);
    while (bigramsIt.hasNext()) {
        // Fetch the next bigram information and forward the iterator.
        bigramsIt.next();
        // Skip the entry if the entry has been deleted. This never happens for ver2 dicts.
        if (bigramsIt.getBigramPos() != NOT_A_DICT_POS) {
            int word1Probability = NOT_A_PROBABILITY;
            const int word1CodePointCount = getCodePointsAndProbabilityAndReturnCodePointCount(
                    bigramsIt.getBigramPos(), MAX_WORD_LENGTH, bigramWord1CodePoints,
                    &word1Probability);
            const std::vector<int> word1(bigramWord1CodePoints,
                    bigramWord1CodePoints + word1CodePointCount);
            const int probability = getProbability(word1Probability, bigramsIt.getProbability());
            bigrams.emplace_back(&word1, probability,
                    NOT_A_TIMESTAMP /* timestamp */, 0 /* level */, 0 /* count */);
        }
    }
    // Fetch shortcut information.
    std::vector<UnigramProperty::ShortcutProperty> shortcuts;
    int shortcutPos = getShortcutPositionOfPtNode(ptNodePos);
    if (shortcutPos != NOT_A_DICT_POS) {
        int shortcutTargetCodePoints[MAX_WORD_LENGTH];
        ShortcutListReadingUtils::getShortcutListSizeAndForwardPointer(mDictRoot, &shortcutPos);
        bool hasNext = true;
        while (hasNext) {
            const ShortcutListReadingUtils::ShortcutFlags shortcutFlags =
                    ShortcutListReadingUtils::getFlagsAndForwardPointer(mDictRoot, &shortcutPos);
            hasNext = ShortcutListReadingUtils::hasNext(shortcutFlags);
            const int shortcutTargetLength = ShortcutListReadingUtils::readShortcutTarget(
                    mDictRoot, MAX_WORD_LENGTH, shortcutTargetCodePoints, &shortcutPos);
            const std::vector<int> shortcutTarget(shortcutTargetCodePoints,
                    shortcutTargetCodePoints + shortcutTargetLength);
            const int shortcutProbability =
                    ShortcutListReadingUtils::getProbabilityFromFlags(shortcutFlags);
            shortcuts.emplace_back(&shortcutTarget, shortcutProbability);
        }
    }
    const UnigramProperty unigramProperty(ptNodeParams.representsBeginningOfSentence(),
            ptNodeParams.isNotAWord(), ptNodeParams.isBlacklisted(), ptNodeParams.getProbability(),
            NOT_A_TIMESTAMP /* timestamp */, 0 /* level */, 0 /* count */, &shortcuts);
    return WordProperty(&codePointVector, &unigramProperty, &bigrams);
}

int PatriciaTriePolicy::getNextWordAndNextToken(const int token, int *const outCodePoints,
        int *const outCodePointCount) {
    *outCodePointCount = 0;
    if (token == 0) {
        // Start iterating the dictionary.
        mTerminalPtNodePositionsForIteratingWords.clear();
        DynamicPtReadingHelper::TraversePolicyToGetAllTerminalPtNodePositions traversePolicy(
                &mTerminalPtNodePositionsForIteratingWords);
        DynamicPtReadingHelper readingHelper(&mPtNodeReader, &mPtNodeArrayReader);
        readingHelper.initWithPtNodeArrayPos(getRootPosition());
        readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(&traversePolicy);
    }
    const int terminalPtNodePositionsVectorSize =
            static_cast<int>(mTerminalPtNodePositionsForIteratingWords.size());
    if (token < 0 || token >= terminalPtNodePositionsVectorSize) {
        AKLOGE("Given token %d is invalid.", token);
        return 0;
    }
    const int terminalPtNodePos = mTerminalPtNodePositionsForIteratingWords[token];
    int unigramProbability = NOT_A_PROBABILITY;
    *outCodePointCount = getCodePointsAndProbabilityAndReturnCodePointCount(terminalPtNodePos,
            MAX_WORD_LENGTH, outCodePoints, &unigramProbability);
    const int nextToken = token + 1;
    if (nextToken >= terminalPtNodePositionsVectorSize) {
        // All words have been iterated.
        mTerminalPtNodePositionsForIteratingWords.clear();
        return 0;
    }
    return nextToken;
}

} // namespace latinime
