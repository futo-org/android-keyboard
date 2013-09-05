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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"

namespace latinime {

bool DynamicPatriciaTrieWritingHelper::addUnigramWord(
        DynamicPatriciaTrieReadingHelper *const readingHelper,
        const int *const wordCodePoints, const int codePointCount, const int probability) {
    while (!readingHelper->isEnd()) {
        const int matchedCodePointCount = readingHelper->getPrevTotalCodePointCount();
        if (!readingHelper->isMatchedCodePoint(0 /* index */,
                wordCodePoints[matchedCodePointCount])) {
            // The first code point is different from target code point. Skip this node and read
            // the next sibling node.
            readingHelper->readNextSiblingNode();
            continue;
        }
        // Check following merged node code points.
        const DynamicPatriciaTrieNodeReader *const nodeReader = readingHelper->getNodeReader();
        const int nodeCodePointCount = nodeReader->getCodePointCount();
        for (int j = 1; j < nodeCodePointCount; ++j) {
            const int nextIndex = matchedCodePointCount + j;
            if (nextIndex >= codePointCount) {
                // TODO: split current node after j - 1, create child and make this terminal.
                return false;
            }
            if (!readingHelper->isMatchedCodePoint(j,
                    wordCodePoints[matchedCodePointCount + j])) {
                // TODO: split current node after j - 1 and create two children.
                return false;
            }
        }
        // All characters are matched.
        if (codePointCount == readingHelper->getTotalCodePointCount()) {
            if (nodeReader->isTerminal()) {
                // TODO: Update probability.
            } else {
                // TODO: Make it terminal and update probability.
            }
            return false;
        }
        if (!nodeReader->hasChildren()) {
            // TODO: Create children node array and add new node as a child.
            return false;
        }
        // Advance to the children nodes.
        readingHelper->readChildNode();
    }
    if (readingHelper->isError()) {
        // The dictionary is invalid.
        return false;
    }
    // TODO: add at the last position of the node array.
    return false;
}

bool DynamicPatriciaTrieWritingHelper::addBigramWords(const int word0Pos, const int word1Pos,
        const int probability) {
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(word0Pos);
    if (nodeReader.isDeleted()) {
        return false;
    }
    // TODO: Implement.
    return false;
}

// Remove a bigram relation from word0Pos to word1Pos.
bool DynamicPatriciaTrieWritingHelper::removeBigramWords(const int word0Pos, const int word1Pos) {
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(word0Pos);
    if (nodeReader.isDeleted() || nodeReader.getBigramsPos() == NOT_A_DICT_POS) {
        return false;
    }
    // TODO: Implement.
    return false;
}

} // namespace latinime
