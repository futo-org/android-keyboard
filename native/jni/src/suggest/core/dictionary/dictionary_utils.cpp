/*
 * Copyright (C) 2014, The Android Open Source Project
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

#include "suggest/core/dictionary/dictionary_utils.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_priority_queue.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/dictionary/digraph_utils.h"
#include "suggest/core/session/prev_words_info.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"

namespace latinime {

/* static */ int DictionaryUtils::getMaxProbabilityOfExactMatches(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        const int *const codePoints, const int codePointCount) {
    std::vector<DicNode> current;
    std::vector<DicNode> next;

    // No prev words information.
    PrevWordsInfo emptyPrevWordsInfo;
    int prevWordsPtNodePos[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    emptyPrevWordsInfo.getPrevWordsTerminalPtNodePos(dictionaryStructurePolicy,
            prevWordsPtNodePos, false /* tryLowerCaseSearch */);
    current.emplace_back();
    DicNodeUtils::initAsRoot(dictionaryStructurePolicy, prevWordsPtNodePos, &current.front());
    for (int i = 0; i < codePointCount; ++i) {
        // The base-lower input is used to ignore case errors and accent errors.
        const int codePoint = CharUtils::toBaseLowerCase(codePoints[i]);
        for (const DicNode &dicNode : current) {
            if (dicNode.isInDigraph() && dicNode.getNodeCodePoint() == codePoint) {
                next.emplace_back(dicNode);
                next.back().advanceDigraphIndex();
                continue;
            }
            processChildDicNodes(dictionaryStructurePolicy, codePoint, &dicNode, &next);
        }
        current.clear();
        current.swap(next);
    }

    int maxProbability = NOT_A_PROBABILITY;
    for (const DicNode &dicNode : current) {
        if (!dicNode.isTerminalDicNode()) {
            continue;
        }
        // dicNode can contain case errors, accent errors, intentional omissions or digraphs.
        maxProbability = std::max(maxProbability, dicNode.getProbability());
    }
    return maxProbability;
}

/* static */ void DictionaryUtils::processChildDicNodes(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
        const int inputCodePoint, const DicNode *const parentDicNode,
        std::vector<DicNode> *const outDicNodes) {
    DicNodeVector childDicNodes;
    DicNodeUtils::getAllChildDicNodes(parentDicNode, dictionaryStructurePolicy, &childDicNodes);
    for (int childIndex = 0; childIndex < childDicNodes.getSizeAndLock(); ++childIndex) {
        DicNode *const childDicNode = childDicNodes[childIndex];
        const int codePoint = CharUtils::toBaseLowerCase(childDicNode->getNodeCodePoint());
        if (inputCodePoint == codePoint) {
            outDicNodes->emplace_back(*childDicNode);
        }
        if (childDicNode->canBeIntentionalOmission()) {
            processChildDicNodes(dictionaryStructurePolicy, inputCodePoint, childDicNode,
                    outDicNodes);
        }
        if (DigraphUtils::hasDigraphForCodePoint(
                dictionaryStructurePolicy->getHeaderStructurePolicy(),
                childDicNode->getNodeCodePoint())) {
            childDicNode->advanceDigraphIndex();
            if (childDicNode->getNodeCodePoint() == codePoint) {
                childDicNode->advanceDigraphIndex();
                outDicNodes->emplace_back(*childDicNode);
            }
        }
    }
}

} // namespace latinime
