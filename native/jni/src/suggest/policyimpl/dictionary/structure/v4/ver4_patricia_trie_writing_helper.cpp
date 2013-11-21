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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_writing_helper.h"

#include <cstring>

#include "suggest/policyimpl/dictionary/bigram/ver4_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/ver4_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_writer.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

void Ver4PatriciaTrieWritingHelper::writeToDictFile(const char *const trieFilePath,
        const HeaderPolicy *const headerPolicy, const int unigramCount,
        const int bigramCount) const {
    const int dirPathBufSize = strlen(trieFilePath) + 1 /* terminator */;
    char dirPath[dirPathBufSize];
    FileUtils::getDirPath(trieFilePath, dirPathBufSize, dirPath);
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    const int extendedRegionSize = headerPolicy->getExtendedRegionSize()
            + mBuffers->getTrieBuffer()->getUsedAdditionalBufferSize();
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, false /* updatesLastUpdatedTime */,
            false /* updatesLastDecayedTime */, unigramCount, bigramCount, extendedRegionSize)) {
        AKLOGE("Cannot write header structure to buffer. updatesLastUpdatedTime: %d, "
                "updatesLastDecayedTime: %d, unigramCount: %d, bigramCount: %d, "
                "extendedRegionSize: %d", false, false, unigramCount, bigramCount,
                extendedRegionSize);
        return;
    }
    mBuffers->flushHeaderAndDictBuffers(dirPath, &headerBuffer);
}

void Ver4PatriciaTrieWritingHelper::writeToDictFileWithGC(const int rootPtNodeArrayPos,
        const char *const trieFilePath, const HeaderPolicy *const headerPolicy,
        const bool needsToDecay) {
    Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers(Ver4DictBuffers::createVer4DictBuffers());
    int unigramCount = 0;
    int bigramCount = 0;
    if (needsToDecay) {
        ForgettingCurveUtils::sTimeKeeper.setCurrentTime();
    }
    if (!runGC(rootPtNodeArrayPos, headerPolicy, dictBuffers.get(), &unigramCount, &bigramCount,
            needsToDecay)) {
        return;
    }
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            needsToDecay, unigramCount, bigramCount, 0 /* extendedRegionSize */)) {
        return;
    }
    const int dirPathBufSize = strlen(trieFilePath) + 1 /* terminator */;
    char dirPath[dirPathBufSize];
    FileUtils::getDirPath(trieFilePath, dirPathBufSize, dirPath);
    dictBuffers.get()->flushHeaderAndDictBuffers(dirPath, &headerBuffer);
}

bool Ver4PatriciaTrieWritingHelper::runGC(const int rootPtNodeArrayPos,
        const HeaderPolicy *const headerPolicy, Ver4DictBuffers *const buffersToWrite,
        int *const outUnigramCount, int *const outBigramCount, const bool needsToDecay) {
    Ver4PatriciaTrieNodeReader ptNodeReader(mBuffers->getTrieBuffer(),
            mBuffers->getProbabilityDictContent());
    Ver4BigramListPolicy bigramPolicy(mBuffers->getUpdatableBigramDictContent(),
            mBuffers->getTerminalPositionLookupTable());
    Ver4ShortcutListPolicy shortcutPolicy(mBuffers->getShortcutDictContent(),
            mBuffers->getTerminalPositionLookupTable());
    Ver4PatriciaTrieNodeWriter ptNodeWriter(mBuffers->getWritableTrieBuffer(),
            mBuffers, &ptNodeReader, &bigramPolicy, &shortcutPolicy);

    DynamicPatriciaTrieReadingHelper readingHelper(mBuffers->getTrieBuffer(), &ptNodeReader);
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);

    return true;
}

} // namespace latinime
