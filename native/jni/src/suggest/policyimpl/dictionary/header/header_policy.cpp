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

#include "suggest/policyimpl/dictionary/header/header_policy.h"

namespace latinime {

// Note that these are corresponding definitions in Java side in FormatSpec.FileHeader.
const char *const HeaderPolicy::MULTIPLE_WORDS_DEMOTION_RATE_KEY = "MULTIPLE_WORDS_DEMOTION_RATE";
// TODO: Change attribute string to "IS_DECAYING_DICT".
const char *const HeaderPolicy::IS_DECAYING_DICT_KEY = "USES_FORGETTING_CURVE";
const char *const HeaderPolicy::LAST_UPDATED_TIME_KEY = "date";
const char *const HeaderPolicy::LAST_DECAYED_TIME_KEY = "LAST_DECAYED_TIME";
const char *const HeaderPolicy::UNIGRAM_COUNT_KEY = "UNIGRAM_COUNT";
const char *const HeaderPolicy::BIGRAM_COUNT_KEY = "BIGRAM_COUNT";
const char *const HeaderPolicy::EXTENDED_REGION_SIZE_KEY = "EXTENDED_REGION_SIZE";
const int HeaderPolicy::DEFAULT_MULTIPLE_WORDS_DEMOTION_RATE = 100;
const float HeaderPolicy::MULTIPLE_WORD_COST_MULTIPLIER_SCALE = 100.0f;

// Used for logging. Question mark is used to indicate that the key is not found.
void HeaderPolicy::readHeaderValueOrQuestionMark(const char *const key, int *outValue,
        int outValueSize) const {
    if (outValueSize <= 0) return;
    if (outValueSize == 1) {
        outValue[0] = '\0';
        return;
    }
    std::vector<int> keyCodePointVector;
    HeaderReadWriteUtils::insertCharactersIntoVector(key, &keyCodePointVector);
    HeaderReadWriteUtils::AttributeMap::const_iterator it = mAttributeMap.find(keyCodePointVector);
    if (it == mAttributeMap.end()) {
        // The key was not found.
        outValue[0] = '?';
        outValue[1] = '\0';
        return;
    }
    const int terminalIndex = min(static_cast<int>(it->second.size()), outValueSize - 1);
    for (int i = 0; i < terminalIndex; ++i) {
        outValue[i] = it->second[i];
    }
    outValue[terminalIndex] = '\0';
}

float HeaderPolicy::readMultipleWordCostMultiplier() const {
    const int demotionRate = HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
            MULTIPLE_WORDS_DEMOTION_RATE_KEY, DEFAULT_MULTIPLE_WORDS_DEMOTION_RATE);
    if (demotionRate <= 0) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    return MULTIPLE_WORD_COST_MULTIPLIER_SCALE / static_cast<float>(demotionRate);
}

bool HeaderPolicy::writeHeaderToBuffer(BufferWithExtendableBuffer *const bufferToWrite,
        const bool updatesLastUpdatedTime, const bool updatesLastDecayedTime,
        const int unigramCount, const int bigramCount, const int extendedRegionSize) const {
    int writingPos = 0;
    if (!HeaderReadWriteUtils::writeDictionaryVersion(bufferToWrite, mDictFormatVersion,
            &writingPos)) {
        return false;
    }
    if (!HeaderReadWriteUtils::writeDictionaryFlags(bufferToWrite, mDictionaryFlags,
            &writingPos)) {
        return false;
    }
    // Temporarily writes a dummy header size.
    int headerSizeFieldPos = writingPos;
    if (!HeaderReadWriteUtils::writeDictionaryHeaderSize(bufferToWrite, 0 /* size */,
            &writingPos)) {
        return false;
    }
    HeaderReadWriteUtils::AttributeMap attributeMapTowrite(mAttributeMap);
    HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, UNIGRAM_COUNT_KEY, unigramCount);
    HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, BIGRAM_COUNT_KEY, bigramCount);
    HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, EXTENDED_REGION_SIZE_KEY,
            extendedRegionSize);
    if (updatesLastUpdatedTime) {
        // Set current time as a last updated time.
        HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, LAST_UPDATED_TIME_KEY,
                time(0));
    }
    if (updatesLastDecayedTime) {
        // Set current time as a last updated time.
        HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, LAST_DECAYED_TIME_KEY,
                time(0));
    }
    if (!HeaderReadWriteUtils::writeHeaderAttributes(bufferToWrite, &attributeMapTowrite,
            &writingPos)) {
        return false;
    }
    // Writes an actual header size.
    if (!HeaderReadWriteUtils::writeDictionaryHeaderSize(bufferToWrite, writingPos,
            &headerSizeFieldPos)) {
        return false;
    }
    return true;
}

/* static */ HeaderReadWriteUtils::AttributeMap
        HeaderPolicy::createAttributeMapAndReadAllAttributes(const uint8_t *const dictBuf) {
    HeaderReadWriteUtils::AttributeMap attributeMap;
    HeaderReadWriteUtils::fetchAllHeaderAttributes(dictBuf, &attributeMap);
    return attributeMap;
}

} // namespace latinime
