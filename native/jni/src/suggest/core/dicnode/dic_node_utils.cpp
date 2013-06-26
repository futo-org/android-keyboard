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

#include <cstring>
#include <vector>

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/binary_format.h"
#include "suggest/core/dictionary/multi_bigram_map.h"
#include "suggest/core/dictionary/probability_utils.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "utils/char_utils.h"

namespace latinime {

///////////////////////////////
// Node initialization utils //
///////////////////////////////

/* static */ void DicNodeUtils::initAsRoot(const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int prevWordNodePos, DicNode *const newRootNode) {
    const int rootPos = binaryDictionaryInfo->getRootPosition();
    const int childrenPos = rootPos;
    newRootNode->initAsRoot(rootPos, childrenPos, prevWordNodePos);
}

/*static */ void DicNodeUtils::initAsRootWithPreviousWord(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        DicNode *const prevWordLastNode, DicNode *const newRootNode) {
    const int rootPos = binaryDictionaryInfo->getRootPosition();
    const int childrenPos = rootPos;
    newRootNode->initAsRootWithPreviousWord(prevWordLastNode, rootPos, childrenPos);
}

/* static */ void DicNodeUtils::initByCopy(DicNode *srcNode, DicNode *destNode) {
    destNode->initByCopy(srcNode);
}

///////////////////////////////////
// Traverse node expansion utils //
///////////////////////////////////

/* static */ void DicNodeUtils::createAndGetPassingChildNode(DicNode *dicNode,
        const ProximityInfoState *pInfoState, const int pointIndex, const bool exactOnly,
        DicNodeVector *childDicNodes) {
    // Passing multiple chars node. No need to traverse child
    const int codePoint = dicNode->getNodeTypedCodePoint();
    const int baseLowerCaseCodePoint = CharUtils::toBaseLowerCase(codePoint);
    const bool isMatch = isMatchedNodeCodePoint(pInfoState, pointIndex, exactOnly, codePoint);
    if (isMatch || CharUtils::isIntentionalOmissionCodePoint(baseLowerCaseCodePoint)) {
        childDicNodes->pushPassingChild(dicNode);
    }
}

/* static */ int DicNodeUtils::createAndGetLeavingChildNode(DicNode *dicNode, int pos,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const ProximityInfoState *pInfoState, const int pointIndex, const bool exactOnly,
        const std::vector<int> *const codePointsFilter, const ProximityInfo *const pInfo,
        DicNodeVector *childDicNodes) {
    int nextPos = pos;
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &pos);
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    const bool isTerminal = (0 != (BinaryFormat::FLAG_IS_TERMINAL & flags));
    const bool hasChildren = BinaryFormat::hasChildrenInFlags(flags);

    int codePoint = BinaryFormat::getCodePointAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &pos);
    ASSERT(NOT_A_CODE_POINT != codePoint);
    // TODO: optimize this
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    uint16_t mergedNodeCodePointCount = 0;
    mergedNodeCodePoints[mergedNodeCodePointCount++] = codePoint;

    do {
        const int nextCodePoint = hasMultipleChars
                ? BinaryFormat::getCodePointAndForwardPointer(
                        binaryDictionaryInfo->getDictRoot(), &pos) : NOT_A_CODE_POINT;
        const bool isLastChar = (NOT_A_CODE_POINT == nextCodePoint);
        if (!isLastChar) {
            mergedNodeCodePoints[mergedNodeCodePointCount++] = nextCodePoint;
        }
        codePoint = nextCodePoint;
    } while (NOT_A_CODE_POINT != codePoint);

    const int probability = isTerminal ? BinaryFormat::readProbabilityWithoutMovingPointer(
            binaryDictionaryInfo->getDictRoot(), pos) : -1;
    pos = BinaryFormat::skipProbability(flags, pos);
    int childrenPos = hasChildren ? BinaryFormat::readChildrenPosition(
            binaryDictionaryInfo->getDictRoot(), flags, pos) : 0;
    const int attributesPos = BinaryFormat::skipChildrenPosition(flags, pos);
    const int siblingPos = BinaryFormat::skipChildrenPosAndAttributes(
            binaryDictionaryInfo->getDictRoot(), flags, pos);

    if (isDicNodeFilteredOut(mergedNodeCodePoints[0], pInfo, codePointsFilter)) {
        return siblingPos;
    }
    if (!isMatchedNodeCodePoint(pInfoState, pointIndex, exactOnly, mergedNodeCodePoints[0])) {
        return siblingPos;
    }
    childDicNodes->pushLeavingChild(dicNode, nextPos, flags, childrenPos, attributesPos,
            probability, isTerminal, hasChildren, mergedNodeCodePointCount, mergedNodeCodePoints);
    return siblingPos;
}

