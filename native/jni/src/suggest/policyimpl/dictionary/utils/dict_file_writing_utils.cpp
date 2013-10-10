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
#include <cstring>

#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"

namespace latinime {

const char *const DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE = ".tmp";

/* static */ bool DictFileWritingUtils::createEmptyDictFile(const char *const filePath,
        const int dictVersion, const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    switch (dictVersion) {
        case 3:
            return createEmptyV3DictFile(filePath, attributeMap);
        default:
            // Only version 3 dictionary is supported for now.
            return false;
    }
}

/* static */ bool DictFileWritingUtils::createEmptyV3DictFile(const char *const filePath,
        const HeaderReadWriteUtils::AttributeMap *const attributeMap) {
    BufferWithExtendableBuffer headerBuffer(0 /* originalBuffer */, 0 /* originalBufferSize */);
    HeaderPolicy headerPolicy(FormatUtils::VERSION_3, attributeMap);
    headerPolicy.writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            true /* updatesLastDecayedTime */, 0 /* unigramCount */, 0 /* bigramCount */,
            0 /* extendedRegionSize */);
    BufferWithExtendableBuffer bodyBuffer(0 /* originalBuffer */, 0 /* originalBufferSize */);
    if (!DynamicPatriciaTrieWritingUtils::writeEmptyDictionary(&bodyBuffer, 0 /* rootPos */)) {
        return false;
    }
    return flushAllHeaderAndBodyToFile(filePath, &headerBuffer, &bodyBuffer);
}

/* static */ bool DictFileWritingUtils::flushAllHeaderAndBodyToFile(const char *const filePath,
        BufferWithExtendableBuffer *const dictHeader, BufferWithExtendableBuffer *const dictBody) {
    const int tmpFileNameBufSize = strlen(filePath)
            + strlen(TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE) + 1 /* terminator */;
    // Name of a temporary file used for writing that is a connected string of original name and
    // TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE.
    char tmpFileName[tmpFileNameBufSize];
    snprintf(tmpFileName, tmpFileNameBufSize, "%s%s", filePath,
            TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE);
    FILE *const file = fopen(tmpFileName, "wb");
    if (!file) {
        AKLOGE("Dictionary file %s cannnot be opened.", tmpFileName);
        ASSERT(false);
        return false;
    }
    // Write the dictionary header.
    if (!writeBufferToFile(file, dictHeader)) {
        remove(tmpFileName);
        AKLOGE("Dictionary header cannnot be written. size: %d", dictHeader->getTailPosition());
        ASSERT(false);
        return false;
    }
    // Write the dictionary body.
    if (!writeBufferToFile(file, dictBody)) {
        remove(tmpFileName);
        AKLOGE("Dictionary body cannnot be written. size: %d", dictBody->getTailPosition());
        ASSERT(false);
        return false;
    }
    fclose(file);
    rename(tmpFileName, filePath);
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
