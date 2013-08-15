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

#ifndef LATINIME_MMAPED_BUFFER_H
#define LATINIME_MMAPED_BUFFER_H

#include <cerrno>
#include <fcntl.h>
#include <stdint.h>
#include <sys/mman.h>
#include <unistd.h>

#include "defines.h"

namespace latinime {

class MmapedBuffer {
 public:
    static MmapedBuffer* openBuffer(const char *const path, const int pathLength,
            const int bufOffset, const int size, const bool isUpdatable) {
        const int openMode = isUpdatable ? O_RDWR : O_RDONLY;
        const int fd = open(path, openMode);
        if (fd < 0) {
            AKLOGE("DICT: Can't open the source. path=%s errno=%d", path, errno);
            return 0;
        }
        const int pagesize = getpagesize();
        const int offset = bufOffset % pagesize;
        int adjOffset = bufOffset - offset;
        int adjSize = size + offset;
        const int protMode = isUpdatable ? PROT_READ | PROT_WRITE : PROT_READ;
        void *const mmapedBuffer = mmap(0, adjSize, protMode, MAP_PRIVATE, fd, adjOffset);
        if (mmapedBuffer == MAP_FAILED) {
            AKLOGE("DICT: Can't mmap dictionary. errno=%d", errno);
            close(fd);
            return 0;
        }
        uint8_t *const buffer = static_cast<uint8_t *>(mmapedBuffer) + bufOffset;
        if (!buffer) {
            AKLOGE("DICT: buffer is null");
            close(fd);
            return 0;
        }
        return new MmapedBuffer(buffer, adjSize, fd, adjOffset, isUpdatable);
    }

    ~MmapedBuffer() {
        int ret = munmap(static_cast<void *>(mBuffer - mBufferOffset),
                mBufferSize + mBufferOffset);
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
    AK_FORCE_INLINE MmapedBuffer(uint8_t *const buffer, const int bufferSize, const int mmapFd,
            const int bufferOffset, const bool isUpdatable)
            : mBuffer(buffer), mBufferSize(bufferSize), mMmapFd(mmapFd),
              mBufferOffset(bufferOffset), mIsUpdatable(isUpdatable) {}

    DISALLOW_IMPLICIT_CONSTRUCTORS(MmapedBuffer);

    uint8_t *const mBuffer;
    const int mBufferSize;
    const int mMmapFd;
    const int mBufferOffset;
    const bool mIsUpdatable;
};
}
#endif /* LATINIME_MMAPED_BUFFER_H */
