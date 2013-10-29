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

#ifndef LATINIME_VER4_DICT_BUFFER_H
#define LATINIME_VER4_DICT_BUFFER_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/probability_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/single_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/sparse_table_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

class Ver4DictBuffers {
 public:
    typedef ExclusiveOwnershipPointer<Ver4DictBuffers> Ver4DictBuffersPtr;

    static Ver4DictBuffersPtr openVer4DictBuffers(const char *const dictDirPath,
            const MmappedBuffer::MmappedBufferPtr &dictBuffer) {
        const bool isUpdatable = dictBuffer.get() ? dictBuffer.get()->isUpdatable() : false;
        return Ver4DictBuffersPtr(new Ver4DictBuffers(dictDirPath, dictBuffer, isUpdatable));
    }

    AK_FORCE_INLINE bool isValid() const {
        return mDictBuffer.get() != 0 && mProbabilityDictContent.isValid()
                && mTerminalAddressTable.isValid() && mBigramDictContent.isValid()
                && mShortcutDictContent.isValid();
    }

    AK_FORCE_INLINE uint8_t *getRawDictBuffer() const {
        return mDictBuffer.get()->getBuffer();
    }

    AK_FORCE_INLINE int getRawDictBufferSize() const {
        return mDictBuffer.get()->getBufferSize();
    }

    AK_FORCE_INLINE const ProbabilityDictContent *getProbabilityDictContent() const {
        return &mProbabilityDictContent;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4DictBuffers);

    AK_FORCE_INLINE Ver4DictBuffers(const char *const dictDirPath,
            const MmappedBuffer::MmappedBufferPtr &dictBuffer, const bool isUpdatable)
            : mDictBuffer(dictBuffer),
              mTerminalAddressTable(dictDirPath,
                      Ver4DictConstants::TERMINAL_ADDRESS_TABLE_FILE_EXTENSION, isUpdatable),
              mProbabilityDictContent(dictDirPath, isUpdatable),
              mBigramDictContent(dictDirPath,
                      Ver4DictConstants::BIGRAM_LOOKUP_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::BIGRAM_CONTENT_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::BIGRAM_FILE_EXTENSION, isUpdatable),
              mShortcutDictContent(dictDirPath,
                      Ver4DictConstants::SHORTCUT_LOOKUP_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::SHORTCUT_CONTENT_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::SHORTCUT_FILE_EXTENSION, isUpdatable) {}

    const MmappedBuffer::MmappedBufferPtr mDictBuffer;
    SingleDictContent mTerminalAddressTable;
    ProbabilityDictContent mProbabilityDictContent;
    SparseTableDictContent mBigramDictContent;
    SparseTableDictContent mShortcutDictContent;
};
} // namespace latinime
#endif /* LATINIME_VER4_DICT_BUFFER_H */
