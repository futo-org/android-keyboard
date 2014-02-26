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

#include <cmath>
#include <stdlib.h>

#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/utils/probability_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_UNIGRAM_COUNT_AFTER_GC = 10000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT = 12000;
const int ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC = 10000;

const int ForgettingCurveUtils::MULTIPLIER_TWO_IN_PROBABILITY_SCALE = 8;
const int ForgettingCurveUtils::DECAY_INTERVAL_SECONDS = 2 * 60 * 60;

const int ForgettingCurveUtils::MAX_LEVEL = 3;
const int ForgettingCurveUtils::MIN_VALID_LEVEL = 1;
const int ForgettingCurveUtils::TIME_STEP_DURATION_IN_SECONDS = 6 * 60 * 60;
const int ForgettingCurveUtils::MAX_ELAPSED_TIME_STEP_COUNT = 15;
const int ForgettingCurveUtils::DISCARD_LEVEL_ZERO_ENTRY_TIME_STEP_COUNT_THRESHOLD = 14;

const ForgettingCurveUtils::ProbabilityTable ForgettingCurveUtils::sProbabilityTable;

// TODO: Revise the logic to decide the initial probability depending on the given probability.
/* static */ const HistoricalInfo ForgettingCurveUtils::createUpdatedHistoricalInfo(
        const HistoricalInfo *const originalHistoricalInfo,
        const int newProbability, const int timestamp, const HeaderPolicy *const headerPolicy) {
    if (newProbability != NOT_A_PROBABILITY && originalHistoricalInfo->getLevel() == 0) {
        return HistoricalInfo(timestamp, MIN_VALID_LEVEL /* level */, 0 /* count */);
    } else if (!originalHistoricalInfo->isValid()) {
        // Initial information.
        return HistoricalInfo(timestamp, 0 /* level */, 1 /* count */);
    } else {
        const int updatedCount = originalHistoricalInfo->getCount() + 1;
        if (updatedCount >= headerPolicy->getForgettingCurveOccurrencesToLevelUp()) {
            // The count exceeds the max value the level can be incremented.
            if (originalHistoricalInfo->getLevel() >= MAX_LEVEL) {
                // The level is already max.
                return HistoricalInfo(timestamp, originalHistoricalInfo->getLevel(),
                        originalHistoricalInfo->getCount());
            } else {
                // Level up.
                return HistoricalInfo(timestamp, originalHistoricalInfo->getLevel() + 1,
                        0 /* count */);
            }
        } else {
            return HistoricalInfo(timestamp, originalHistoricalInfo->getLevel(), updatedCount);
        }
    }
}

/* static */ int ForgettingCurveUtils::decodeProbability(
        const HistoricalInfo *const historicalInfo, const HeaderPolicy *const headerPolicy) {
    const int elapsedTimeStepCount = getElapsedTimeStepCount(historicalInfo->getTimeStamp());
    return sProbabilityTable.getProbability(
            headerPolicy->getForgettingCurveProbabilityValuesTableId(), historicalInfo->getLevel(),
            min(max(elapsedTimeStepCount, 0), MAX_ELAPSED_TIME_STEP_COUNT));
}

