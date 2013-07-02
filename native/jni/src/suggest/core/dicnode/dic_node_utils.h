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

#include "defines.h"

namespace latinime {

class BinaryDictionaryInfo;
class DicNode;
class DicNodeProximityFilter;
class DicNodeVector;
class ProximityInfoState;
class MultiBigramMap;

class DicNodeUtils {
 public:
    static int appendTwoWords(const int *src0, const int16_t length0, const int *src1,
            const int16_t length1, int *dest);
    static void initAsRoot(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int prevWordNodePos, DicNode *newRootNode);
    static void initAsRootWithPreviousWord(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            DicNode *prevWordLastNode, DicNode *newRootNode);
    static void initByCopy(DicNode *srcNode, DicNode *destNode);
    static void getAllChildDicNodes(DicNode *dicNode,
            const BinaryDictionaryInfo *const binaryDictionaryInfo, DicNodeVector *childDicNodes);
    static float getBigramNodeImprobability(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const DicNode *const node, MultiBigramMap *const multiBigramMap);
    // TODO: Move to private
    static void getProximityChildDicNodes(DicNode *dicNode,
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const ProximityInfoState *pInfoState, const int pointIndex, bool exactOnly,
            DicNodeVector *childDicNodes);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicNodeUtils);
    // Max number of bigrams to look up
    static const int MAX_BIGRAMS_CONSIDERED_PER_CONTEXT = 500;

    static int getBigramNodeProbability(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const DicNode *const node, MultiBigramMap *multiBigramMap);
    static void createAndGetPassingChildNode(DicNode *dicNode,
            const DicNodeProximityFilter *const childrenFilter, DicNodeVector *childDicNodes);
    static void createAndGetAllLeavingChildNodes(DicNode *dicNode,
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const DicNodeProximityFilter *const childrenFilter, DicNodeVector *childDicNodes);
    static int createAndGetLeavingChildNode(DicNode *dicNode, int pos,
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const DicNodeProximityFilter *const childrenFilter, DicNodeVector *childDicNodes);
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_UTILS_H
