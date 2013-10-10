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

    virtual int getProbability(const int unigramProbability,
            const int bigramProbability) const = 0;

    virtual int getUnigramProbabilityOfPtNode(const int nodePos) const = 0;

    virtual int getShortcutPositionOfPtNode(const int nodePos) const = 0;

    virtual int getBigramsPositionOfPtNode(const int nodePos) const = 0;

    virtual const DictionaryHeaderStructurePolicy *getHeaderStructurePolicy() const = 0;

    virtual const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const = 0;

    virtual const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const = 0;

    // Returns whether the update was success or not.
    virtual bool addUnigramWord(const int *const word, const int length,
            const int probability) = 0;

    // Returns whether the update was success or not.
    virtual bool addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability) = 0;

    // Returns whether the update was success or not.
    virtual bool removeBigramWords(const int *const word0, const int length0,
            const int *const word1, const int length1) = 0;

    virtual void flush(const char *const filePath) = 0;

    virtual void flushWithGC(const char *const filePath) = 0;

    virtual bool needsToRunGC(const bool mindsBlockByGC) const = 0;

    // Currently, this method is used only for testing. You may want to consider creating new
    // dedicated method instead of this if you want to use this in the production.
    virtual void getProperty(const char *const query, char *const outResult,
            const int maxResultLength) = 0;

 protected:
    DictionaryStructureWithBufferPolicy() {}

 private:
    DISALLOW_COPY_AND_ASSIGN(DictionaryStructureWithBufferPolicy);
};
} // namespace latinime
#endif /* LATINIME_DICTIONARY_STRUCTURE_POLICY_H */