/* static */ int ForgettingCurveUtils::getProbability(const int unigramProbability,
        const int bigramProbability) {
    if (unigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else if (bigramProbability == NOT_A_PROBABILITY) {
        return min(backoff(unigramProbability), MAX_PROBABILITY);
    } else {
        // TODO: Investigate better way to handle bigram probability.
        return min(max(unigramProbability, bigramProbability + MULTIPLIER_TWO_IN_PROBABILITY_SCALE),
                MAX_PROBABILITY);
    }
}

/* static */ bool ForgettingCurveUtils::needsToKeep(const HistoricalInfo *const historicalInfo) {
    return historicalInfo->getLevel() > 0
            || getElapsedTimeStepCount(historicalInfo->getTimeStamp())
                    < DISCARD_LEVEL_ZERO_ENTRY_TIME_STEP_COUNT_THRESHOLD;
}

/* static */ const HistoricalInfo ForgettingCurveUtils::createHistoricalInfoToSave(
        const HistoricalInfo *const originalHistoricalInfo,
        const HeaderPolicy *const headerPolicy) {
    if (originalHistoricalInfo->getTimeStamp() == NOT_A_TIMESTAMP) {
        return HistoricalInfo();
    }
    const int elapsedTimeStep = getElapsedTimeStepCount(originalHistoricalInfo->getTimeStamp());
    if (elapsedTimeStep <= MAX_ELAPSED_TIME_STEP_COUNT) {
        // No need to update historical info.
        return *originalHistoricalInfo;
    }
    // Level down.
    const int maxLevelDownAmonut = elapsedTimeStep / (MAX_ELAPSED_TIME_STEP_COUNT + 1);
    const int levelDownAmount = (maxLevelDownAmonut >= originalHistoricalInfo->getLevel()) ?
            originalHistoricalInfo->getLevel() : maxLevelDownAmonut;
    const int adjustedTimestamp = originalHistoricalInfo->getTimeStamp() +
            levelDownAmount * (MAX_ELAPSED_TIME_STEP_COUNT + 1) * TIME_STEP_DURATION_IN_SECONDS;
    return HistoricalInfo(adjustedTimestamp,
            originalHistoricalInfo->getLevel() - levelDownAmount, 0 /* count */);
}

/* static */ bool ForgettingCurveUtils::needsToDecay(const bool mindsBlockByDecay,
        const int unigramCount, const int bigramCount, const HeaderPolicy *const headerPolicy) {
    if (unigramCount >= ForgettingCurveUtils::MAX_UNIGRAM_COUNT) {
        // Unigram count exceeds the limit.
        return true;
    } else if (bigramCount >= ForgettingCurveUtils::MAX_BIGRAM_COUNT) {
        // Bigram count exceeds the limit.
        return true;
    }
    if (mindsBlockByDecay) {
        return false;
    }
    if (headerPolicy->getLastDecayedTime() + DECAY_INTERVAL_SECONDS
            < TimeKeeper::peekCurrentTime()) {
        // Time to decay.
        return true;
    }
    return false;
}

// See comments in ProbabilityUtils::backoff().
/* static */ int ForgettingCurveUtils::backoff(const int unigramProbability) {
    // See TODO comments in ForgettingCurveUtils::getProbability().
    return unigramProbability;
}

/* static */ int ForgettingCurveUtils::getElapsedTimeStepCount(const int timestamp) {
    return (TimeKeeper::peekCurrentTime() - timestamp) / TIME_STEP_DURATION_IN_SECONDS;
}

const int ForgettingCurveUtils::ProbabilityTable::PROBABILITY_TABLE_COUNT = 4;
const int ForgettingCurveUtils::ProbabilityTable::WEAK_PROBABILITY_TABLE_ID = 0;
const int ForgettingCurveUtils::ProbabilityTable::MODEST_PROBABILITY_TABLE_ID = 1;
const int ForgettingCurveUtils::ProbabilityTable::STRONG_PROBABILITY_TABLE_ID = 2;
const int ForgettingCurveUtils::ProbabilityTable::AGGRESSIVE_PROBABILITY_TABLE_ID = 3;
const int ForgettingCurveUtils::ProbabilityTable::WEAK_MAX_PROBABILITY = 127;
const int ForgettingCurveUtils::ProbabilityTable::MODEST_BASE_PROBABILITY = 32;
const int ForgettingCurveUtils::ProbabilityTable::STRONG_BASE_PROBABILITY = 35;
const int ForgettingCurveUtils::ProbabilityTable::AGGRESSIVE_BASE_PROBABILITY = 40;


ForgettingCurveUtils::ProbabilityTable::ProbabilityTable() : mTables() {
    mTables.resize(PROBABILITY_TABLE_COUNT);
    for (int tableId = 0; tableId < PROBABILITY_TABLE_COUNT; ++tableId) {
        mTables[tableId].resize(MAX_LEVEL + 1);
        for (int level = 0; level <= MAX_LEVEL; ++level) {
            mTables[tableId][level].resize(MAX_ELAPSED_TIME_STEP_COUNT + 1);
            const float initialProbability = getBaseProbabilityForLevel(tableId, level);
            const float endProbability = getBaseProbabilityForLevel(tableId, level - 1);
            for (int timeStepCount = 0; timeStepCount <= MAX_ELAPSED_TIME_STEP_COUNT;
                    ++timeStepCount) {
                if (level == 0) {
                    mTables[tableId][level][timeStepCount] = NOT_A_PROBABILITY;
                    continue;
                }
                const int elapsedTime = timeStepCount * TIME_STEP_DURATION_IN_SECONDS;
                const float probability = initialProbability
                        * powf(initialProbability / endProbability,
                                -1.0f * static_cast<float>(elapsedTime)
                                        / static_cast<float>(TIME_STEP_DURATION_IN_SECONDS
                                                * (MAX_ELAPSED_TIME_STEP_COUNT + 1)));
                mTables[tableId][level][timeStepCount] =
                        min(max(static_cast<int>(probability), 1), MAX_PROBABILITY);
            }
        }
    }
}

/* static */ int ForgettingCurveUtils::ProbabilityTable::getBaseProbabilityForLevel(
        const int tableId, const int level) {
    if (tableId == WEAK_PROBABILITY_TABLE_ID) {
        // Max probability is 127.
        return static_cast<float>(WEAK_MAX_PROBABILITY / (1 << (MAX_LEVEL - level)));
    } else if (tableId == MODEST_PROBABILITY_TABLE_ID) {
        // Max probability is 128.
        return static_cast<float>(MODEST_BASE_PROBABILITY * (level + 1));
    } else if (tableId == STRONG_PROBABILITY_TABLE_ID) {
        // Max probability is 140.
        return static_cast<float>(STRONG_BASE_PROBABILITY * (level + 1));
    } else if (tableId == AGGRESSIVE_PROBABILITY_TABLE_ID) {
        // Max probability is 160.
        return static_cast<float>(AGGRESSIVE_BASE_PROBABILITY * (level + 1));
    } else {
        return NOT_A_PROBABILITY;
    }
}

} // namespace latinime
