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

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/dynamic_pt_writing_utils.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/utils/dict_file_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
                const char *const path, const int bufOffset, const int size,
                const bool isUpdatable) {
    if (FileUtils::existsDir(path)) {
        // Given path represents a directory.
        return newPolicyForDirectoryDict(path, isUpdatable);
    } else {
        if (isUpdatable) {
            AKLOGE("One file dictionaries don't support updating. path: %s", path);
            ASSERT(false);
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
        }
        return newPolicyForFileDict(path, bufOffset, size);
    }
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory:: newPolicyForOnMemoryDict(
                const int formatVersion, const std::vector<int> &locale,
                const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap) {
    switch (formatVersion) {
        case FormatUtils::VERSION_4: {
            HeaderPolicy headerPolicy(FormatUtils::VERSION_4, locale, attributeMap);
            Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers =
                    Ver4DictBuffers::createVer4DictBuffers(&headerPolicy,
                            Ver4DictConstants::MAX_DICT_EXTENDED_REGION_SIZE);
            if (!DynamicPtWritingUtils::writeEmptyDictionary(
                    dictBuffers->getWritableTrieBuffer(), 0 /* rootPos */)) {
                AKLOGE("Empty ver4 dictionary structure cannot be created on memory.");
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
            }
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new Ver4PatriciaTriePolicy(std::move(dictBuffers)));
        }
        default:
            AKLOGE("DICT: dictionary format %d is not supported for on memory dictionary",
                    formatVersion);
            break;
    }
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForDirectoryDict(
                const char *const path, const bool isUpdatable) {
    const int headerFilePathBufSize = PATH_MAX + 1 /* terminator */;
    char headerFilePath[headerFilePathBufSize];
    getHeaderFilePathInDictDir(path, headerFilePathBufSize, headerFilePath);
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer(
            MmappedBuffer::openBuffer(headerFilePath, isUpdatable));
    if (!mmappedBuffer) {
        return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
    }
    switch (FormatUtils::detectFormatVersion(mmappedBuffer->getBuffer(),
            mmappedBuffer->getBufferSize())) {
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
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
            }
            Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers(
                    Ver4DictBuffers::openVer4DictBuffers(dictPath, std::move(mmappedBuffer)));
            if (!dictBuffers || !dictBuffers->isValid()) {
                AKLOGE("DICT: The dictionary doesn't satisfy ver4 format requirements. path: %s",
                        path);
                ASSERT(false);
                return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
            }
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new Ver4PatriciaTriePolicy(std::move(dictBuffers)));
        }
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForFileDict(
                const char *const path, const int bufOffset, const int size) {
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer(
            MmappedBuffer::openBuffer(path, bufOffset, size, false /* isUpdatable */));
    if (!mmappedBuffer) {
        return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
    }
    switch (FormatUtils::detectFormatVersion(mmappedBuffer->getBuffer(),
            mmappedBuffer->getBufferSize())) {
        case FormatUtils::VERSION_2:
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new PatriciaTriePolicy(std::move(mmappedBuffer)));
        case FormatUtils::VERSION_4:
            AKLOGE("Given path is a file but the format is version 4. path: %s", path);
            break;
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(nullptr);
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
