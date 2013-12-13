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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_policy.h"

#include <cstdio>
#include <cstring>
#include <ctime>

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

// Note that these are corresponding definitions in Java side in BinaryDictionaryTests and
// BinaryDictionaryDecayingTests.
const char *const DynamicPatriciaTriePolicy::UNIGRAM_COUNT_QUERY = "UNIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::BIGRAM_COUNT_QUERY = "BIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::MAX_UNIGRAM_COUNT_QUERY = "MAX_UNIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::MAX_BIGRAM_COUNT_QUERY = "MAX_BIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY =
        "SET_NEEDS_TO_DECAY_FOR_TESTING";
const int DynamicPatriciaTriePolicy::MAX_DICT_EXTENDED_REGION_SIZE = 1024 * 1024;
const int DynamicPatriciaTriePolicy::MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS =
        DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE - 1024;

void DynamicPatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    readingHelper.initWithPtNodeArrayPos(dicNode->getChildrenPos());
    const DynamicPatriciaTrieNodeReader *const nodeReader = readingHelper.getNodeReader();
    while (!readingHelper.isEnd()) {
        bool isTerminal = nodeReader->isTerminal() && !nodeReader->isDeleted();
        if (isTerminal && mHeaderPolicy.isDecayingDict()) {
            // A DecayingDict may have a terminal PtNode that has a terminal DicNode whose
            // probability is NOT_A_PROBABILITY. In such case, we don't want to treat it as a
            // valid terminal DicNode.
            isTerminal = getProbability(nodeReader->getProbability(), NOT_A_PROBABILITY)
                    != NOT_A_PROBABILITY;
        }
        childDicNodes->pushLeavingChild(dicNode, nodeReader->getHeadPos(),
                nodeReader->getChildrenPos(), nodeReader->getProbability(), isTerminal,
                nodeReader->hasChildren(), nodeReader->isBlacklisted() || nodeReader->isNotAWord(),
                nodeReader->getCodePointCount(), readingHelper.getMergedNodeCodePoints());
        readingHelper.readNextSiblingNode();
    }
}

int DynamicPatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    // This method traverses parent nodes from the terminal by following parent pointers; thus,
    // node code points are stored in the buffer in the reverse order.
    int reverseCodePoints[maxCodePointCount];
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    // First, read the terminal node and get its probability.
    readingHelper.initWithPtNodePos(ptNodePos);
    if (!readingHelper.isValidTerminalNode()) {
        // Node at the ptNodePos is not a valid terminal node.
        *outUnigramProbability = NOT_A_PROBABILITY;
        return 0;
    }
    // Store terminal node probability.
    *outUnigramProbability = readingHelper.getNodeReader()->getProbability();
    // Then, following parent node link to the dictionary root and fetch node code points.
    while (!readingHelper.isEnd()) {
        if (readingHelper.getTotalCodePointCount() > maxCodePointCount) {
            // The ptNodePos is not a valid terminal node position in the dictionary.
            *outUnigramProbability = NOT_A_PROBABILITY;
            return 0;
        }
        // Store node code points to buffer in the reverse order.
        readingHelper.fetchMergedNodeCodePointsInReverseOrder(
                readingHelper.getPrevTotalCodePointCount(), reverseCodePoints);
        // Follow parent node toward the root node.
        readingHelper.readParentNode();
    }
    if (readingHelper.isError()) {
        // The node position or the dictionary is invalid.
        *outUnigramProbability = NOT_A_PROBABILITY;
        return 0;
    }
    // Reverse the stored code points to output them.
    const int codePointCount = readingHelper.getTotalCodePointCount();
    for (int i = 0; i < codePointCount; ++i) {
        outCodePoints[i] = reverseCodePoints[codePointCount - i - 1];
    }
    return codePointCount;
}

