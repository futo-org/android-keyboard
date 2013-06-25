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

#ifndef LATINIME_BINARY_DICTIONARY_INFO_H
#define LATINIME_BINARY_DICTIONARY_INFO_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_format_utils.h"
#include "suggest/core/dictionary/binary_dictionary_header.h"

namespace latinime {

class BinaryDictionaryHeader;

class BinaryDictionaryInfo {
 public:
    BinaryDictionaryInfo(const uint8_t *const dictBuf, const int dictSize, const int mmapFd,
            const int dictBufOffset)
            : mDictBuf(dictBuf), mDictSize(dictSize), mMmapFd(mmapFd),
              mDictBufOffset(dictBufOffset),
              mDictionaryFormat(BinaryDictionaryFormat::detectFormatVersion(mDictBuf, mDictSize)),
              mDictionaryHeader(this), mDictRoot(mDictBuf + mDictionaryHeader.getSize()) {}

    AK_FORCE_INLINE const uint8_t *getDictBuf() const {
        return mDictBuf;
    }

    AK_FORCE_INLINE int getDictSize() const {
        return mDictSize;
    }

    AK_FORCE_INLINE int getMmapFd() const {
        return mMmapFd;
    }

    AK_FORCE_INLINE int getDictBufOffset() const {
        return mDictBufOffset;
    }

    AK_FORCE_INLINE const uint8_t *getDictRoot() const {
        return mDictRoot;
    }

    AK_FORCE_INLINE BinaryDictionaryFormat::FORMAT_VERSION getFormat() const {
        return mDictionaryFormat;
    }

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    AK_FORCE_INLINE const BinaryDictionaryHeader *getHeader() const {
        return &mDictionaryHeader;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(BinaryDictionaryInfo);

    const uint8_t *const mDictBuf;
    const int mDictSize;
    const int mMmapFd;
    const int mDictBufOffset;
    const BinaryDictionaryFormat::FORMAT_VERSION mDictionaryFormat;
    const BinaryDictionaryHeader mDictionaryHeader;
    const uint8_t *const mDictRoot;
};
}
#endif /* LATINIME_BINARY_DICTIONARY_INFO_H */
