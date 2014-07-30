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

class HeaderPolicy;

class ForgettingCurveUtils {
 public:
    static const HistoricalInfo createUpdatedHistoricalInfo(
            const HistoricalInfo *const originalHistoricalInfo, const int newProbability,
            const HistoricalInfo *const newHistoricalInfo, const HeaderPolicy *const headerPolicy);

    static const HistoricalInfo createHistoricalInfoToSave(
            const HistoricalInfo *const originalHistoricalInfo,
            const HeaderPolicy *const headerPolicy);

    static int decodeProbability(const HistoricalInfo *const historicalInfo,
            const HeaderPolicy *const headerPolicy);

    static int getProbability(const int encodedUnigramProbability,
            const int encodedBigramProbability);

    static bool needsToKeep(const HistoricalInfo *const historicalInfo,
            const HeaderPolicy *const headerPolicy);

    static bool needsToDecay(const bool mindsBlockByDecay, const int unigramCount,
            const int bigramCount, const HeaderPolicy *const headerPolicy);

    AK_FORCE_INLINE static int getUnigramCountHardLimit(const int maxUnigramCount) {
        return static_cast<int>(static_cast<float>(maxUnigramCount)
                * UNIGRAM_COUNT_HARD_LIMIT_WEIGHT);
    }

    AK_FORCE_INLINE static int getBigramCountHardLimit(const int maxBigramCount) {
        return static_cast<int>(static_cast<float>(maxBigramCount)
                * BIGRAM_COUNT_HARD_LIMIT_WEIGHT);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ForgettingCurveUtils);

    class ProbabilityTable {
     public:
        ProbabilityTable();

        int getProbability(const int tableId, const int level,
                const int elapsedTimeStepCount) const {
            return mTables[tableId][level][elapsedTimeStepCount];
        }

     private:
        DISALLOW_COPY_AND_ASSIGN(ProbabilityTable);

        static const int PROBABILITY_TABLE_COUNT;
        static const int WEAK_PROBABILITY_TABLE_ID;
        static const int MODEST_PROBABILITY_TABLE_ID;
        static const int STRONG_PROBABILITY_TABLE_ID;
        static const int AGGRESSIVE_PROBABILITY_TABLE_ID;

        static const int WEAK_MAX_PROBABILITY;
        static const int MODEST_BASE_PROBABILITY;
        static const int STRONG_BASE_PROBABILITY;
        static const int AGGRESSIVE_BASE_PROBABILITY;

        std::vector<std::vector<std::vector<int>>> mTables;

        static int getBaseProbabilityForLevel(const int tableId, const int level);
    };

    static const int MULTIPLIER_TWO_IN_PROBABILITY_SCALE;
    static const int DECAY_INTERVAL_SECONDS;

    static const int MAX_LEVEL;
    static const int MIN_VISIBLE_LEVEL;
    static const int MAX_ELAPSED_TIME_STEP_COUNT;
    static const int DISCARD_LEVEL_ZERO_ENTRY_TIME_STEP_COUNT_THRESHOLD;

    static const float UNIGRAM_COUNT_HARD_LIMIT_WEIGHT;
    static const float BIGRAM_COUNT_HARD_LIMIT_WEIGHT;

    static const ProbabilityTable sProbabilityTable;

    static int backoff(const int unigramProbability);
    static int getElapsedTimeStepCount(const int timestamp, const int durationToLevelDown);
    static int clampToVisibleEntryLevelRange(const int level);
    static int clampToValidLevelRange(const int level);
    static int clampToValidCountRange(const int count, const HeaderPolicy *const headerPolicy);
    static int clampToValidTimeStepCountRange(const int timeStepCount);
};
} // namespace latinime
#endif /* LATINIME_FORGETTING_CURVE_UTILS_H */
