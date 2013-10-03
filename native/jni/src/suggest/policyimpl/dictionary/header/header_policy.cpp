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

#include <cstddef>
#include <cstdio>
#include <ctime>

namespace latinime {


// Note that these are corresponding definitions in Java side in FormatSpec.FileHeader.
const char *const HeaderPolicy::MULTIPLE_WORDS_DEMOTION_RATE_KEY = "MULTIPLE_WORDS_DEMOTION_RATE";
const char *const HeaderPolicy::USES_FORGETTING_CURVE_KEY = "USES_FORGETTING_CURVE";
const char *const HeaderPolicy::LAST_UPDATED_TIME_KEY = "date";
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
    std::vector<int> keyVector;
    HeaderReadWriteUtils::insertCharactersIntoVector(MULTIPLE_WORDS_DEMOTION_RATE_KEY, &keyVector);
    const int demotionRate = HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap,
            &keyVector, DEFAULT_MULTIPLE_WORDS_DEMOTION_RATE);
    if (demotionRate <= 0) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    return MULTIPLE_WORD_COST_MULTIPLIER_SCALE / static_cast<float>(demotionRate);
}

bool HeaderPolicy::readUsesForgettingCurveFlag() const {
    std::vector<int> keyVector;
    HeaderReadWriteUtils::insertCharactersIntoVector(USES_FORGETTING_CURVE_KEY, &keyVector);
    return HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap, &keyVector,
            false /* defaultValue */);
}

// Returns current time when the key is not found or the value is invalid.
int HeaderPolicy::readLastUpdatedTime() const {
    std::vector<int> keyVector;
    HeaderReadWriteUtils::insertCharactersIntoVector(LAST_UPDATED_TIME_KEY, &keyVector);
    return HeaderReadWriteUtils::readIntAttributeValue(&mAttributeMap, &keyVector,
            time(0) /* defaultValue */);
}

bool HeaderPolicy::writeHeaderToBuffer(BufferWithExtendableBuffer *const bufferToWrite,
        const bool updatesLastUpdatedTime) const {
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
    if (updatesLastUpdatedTime) {
        // Set current time as a last updated time.
        HeaderReadWriteUtils::AttributeMap attributeMapTowrite(mAttributeMap);
        std::vector<int> updatedTimekey;
        HeaderReadWriteUtils::insertCharactersIntoVector(LAST_UPDATED_TIME_KEY, &updatedTimekey);
        HeaderReadWriteUtils::setIntAttribute(&attributeMapTowrite, &updatedTimekey, time(0));
        if (!HeaderReadWriteUtils::writeHeaderAttributes(bufferToWrite, &attributeMapTowrite,
                &writingPos)) {
            return false;
        }
    } else {
        if (!HeaderReadWriteUtils::writeHeaderAttributes(bufferToWrite, &mAttributeMap,
                &writingPos)) {
            return false;
        }
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
