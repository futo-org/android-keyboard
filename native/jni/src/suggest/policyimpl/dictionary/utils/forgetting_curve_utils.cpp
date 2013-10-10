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

#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

#include "suggest/policyimpl/dictionary/utils/probability_utils.h"

namespace latinime {

const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT_AFTER_GC = 10000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC = 10000;

const int ForgettingCurveUtils::MAX_COMPUTED_PROBABILITY = 127;
const int ForgettingCurveUtils::MAX_UNIGRAM_PROBABILITY = 120;
const int ForgettingCurveUtils::MIN_VALID_UNIGRAM_PROBABILITY = 24;
const int ForgettingCurveUtils::UNIGRAM_PROBABILITY_STEP = 8;
const int ForgettingCurveUtils::MAX_BIGRAM_PROBABILITY_DELTA = 15;
const int ForgettingCurveUtils::MIN_VALID_BIGRAM_PROBABILITY_DELTA = 3;
const int ForgettingCurveUtils::BIGRAM_PROBABILITY_DELTA_STEP = 1;

/* static */ int ForgettingCurveUtils::getProbability(const int encodedUnigramProbability,
        const int encodedBigramProbabilityDelta) {
    if (encodedUnigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else if (encodedBigramProbabilityDelta == NOT_A_PROBABILITY) {
        const int rawProbability = ProbabilityUtils::backoff(decodeUnigramProbability(
                encodedUnigramProbability));
        return min(getDecayedProbability(rawProbability), MAX_COMPUTED_PROBABILITY);
    } else {
        const int rawProbability = ProbabilityUtils::computeProbabilityForBigram(
                decodeUnigramProbability(encodedUnigramProbability),
                decodeBigramProbabilityDelta(encodedBigramProbabilityDelta));
        return min(getDecayedProbability(rawProbability), MAX_COMPUTED_PROBABILITY);
    }
}

/* static */ int ForgettingCurveUtils::getUpdatedUnigramProbability(
        const int originalEncodedProbability, const int newProbability) {
    if (originalEncodedProbability == NOT_A_PROBABILITY) {
        // The unigram is not in this dictionary.
        if (newProbability == NOT_A_PROBABILITY) {
            // The unigram is not in other dictionaries.
            return 0;
        } else {
            return MIN_VALID_UNIGRAM_PROBABILITY;
        }
    } else {
        if (newProbability != NOT_A_PROBABILITY
                && originalEncodedProbability < MIN_VALID_UNIGRAM_PROBABILITY) {
            return MIN_VALID_UNIGRAM_PROBABILITY;
        }
        return min(originalEncodedProbability + UNIGRAM_PROBABILITY_STEP, MAX_UNIGRAM_PROBABILITY);
    }
}

/* static */ int ForgettingCurveUtils::getUnigramProbabilityToSave(const int encodedProbability) {
    return max(encodedProbability - UNIGRAM_PROBABILITY_STEP, 0);
}

/* static */ int ForgettingCurveUtils::getBigramProbabilityDeltaToSave(
        const int encodedProbabilityDelta) {
    return max(encodedProbabilityDelta - BIGRAM_PROBABILITY_DELTA_STEP, 0);
}

/* static */ int ForgettingCurveUtils::getUpdatedBigramProbabilityDelta(
        const int originalEncodedProbabilityDelta, const int newProbability) {
    if (originalEncodedProbabilityDelta == NOT_A_PROBABILITY) {
        // The bigram relation is not in this dictionary.
        if (newProbability == NOT_A_PROBABILITY) {
            // The bigram target is not in other dictionaries.
            return 0;
        } else {
            return MIN_VALID_BIGRAM_PROBABILITY_DELTA;
        }
    } else {
        if (newProbability != NOT_A_PROBABILITY
                && originalEncodedProbabilityDelta < MIN_VALID_BIGRAM_PROBABILITY_DELTA) {
            return MIN_VALID_BIGRAM_PROBABILITY_DELTA;
        }
        return min(originalEncodedProbabilityDelta + BIGRAM_PROBABILITY_DELTA_STEP,
                MAX_BIGRAM_PROBABILITY_DELTA);
    }
}

/* static */ int ForgettingCurveUtils::isValidUnigram(const int encodedUnigramProbability) {
    return encodedUnigramProbability >= MIN_VALID_UNIGRAM_PROBABILITY;
}

/* static */ int ForgettingCurveUtils::isValidBigram(const int encodedBigramProbabilityDelta) {
    return encodedBigramProbabilityDelta >= MIN_VALID_BIGRAM_PROBABILITY_DELTA;
}

/* static */ int ForgettingCurveUtils::decodeUnigramProbability(const int encodedProbability) {
    const int probability = encodedProbability - MIN_VALID_UNIGRAM_PROBABILITY;
    if (probability < 0) {
        return NOT_A_PROBABILITY;
    } else {
        return min(probability, MAX_UNIGRAM_PROBABILITY);
    }
}

/* static */ int ForgettingCurveUtils::decodeBigramProbabilityDelta(
        const int encodedProbabilityDelta) {
    const int probabilityDelta = encodedProbabilityDelta - MIN_VALID_BIGRAM_PROBABILITY_DELTA;
    if (probabilityDelta < 0) {
        return NOT_A_PROBABILITY;
    } else {
        return min(probabilityDelta, MAX_BIGRAM_PROBABILITY_DELTA);
    }
}

/* static */ int ForgettingCurveUtils::getDecayedProbability(const int rawProbability) {
    return rawProbability;
}

} // namespace latinime
