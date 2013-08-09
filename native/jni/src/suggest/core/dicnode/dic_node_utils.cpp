/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "suggest/core/dicnode/dic_node_utils.h"

#include <cstring>

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_proximity_filter.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/multi_bigram_map.h"
#include "suggest/core/dictionary/probability_utils.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "utils/char_utils.h"

namespace latinime {

///////////////////////////////
// Node initialization utils //
///////////////////////////////

/* static */ void DicNodeUtils::initAsRoot(const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int prevWordNodePos, DicNode *const newRootNode) {
    newRootNode->initAsRoot(binaryDictionaryInfo->getStructurePolicy()->getRootPosition(),
            prevWordNodePos);
}

/*static */ void DicNodeUtils::initAsRootWithPreviousWord(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        DicNode *const prevWordLastNode, DicNode *const newRootNode) {
    newRootNode->initAsRootWithPreviousWord(
            prevWordLastNode, binaryDictionaryInfo->getStructurePolicy()->getRootPosition());
}

/* static */ void DicNodeUtils::initByCopy(DicNode *srcNode, DicNode *destNode) {
    destNode->initByCopy(srcNode);
}

///////////////////////////////////
// Traverse node expansion utils //
///////////////////////////////////

/* static */ void DicNodeUtils::createAndGetPassingChildNode(DicNode *dicNode,
        const DicNodeProximityFilter *const childrenFilter,
        DicNodeVector *childDicNodes) {
    // Passing multiple chars node. No need to traverse child
    const int codePoint = dicNode->getNodeTypedCodePoint();
    const int baseLowerCaseCodePoint = CharUtils::toBaseLowerCase(codePoint);
    if (!childrenFilter->isFilteredOut(codePoint)
            || CharUtils::isIntentionalOmissionCodePoint(baseLowerCaseCodePoint)) {
        childDicNodes->pushPassingChild(dicNode);
    }
}

/* static */ void DicNodeUtils::getAllChildDicNodes(DicNode *dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo, DicNodeVector *childDicNodes) {
    getProximityChildDicNodes(dicNode, binaryDictionaryInfo, 0, 0, false, childDicNodes);
}

/* static */ void DicNodeUtils::getProximityChildDicNodes(DicNode *dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const ProximityInfoState *pInfoState, const int pointIndex, bool exactOnly,
        DicNodeVector *childDicNodes) {
    if (dicNode->isTotalInputSizeExceedingLimit()) {
        return;
    }
    const DicNodeProximityFilter childrenFilter(pInfoState, pointIndex, exactOnly);
    if (!dicNode->isLeavingNode()) {
        DicNodeUtils::createAndGetPassingChildNode(dicNode, &childrenFilter, childDicNodes);
    } else {
        binaryDictionaryInfo->getStructurePolicy()->createAndGetAllChildNodes(dicNode,
                &childrenFilter, childDicNodes);
    }
}

///////////////////
// Scoring utils //
///////////////////
/**
 * Computes the combined bigram / unigram cost for the given dicNode.
 */
/* static */ float DicNodeUtils::getBigramNodeImprobability(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const DicNode *const node, MultiBigramMap *multiBigramMap) {
    if (node->hasMultipleWords() && !node->isValidMultipleWordSuggestion()) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    const int probability = getBigramNodeProbability(binaryDictionaryInfo, node, multiBigramMap);
    // TODO: This equation to calculate the improbability looks unreasonable.  Investigate this.
    const float cost = static_cast<float>(MAX_PROBABILITY - probability)
            / static_cast<float>(MAX_PROBABILITY);
    return cost;
}

/* static */ int DicNodeUtils::getBigramNodeProbability(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const DicNode *const node, MultiBigramMap *multiBigramMap) {
    const int unigramProbability = node->getProbability();
    const int wordPos = node->getPos();
    const int prevWordPos = node->getPrevWordPos();
    if (NOT_A_VALID_WORD_POS == wordPos || NOT_A_VALID_WORD_POS == prevWordPos) {
        // Note: Normally wordPos comes from the dictionary and should never equal
        // NOT_A_VALID_WORD_POS.
        return ProbabilityUtils::backoff(unigramProbability);
    }
    if (multiBigramMap) {
        return multiBigramMap->getBigramProbability(binaryDictionaryInfo->getStructurePolicy(),
                prevWordPos, wordPos, unigramProbability);
    }
    return ProbabilityUtils::backoff(unigramProbability);
}

////////////////
// Char utils //
////////////////

// TODO: Move to char_utils?
/* static */ int DicNodeUtils::appendTwoWords(const int *const src0, const int16_t length0,
        const int *const src1, const int16_t length1, int *dest) {
    int actualLength0 = 0;
    for (int i = 0; i < length0; ++i) {
        if (src0[i] == 0) {
            break;
        }
        actualLength0 = i + 1;
    }
    actualLength0 = min(actualLength0, MAX_WORD_LENGTH);
    memcpy(dest, src0, actualLength0 * sizeof(dest[0]));
    if (!src1 || length1 == 0) {
        return actualLength0;
    }
    int actualLength1 = 0;
    for (int i = 0; i < length1; ++i) {
        if (src1[i] == 0) {
            break;
        }
        actualLength1 = i + 1;
    }
    actualLength1 = min(actualLength1, MAX_WORD_LENGTH - actualLength0);
    memcpy(&dest[actualLength0], src1, actualLength1 * sizeof(dest[0]));
    return actualLength0 + actualLength1;
}
} // namespace latinime
