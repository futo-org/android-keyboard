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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_policy.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

const int Ver4PatriciaTriePolicy::MARGIN_TO_REFUSE_DYNAMIC_OPERATIONS = 1024;
const int Ver4PatriciaTriePolicy::MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS =
        DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE - MARGIN_TO_REFUSE_DYNAMIC_OPERATIONS;

void Ver4PatriciaTriePolicy::createAndGetAllChildDicNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mDictBuffer, &mNodeReader);
    readingHelper.initWithPtNodeArrayPos(dicNode->getChildrenPtNodeArrayPos());
    while (!readingHelper.isEnd()) {
        const PtNodeParams ptNodeParams = readingHelper.getPtNodeParams();
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
                ptNodeParams.isBlacklisted()
                        || ptNodeParams.isNotAWord() /* isBlacklistedOrNotAWord */,
                ptNodeParams.getCodePointCount(), ptNodeParams.getCodePoints());
        readingHelper.readNextSiblingNode(ptNodeParams);
    }
}

int Ver4PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    DynamicPatriciaTrieReadingHelper readingHelper(&mDictBuffer, &mNodeReader);
    readingHelper.initWithPtNodePos(ptNodePos);
    return readingHelper.getCodePointsAndProbabilityAndReturnCodePointCount(
            maxCodePointCount, outCodePoints, outUnigramProbability);
}

int Ver4PatriciaTriePolicy::getTerminalPtNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    DynamicPatriciaTrieReadingHelper readingHelper(&mDictBuffer, &mNodeReader);
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    return readingHelper.getTerminalPtNodePositionOfWord(inWord, length, forceLowerCaseSearch);
}

int Ver4PatriciaTriePolicy::getProbability(const int unigramProbability,
        const int bigramProbability) const {
    if (mHeaderPolicy.isDecayingDict()) {
        // Both probabilities are encoded. Decode them and get probability.
        return ForgettingCurveUtils::getProbability(unigramProbability, bigramProbability);
    } else {
        if (unigramProbability == NOT_A_PROBABILITY) {
            return NOT_A_PROBABILITY;
        } else if (bigramProbability == NOT_A_PROBABILITY) {
            return ProbabilityUtils::backoff(unigramProbability);
        } else {
            // bigramProbability is a bigram probability delta.
            return ProbabilityUtils::computeProbabilityForBigram(unigramProbability,
                    bigramProbability);
        }
    }
}

int Ver4PatriciaTriePolicy::getUnigramProbabilityOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_PROBABILITY;
    }
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted() || ptNodeParams.isBlacklisted() || ptNodeParams.isNotAWord()) {
        return NOT_A_PROBABILITY;
    }
    return getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY);
}

int Ver4PatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return mBuffers.get()->getShortcutDictContent()->getShortcutListHeadPos(
            ptNodeParams.getTerminalId());
}

int Ver4PatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    const PtNodeParams ptNodeParams(mNodeReader.fetchNodeInfoInBufferFromPtNodePos(ptNodePos));
    if (ptNodeParams.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return mBuffers.get()->getBigramDictContent()->getBigramListHeadPos(
            ptNodeParams.getTerminalId());
}

bool Ver4PatriciaTriePolicy::addUnigramWord(const int *const word, const int length,
        const int probability) {
    if (!mBuffers.get()->isUpdatable()) {
        AKLOGI("Warning: addUnigramWord() is called for non-updatable dictionary.");
        return false;
    }
    if (mDictBuffer.getTailPosition() >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update. Dictionary size: %d",
                mDictBuffer.getTailPosition());
        return false;
    }
    DynamicPatriciaTrieReadingHelper readingHelper(&mDictBuffer, &mNodeReader);
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

bool Ver4PatriciaTriePolicy::addBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1, const int probability) {
    if (!mBuffers.get()->isUpdatable()) {
        AKLOGI("Warning: addBigramWords() is called for non-updatable dictionary.");
        return false;
    }
    if (mDictBuffer.getTailPosition() >= MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS) {
        AKLOGE("The dictionary is too large to dynamically update. Dictionary size: %d",
                mDictBuffer.getTailPosition());
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

bool Ver4PatriciaTriePolicy::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    // TODO: Implement.
    return false;
}

void Ver4PatriciaTriePolicy::flush(const char *const filePath) {
    // TODO: Implement.
}

void Ver4PatriciaTriePolicy::flushWithGC(const char *const filePath) {
    // TODO: Implement.
}

bool Ver4PatriciaTriePolicy::needsToRunGC(const bool mindsBlockByGC) const {
    // TODO: Implement.
    return false;
}

void Ver4PatriciaTriePolicy::getProperty(const char *const query, char *const outResult,
        const int maxResultLength) {
    // TODO: Implement.
}

} // namespace latinime
