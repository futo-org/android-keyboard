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

#include "suggest/policyimpl/dictionary/structure/v4/content/sparse_table_dict_content.h"

namespace latinime {

bool SparseTableDictContent::flush(const char *const dictDirPath,
        const char *const lookupTableFileName, const char *const addressTableFileName,
        const char *const contentFileName) const {
    const BufferWithExtendableBuffer *lookupTableBufferPtr = &mExpandableLookupTableBuffer;
    if (!DictFileWritingUtils::flushBuffersToFileInDir(dictDirPath, lookupTableFileName,
            &lookupTableBufferPtr, 1 /* bufferCount */)) {
        return false;
    }
    const BufferWithExtendableBuffer *addressTableBufferPtr = &mExpandableAddressTableBuffer;
    if (!DictFileWritingUtils::flushBuffersToFileInDir(dictDirPath, addressTableFileName,
            &addressTableBufferPtr, 1 /* bufferCount */)) {
        return false;
    }
    const BufferWithExtendableBuffer *contentBufferPtr = &mExpandableContentBuffer;
    if (!DictFileWritingUtils::flushBuffersToFileInDir(dictDirPath, contentFileName,
            &contentBufferPtr, 1 /* bufferCount */)) {
        return false;
    }
    return true;
}

} // namespace latinime
