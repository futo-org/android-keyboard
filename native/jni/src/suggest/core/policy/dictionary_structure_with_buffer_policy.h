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

class DicNode;
class DicNodeVector;
class DictionaryBigramsStructurePolicy;
class DictionaryHeaderStructurePolicy;
class DictionaryShortcutsStructurePolicy;

/*
 * This class abstracts structure of dictionaries.
 * Implement this policy to support additional dictionaries.
 */
class DictionaryStructureWithBufferPolicy {
 public:
    virtual ~DictionaryStructureWithBufferPolicy() {}

    virtual int getRootPosition() const = 0;

    virtual void createAndGetAllChildNodes(const DicNode *const dicNode,
            DicNodeVector *const childDicNodes) const = 0;

    virtual int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int nodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const = 0;

    virtual int getTerminalNodePositionOfWord(const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const = 0;

    virtual int getUnigramProbability(const int nodePos) const = 0;

    virtual int getShortcutPositionOfNode(const int nodePos) const = 0;

    virtual int getBigramsPositionOfNode(const int nodePos) const = 0;

    virtual const DictionaryHeaderStructurePolicy *getHeaderStructurePolicy() const = 0;

    virtual const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const = 0;

    virtual const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const = 0;

 protected:
    DictionaryStructureWithBufferPolicy() {}

 private:
    DISALLOW_COPY_AND_ASSIGN(DictionaryStructureWithBufferPolicy);
};
} // namespace latinime
#endif /* LATINIME_DICTIONARY_STRUCTURE_POLICY_H */
