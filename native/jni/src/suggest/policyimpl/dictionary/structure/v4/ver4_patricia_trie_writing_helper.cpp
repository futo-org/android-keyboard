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

#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"

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

} // namespace latinime
