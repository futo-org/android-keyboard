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

#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

const size_t BufferWithExtendableBuffer::MAX_ADDITIONAL_BUFFER_SIZE = 1024 * 1024;
const int BufferWithExtendableBuffer::NEAR_BUFFER_LIMIT_THRESHOLD_PERCENTILE = 90;
// TODO: Needs to allocate larger memory corresponding to the current vector size.
const size_t BufferWithExtendableBuffer::EXTEND_ADDITIONAL_BUFFER_SIZE_STEP = 128 * 1024;

bool BufferWithExtendableBuffer::writeUintAndAdvancePosition(const uint32_t data, const int size,
        int *const pos) {
    if (!(size >= 1 && size <= 4)) {
        AKLOGI("writeUintAndAdvancePosition() is called with invalid size: %d", size);
        ASSERT(false);
        return false;
    }
    if (!checkAndPrepareWriting(*pos, size)) {
        return false;
    }
    const bool usesAdditionalBuffer = isInAdditionalBuffer(*pos);
    uint8_t *const buffer = usesAdditionalBuffer ? &mAdditionalBuffer[0] : mOriginalBuffer;
    if (usesAdditionalBuffer) {
        *pos -= mOriginalBufferSize;
    }
    ByteArrayUtils::writeUintAndAdvancePosition(buffer, data, size, pos);
    if (usesAdditionalBuffer) {
        *pos += mOriginalBufferSize;
    }
    return true;
}

bool BufferWithExtendableBuffer::writeCodePointsAndAdvancePosition(const int *const codePoints,
        const int codePointCount, const bool writesTerminator ,int *const pos) {
    const size_t size = ByteArrayUtils::calculateRequiredByteCountToStoreCodePoints(
            codePoints, codePointCount, writesTerminator);
    if (!checkAndPrepareWriting(*pos, size)) {
        return false;
    }
    const bool usesAdditionalBuffer = isInAdditionalBuffer(*pos);
    uint8_t *const buffer = usesAdditionalBuffer ? &mAdditionalBuffer[0] : mOriginalBuffer;
    if (usesAdditionalBuffer) {
        *pos -= mOriginalBufferSize;
    }
    ByteArrayUtils::writeCodePointsAndAdvancePosition(buffer, codePoints, codePointCount,
            writesTerminator, pos);
    if (usesAdditionalBuffer) {
        *pos += mOriginalBufferSize;
    }
    return true;
}

bool BufferWithExtendableBuffer::extendBuffer() {
    const size_t sizeAfterExtending =
            mAdditionalBuffer.size() + EXTEND_ADDITIONAL_BUFFER_SIZE_STEP;
    if (sizeAfterExtending > mMaxAdditionalBufferSize) {
        return false;
    }
    mAdditionalBuffer.resize(mAdditionalBuffer.size() + EXTEND_ADDITIONAL_BUFFER_SIZE_STEP);
    return true;
}

bool BufferWithExtendableBuffer::checkAndPrepareWriting(const int pos, const int size) {
    if (isInAdditionalBuffer(pos)) {
        const int tailPosition = getTailPosition();
        if (pos == tailPosition) {
            // Append data to the tail.
            if (pos + size > static_cast<int>(mAdditionalBuffer.size()) + mOriginalBufferSize) {
                // Need to extend buffer.
                if (!extendBuffer()) {
                    return false;
                }
            }
            mUsedAdditionalBufferSize += size;
        } else if (pos + size > tailPosition) {
            // The access will beyond the tail of used region.
            return false;
        }
    } else {
        if (pos < 0 || mOriginalBufferSize < pos + size) {
            // Invalid position or violate the boundary.
            return false;
        }
    }
    return true;
}

}