int DynamicPatriciaTriePolicy::getTerminalNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    int searchCodePoints[length];
    for (int i = 0; i < length; ++i) {
        searchCodePoints[i] = forceLowerCaseSearch ? CharUtils::toLowerCase(inWord[i]) : inWord[i];
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    const DynamicPatriciaTrieNodeReader *const nodeReader = readingHelper.getNodeReader();
    while (!readingHelper.isEnd()) {
        const int matchedCodePointCount = readingHelper.getPrevTotalCodePointCount();
        if (readingHelper.getTotalCodePointCount() > length
                || !readingHelper.isMatchedCodePoint(0 /* index */,
                        searchCodePoints[matchedCodePointCount])) {
            // Current node has too many code points or its first code point is different from
            // target code point. Skip this node and read the next sibling node.
            readingHelper.readNextSiblingNode();
            continue;
        }
        // Check following merged node code points.
        const int nodeCodePointCount = nodeReader->getCodePointCount();
        for (int j = 1; j < nodeCodePointCount; ++j) {
            if (!readingHelper.isMatchedCodePoint(
                    j, searchCodePoints[matchedCodePointCount + j])) {
                // Different code point is found. The given word is not included in the dictionary.
                return NOT_A_DICT_POS;
            }
        }
        // All characters are matched.
        if (length == readingHelper.getTotalCodePointCount()) {
            // Terminal position is found.
            return nodeReader->getHeadPos();
        }
        if (!nodeReader->hasChildren()) {
            return NOT_A_DICT_POS;
        }
        // Advance to the children nodes.
        readingHelper.readChildNode();
    }
    // If we already traversed the tree further than the word is long, there means
    // there was no match (or we would have found it).
    return NOT_A_DICT_POS;
}

int DynamicPatriciaTriePolicy::getProbability(const int unigramProbability,
        const int bigramProbability) const {
    if (mHeaderPolicy.isDecayingDict()) {
        return ForgettingCurveUtils::getProbability(unigramProbability, bigramProbability);
    } else {
        if (unigramProbability == NOT_A_PROBABILITY) {
            return NOT_A_PROBABILITY;
        } else if (bigramProbability == NOT_A_PROBABILITY) {
            return ProbabilityUtils::backoff(unigramProbability);
        } else {
            return ProbabilityUtils::computeProbabilityForBigram(unigramProbability,
                    bigramProbability);
        }
    }
}

int DynamicPatriciaTriePolicy::getUnigramProbabilityOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_PROBABILITY;
    }
    DynamicPatriciaTrieNodeReader nodeReader(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos);
    if (nodeReader.isDeleted() || nodeReader.isBlacklisted() || nodeReader.isNotAWord()) {
        return NOT_A_PROBABILITY;
    }
    return getProbability(nodeReader.getProbability(), NOT_A_PROBABILITY);
}

int DynamicPatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    DynamicPatriciaTrieNodeReader nodeReader(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getShortcutPos();
}

int DynamicPatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    DynamicPatriciaTrieNodeReader nodeReader(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getBigramsPos();
}

bool DynamicPatriciaTriePolicy::addUnigramWord(const int *const word, const int length,
        const int probability) {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: addUnigramWord() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer,
            getBigramsStructurePolicy(), getShortcutsStructurePolicy());
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &mBigramListPolicy, &mShortcutListPolicy, mHeaderPolicy.isDecayingDict());
    bool addedNewUnigram = false;
    if (writingHelper.addUnigramWord(&readingHelper, word, length, probability,
            &addedNewUnigram)) {
        if (addedNewUnigram) {
            mUnigramCount++;
        }
        return true;
    } else {
        return false;
    }
}

bool DynamicPatriciaTriePolicy::addBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1, const int probability) {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: addBigramWords() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    const int word0Pos = getTerminalNodePositionOfWord(word0, length0,
            false /* forceLowerCaseSearch */);
    if (word0Pos == NOT_A_DICT_POS) {
        return false;
    }
    const int word1Pos = getTerminalNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (word1Pos == NOT_A_DICT_POS) {
        return false;
    }
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &mBigramListPolicy, &mShortcutListPolicy, mHeaderPolicy.isDecayingDict());
    bool addedNewBigram = false;
    if (writingHelper.addBigramWords(word0Pos, word1Pos, probability, &addedNewBigram)) {
        if (addedNewBigram) {
            mBigramCount++;
        }
        return true;
    } else {
        return false;
    }
}

