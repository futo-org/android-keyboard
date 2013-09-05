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

namespace latinime {

const char *const HeaderPolicy::MULTIPLE_WORDS_DEMOTION_RATE_KEY = "MULTIPLE_WORDS_DEMOTION_RATE";
const char *const HeaderPolicy::USES_FORGETTING_CURVE_KEY = "USES_FORGETTING_CURVE";
const char *const HeaderPolicy::LAST_UPDATED_TIME_KEY = "date";
const float HeaderPolicy::DEFAULT_MULTIPLE_WORD_COST_MULTIPLIER = 1.0f;
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
    insertCharactersIntoVector(key, &keyCodePointVector);
    HeaderReadingUtils::AttributeMap::const_iterator it = mAttributeMap.find(keyCodePointVector);
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
    int attributeValue = 0;
    if (getAttributeValueAsInt(MULTIPLE_WORDS_DEMOTION_RATE_KEY, &attributeValue)) {
        if (attributeValue <= 0) {
            return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
        }
        return MULTIPLE_WORD_COST_MULTIPLIER_SCALE / static_cast<float>(attributeValue);
    } else {
        return DEFAULT_MULTIPLE_WORD_COST_MULTIPLIER;
    }
}

bool HeaderPolicy::readUsesForgettingCurveFlag() const {
    int attributeValue = 0;
    if (getAttributeValueAsInt(USES_FORGETTING_CURVE_KEY, &attributeValue)) {
        return attributeValue != 0;
    } else {
        return false;
    }
}

// Returns S_INT_MIN when the key is not found or the value is invalid.
int HeaderPolicy::readLastUpdatedTime() const {
    int attributeValue = 0;
    if (getAttributeValueAsInt(LAST_UPDATED_TIME_KEY, &attributeValue)) {
        return attributeValue;
    } else {
        return S_INT_MIN;
    }
}

// Returns whether the key is found or not and stores the found value into outValue.
bool HeaderPolicy::getAttributeValueAsInt(const char *const key, int *const outValue) const {
    std::vector<int> keyVector;
    insertCharactersIntoVector(key, &keyVector);
    HeaderReadingUtils::AttributeMap::const_iterator it = mAttributeMap.find(keyVector);
    if (it == mAttributeMap.end()) {
        // The key was not found.
        return false;
    }
    *outValue = parseIntAttributeValue(&(it->second));
    return true;
}

/* static */ HeaderReadingUtils::AttributeMap HeaderPolicy::createAttributeMapAndReadAllAttributes(
        const uint8_t *const dictBuf) {
    HeaderReadingUtils::AttributeMap attributeMap;
    HeaderReadingUtils::fetchAllHeaderAttributes(dictBuf, &attributeMap);
    return attributeMap;
}

/* static */ int HeaderPolicy::parseIntAttributeValue(
        const std::vector<int> *const attributeValue) {
    int value = 0;
    bool isNegative = false;
    for (size_t i = 0; i < attributeValue->size(); ++i) {
        if (i == 0 && attributeValue->at(i) == '-') {
            isNegative = true;
        } else {
            if (!isdigit(attributeValue->at(i))) {
                // If not a number, return S_INT_MIN
                return S_INT_MIN;
            }
            value *= 10;
            value += attributeValue->at(i) - '0';
        }
    }
    return isNegative ? -value : value;
}

/* static */ void HeaderPolicy::insertCharactersIntoVector(const char *const characters,
        std::vector<int> *const vector) {
    for (int i = 0; characters[i]; ++i) {
        vector->push_back(characters[i]);
    }
}

} // namespace latinime
