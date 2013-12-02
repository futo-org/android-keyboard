/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"

#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

const int DynamicBigramListPolicy::CONTINUING_BIGRAM_LINK_COUNT_LIMIT = 10000;
const int DynamicBigramListPolicy::BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT = 100000;

void DynamicBigramListPolicy::getNextBigram(int *const outBigramPos, int *const outProbability,
        bool *const outHasNext, int *const bigramEntryPos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramEntryPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *bigramEntryPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int originalBigramPos;
    BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(buffer, &bigramFlags,
            &originalBigramPos, bigramEntryPos);
    if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
        originalBigramPos += mBuffer->getOriginalBufferSize();
    }
    *outProbability = BigramListReadWriteUtils::getProbabilityFromFlags(bigramFlags);
    *outHasNext = BigramListReadWriteUtils::hasNext(bigramFlags);
    if (mIsDecayingDict && !ForgettingCurveUtils::isValidEncodedProbability(*outProbability)) {
        // This bigram is too weak to output.
        *outBigramPos = NOT_A_DICT_POS;
    } else {
        *outBigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
    }
    if (usesAdditionalBuffer) {
        *bigramEntryPos += mBuffer->getOriginalBufferSize();
    }
}

void DynamicBigramListPolicy::skipAllBigrams(int *const bigramListPos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramListPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *bigramListPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::skipExistingBigrams(buffer, bigramListPos);
    if (usesAdditionalBuffer) {
        *bigramListPos += mBuffer->getOriginalBufferSize();
    }
}

int DynamicBigramListPolicy::followBigramLinkAndGetCurrentBigramPtNodePos(
        const int originalBigramPos) const {
    if (originalBigramPos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, this /* bigramsPolicy */, mShortcutPolicy);
    int currentPos = NOT_A_DICT_POS;
    int bigramLinkCount = 0;
    int bigramLinkedNodePos = originalBigramPos;
    do {
        currentPos = bigramLinkedNodePos;
        const PtNodeParams ptNodeParams(nodeReader.fetchNodeInfoInBufferFromPtNodePos(currentPos));
        bigramLinkedNodePos = ptNodeParams.getBigramLinkedNodePos();
        bigramLinkCount++;
        if (bigramLinkCount > CONTINUING_BIGRAM_LINK_COUNT_LIMIT) {
            AKLOGE("Bigram link is invalid. start position: %d", originalBigramPos);
            ASSERT(false);
            return NOT_A_DICT_POS;
        }
        bigramLinkedNodePos = ptNodeParams.getBigramLinkedNodePos();
    } while (bigramLinkedNodePos != NOT_A_DICT_POS);
    return currentPos;
}

} // namespace latinime