bool DynamicPatriciaTriePolicy::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: removeBigramWords() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    const int word0Pos = getTerminalNodePositionOfWord(word0, length0,
            false /* forceLowerCaseSearch */);
    if (word0Pos == NOT_A_DICT_POS) {
        return false;
    }
    const int word1Pos = getTerminalNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (word1Pos == NOT_A_DICT_POS) {
        return false;
    }
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &mBigramListPolicy, &mShortcutListPolicy, mHeaderPolicy.isDecayingDict());
    if (writingHelper.removeBigramWords(word0Pos, word1Pos)) {
        mBigramCount--;
        return true;
    } else {
        return false;
    }
}

void DynamicPatriciaTriePolicy::flush(const char *const filePath) {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: flush() is called for non-updatable dictionary.");
        return;
    }
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &mBigramListPolicy, &mShortcutListPolicy, false /* needsToDecay */);
    writingHelper.writeToDictFile(filePath, &mHeaderPolicy, mUnigramCount, mBigramCount);
}

void DynamicPatriciaTriePolicy::flushWithGC(const char *const filePath) {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: flushWithGC() is called for non-updatable dictionary.");
        return;
    }
    const bool needsToDecay = mHeaderPolicy.isDecayingDict()
            && (mNeedsToDecayForTesting || ForgettingCurveUtils::needsToDecay(
                    false /* mindsBlockByDecay */, mUnigramCount, mBigramCount, &mHeaderPolicy));
    DynamicBigramListPolicy bigramListPolicyForGC(&mHeaderPolicy, &mBufferWithExtendableBuffer,
            &mShortcutListPolicy, needsToDecay);
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &bigramListPolicyForGC, &mShortcutListPolicy, needsToDecay);
    writingHelper.writeToDictFileWithGC(getRootPosition(), filePath, &mHeaderPolicy);
    mNeedsToDecayForTesting = false;
}

bool DynamicPatriciaTriePolicy::needsToRunGC(const bool mindsBlockByGC) const {
    if (!mBuffer->isUpdatable()) {
        AKLOGI("Warning: needsToRunGC() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.isNearSizeLimit()) {
        // Additional buffer size is near the limit.
        return true;
    } else if (mHeaderPolicy.getExtendedRegionSize()
            + mBufferWithExtendableBuffer.getUsedAdditionalBufferSize()
                    > MAX_DICT_EXTENDED_REGION_SIZE) {
        // Total extended region size exceeds the limit.
        return true;
    } else if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS
                    && mBufferWithExtendableBuffer.getUsedAdditionalBufferSize() > 0) {
        // Needs to reduce dictionary size.
        return true;
    } else if (mHeaderPolicy.isDecayingDict()) {
        return mNeedsToDecayForTesting || ForgettingCurveUtils::needsToDecay(
                mindsBlockByGC, mUnigramCount, mBigramCount, &mHeaderPolicy);
    }
    return false;
}

void DynamicPatriciaTriePolicy::getProperty(const char *const query, char *const outResult,
        const int maxResultLength) {
    if (strncmp(query, UNIGRAM_COUNT_QUERY, maxResultLength) == 0) {
        snprintf(outResult, maxResultLength, "%d", mUnigramCount);
    } else if (strncmp(query, BIGRAM_COUNT_QUERY, maxResultLength) == 0) {
        snprintf(outResult, maxResultLength, "%d", mBigramCount);
    } else if (strncmp(query, MAX_UNIGRAM_COUNT_QUERY, maxResultLength) == 0) {
        snprintf(outResult, maxResultLength, "%d",
                mHeaderPolicy.isDecayingDict() ? ForgettingCurveUtils::MAX_UNIGRAM_COUNT :
                        static_cast<int>(DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE));
    } else if (strncmp(query, MAX_BIGRAM_COUNT_QUERY, maxResultLength) == 0) {
        snprintf(outResult, maxResultLength, "%d",
                mHeaderPolicy.isDecayingDict() ? ForgettingCurveUtils::MAX_BIGRAM_COUNT :
                        static_cast<int>(DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE));
    } else if (strncmp(query, SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY, maxResultLength) == 0) {
        mNeedsToDecayForTesting = true;
    }
}

} // namespace latinime
