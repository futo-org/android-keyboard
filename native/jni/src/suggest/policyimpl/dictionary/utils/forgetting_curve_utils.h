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

#ifndef LATINIME_FORGETTING_CURVE_UTILS_H
#define LATINIME_FORGETTING_CURVE_UTILS_H

#include "defines.h"

namespace latinime {

// TODO: Check the elapsed time and decrease the probability depending on the time. Time field is
// required to introduced to each terminal PtNode and bigram entry.
// TODO: Quit using bigram probability to indicate the delta.
// TODO: Quit using bigram probability delta.
class ForgettingCurveUtils {
 public:
    static const int MAX_UNIGRAM_COUNT;
    static const int MAX_UNIGRAM_COUNT_AFTER_GC;
    static const int MAX_BIGRAM_COUNT;
    static const int MAX_BIGRAM_COUNT_AFTER_GC;

    static int getProbability(const int encodedUnigramProbability,
            const int encodedBigramProbabilityDelta);

    static int getUpdatedUnigramProbability(const int originalEncodedProbability,
            const int newProbability);

    static int getUpdatedBigramProbabilityDelta(const int originalEncodedProbabilityDelta,
            const int newProbability);

    static int isValidUnigram(const int encodedUnigramProbability);

    static int isValidBigram(const int encodedProbabilityDelta);

    static int getUnigramProbabilityToSave(const int encodedProbability);

    static int getBigramProbabilityDeltaToSave(const int encodedProbabilityDelta);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ForgettingCurveUtils);

    static const int MAX_COMPUTED_PROBABILITY;
    static const int MAX_UNIGRAM_PROBABILITY;
    static const int MIN_VALID_UNIGRAM_PROBABILITY;
    static const int UNIGRAM_PROBABILITY_STEP;
    static const int MAX_BIGRAM_PROBABILITY_DELTA;
    static const int MIN_VALID_BIGRAM_PROBABILITY_DELTA;
    static const int BIGRAM_PROBABILITY_DELTA_STEP;

    static int decodeUnigramProbability(const int encodedProbability);

    static int decodeBigramProbabilityDelta(const int encodedProbability);

    static int getDecayedProbability(const int rawProbability);
};
} // namespace latinime
#endif /* LATINIME_FORGETTING_CURVE_UTILS_H */
