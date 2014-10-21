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

#ifndef LATINIME_ENTRY_COUNTERS_H
#define LATINIME_ENTRY_COUNTERS_H

#include <array>

#include "defines.h"

namespace latinime {

// Copyable but immutable
class EntryCounts final {
 public:
    EntryCounts() : mEntryCounts({{0, 0, 0}}) {}

    EntryCounts(const int unigramCount, const int bigramCount, const int trigramCount)
            : mEntryCounts({{unigramCount, bigramCount, trigramCount}}) {}

    explicit EntryCounts(const std::array<int, MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1> &counters)
            : mEntryCounts(counters) {}

    int getUnigramCount() const {
        return mEntryCounts[0];
    }

    int getBigramCount() const {
        return mEntryCounts[1];
    }

    int getTrigramCount() const {
        return mEntryCounts[2];
    }

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(EntryCounts);

    const std::array<int, MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1> mEntryCounts;
};

class MutableEntryCounters final {
 public:
    MutableEntryCounters() {
        mEntryCounters.fill(0);
    }

    MutableEntryCounters(const int unigramCount, const int bigramCount, const int trigramCount)
            : mEntryCounters({{unigramCount, bigramCount, trigramCount}}) {}

    const EntryCounts getEntryCounts() const {
        return EntryCounts(mEntryCounters);
    }

    int getUnigramCount() const {
        return mEntryCounters[0];
    }

    int getBigramCount() const {
        return mEntryCounters[1];
    }

    int getTrigramCount() const {
        return mEntryCounters[2];
    }

    void incrementUnigramCount() {
        ++mEntryCounters[0];
    }

    void decrementUnigramCount() {
        ASSERT(mEntryCounters[0] != 0);
        --mEntryCounters[0];
    }

    void incrementBigramCount() {
        ++mEntryCounters[1];
    }

    void decrementBigramCount() {
        ASSERT(mEntryCounters[1] != 0);
        --mEntryCounters[1];
    }

    void incrementNgramCount(const size_t n) {
        if (n < 1 || n > mEntryCounters.size()) {
            return;
        }
        ++mEntryCounters[n - 1];
    }

    void decrementNgramCount(const size_t n) {
        if (n < 1 || n > mEntryCounters.size()) {
            return;
        }
        ASSERT(mEntryCounters[n - 1] != 0);
        --mEntryCounters[n - 1];
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(MutableEntryCounters);

    std::array<int, MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1> mEntryCounters;
};
} // namespace latinime
#endif /* LATINIME_ENTRY_COUNTERS_H */
