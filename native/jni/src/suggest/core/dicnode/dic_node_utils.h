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

class DicNode;
class DicNodeVector;
class DictionaryStructureWithBufferPolicy;
class MultiBigramMap;

class DicNodeUtils {
 public:
    static int appendTwoWords(const int *src0, const int16_t length0, const int *src1,
            const int16_t length1, int *dest);
    static void initAsRoot(
            const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
            const int prevWordNodePos, DicNode *newRootNode);
    static void initAsRootWithPreviousWord(
            const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
            DicNode *prevWordLastNode, DicNode *newRootNode);
    static void initByCopy(DicNode *srcNode, DicNode *destNode);
    static void getAllChildDicNodes(DicNode *dicNode,
            const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
            DicNodeVector *childDicNodes);
    static float getBigramNodeImprobability(
            const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
            const DicNode *const node, MultiBigramMap *const multiBigramMap);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicNodeUtils);
    // Max number of bigrams to look up
    static const int MAX_BIGRAMS_CONSIDERED_PER_CONTEXT = 500;

    static int getBigramNodeProbability(
            const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy,
            const DicNode *const node, MultiBigramMap *multiBigramMap);
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_UTILS_H
