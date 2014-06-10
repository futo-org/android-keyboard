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

#include <cstdint>

#include "defines.h"
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/policyimpl/dictionary/header/header_read_write_utils.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "utils/char_utils.h"
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
              mLocale(readLocale()),
              mMultiWordCostMultiplier(readMultipleWordCostMultiplier()),
              mRequiresGermanUmlautProcessing(readRequiresGermanUmlautProcessing()),
              mIsDecayingDict(HeaderReadWriteUtils::readBoolAttributeValue(&mAttributeMap,
                      IS_DECAYING_DICT_KEY, false /* defaultValue */)),
              mDate(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      DATE_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mLastDecayedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      LAST_DECAYED_TIME_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mUnigramCount(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      UNIGRAM_COUNT_KEY, 0 /* defaultValue */)),
              mBigramCount(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      BIGRAM_COUNT_KEY, 0 /* defaultValue */)),
              mExtendedRegionSize(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      EXTENDED_REGION_SIZE_KEY, 0 /* defaultValue */)),
              mHasHistoricalInfoOfWords(HeaderReadWriteUtils::readBoolAttributeValue(
                      &mAttributeMap, HAS_HISTORICAL_INFO_KEY, false /* defaultValue */)),
              mForgettingCurveOccurrencesToLevelUp(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP_KEY,
                      DEFAULT_FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP)),
              mForgettingCurveProbabilityValuesTableId(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID_KEY,
                      DEFAULT_FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID)),
              mForgettingCurveDurationToLevelDown(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS_KEY,
                      DEFAULT_FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS)),
              mMaxUnigramCount(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, MAX_UNIGRAM_COUNT_KEY, DEFAULT_MAX_UNIGRAM_COUNT)),
              mMaxBigramCount(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, MAX_BIGRAM_COUNT_KEY, DEFAULT_MAX_BIGRAM_COUNT)) {}

    // Constructs header information using an attribute map.
    HeaderPolicy(const FormatUtils::FORMAT_VERSION dictFormatVersion,
            const std::vector<int> &locale,
            const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap)
            : mDictFormatVersion(dictFormatVersion),
              mDictionaryFlags(HeaderReadWriteUtils::createAndGetDictionaryFlagsUsingAttributeMap(
                      attributeMap)), mSize(0), mAttributeMap(*attributeMap), mLocale(locale),
              mMultiWordCostMultiplier(readMultipleWordCostMultiplier()),
              mRequiresGermanUmlautProcessing(readRequiresGermanUmlautProcessing()),
              mIsDecayingDict(HeaderReadWriteUtils::readBoolAttributeValue(&mAttributeMap,
                      IS_DECAYING_DICT_KEY, false /* defaultValue */)),
              mDate(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      DATE_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mLastDecayedTime(HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
                      DATE_KEY, TimeKeeper::peekCurrentTime() /* defaultValue */)),
              mUnigramCount(0), mBigramCount(0), mExtendedRegionSize(0),
              mHasHistoricalInfoOfWords(HeaderReadWriteUtils::readBoolAttributeValue(
                      &mAttributeMap, HAS_HISTORICAL_INFO_KEY, false /* defaultValue */)),
              mForgettingCurveOccurrencesToLevelUp(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP_KEY,
                      DEFAULT_FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP)),
              mForgettingCurveProbabilityValuesTableId(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID_KEY,
                      DEFAULT_FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID)),
              mForgettingCurveDurationToLevelDown(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS_KEY,
                      DEFAULT_FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS)),
              mMaxUnigramCount(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, MAX_UNIGRAM_COUNT_KEY, DEFAULT_MAX_UNIGRAM_COUNT)),
              mMaxBigramCount(HeaderReadWriteUtils::readIntAttributeValue(
                      &mAttributeMap, MAX_BIGRAM_COUNT_KEY, DEFAULT_MAX_BIGRAM_COUNT)) {}

    // Copy header information
    HeaderPolicy(const HeaderPolicy *const headerPolicy)
            : mDictFormatVersion(headerPolicy->mDictFormatVersion),
              mDictionaryFlags(headerPolicy->mDictionaryFlags), mSize(headerPolicy->mSize),
              mAttributeMap(headerPolicy->mAttributeMap), mLocale(headerPolicy->mLocale),
              mMultiWordCostMultiplier(headerPolicy->mMultiWordCostMultiplier),
              mRequiresGermanUmlautProcessing(headerPolicy->mRequiresGermanUmlautProcessing),
              mIsDecayingDict(headerPolicy->mIsDecayingDict),
              mDate(headerPolicy->mDate), mLastDecayedTime(headerPolicy->mLastDecayedTime),
              mUnigramCount(headerPolicy->mUnigramCount), mBigramCount(headerPolicy->mBigramCount),
              mExtendedRegionSize(headerPolicy->mExtendedRegionSize),
              mHasHistoricalInfoOfWords(headerPolicy->mHasHistoricalInfoOfWords),
              mForgettingCurveOccurrencesToLevelUp(
                      headerPolicy->mForgettingCurveOccurrencesToLevelUp),
              mForgettingCurveProbabilityValuesTableId(
                      headerPolicy->mForgettingCurveProbabilityValuesTableId),
              mForgettingCurveDurationToLevelDown(
                      headerPolicy->mForgettingCurveDurationToLevelDown),
              mMaxUnigramCount(headerPolicy->mMaxUnigramCount),
              mMaxBigramCount(headerPolicy->mMaxBigramCount) {}

    // Temporary dummy header.
    HeaderPolicy()
            : mDictFormatVersion(FormatUtils::UNKNOWN_VERSION), mDictionaryFlags(0), mSize(0),
              mAttributeMap(), mLocale(CharUtils::EMPTY_STRING), mMultiWordCostMultiplier(0.0f),
              mRequiresGermanUmlautProcessing(false), mIsDecayingDict(false),
              mDate(0), mLastDecayedTime(0), mUnigramCount(0), mBigramCount(0),
              mExtendedRegionSize(0), mHasHistoricalInfoOfWords(false),
              mForgettingCurveOccurrencesToLevelUp(0), mForgettingCurveProbabilityValuesTableId(0),
              mForgettingCurveDurationToLevelDown(0), mMaxUnigramCount(0), mMaxBigramCount(0) {}

    ~HeaderPolicy() {}

    virtual int getFormatVersionNumber() const {
        // Conceptually this converts the symbolic value we use in the code into the
        // hardcoded of the bytes in the file. But we want the constants to be the
        // same so we use them for both here.
        switch (mDictFormatVersion) {
            case FormatUtils::VERSION_2:
                return FormatUtils::VERSION_2;
            case FormatUtils::VERSION_4_ONLY_FOR_TESTING:
                return FormatUtils::VERSION_4_ONLY_FOR_TESTING;
            case FormatUtils::VERSION_4:
                return FormatUtils::VERSION_4;
            case FormatUtils::VERSION_4_DEV:
                return FormatUtils::VERSION_4_DEV;
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

    AK_FORCE_INLINE int getDate() const {
        return mDate;
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

    AK_FORCE_INLINE bool shouldBoostExactMatches() const {
        // TODO: Investigate better ways to handle exact matches for personalized dictionaries.
        return !isDecayingDict();
    }

    const DictionaryHeaderStructurePolicy::AttributeMap *getAttributeMap() const {
        return &mAttributeMap;
    }

    AK_FORCE_INLINE int getForgettingCurveOccurrencesToLevelUp() const {
        return mForgettingCurveOccurrencesToLevelUp;
    }

    AK_FORCE_INLINE int getForgettingCurveProbabilityValuesTableId() const {
        return mForgettingCurveProbabilityValuesTableId;
    }

    AK_FORCE_INLINE int getForgettingCurveDurationToLevelDown() const {
        return mForgettingCurveDurationToLevelDown;
    }

    AK_FORCE_INLINE int getMaxUnigramCount() const {
        return mMaxUnigramCount;
    }

    AK_FORCE_INLINE int getMaxBigramCount() const {
        return mMaxBigramCount;
    }

    void readHeaderValueOrQuestionMark(const char *const key,
            int *outValue, int outValueSize) const;

    bool fillInAndWriteHeaderToBuffer(const bool updatesLastDecayedTime,
            const int unigramCount, const int bigramCount,
            const int extendedRegionSize, BufferWithExtendableBuffer *const outBuffer) const;

    void fillInHeader(const bool updatesLastDecayedTime,
            const int unigramCount, const int bigramCount, const int extendedRegionSize,
            DictionaryHeaderStructurePolicy::AttributeMap *outAttributeMap) const;

    AK_FORCE_INLINE const std::vector<int> *getLocale() const {
        return &mLocale;
    }

    bool supportsBeginningOfSentence() const {
        return mDictFormatVersion >= FormatUtils::VERSION_4;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(HeaderPolicy);

    static const char *const MULTIPLE_WORDS_DEMOTION_RATE_KEY;
    static const char *const REQUIRES_GERMAN_UMLAUT_PROCESSING_KEY;
    static const char *const IS_DECAYING_DICT_KEY;
    static const char *const DATE_KEY;
    static const char *const LAST_DECAYED_TIME_KEY;
    static const char *const UNIGRAM_COUNT_KEY;
    static const char *const BIGRAM_COUNT_KEY;
    static const char *const EXTENDED_REGION_SIZE_KEY;
    static const char *const HAS_HISTORICAL_INFO_KEY;
    static const char *const LOCALE_KEY;
    static const char *const FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP_KEY;
    static const char *const FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID_KEY;
    static const char *const FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS_KEY;
    static const char *const MAX_UNIGRAM_COUNT_KEY;
    static const char *const MAX_BIGRAM_COUNT_KEY;
    static const int DEFAULT_MULTIPLE_WORDS_DEMOTION_RATE;
    static const float MULTIPLE_WORD_COST_MULTIPLIER_SCALE;
    static const int DEFAULT_FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP;
    static const int DEFAULT_FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID;
    static const int DEFAULT_FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS;
    static const int DEFAULT_MAX_UNIGRAM_COUNT;
    static const int DEFAULT_MAX_BIGRAM_COUNT;

    const FormatUtils::FORMAT_VERSION mDictFormatVersion;
    const HeaderReadWriteUtils::DictionaryFlags mDictionaryFlags;
    const int mSize;
    DictionaryHeaderStructurePolicy::AttributeMap mAttributeMap;
    const std::vector<int> mLocale;
    const float mMultiWordCostMultiplier;
    const bool mRequiresGermanUmlautProcessing;
    const bool mIsDecayingDict;
    const int mDate;
    const int mLastDecayedTime;
    const int mUnigramCount;
    const int mBigramCount;
    const int mExtendedRegionSize;
    const bool mHasHistoricalInfoOfWords;
    const int mForgettingCurveOccurrencesToLevelUp;
    const int mForgettingCurveProbabilityValuesTableId;
    const int mForgettingCurveDurationToLevelDown;
    const int mMaxUnigramCount;
    const int mMaxBigramCount;

    const std::vector<int> readLocale() const;
    float readMultipleWordCostMultiplier() const;
    bool readRequiresGermanUmlautProcessing() const;

    static DictionaryHeaderStructurePolicy::AttributeMap createAttributeMapAndReadAllAttributes(
            const uint8_t *const dictBuf);
};
} // namespace latinime
#endif /* LATINIME_HEADER_POLICY_H */
