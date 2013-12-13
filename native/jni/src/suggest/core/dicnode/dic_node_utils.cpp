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
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/multi_bigram_map.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "utils/char_utils.h"

namespace latinime {

///////////////////////////////
// Node initialization utils //
///////////////////////////////

/* static */ void DicNodeUtils::initAsRoot(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        const int prevWordNodePos, DicNode *const newRootNode) {
    newRootNode->initAsRoot(dictionaryStructurePolicy->getRootPosition(), prevWordNodePos);
}

/*static */ void DicNodeUtils::initAsRootWithPreviousWord(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        DicNode *const prevWordLastNode, DicNode *const newRootNode) {
    newRootNode->initAsRootWithPreviousWord(
            prevWordLastNode, dictionaryStructurePolicy->getRootPosition());
}

/* static */ void DicNodeUtils::initByCopy(DicNode *srcNode, DicNode *destNode) {
    destNode->initByCopy(srcNode);
}

///////////////////////////////////
// Traverse node expansion utils //
///////////////////////////////////
/* static */ void DicNodeUtils::getAllChildDicNodes(DicNode *dicNode,
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        DicNodeVector *childDicNodes) {
    if (dicNode->isTotalInputSizeExceedingLimit()) {
        return;
    }
    if (!dicNode->isLeavingNode()) {
        childDicNodes->pushPassingChild(dicNode);
    } else {
        dictionaryStructurePolicy->createAndGetAllChildNodes(dicNode, childDicNodes);
    }
}

///////////////////
// Scoring utils //
///////////////////
/**
 * Computes the combined bigram / unigram cost for the given dicNode.
 */
/* static */ float DicNodeUtils::getBigramNodeImprobability(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        const DicNode *const node, MultiBigramMap *multiBigramMap) {
    if (node->hasMultipleWords() && !node->isValidMultipleWordSuggestion()) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    const int probability = getBigramNodeProbability(dictionaryStructurePolicy, node,
            multiBigramMap);
    // TODO: This equation to calculate the improbability looks unreasonable.  Investigate this.
    const float cost = static_cast<float>(MAX_PROBABILITY - probability)
            / static_cast<float>(MAX_PROBABILITY);
    return cost;
}

/* static */ int DicNodeUtils::getBigramNodeProbability(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        const DicNode *const node, MultiBigramMap *multiBigramMap) {
    const int unigramProbability = node->getProbability();
    const int wordPos = node->getPos();
    const int prevWordPos = node->getPrevWordPos();
    if (NOT_A_DICT_POS == wordPos || NOT_A_DICT_POS == prevWordPos) {
        // Note: Normally wordPos comes from the dictionary and should never equal
        // NOT_A_VALID_WORD_POS.
        return dictionaryStructurePolicy->getProbability(unigramProbability,
                NOT_A_PROBABILITY);
    }
    if (multiBigramMap) {
        return multiBigramMap->getBigramProbability(dictionaryStructurePolicy, prevWordPos,
                wordPos, unigramProbability);
    }
    return dictionaryStructurePolicy->getProbability(unigramProbability,
            NOT_A_PROBABILITY);
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
