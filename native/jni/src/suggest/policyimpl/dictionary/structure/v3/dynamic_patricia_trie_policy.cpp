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
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

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

} // namespace latinime
