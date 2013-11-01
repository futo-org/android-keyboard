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

#ifndef LATINIME_SPARSE_TABLE_DICT_CONTENT_H
#define LATINIME_SPARSE_TABLE_DICT_CONTENT_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/dict_content.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"
#include "suggest/policyimpl/dictionary/utils/sparse_table.h"

namespace latinime {

// TODO: Support multiple contents.
class SparseTableDictContent : public DictContent {
 public:
    AK_FORCE_INLINE SparseTableDictContent(const char *const dictDirPath,
            const char *const lookupTableFileName, const char *const addressTableFileName,
            const char *const contentFileName, const bool isUpdatable,
            const int sparseTableBlockSize, const int sparseTableDataSize)
            : mLookupTableBuffer(
                      MmappedBuffer::openBuffer(dictDirPath, lookupTableFileName, isUpdatable)),
              mAddressTableBuffer(
                      MmappedBuffer::openBuffer(dictDirPath, addressTableFileName, isUpdatable)),
              mContentBuffer(MmappedBuffer::openBuffer(dictDirPath, contentFileName, isUpdatable)),
              mExpandableLookupTableBuffer(
                      mLookupTableBuffer.get() ? mLookupTableBuffer.get()->getBuffer() : 0,
                      mLookupTableBuffer.get() ? mLookupTableBuffer.get()->getBufferSize() : 0,
                      BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
              mExpandableAddressTableBuffer(
                      mAddressTableBuffer.get() ? mAddressTableBuffer.get()->getBuffer() : 0,
                      mAddressTableBuffer.get() ? mAddressTableBuffer.get()->getBufferSize() : 0,
                      BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
              mExpandableContentBuffer(mContentBuffer.get() ? mContentBuffer.get()->getBuffer() : 0,
                      mContentBuffer.get() ? mContentBuffer.get()->getBufferSize() : 0,
                      BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
              mAddressLookupTable(&mExpandableLookupTableBuffer, &mExpandableAddressTableBuffer,
                      sparseTableBlockSize, sparseTableDataSize) {}

    virtual ~SparseTableDictContent() {}

    virtual bool isValid() const {
        return mLookupTableBuffer.get() != 0 && mAddressTableBuffer.get() != 0
                && mContentBuffer.get() != 0;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(SparseTableDictContent);

    // TODO: Have sparse table.
    const MmappedBuffer::MmappedBufferPtr mLookupTableBuffer;
    const MmappedBuffer::MmappedBufferPtr mAddressTableBuffer;
    const MmappedBuffer::MmappedBufferPtr mContentBuffer;
    BufferWithExtendableBuffer mExpandableLookupTableBuffer;
    BufferWithExtendableBuffer mExpandableAddressTableBuffer;
    BufferWithExtendableBuffer mExpandableContentBuffer;
    SparseTable mAddressLookupTable;
};
} // namespace latinime
#endif /* LATINIME_SPARSE_TABLE_DICT_CONTENT_H */
