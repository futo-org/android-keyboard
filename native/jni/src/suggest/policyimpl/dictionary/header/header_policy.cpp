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

const char *const HeaderPolicy::MULTIPLE_WORDS_DEMOTION_RATE_KEY =
        "MULTIPLE_WORDS_DEMOTION_RATE";
const float HeaderPolicy::DEFAULT_MULTI_WORD_COST_MULTIPLIER = 1.0f;
const float HeaderPolicy::MULTI_WORD_COST_MULTIPLIER_SCALE = 100.0f;

float HeaderPolicy::readMultiWordCostMultiplier() const {
    const int headerValue = HeaderReadingUtils::readHeaderValueInt(
            mDictBuf, MULTIPLE_WORDS_DEMOTION_RATE_KEY);
    if (headerValue == S_INT_MIN) {
        // not found
        return DEFAULT_MULTI_WORD_COST_MULTIPLIER;
    }
    if (headerValue <= 0) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    return MULTI_WORD_COST_MULTIPLIER_SCALE / static_cast<float>(headerValue);
}

} // namespace latinime
