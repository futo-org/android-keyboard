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

#include "suggest/policyimpl/dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory
                ::newDictionaryStructureWithBufferPolicy(const char *const path,
                        const int bufOffset, const int size, const bool isUpdatable) {
    // Allocated buffer in MmapedBuffer::newBuffer() will be freed in the destructor of
    // MmappedBufferWrapper if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer = MmappedBuffer::openBuffer(path, bufOffset, size,
            isUpdatable);
    if (!mmappedBuffer.get()) {
        return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
    }
    switch (FormatUtils::detectFormatVersion(mmappedBuffer.get()->getBuffer(),
            mmappedBuffer.get()->getBufferSize())) {
        case FormatUtils::VERSION_2:
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new PatriciaTriePolicy(mmappedBuffer));
        case FormatUtils::VERSION_4: {
            const int dictDirPathBufSize = strlen(path) + 1 /* terminator */;
            char dictDirPath[dictDirPathBufSize];
            if (!FileUtils::getFilePathWithoutSuffix(path, Ver4DictConstants::TRIE_FILE_EXTENSION,
                    dictDirPathBufSize, dictDirPath)) {
                // Dictionary file name is not valid as a version 4 dictionary.
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
            }
            const Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers =
                    Ver4DictBuffers::openVer4DictBuffers(dictDirPath, mmappedBuffer);
            if (!dictBuffers.get()->isValid()) {
                AKLOGE("DICT: The dictionary doesn't satisfy ver4 format requirements.");
                ASSERT(false);
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
            }
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new Ver4PatriciaTriePolicy(dictBuffers));
        }
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number");
            ASSERT(false);
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
    }
}

} // namespace latinime
