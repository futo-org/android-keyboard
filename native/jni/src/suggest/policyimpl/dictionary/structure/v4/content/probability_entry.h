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

#ifndef LATINIME_PROBABILITY_ENTRY_H
#define LATINIME_PROBABILITY_ENTRY_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/historical_info.h"

namespace latinime {

class ProbabilityEntry {
 public:
    ProbabilityEntry(const ProbabilityEntry &probabilityEntry)
            : mFlags(probabilityEntry.mFlags), mProbability(probabilityEntry.mProbability),
              mHistoricalInfo(probabilityEntry.mHistoricalInfo) {}

    // Dummy entry
    ProbabilityEntry()
            : mFlags(0), mProbability(NOT_A_PROBABILITY), mHistoricalInfo() {}

    // Entry without historical information
    ProbabilityEntry(const int flags, const int probability)
            : mFlags(flags), mProbability(probability), mHistoricalInfo() {}

    // Entry with historical information.
    ProbabilityEntry(const int flags, const int probability,
            const HistoricalInfo *const historicalInfo)
            : mFlags(flags), mProbability(probability), mHistoricalInfo(*historicalInfo) {}

    const ProbabilityEntry createEntryWithUpdatedProbability(const int probability) const {
        return ProbabilityEntry(mFlags, probability, &mHistoricalInfo);
    }

    const ProbabilityEntry createEntryWithUpdatedHistoricalInfo(
            const HistoricalInfo *const historicalInfo) const {
        return ProbabilityEntry(mFlags, mProbability, historicalInfo);
    }

    bool hasHistoricalInfo() const {
        return mHistoricalInfo.isValid();
    }

    int getFlags() const {
        return mFlags;
    }

    int getProbability() const {
        return mProbability;
    }

    const HistoricalInfo *getHistoricalInfo() const {
        return &mHistoricalInfo;
    }

 private:
    // Copy constructor is public to use this class as a type of return value.
    DISALLOW_ASSIGNMENT_OPERATOR(ProbabilityEntry);

    const int mFlags;
    const int mProbability;
    const HistoricalInfo mHistoricalInfo;
};
} // namespace latinime
#endif /* LATINIME_PROBABILITY_ENTRY_H */
