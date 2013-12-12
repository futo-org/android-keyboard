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

#ifndef LATINIME_HEADER_POLICY_H
#define LATINIME_HEADER_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/policyimpl/dictionary/header/header_read_write_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

class HeaderPolicy : public DictionaryHeaderStructurePolicy {
 public:
    // Reads information from existing dictionary buffer.
    HeaderPolicy(const uint8_t *const dictBuf, const FormatUtils::FORMAT_VERSION formatVersion)
            : mDictFormatVersion(formatVersion),
              mDictionaryFlags(HeaderReadWriteUtils::getFlags(dictBuf)),
              mSize(HeaderReadWriteUtils::getHeaderSize(dictBuf)),
              mAttributeMap(createAttributeMapAndReadAllAttributes(dictBuf)),
              mMultiWordCostMultiplier(readMultipleWordCostMultiplier()),
              mRequiresGermanUmlautProcessing(readRequiresGermanUmlautProcessing()),
              mIsDecayingDict(HeaderReadWriteUtils::readBoolAttributeValue(&mAttributeMap,
                      IS_DECAYING_DICT_KEY, false /* defaultValue */)),
              mLastUpdatedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      LAST_UPDATED_TIME_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mLastDecayedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      LAST_DECAYED_TIME_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mUnigramCount(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      UNIGRAM_COUNT_KEY, 0 /* defaultValue */)),
              mBigramCount(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      BIGRAM_COUNT_KEY, 0 /* defaultValue */)),
              mExtendedRegionSize(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      EXTENDED_REGION_SIZE_KEY, 0 /* defaultValue */)),
              mHasHistoricalInfoOfWords(HeaderReadWriteUtils::readBoolAttributeValue(
                      &mAttributeMap, HAS_HISTORICAL_INFO_KEY, false /* defaultValue */)) {}

    // Constructs header information using an attribute map.
    HeaderPolicy(const FormatUtils::FORMAT_VERSION dictFormatVersion,
            const HeaderReadWriteUtils::AttributeMap *const attributeMap)
            : mDictFormatVersion(dictFormatVersion),
              mDictionaryFlags(HeaderReadWriteUtils::createAndGetDictionaryFlagsUsingAttributeMap(
                      attributeMap)), mSize(0), mAttributeMap(*attributeMap),
              mMultiWordCostMultiplier(readMultipleWordCostMultiplier()),
              mRequiresGermanUmlautProcessing(readRequiresGermanUmlautProcessing()),
              mIsDecayingDict(HeaderReadWriteUtils::readBoolAttributeValue(&mAttributeMap,
                      IS_DECAYING_DICT_KEY, false /* defaultValue */)),
              mLastUpdatedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      LAST_UPDATED_TIME_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mLastDecayedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      LAST_UPDATED_TIME_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mUnigramCount(0), mBigramCount(0), mExtendedRegionSize(0),
              mHasHistoricalInfoOfWords(HeaderReadWriteUtils::readBoolAttributeValue(
                      &mAttributeMap, HAS_HISTORICAL_INFO_KEY, false /* defaultValue */)) {}

    // Temporary dummy header.
    HeaderPolicy()
            : mDictFormatVersion(FormatUtils::UNKNOWN_VERSION), mDictionaryFlags(0), mSize(0),
              mAttributeMap(), mMultiWordCostMultiplier(0.0f),
              mRequiresGermanUmlautProcessing(false), mIsDecayingDict(false),
              mLastUpdatedTime(0), mLastDecayedTime(0), mUnigramCount(0), mBigramCount(0),
              mExtendedRegionSize(0), mHasHistoricalInfoOfWords(false) {}

    ~HeaderPolicy() {}

    virtual int getFormatVersionNumber() const {
        // Conceptually this converts the symbolic value we use in the code into the
        // hardcoded of the bytes in the file. But we want the constants to be the
        // same so we use them for both here.
        switch (mDictFormatVersion) {
            case FormatUtils::VERSION_2:
                return FormatUtils::VERSION_2;
            case FormatUtils::VERSION_4:
                return FormatUtils::VERSION_4;
            default:
                return FormatUtils::UNKNOWN_VERSION;
        }
    }

    AK_FORCE_INLINE bool isValid() const {
        // Decaying dictionary must have historical information.
        if (!mIsDecayingDict) {
            return true;
        }
        if (mHasHistoricalInfoOfWords) {
            return true;
        } else {
            return false;
        }
    }

    AK_FORCE_INLINE int getSize() const {
        return mSize;
    }

    AK_FORCE_INLINE float getMultiWordCostMultiplier() const {
        return mMultiWordCostMultiplier;
    }

    AK_FORCE_INLINE bool isDecayingDict() const {
        return mIsDecayingDict;
    }

    AK_FORCE_INLINE bool requiresGermanUmlautProcessing() const {
        return mRequiresGermanUmlautProcessing;
    }

    AK_FORCE_INLINE int getLastUpdatedTime() const {
        return mLastUpdatedTime;
    }

    AK_FORCE_INLINE int getLastDecayedTime() const {
        return mLastDecayedTime;
    }

    AK_FORCE_INLINE int getUnigramCount() const {
        return mUnigramCount;
    }

    AK_FORCE_INLINE int getBigramCount() const {
        return mBigramCount;
    }

    AK_FORCE_INLINE int getExtendedRegionSize() const {
        return mExtendedRegionSize;
    }

    AK_FORCE_INLINE bool hasHistoricalInfoOfWords() const {
        return mHasHistoricalInfoOfWords;
    }

    void readHeaderValueOrQuestionMark(const char *const key,
            int *outValue, int outValueSize) const;

    bool writeHeaderToBuffer(BufferWithExtendableBuffer *const bufferToWrite,
            const bool updatesLastUpdatedTime, const bool updatesLastDecayedTime,
            const int unigramCount, const int bigramCount, const int extendedRegionSize) const;

 private:
    DISALLOW_COPY_AND_ASSIGN(HeaderPolicy);

    static const char *const MULTIPLE_WORDS_DEMOTION_RATE_KEY;
    static const char *const REQUIRES_GERMAN_UMLAUT_PROCESSING_KEY;
    static const char *const IS_DECAYING_DICT_KEY;
    static const char *const LAST_UPDATED_TIME_KEY;
    static const char *const LAST_DECAYED_TIME_KEY;
    static const char *const UNIGRAM_COUNT_KEY;
    static const char *const BIGRAM_COUNT_KEY;
    static const char *const EXTENDED_REGION_SIZE_KEY;
    static const char *const HAS_HISTORICAL_INFO_KEY;
    static const int DEFAULT_MULTIPLE_WORDS_DEMOTION_RATE;
    static const float MULTIPLE_WORD_COST_MULTIPLIER_SCALE;

    const FormatUtils::FORMAT_VERSION mDictFormatVersion;
    const HeaderReadWriteUtils::DictionaryFlags mDictionaryFlags;
    const int mSize;
    HeaderReadWriteUtils::AttributeMap mAttributeMap;
    const float mMultiWordCostMultiplier;
    const bool mRequiresGermanUmlautProcessing;
    const bool mIsDecayingDict;
    const int mLastUpdatedTime;
    const int mLastDecayedTime;
    const int mUnigramCount;
    const int mBigramCount;
    const int mExtendedRegionSize;
    const bool mHasHistoricalInfoOfWords;

    float readMultipleWordCostMultiplier() const;
    bool readRequiresGermanUmlautProcessing() const;

    static HeaderReadWriteUtils::AttributeMap createAttributeMapAndReadAllAttributes(
            const uint8_t *const dictBuf);
};
} // namespace latinime
#endif /* LATINIME_HEADER_POLICY_H */