/* static */ bool DicNodeUtils::isDicNodeFilteredOut(const int nodeCodePoint,
        const ProximityInfo *const pInfo, const std::vector<int> *const codePointsFilter) {
    const int filterSize = codePointsFilter ? codePointsFilter->size() : 0;
    if (filterSize <= 0) {
        return false;
    }
    if (pInfo && (pInfo->getKeyIndexOf(nodeCodePoint) == NOT_AN_INDEX
            || CharUtils::isIntentionalOmissionCodePoint(nodeCodePoint))) {
        // If normalized nodeCodePoint is not on the keyboard or skippable, this child is never
        // filtered.
        return false;
    }
    const int lowerCodePoint = CharUtils::toLowerCase(nodeCodePoint);
    const int baseLowerCodePoint = CharUtils::toBaseCodePoint(lowerCodePoint);
    // TODO: Avoid linear search
    for (int i = 0; i < filterSize; ++i) {
        // Checking if a normalized code point is in filter characters when pInfo is not
        // null. When pInfo is null, nodeCodePoint is used to check filtering without
        // normalizing.
        if ((pInfo && ((*codePointsFilter)[i] == lowerCodePoint
                || (*codePointsFilter)[i] == baseLowerCodePoint))
                        || (!pInfo && (*codePointsFilter)[i] == nodeCodePoint)) {
            return false;
        }
    }
    return true;
}

/* static */ void DicNodeUtils::createAndGetAllLeavingChildNodes(DicNode *dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const ProximityInfoState *pInfoState, const int pointIndex, const bool exactOnly,
        const std::vector<int> *const codePointsFilter, const ProximityInfo *const pInfo,
        DicNodeVector *childDicNodes) {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPos();
    const int childCount = BinaryFormat::getGroupCountAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &nextPos);
    for (int i = 0; i < childCount; i++) {
        const int filterSize = codePointsFilter ? codePointsFilter->size() : 0;
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, binaryDictionaryInfo,
                pInfoState, pointIndex, exactOnly, codePointsFilter, pInfo,
                childDicNodes);
        if (!pInfo && filterSize > 0 && childDicNodes->exceeds(filterSize)) {
            // All code points have been found.
            break;
        }
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
    if (!dicNode->isLeavingNode()) {
        DicNodeUtils::createAndGetPassingChildNode(dicNode, pInfoState, pointIndex, exactOnly,
                childDicNodes);
    } else {
        DicNodeUtils::createAndGetAllLeavingChildNodes(
                dicNode, binaryDictionaryInfo, pInfoState, pointIndex, exactOnly,
                0 /* codePointsFilter */, 0 /* pInfo */, childDicNodes);
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
    if (node->isImpossibleBigramWord()) {
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
    if (NOT_VALID_WORD == wordPos || NOT_VALID_WORD == prevWordPos) {
        // Note: Normally wordPos comes from the dictionary and should never equal NOT_VALID_WORD.
        return ProbabilityUtils::backoff(unigramProbability);
    }
    if (multiBigramMap) {
        return multiBigramMap->getBigramProbability(
                binaryDictionaryInfo, prevWordPos, wordPos, unigramProbability);
    }
    return ProbabilityUtils::backoff(unigramProbability);
}

///////////////////////////////////////
// Bigram / Unigram dictionary utils //
///////////////////////////////////////

/* static */ bool DicNodeUtils::isMatchedNodeCodePoint(const ProximityInfoState *pInfoState,
        const int pointIndex, const bool exactOnly, const int nodeCodePoint) {
    if (!pInfoState) {
        return true;
    }
    if (exactOnly) {
        return pInfoState->getPrimaryCodePointAt(pointIndex) == nodeCodePoint;
    }
    const ProximityType matchedId = pInfoState->getProximityType(pointIndex, nodeCodePoint,
            true /* checkProximityChars */);
    return isProximityChar(matchedId);
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
    actualLength1 = min(actualLength1, MAX_WORD_LENGTH - actualLength0 - 1);
    memcpy(&dest[actualLength0], src1, actualLength1 * sizeof(dest[0]));
    return actualLength0 + actualLength1;
}
} // namespace latinime
