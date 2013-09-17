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

#ifndef LATINIME_MMAPPED_BUFFER_H
#define LATINIME_MMAPPED_BUFFER_H

#include <cerrno>
#include <fcntl.h>
#include <stdint.h>
#include <sys/mman.h>
#include <unistd.h>

#include "defines.h"

namespace latinime {

class MmappedBuffer {
 public:
    static MmappedBuffer* openBuffer(const char *const path, const int bufferOffset,
            const int bufferSize, const bool isUpdatable) {
        const int openMode = isUpdatable ? O_RDWR : O_RDONLY;
        const int mmapFd = open(path, openMode);
        if (mmapFd < 0) {
            AKLOGE("DICT: Can't open the source. path=%s errno=%d", path, errno);
            return 0;
        }
        const int pagesize = getpagesize();
        const int offset = bufferOffset % pagesize;
        int alignedOffset = bufferOffset - offset;
        int alignedSize = bufferSize + offset;
        const int protMode = isUpdatable ? PROT_READ | PROT_WRITE : PROT_READ;
        void *const mmappedBuffer = mmap(0, alignedSize, protMode, MAP_PRIVATE, mmapFd,
                alignedOffset);
        if (mmappedBuffer == MAP_FAILED) {
            AKLOGE("DICT: Can't mmap dictionary. errno=%d", errno);
            close(mmapFd);
            return 0;
        }
        uint8_t *const buffer = static_cast<uint8_t *>(mmappedBuffer) + offset;
        if (!buffer) {
            AKLOGE("DICT: buffer is null");
            close(mmapFd);
            return 0;
        }
        return new MmappedBuffer(buffer, bufferSize, mmappedBuffer, alignedSize, mmapFd,
                isUpdatable);
    }

    ~MmappedBuffer() {
        int ret = munmap(mMmappedBuffer, mAlignedSize);
        if (ret != 0) {
            AKLOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
        }
        ret = close(mMmapFd);
        if (ret != 0) {
            AKLOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
        }
    }

    AK_FORCE_INLINE uint8_t *getBuffer() const {
        return mBuffer;
    }

    AK_FORCE_INLINE int getBufferSize() const {
        return mBufferSize;
    }

    AK_FORCE_INLINE bool isUpdatable() const {
        return mIsUpdatable;
    }

 private:
    AK_FORCE_INLINE MmappedBuffer(uint8_t *const buffer, const int bufferSize,
            void *const mmappedBuffer, const int alignedSize, const int mmapFd,
            const bool isUpdatable)
            : mBuffer(buffer), mBufferSize(bufferSize), mMmappedBuffer(mmappedBuffer),
              mAlignedSize(alignedSize), mMmapFd(mmapFd), mIsUpdatable(isUpdatable) {}

    DISALLOW_IMPLICIT_CONSTRUCTORS(MmappedBuffer);

    uint8_t *const mBuffer;
    const int mBufferSize;
    void *const mMmappedBuffer;
    const int mAlignedSize;
    const int mMmapFd;
    const bool mIsUpdatable;
};
}
#endif /* LATINIME_MMAPPED_BUFFER_H */
