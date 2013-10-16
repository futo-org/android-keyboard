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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_policy.h"

namespace latinime {

void Ver4PatriciaTriePolicy::createAndGetAllChildDicNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    // TODO: Implement.
}

int Ver4PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    // TODO: Implement.
    return 0;
}

int Ver4PatriciaTriePolicy::getTerminalPtNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    // TODO: Implement.
    return NOT_A_DICT_POS;
}

int Ver4PatriciaTriePolicy::getProbability(const int unigramProbability,
        const int bigramProbability) const {
    // TODO: Implement.
    return NOT_A_PROBABILITY;
}

int Ver4PatriciaTriePolicy::getUnigramProbabilityOfPtNode(const int ptNodePos) const {
    // TODO: Implement.
    return NOT_A_PROBABILITY;
}

int Ver4PatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    // TODO: Implement.
    return NOT_A_DICT_POS;
}

int Ver4PatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    // TODO: Implement.
    return NOT_A_DICT_POS;
}

bool Ver4PatriciaTriePolicy::addUnigramWord(const int *const word, const int length,
        const int probability) {
    // TODO: Implement.
    return false;
}

bool Ver4PatriciaTriePolicy::addBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1, const int probability) {
    // TODO: Implement.
    return false;
}

bool Ver4PatriciaTriePolicy::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    // TODO: Implement.
    return false;
}

void Ver4PatriciaTriePolicy::flush(const char *const filePath) {
    // TODO: Implement.
}

void Ver4PatriciaTriePolicy::flushWithGC(const char *const filePath) {
    // TODO: Implement.
}

bool Ver4PatriciaTriePolicy::needsToRunGC(const bool mindsBlockByGC) const {
    // TODO: Implement.
    return false;
}

void Ver4PatriciaTriePolicy::getProperty(const char *const query, char *const outResult,
        const int maxResultLength) {
    // TODO: Implement.
}

} // namespace latinime
