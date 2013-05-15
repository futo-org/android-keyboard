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

#ifndef LATINIME_DIC_NODE_UTILS_H
#define LATINIME_DIC_NODE_UTILS_H

#include <stdint.h>
#include <vector>

#include "defines.h"

namespace latinime {

class DicNode;
class DicNodeVector;
class ProximityInfo;
class ProximityInfoState;
class MultiBigramMap;

class DicNodeUtils {
 public:
    static int appendTwoWords(const int *src0, const int16_t length0, const int *src1,
            const int16_t length1, int *dest);
    static void initAsRoot(const int rootPos, const uint8_t *const dicRoot,
            const int prevWordNodePos, DicNode *newRootNode);
    static void initAsRootWithPreviousWord(const int rootPos, const uint8_t *const dicRoot,
            DicNode *prevWordLastNode, DicNode *newRootNode);
    static void initByCopy(DicNode *srcNode, DicNode *destNode);
    static void getAllChildDicNodes(DicNode *dicNode, const uint8_t *const dicRoot,
            DicNodeVector *childDicNodes);
    static float getBigramNodeImprobability(const uint8_t *const dicRoot,
            const DicNode *const node, MultiBigramMap *const multiBigramMap);
    static bool isDicNodeFilteredOut(const int nodeCodePoint, const ProximityInfo *const pInfo,
            const std::vector<int> *const codePointsFilter);
    // TODO: Move to private
    static void getProximityChildDicNodes(DicNode *dicNode, const uint8_t *const dicRoot,
            const ProximityInfoState *pInfoState, const int pointIndex, bool exactOnly,
            DicNodeVector *childDicNodes);

    // TODO: Move to proximity info
    static bool isProximityChar(ProximityType type) {
        return type == MATCH_CHAR || type == PROXIMITY_CHAR || type == ADDITIONAL_PROXIMITY_CHAR;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicNodeUtils);
    // Max number of bigrams to look up
    static const int MAX_BIGRAMS_CONSIDERED_PER_CONTEXT = 500;

    static int getBigramNodeProbability(const uint8_t *const dicRoot, const DicNode *const node,
            MultiBigramMap *multiBigramMap);
    static void createAndGetPassingChildNode(DicNode *dicNode, const ProximityInfoState *pInfoState,
            const int pointIndex, const bool exactOnly, DicNodeVector *childDicNodes);
    static void createAndGetAllLeavingChildNodes(DicNode *dicNode, const uint8_t *const dicRoot,
            const ProximityInfoState *pInfoState, const int pointIndex, const bool exactOnly,
            const std::vector<int> *const codePointsFilter,
            const ProximityInfo *const pInfo, DicNodeVector *childDicNodes);
    static int createAndGetLeavingChildNode(DicNode *dicNode, int pos, const uint8_t *const dicRoot,
            const int terminalDepth, const ProximityInfoState *pInfoState, const int pointIndex,
            const bool exactOnly, const std::vector<int> *const codePointsFilter,
            const ProximityInfo *const pInfo, DicNodeVector *childDicNodes);

    // TODO: Move to proximity info
    static bool isMatchedNodeCodePoint(const ProximityInfoState *pInfoState, const int pointIndex,
            const bool exactOnly, const int nodeCodePoint);
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_UTILS_H
