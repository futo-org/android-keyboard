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

#ifndef LATINIME_DICTIONARY_STRUCTURE_POLICY_H
#define LATINIME_DICTIONARY_STRUCTURE_POLICY_H

#include "defines.h"

namespace latinime {

class BinaryDictionaryInfo;
class DicNode;
class DicNodeVector;

/*
 * This class abstracts structure of dictionaries.
 * Implement this policy to support additional dictionaries.
 */
class DictionaryStructurePolicy {
 public:
    // This provides a filtering method for filtering new node.
    class NodeFilter {
     public:
        virtual bool isFilteredOut(const int codePoint) const = 0;

     protected:
        NodeFilter() {}
        virtual ~NodeFilter() {}

     private:
        DISALLOW_COPY_AND_ASSIGN(NodeFilter);
    };

    virtual int getRootPosition() const = 0;

    virtual void createAndGetAllChildNodes(const DicNode *const dicNode,
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const = 0;

    virtual int getCodePointsAndProbabilityAndReturnCodePointCount(
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int nodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const = 0;

    virtual int getTerminalNodePositionOfWord(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const = 0;

    virtual int getUnigramProbability(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int nodePos) const = 0;

    virtual int getShortcutPositionOfNode(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int nodePos) const = 0;

    virtual int getBigramsPositionOfNode(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int nodePos) const = 0;

 protected:
    DictionaryStructurePolicy() {}
    virtual ~DictionaryStructurePolicy() {}

 private:
    DISALLOW_COPY_AND_ASSIGN(DictionaryStructurePolicy);
};
} // namespace latinime
#endif /* LATINIME_DICTIONARY_STRUCTURE_POLICY_H */
