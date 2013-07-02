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
#include "jni.h"
#include "suggest/core/dictionary/binary_dictionary_format_utils.h"
#include "suggest/core/dictionary/binary_dictionary_header.h"
#include "suggest/policyimpl/dictionary/dictionary_structure_policy_factory.h"
#include "utils/log_utils.h"

namespace latinime {

class BinaryDictionaryInfo {
 public:
     AK_FORCE_INLINE BinaryDictionaryInfo(JNIEnv *env, const uint8_t *const dictBuf,
            const int dictSize, const int mmapFd, const int dictBufOffset, const bool isUpdatable)
            : mDictBuf(dictBuf), mDictSize(dictSize), mMmapFd(mmapFd),
              mDictBufOffset(dictBufOffset), mIsUpdatable(isUpdatable),
              mDictionaryFormat(BinaryDictionaryFormatUtils::detectFormatVersion(
                      mDictBuf, mDictSize)),
              mDictionaryHeader(this), mDictRoot(mDictBuf + mDictionaryHeader.getSize()),
              mStructurePolicy(DictionaryStructurePolicyFactory::getDictionaryStructurePolicy(
                      mDictionaryFormat)) {
        logDictionaryInfo(env);
    }

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

    AK_FORCE_INLINE BinaryDictionaryFormatUtils::FORMAT_VERSION getFormat() const {
        return mDictionaryFormat;
    }

    AK_FORCE_INLINE const BinaryDictionaryHeader *getHeader() const {
        return &mDictionaryHeader;
    }

    AK_FORCE_INLINE bool isDynamicallyUpdatable() const {
        // TODO: Support dynamic dictionary formats.
        const bool isUpdatableDictionaryFormat = false;
        return mIsUpdatable && isUpdatableDictionaryFormat;
    }

    AK_FORCE_INLINE const DictionaryStructurePolicy *getStructurePolicy() const {
        return mStructurePolicy;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(BinaryDictionaryInfo);

    const uint8_t *const mDictBuf;
    const int mDictSize;
    const int mMmapFd;
    const int mDictBufOffset;
    const bool mIsUpdatable;
    const BinaryDictionaryFormatUtils::FORMAT_VERSION mDictionaryFormat;
    const BinaryDictionaryHeader mDictionaryHeader;
    const uint8_t *const mDictRoot;
    const DictionaryStructurePolicy *const mStructurePolicy;

    AK_FORCE_INLINE void logDictionaryInfo(JNIEnv *const env) const {
        const int BUFFER_SIZE = 16;
        int dictionaryIdCodePointBuffer[BUFFER_SIZE];
        int versionStringCodePointBuffer[BUFFER_SIZE];
        int dateStringCodePointBuffer[BUFFER_SIZE];
        mDictionaryHeader.readHeaderValueOrQuestionMark("dictionary",
                dictionaryIdCodePointBuffer, BUFFER_SIZE);
        mDictionaryHeader.readHeaderValueOrQuestionMark("version",
                versionStringCodePointBuffer, BUFFER_SIZE);
        mDictionaryHeader.readHeaderValueOrQuestionMark("date",
                dateStringCodePointBuffer, BUFFER_SIZE);

        char dictionaryIdCharBuffer[BUFFER_SIZE];
        char versionStringCharBuffer[BUFFER_SIZE];
        char dateStringCharBuffer[BUFFER_SIZE];
        intArrayToCharArray(dictionaryIdCodePointBuffer, BUFFER_SIZE,
                dictionaryIdCharBuffer, BUFFER_SIZE);
        intArrayToCharArray(versionStringCodePointBuffer, BUFFER_SIZE,
                versionStringCharBuffer, BUFFER_SIZE);
        intArrayToCharArray(dateStringCodePointBuffer, BUFFER_SIZE,
                dateStringCharBuffer, BUFFER_SIZE);

        LogUtils::logToJava(env,
                "Dictionary info: dictionary = %s ; version = %s ; date = %s ; filesize = %i",
                dictionaryIdCharBuffer, versionStringCharBuffer, dateStringCharBuffer, mDictSize);
    }
};
}
#endif /* LATINIME_BINARY_DICTIONARY_INFO_H */
