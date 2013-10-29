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

#ifndef LATINIME_SINGLE_DICT_CONTENT_H
#define LATINIME_SINGLE_DICT_CONTENT_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/dict_content.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

class SingleDictContent : public DictContent {
 public:
    SingleDictContent(const char *const dictDirPath, const char *const contentFileName,
            const bool isUpdatable)
            : mMmappedBuffer(MmappedBuffer::openBuffer(dictDirPath, contentFileName, isUpdatable)),
              mExpandableContentBuffer(mMmappedBuffer.get() ? mMmappedBuffer.get()->getBuffer() : 0,
                      mMmappedBuffer.get() ? mMmappedBuffer.get()->getBufferSize() : 0,
                      BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE) {}

    virtual ~SingleDictContent() {}

    virtual bool isValid() const {
        return mMmappedBuffer.get() != 0;
    }

 protected:
    BufferWithExtendableBuffer *getWritableBuffer() {
        return &mExpandableContentBuffer;
    }

    const BufferWithExtendableBuffer *getBuffer() const {
        return &mExpandableContentBuffer;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(SingleDictContent);

    const MmappedBuffer::MmappedBufferPtr mMmappedBuffer;
    BufferWithExtendableBuffer mExpandableContentBuffer;
};
} // namespace latinime
#endif /* LATINIME_SINGLE_DICT_CONTENT_H */
