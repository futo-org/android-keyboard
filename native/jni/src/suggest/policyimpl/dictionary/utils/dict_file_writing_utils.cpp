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
#include "suggest/policyimpl/dictionary/structure/pt_common/dynamic_pt_writing_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

const char *const DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE = ".tmp";

/* static */ bool DictFileWritingUtils::createEmptyDictFile(const char *const filePath,
        const int dictVersion, const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    TimeKeeper::setCurrentTime();
    switch (dictVersion) {
        case FormatUtils::VERSION_4:
            return createEmptyV4DictFile(filePath, attributeMap);
        default:
            AKLOGE("Cannot create dictionary %s because format version %d is not supported.",
                    filePath, dictVersion);
            return false;
    }
}

/* static */ bool DictFileWritingUtils::createEmptyV4DictFile(const char *const dirPath,
        const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    HeaderPolicy headerPolicy(FormatUtils::VERSION_4, attributeMap);
    Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers =
            Ver4DictBuffers::createVer4DictBuffers(&headerPolicy);
    headerPolicy.writeHeaderToBuffer(dictBuffers.get()->getWritableHeaderBuffer(),
            true /* updatesLastUpdatedTime */, true /* updatesLastDecayedTime */,
            0 /* unigramCount */, 0 /* bigramCount */, 0 /* extendedRegionSize */);
    if (!DynamicPtWritingUtils::writeEmptyDictionary(
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
    if (!DictFileWritingUtils::flushBufferToFile(tmpFileName, dictHeader)) {
        AKLOGE("Dictionary header cannot be written to %s.", tmpFileName);
        return false;
    }
    if (!DictFileWritingUtils::flushBufferToFile(tmpFileName, dictBody)) {
        AKLOGE("Dictionary structure cannot be written to %s.", tmpFileName);
        return false;
    }
    if (rename(tmpFileName, filePath) != 0) {
        AKLOGE("Dictionary file %s cannot be renamed to %s", tmpFileName, filePath);;
        return false;
    }
    return true;
}

/* static */ bool DictFileWritingUtils::flushBufferToFileWithSuffix(const char *const basePath,
        const char *const suffix, const BufferWithExtendableBuffer *const buffer) {
    const int filePathBufSize = FileUtils::getFilePathWithSuffixBufSize(basePath, suffix);
    char filePath[filePathBufSize];
    FileUtils::getFilePathWithSuffix(basePath, suffix, filePathBufSize, filePath);
    return flushBufferToFile(filePath, buffer);
}

/* static */ bool DictFileWritingUtils::flushBufferToFile(const char *const filePath,
        const BufferWithExtendableBuffer *const buffer) {
    FILE *const file = fopen(filePath, "wb");
    if (!file) {
        AKLOGE("File %s cannot be opened.", filePath);
        ASSERT(false);
        return false;
    }
    if (!writeBufferToFile(file, buffer)) {
        remove(filePath);
        AKLOGE("Buffer cannot be written to the file %s. size: %d", filePath,
                buffer->getTailPosition());
        ASSERT(false);
        return false;
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
