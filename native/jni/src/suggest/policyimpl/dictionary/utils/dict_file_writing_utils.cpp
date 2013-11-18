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

#include "suggest/policyimpl/dictionary/utils/dict_file_writing_utils.h"

#include <cstdio>

#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"

namespace latinime {

const char *const DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE = ".tmp";

/* static */ bool DictFileWritingUtils::createEmptyDictFile(const char *const filePath,
        const int dictVersion, const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    switch (dictVersion) {
        case 3:
            return createEmptyV3DictFile(filePath, attributeMap);
        case 4:
            return createEmptyV4DictFile(filePath, attributeMap);
        default:
            AKLOGE("Cannot create dictionary %s because format version %d is not supported.",
                    filePath, dictVersion);
            return false;
    }
}

/* static */ bool DictFileWritingUtils::createEmptyV3DictFile(const char *const filePath,
        const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    HeaderPolicy headerPolicy(FormatUtils::VERSION_3, attributeMap);
    headerPolicy.writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            true /* updatesLastDecayedTime */, 0 /* unigramCount */, 0 /* bigramCount */,
            0 /* extendedRegionSize */);
    BufferWithExtendableBuffer bodyBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    if (!DynamicPatriciaTrieWritingUtils::writeEmptyDictionary(&bodyBuffer, 0 /* rootPos */)) {
        AKLOGE("Empty ver3 dictionary structure cannot be created on memory.");
        return false;
    }
    return flushAllHeaderAndBodyToFile(filePath, &headerBuffer, &bodyBuffer);
}

/* static */ bool DictFileWritingUtils::createEmptyV4DictFile(const char *const dirPath,
        const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers = Ver4DictBuffers::createVer4DictBuffers();
    HeaderPolicy headerPolicy(FormatUtils::VERSION_4, attributeMap);
    headerPolicy.writeHeaderToBuffer(dictBuffers.get()->getWritableHeaderBuffer(),
            true /* updatesLastUpdatedTime */, true /* updatesLastDecayedTime */,
            0 /* unigramCount */, 0 /* bigramCount */, 0 /* extendedRegionSize */);
    if (!DynamicPatriciaTrieWritingUtils::writeEmptyDictionary(
            dictBuffers.get()->getWritableTrieBuffer(), 0 /* rootPos */)) {
        AKLOGE("Empty ver4 dictionary structure cannot be created on memory.");
        return false;
    }
    return dictBuffers.get()->flush(dirPath);
}

/* static */ bool DictFileWritingUtils::flushAllHeaderAndBodyToFile(const char *const filePath,
        BufferWithExtendableBuffer *const dictHeader, BufferWithExtendableBuffer *const dictBody) {
    const int tmpFileNameBufSize = FileUtils::getFilePathWithSuffixBufSize(filePath,
            TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE);
    // Name of a temporary file used for writing that is a connected string of original name and
    // TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE.
    char tmpFileName[tmpFileNameBufSize];
    FileUtils::getFilePathWithSuffix(filePath, TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE,
            tmpFileNameBufSize, tmpFileName);
    const BufferWithExtendableBuffer *buffers[] = {dictHeader, dictBody};
    if (!DictFileWritingUtils::flushBuffersToFile(tmpFileName, buffers, 2 /* bufferCount */)) {
        AKLOGE("Dictionary structure cannot be written to %s.", tmpFileName);
        return false;
    }
    if (rename(tmpFileName, filePath) != 0) {
        AKLOGE("Dictionary file %s cannot be renamed to %s", tmpFileName, filePath);;
    }
    return true;
}

/* static */ bool DictFileWritingUtils::flushBuffersToFileInDir(const char *const dirPath,
        const char *const fileName, const BufferWithExtendableBuffer **const buffers,
        const int bufferCount) {
    const int filePathBufSize = FileUtils::getFilePathBufSize(dirPath, fileName);
    char filePath[filePathBufSize];
    FileUtils::getFilePath(dirPath, fileName, filePathBufSize, filePath);
    return flushBuffersToFile(filePath, buffers, bufferCount);
}

/* static */ bool DictFileWritingUtils::flushBuffersToFile(const char *const filePath,
        const BufferWithExtendableBuffer **const buffers, const int bufferCount) {
    FILE *const file = fopen(filePath, "wb");
    if (!file) {
        AKLOGE("File %s cannot be opened.", filePath);
        ASSERT(false);
        return false;
    }
    for (int i = 0; i < bufferCount; ++i) {
        if (!writeBufferToFile(file, buffers[i])) {
            remove(filePath);
            AKLOGE("Buffer cannot be written to the file %s. size: %d", filePath,
                    buffers[i]->getTailPosition());
            ASSERT(false);
            return false;
        }
    }
    fclose(file);
    return true;
}

// This closes file pointer when an error is caused and returns whether the writing was succeeded
// or not.
/* static */ bool DictFileWritingUtils::writeBufferToFile(FILE *const file,
        const BufferWithExtendableBuffer *const buffer) {
    const int originalBufSize = buffer->getOriginalBufferSize();
    if (originalBufSize > 0 && fwrite(buffer->getBuffer(false /* usesAdditionalBuffer */),
            originalBufSize, 1, file) < 1) {
        fclose(file);
        return false;
    }
    const int additionalBufSize = buffer->getUsedAdditionalBufferSize();
    if (additionalBufSize > 0 && fwrite(buffer->getBuffer(true /* usesAdditionalBuffer */),
            additionalBufSize, 1, file) < 1) {
        fclose(file);
        return false;
    }
    return true;
}

} // namespace latinime
