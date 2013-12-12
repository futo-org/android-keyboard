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

#include <climits>
#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory
                ::newDictionaryStructureWithBufferPolicy(const char *const path,
                        const int bufOffset, const int size, const bool isUpdatable) {
    if (FileUtils::existsDir(path)) {
        // Given path represents a directory.
        return newPolicyforDirectoryDict(path, isUpdatable);
    } else {
        if (isUpdatable) {
            AKLOGE("One file dictionaries don't support updating. path: %s", path);
            ASSERT(false);
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
        }
        return newPolicyforFileDict(path, bufOffset, size);
    }
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyforDirectoryDict(
                const char *const path, const bool isUpdatable) {
    const int headerFilePathBufSize = PATH_MAX + 1 /* terminator */;
    char headerFilePath[headerFilePathBufSize];
    getHeaderFilePathInDictDir(path, headerFilePathBufSize, headerFilePath);
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer = MmappedBuffer::openBuffer(headerFilePath,
            isUpdatable);
    if (!mmappedBuffer.get()) {
        return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
    }
    switch (FormatUtils::detectFormatVersion(mmappedBuffer.get()->getBuffer(),
            mmappedBuffer.get()->getBufferSize())) {
        case FormatUtils::VERSION_2:
            AKLOGE("Given path is a directory but the format is version 2. path: %s", path);
            break;
        case FormatUtils::VERSION_4: {
            const int dictDirPathBufSize = strlen(headerFilePath) + 1 /* terminator */;
            char dictPath[dictDirPathBufSize];
            if (!FileUtils::getFilePathWithoutSuffix(headerFilePath,
                    Ver4DictConstants::HEADER_FILE_EXTENSION, dictDirPathBufSize, dictPath)) {
                AKLOGE("Dictionary file name is not valid as a ver4 dictionary. path: %s", path);
                ASSERT(false);
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
            }
            const Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers =
                    Ver4DictBuffers::openVer4DictBuffers(dictPath, mmappedBuffer);
            if (!dictBuffers.get()->isValid()) {
                AKLOGE("DICT: The dictionary doesn't satisfy ver4 format requirements. path: %s",
                        path);
                ASSERT(false);
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
            }
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new Ver4PatriciaTriePolicy(dictBuffers));
        }
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyforFileDict(
                const char *const path, const int bufOffset, const int size) {
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer = MmappedBuffer::openBuffer(path, bufOffset,
            size, false /* isUpdatable */);
    if (!mmappedBuffer.get()) {
        return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
    }
    switch (FormatUtils::detectFormatVersion(mmappedBuffer.get()->getBuffer(),
            mmappedBuffer.get()->getBufferSize())) {
        case FormatUtils::VERSION_2:
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new PatriciaTriePolicy(mmappedBuffer));
        case FormatUtils::VERSION_4:
            AKLOGE("Given path is a file but the format is version 4. path: %s", path);
            break;
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(0);
}

/* static */ void DictionaryStructureWithBufferPolicyFactory::getHeaderFilePathInDictDir(
        const char *const dictDirPath, const int outHeaderFileBufSize,
        char *const outHeaderFilePath) {
    const int dictNameBufSize = strlen(dictDirPath) + 1 /* terminator */;
    char dictName[dictNameBufSize];
    FileUtils::getBasename(dictDirPath, dictNameBufSize, dictName);
    snprintf(outHeaderFilePath, outHeaderFileBufSize, "%s/%s%s", dictDirPath,
            dictName, Ver4DictConstants::HEADER_FILE_EXTENSION);
}

} // namespace latinime
