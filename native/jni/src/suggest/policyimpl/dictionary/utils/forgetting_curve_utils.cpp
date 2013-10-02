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

#include <stdlib.h>

#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT_AFTER_GC = 10000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC = 10000;

const int ForgettingCurveUtils::MAX_COMPUTED_PROBABILITY = 127;
const int ForgettingCurveUtils::MAX_ENCODED_PROBABILITY = 15;
const int ForgettingCurveUtils::MIN_VALID_ENCODED_PROBABILITY = 3;
const int ForgettingCurveUtils::ENCODED_PROBABILITY_STEP = 1;
// Currently, we try to decay each uni/bigram once every 2 hours. Accordingly, the expected
// duration of the decay is approximately 66hours.
const float ForgettingCurveUtils::MIN_PROBABILITY_TO_DECAY = 0.03f;

/* static */ int ForgettingCurveUtils::getProbability(const int encodedUnigramProbability,
        const int encodedBigramProbability) {
    if (encodedUnigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else if (encodedBigramProbability == NOT_A_PROBABILITY) {
        return backoff(decodeUnigramProbability(encodedUnigramProbability));
    } else {
        const int unigramProbability = decodeUnigramProbability(encodedUnigramProbability);
        const int bigramProbability = decodeBigramProbability(encodedBigramProbability);
        return min(max(unigramProbability, bigramProbability), MAX_COMPUTED_PROBABILITY);
    }
}

// Caveat: Unlike getProbability(), this method doesn't assume special bigram probability encoding
// (i.e. unigram probability + bigram probability delta).
/* static */ int ForgettingCurveUtils::getUpdatedEncodedProbability(
        const int originalEncodedProbability, const int newProbability) {
    if (originalEncodedProbability == NOT_A_PROBABILITY) {
        // The bigram relation is not in this dictionary.
        if (newProbability == NOT_A_PROBABILITY) {
            // The bigram target is not in other dictionaries.
            return 0;
        } else {
            return MIN_VALID_ENCODED_PROBABILITY;
        }
    } else {
        if (newProbability != NOT_A_PROBABILITY
                && originalEncodedProbability < MIN_VALID_ENCODED_PROBABILITY) {
            return MIN_VALID_ENCODED_PROBABILITY;
        }
        return min(originalEncodedProbability + ENCODED_PROBABILITY_STEP, MAX_ENCODED_PROBABILITY);
    }
}

/* static */ int ForgettingCurveUtils::isValidEncodedProbability(const int encodedProbability) {
    return encodedProbability >= MIN_VALID_ENCODED_PROBABILITY;
}

/* static */ int ForgettingCurveUtils::getEncodedProbabilityToSave(const int encodedProbability) {
    const int currentEncodedProbability = max(min(encodedProbability, MAX_ENCODED_PROBABILITY), 0);
    // TODO: Implement the decay in more proper way.
    const float currentRate = static_cast<float>(currentEncodedProbability)
            / static_cast<float>(MAX_ENCODED_PROBABILITY);
    const float thresholdToDecay = MIN_PROBABILITY_TO_DECAY
            + (1.0f - MIN_PROBABILITY_TO_DECAY) * (1.0f - currentRate);
    const float randValue = static_cast<float>(rand()) / static_cast<float>(RAND_MAX);
    if (thresholdToDecay < randValue) {
        return max(currentEncodedProbability - ENCODED_PROBABILITY_STEP, 0);
    } else {
        return currentEncodedProbability;
    }
}

/* static */ int ForgettingCurveUtils::decodeUnigramProbability(const int encodedProbability) {
    const int probability = encodedProbability - MIN_VALID_ENCODED_PROBABILITY;
    if (probability < 0) {
        return NOT_A_PROBABILITY;
    } else {
        return min(probability, MAX_ENCODED_PROBABILITY) * 8;
    }
}

/* static */ int ForgettingCurveUtils::decodeBigramProbability(const int encodedProbability) {
    const int probability = encodedProbability - MIN_VALID_ENCODED_PROBABILITY;
    if (probability < 0) {
        return NOT_A_PROBABILITY;
    } else {
        return min(probability, MAX_ENCODED_PROBABILITY) * 8;
    }
}

// See comments in ProbabilityUtils::backoff().
/* static */ int ForgettingCurveUtils::backoff(const int unigramProbability) {
    if (unigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else {
        return max(unigramProbability - 8, 0);
    }
}

} // namespace latinime
