/*
 * Copyright (C) 2014, The Android Open Source Project
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

#include "suggest/policyimpl/dictionary/structure/v4/content/dynamic_language_model_probability_utils.h"

namespace latinime {

// These counts are used to provide stable probabilities even if the user's input count is small.
const int DynamicLanguageModelProbabilityUtils::ASSUMED_MIN_COUNT_FOR_UNIGRAMS = 8192;
const int DynamicLanguageModelProbabilityUtils::ASSUMED_MIN_COUNT_FOR_BIGRAMS = 2;
const int DynamicLanguageModelProbabilityUtils::ASSUMED_MIN_COUNT_FOR_TRIGRAMS = 2;

// These are encoded backoff weights.
// Note that we give positive value for trigrams that means the weight is more than 1.
// TODO: Apply backoff for main dictionaries and quit giving a positive backoff weight.
const int DynamicLanguageModelProbabilityUtils::ENCODED_BACKOFF_WEIGHT_FOR_UNIGRAMS = -32;
const int DynamicLanguageModelProbabilityUtils::ENCODED_BACKOFF_WEIGHT_FOR_BIGRAMS = 0;
const int DynamicLanguageModelProbabilityUtils::ENCODED_BACKOFF_WEIGHT_FOR_TRIGRAMS = 8;

// This value is used to remove too old entries from the dictionary.
const int DynamicLanguageModelProbabilityUtils::DURATION_TO_DISCARD_ENTRY_IN_SECONDS =
        300 * 24 * 60 * 60; // 300 days

} // namespace latinime
