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

#ifndef LATINIME_EXTENDABLE_BUFFER_H
#define LATINIME_EXTENDABLE_BUFFER_H

#include <cstddef>
#include <stdint.h>
#include <vector>

#include "defines.h"

namespace latinime {

// This is used as a buffer that can be extended for updatable dictionaries.
class ExtendableBuffer {
 public:
    ExtendableBuffer() : mBuffer(INITIAL_BUFFER_SIZE), mUsedSize(0) {}

   AK_FORCE_INLINE uint8_t *getBuffer() {
        return  &mBuffer[0];
    }

    // Return if the buffer is successfully extended or not.
   AK_FORCE_INLINE bool extendBuffer() {
        if (mBuffer.size() + EXTEND_BUFFER_SIZE_STEP > MAX_BUFFER_SIZE) {
            return false;
        }
        mBuffer.resize(mBuffer.size() + EXTEND_BUFFER_SIZE_STEP);
        return true;
    }

    AK_FORCE_INLINE int getAllocatedSize() const {
        return mBuffer.size();
    }

    AK_FORCE_INLINE int getUsedSize() const {
        return mUsedSize;
    }

    AK_FORCE_INLINE void clear() {
        mUsedSize = 0;
        mBuffer.clear();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(ExtendableBuffer);

    static const size_t INITIAL_BUFFER_SIZE;
    static const size_t MAX_BUFFER_SIZE;
    static const size_t EXTEND_BUFFER_SIZE_STEP;

    std::vector<uint8_t> mBuffer;
    int mUsedSize;
};
}
#endif /* LATINIME_MMAPED_BUFFER_H */
