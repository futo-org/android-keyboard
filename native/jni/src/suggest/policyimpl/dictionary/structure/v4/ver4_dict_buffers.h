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
#include "suggest/policyimpl/dictionary/header/header_read_write_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/probability_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/shortcut_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
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
                && mTerminalPositionLookupTable.isValid() && mBigramDictContent.isValid()
                && mShortcutDictContent.isValid();
    }

    AK_FORCE_INLINE uint8_t *getRawDictBuffer() const {
        return mDictBuffer.get()->getBuffer();
    }

    AK_FORCE_INLINE int getRawDictBufferSize() const {
        return mDictBuffer.get()->getBufferSize();
    }


    AK_FORCE_INLINE TerminalPositionLookupTable *getUpdatableTerminalPositionLookupTable() {
        return &mTerminalPositionLookupTable;
    }

    AK_FORCE_INLINE const TerminalPositionLookupTable *getTerminalPositionLookupTable() const {
        return &mTerminalPositionLookupTable;
    }

    AK_FORCE_INLINE ProbabilityDictContent *getUpdatableProbabilityDictContent() {
        return &mProbabilityDictContent;
    }

    AK_FORCE_INLINE const ProbabilityDictContent *getProbabilityDictContent() const {
        return &mProbabilityDictContent;
    }

    AK_FORCE_INLINE BigramDictContent *getUpdatableBigramDictContent() {
        return &mBigramDictContent;
    }

    AK_FORCE_INLINE const BigramDictContent *getBigramDictContent() const {
        return &mBigramDictContent;
    }

    AK_FORCE_INLINE const ShortcutDictContent *getShortcutDictContent() const {
        return &mShortcutDictContent;
    }

    AK_FORCE_INLINE bool isUpdatable() const {
        return mIsUpdatable;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4DictBuffers);

    AK_FORCE_INLINE Ver4DictBuffers(const char *const dictDirPath,
            const MmappedBuffer::MmappedBufferPtr &dictBuffer, const bool isUpdatable)
            : mDictBuffer(dictBuffer),
              // TODO: Quit using getHeaderSize.
              mTerminalPositionLookupTable(dictDirPath, isUpdatable,
                      HeaderReadWriteUtils::getHeaderSize(mDictBuffer.get()->getBuffer())),
              mProbabilityDictContent(dictDirPath, isUpdatable),
              mBigramDictContent(dictDirPath, isUpdatable),
              mShortcutDictContent(dictDirPath, isUpdatable),
              mIsUpdatable(isUpdatable) {}

    const MmappedBuffer::MmappedBufferPtr mDictBuffer;
    TerminalPositionLookupTable mTerminalPositionLookupTable;
    ProbabilityDictContent mProbabilityDictContent;
    BigramDictContent mBigramDictContent;
    ShortcutDictContent mShortcutDictContent;
    const int mIsUpdatable;
};
} // namespace latinime
#endif /* LATINIME_VER4_DICT_BUFFER_H */
