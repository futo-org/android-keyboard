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

#ifndef LATINIME_BINARY_DICTIONARY_BIGRAMS_ITERATOR_H
#define LATINIME_BINARY_DICTIONARY_BIGRAMS_ITERATOR_H

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_bigrams_reading_utils.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"

namespace latinime {

class BinaryDictionaryBigramsIterator {
 public:
    BinaryDictionaryBigramsIterator(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, const int pos)
            : mBinaryDictionaryInfo(binaryDictionaryInfo), mPos(pos), mBigramFlags(0),
              mBigramPos(0), mHasNext(true) {}

    AK_FORCE_INLINE bool hasNext() const {
        return mHasNext;
    }

    AK_FORCE_INLINE void next() {
        mBigramFlags = BinaryDictionaryBigramsReadingUtils::getFlagsAndForwardPointer(
                mBinaryDictionaryInfo, &mPos);
        mBigramPos = BinaryDictionaryBigramsReadingUtils::getBigramAddressAndForwardPointer(
                mBinaryDictionaryInfo, mBigramFlags, &mPos);
        mHasNext = BinaryDictionaryBigramsReadingUtils::hasNext(mBigramFlags);
    }

    AK_FORCE_INLINE int getProbability() const {
        return BinaryDictionaryBigramsReadingUtils::getBigramProbability(mBigramFlags);
    }

    AK_FORCE_INLINE int getBigramPos() const {
        return mBigramPos;
    }

    AK_FORCE_INLINE int getFlags() const {
        return mBigramFlags;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(BinaryDictionaryBigramsIterator);

    const BinaryDictionaryInfo *const mBinaryDictionaryInfo;
    int mPos;
    BinaryDictionaryBigramsReadingUtils::BigramFlags mBigramFlags;
    int mBigramPos;
    bool mHasNext;
};
} // namespace latinime
#endif // LATINIME_BINARY_DICTIONARY_BIGRAMS_ITERATOR_H
