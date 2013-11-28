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

#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_policy.h"

#include <cstdio>
#include <cstring>
#include <ctime>

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_updating_helper.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

// Note that there are corresponding definitions in Java side in BinaryDictionaryTests and
// BinaryDictionaryDecayingTests.
const char *const DynamicPatriciaTriePolicy::UNIGRAM_COUNT_QUERY = "UNIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::BIGRAM_COUNT_QUERY = "BIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::MAX_UNIGRAM_COUNT_QUERY = "MAX_UNIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::MAX_BIGRAM_COUNT_QUERY = "MAX_BIGRAM_COUNT";
const char *const DynamicPatriciaTriePolicy::SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY =
        "SET_NEEDS_TO_DECAY_FOR_TESTING";
const int DynamicPatriciaTriePolicy::MAX_DICT_EXTENDED_REGION_SIZE = 32 * 1024;
const int DynamicPatriciaTriePolicy::MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS =
        DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE - 1024;

void DynamicPatriciaTriePolicy::createAndGetAllChildDicNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer, &mNodeReader);
    readingHelper.initWithPtNodeArrayPos(dicNode->getChildrenPtNodeArrayPos());
    while (!readingHelper.isEnd()) {
        const PtNodeParams ptNodeParams(readingHelper.getPtNodeParams());
        if (!ptNodeParams.isValid()) {
            break;
        }
        bool isTerminal = ptNodeParams.isTerminal() && !ptNodeParams.isDeleted();
        if (isTerminal && mHeaderPolicy.isDecayingDict()) {
            // A DecayingDict may have a terminal PtNode that has a terminal DicNode whose
            // probability is NOT_A_PROBABILITY. In such case, we don't want to treat it as a
            // valid terminal DicNode.
            isTerminal = getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY)
                    != NOT_A_PROBABILITY;
        }
        childDicNodes->pushLeavingChild(dicNode, ptNodeParams.getHeadPos(),
                ptNodeParams.getChildrenPos(), ptNodeParams.getProbability(), isTerminal,
                ptNodeParams.hasChildren(),
                ptNodeParams.isBlacklisted() || ptNodeParams.isNotAWord(),
                ptNodeParams.getCodePointCount(), ptNodeParams.getCodePoints());
        readingHelper.readNextSiblingNode(ptNodeParams);
    }
}

int DynamicPatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer, &mNodeReader);
    readingHelper.initWithPtNodePos(ptNodePos);
    return readingHelper.getCodePointsAndProbabilityAndReturnCodePointCount(maxCodePointCount,
            outCodePoints, outUnigramProbability);
}

int DynamicPatriciaTriePolicy::getTerminalPtNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer, &mNodeReader);
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    return readingHelper.getTerminalPtNodePositionOfWord(inWord, length, forceLowerCaseSearch);
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
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted() || ptNodeParams.isBlacklisted() || ptNodeParams.isNotAWord()) {
        return NOT_A_PROBABILITY;
    }
    return getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY);
}

int DynamicPatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return ptNodeParams.getShortcutPos();
}

int DynamicPatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return ptNodeParams.getBigramsPos();
}

bool DynamicPatriciaTriePolicy::addUnigramWord(const int *const word, const int length,
        const int probability) {
    if (!mMmappedBuffer.get()->isUpdatable()) {
        AKLOGI("Warning: addUnigramWord() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mBufferWithExtendableBuffer, &mNodeReader);
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    bool addedNewUnigram = false;
    if (mUpdatingHelper.addUnigramWord(&readingHelper, word, length, probability,
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
    if (!mMmappedBuffer.get()->isUpdatable()) {
        AKLOGI("Warning: addBigramWords() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    const int word0Pos = getTerminalPtNodePositionOfWord(word0, length0,
            false /* forceLowerCaseSearch */);
    if (word0Pos == NOT_A_DICT_POS) {
        return false;
    }
    const int word1Pos = getTerminalPtNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (word1Pos == NOT_A_DICT_POS) {
        return false;
    }
    bool addedNewBigram = false;
    if (mUpdatingHelper.addBigramWords(word0Pos, word1Pos, probability, &addedNewBigram)) {
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
    if (!mMmappedBuffer.get()->isUpdatable()) {
        AKLOGI("Warning: removeBigramWords() is called for non-updatable dictionary.");
        return false;
    }
    if (mBufferWithExtendableBuffer.getTailPosition()
            >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update.");
        return false;
    }
    const int word0Pos = getTerminalPtNodePositionOfWord(word0, length0,
            false /* forceLowerCaseSearch */);
    if (word0Pos == NOT_A_DICT_POS) {
        return false;
    }
    const int word1Pos = getTerminalPtNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (word1Pos == NOT_A_DICT_POS) {
        return false;
    }
    if (mUpdatingHelper.removeBigramWords(word0Pos, word1Pos)) {
        mBigramCount--;
        return true;
    } else {
        return false;
    }
}

void DynamicPatriciaTriePolicy::flush(const char *const filePath) {
    if (!mMmappedBuffer.get()->isUpdatable()) {
        AKLOGI("Warning: flush() is called for non-updatable dictionary.");
        return;
    }
    DynamicPatriciaTrieWritingHelper writingHelper(&mBufferWithExtendableBuffer,
            &mBigramListPolicy, &mShortcutListPolicy, false /* needsToDecay */);
    writingHelper.writeToDictFile(filePath, &mHeaderPolicy, mUnigramCount, mBigramCount);
}

void DynamicPatriciaTriePolicy::flushWithGC(const char *const filePath) {
    if (!mMmappedBuffer.get()->isUpdatable()) {
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
    if (!mMmappedBuffer.get()->isUpdatable()) {
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

void DynamicPatriciaTriePolicy::getProperty(const char *const query, const int queryLength,
        char *const outResult, const int maxResultLength) {
    const int compareLength = queryLength + 1 /* terminator */;
    if (strncmp(query, UNIGRAM_COUNT_QUERY, compareLength) == 0) {
        snprintf(outResult, maxResultLength, "%d", mUnigramCount);
    } else if (strncmp(query, BIGRAM_COUNT_QUERY, compareLength) == 0) {
        snprintf(outResult, maxResultLength, "%d", mBigramCount);
    } else if (strncmp(query, MAX_UNIGRAM_COUNT_QUERY, compareLength) == 0) {
        snprintf(outResult, maxResultLength, "%d",
                mHeaderPolicy.isDecayingDict() ? ForgettingCurveUtils::MAX_UNIGRAM_COUNT :
                        static_cast<int>(DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE));
    } else if (strncmp(query, MAX_BIGRAM_COUNT_QUERY, compareLength) == 0) {
        snprintf(outResult, maxResultLength, "%d",
                mHeaderPolicy.isDecayingDict() ? ForgettingCurveUtils::MAX_BIGRAM_COUNT :
                        static_cast<int>(DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE));
    } else if (strncmp(query, SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY, compareLength) == 0) {
        mNeedsToDecayForTesting = true;
    }
}

} // namespace latinime
