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

#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/historical_info.h"

namespace latinime {

class DictionaryHeaderStructurePolicy;

// TODO: Check the elapsed time and decrease the probability depending on the time. Time field is
// required to introduced to each terminal PtNode and bigram entry.
// TODO: Quit using bigram probability to indicate the delta.
class ForgettingCurveUtils {
 public:
    static const int MAX_UNIGRAM_COUNT;
    static const int MAX_UNIGRAM_COUNT_AFTER_GC;
    static const int MAX_BIGRAM_COUNT;
    static const int MAX_BIGRAM_COUNT_AFTER_GC;

    static const HistoricalInfo createUpdatedHistoricalInfo(
            const HistoricalInfo *const originalHistoricalInfo, const int newProbability,
            const int timestamp);

    static const HistoricalInfo createHistoricalInfoToSave(
            const HistoricalInfo *const originalHistoricalInfo);

    static int getProbability(const int encodedUnigramProbability,
            const int encodedBigramProbability);

    // TODO: Remove.
    static int getUpdatedEncodedProbability(const int originalEncodedProbability,
            const int newProbability);

    // TODO: Remove.
    static int isValidEncodedProbability(const int encodedProbability);

    static bool needsToKeep(const HistoricalInfo *const historicalInfo);

    // TODO: Remove.
    static int getEncodedProbabilityToSave(const int encodedProbability,
            const DictionaryHeaderStructurePolicy *const headerPolicy);

    static bool needsToDecay(const bool mindsBlockByDecay, const int unigramCount,
            const int bigramCount, const DictionaryHeaderStructurePolicy *const headerPolicy);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ForgettingCurveUtils);

    class ProbabilityTable {
     public:
        ProbabilityTable();

        int getProbability(const int encodedProbability) const {
            if (encodedProbability < 0 || encodedProbability > static_cast<int>(mTable.size())) {
                return NOT_A_PROBABILITY;
            }
            return mTable[encodedProbability];
        }

     private:
        DISALLOW_COPY_AND_ASSIGN(ProbabilityTable);

        std::vector<int> mTable;
    };

    static const int MAX_COMPUTED_PROBABILITY;
    static const int MAX_ENCODED_PROBABILITY;
    static const int MIN_VALID_ENCODED_PROBABILITY;
    static const int ENCODED_PROBABILITY_STEP;
    static const float MIN_PROBABILITY_TO_DECAY;
    static const int DECAY_INTERVAL_SECONDS;

    static const int MAX_LEVEL;
    static const int MAX_COUNT;
    static const int MIN_VALID_LEVEL;
    static const int TIME_STEP_DURATION_IN_SECONDS;
    static const int MAX_ELAPSED_TIME_STEP_COUNT;
    static const int DISCARD_LEVEL_ZERO_ENTRY_TIME_STEP_COUNT_THRESHOLD;

    static const ProbabilityTable sProbabilityTable;

    static int decodeProbability(const int encodedProbability);

    static int backoff(const int unigramProbability);

    static int getElapsedTimeStepCount(const int timestamp);
};
} // namespace latinime
#endif /* LATINIME_FORGETTING_CURVE_UTILS_H */
