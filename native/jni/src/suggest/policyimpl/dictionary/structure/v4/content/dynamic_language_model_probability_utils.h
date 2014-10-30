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

#ifndef LATINIME_DYNAMIC_LANGUAGE_MODEL_PROBABILITY_UTILS_H
#define LATINIME_DYNAMIC_LANGUAGE_MODEL_PROBABILITY_UTILS_H

#include <algorithm>

#include "defines.h"
#include "suggest/core/dictionary/property/historical_info.h"
#include "utils/time_keeper.h"

namespace latinime {

class DynamicLanguageModelProbabilityUtils {
 public:
    static float computeRawProbabilityFromCounts(const int count, const int contextCount,
            const int matchedWordCountInContext) {
        int minCount = 0;
        switch (matchedWordCountInContext) {
            case 1:
                minCount = ASSUMED_MIN_COUNT_FOR_UNIGRAMS;
                break;
            case 2:
                minCount = ASSUMED_MIN_COUNT_FOR_BIGRAMS;
                break;
            case 3:
                minCount = ASSUMED_MIN_COUNT_FOR_TRIGRAMS;
                break;
            default:
                AKLOGE("computeRawProbabilityFromCounts is called with invalid "
                        "matchedWordCountInContext (%d).", matchedWordCountInContext);
                ASSERT(false);
                return 0.0f;
        }
        return static_cast<float>(count) / static_cast<float>(std::max(contextCount, minCount));
    }

    static float backoff(const int ngramProbability, const int matchedWordCountInContext) {
        int probability = NOT_A_PROBABILITY;

        switch (matchedWordCountInContext) {
            case 1:
                probability = ngramProbability + ENCODED_BACKOFF_WEIGHT_FOR_UNIGRAMS;
                break;
            case 2:
                probability = ngramProbability + ENCODED_BACKOFF_WEIGHT_FOR_BIGRAMS;
                break;
            case 3:
                probability = ngramProbability + ENCODED_BACKOFF_WEIGHT_FOR_TRIGRAMS;
                break;
            default:
                AKLOGE("backoff is called with invalid matchedWordCountInContext (%d).",
                        matchedWordCountInContext);
                ASSERT(false);
                return NOT_A_PROBABILITY;
        }
        return std::min(std::max(probability, NOT_A_PROBABILITY), MAX_PROBABILITY);
    }

    static int getDecayedProbability(const int probability, const HistoricalInfo historicalInfo) {
        const int elapsedTime = TimeKeeper::peekCurrentTime() - historicalInfo.getTimestamp();
        if (elapsedTime < 0) {
            AKLOGE("The elapsed time is negatime value. Timestamp overflow?");
            return NOT_A_PROBABILITY;
        }
        // TODO: Improve this logic.
        // We don't modify probability depending on the elapsed time.
        return probability;
    }

    static int shouldRemoveEntryDuringGC(const HistoricalInfo historicalInfo) {
        // TODO: Improve this logic.
        const int elapsedTime = TimeKeeper::peekCurrentTime() - historicalInfo.getTimestamp();
        return elapsedTime > DURATION_TO_DISCARD_ENTRY_IN_SECONDS;
    }

    static int getPriorityToPreventFromEviction(const HistoricalInfo historicalInfo) {
        // TODO: Improve this logic.
        // More recently input entries get higher priority.
        return historicalInfo.getTimestamp();
    }

private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicLanguageModelProbabilityUtils);

    static_assert(MAX_PREV_WORD_COUNT_FOR_N_GRAM <= 2, "Max supported Ngram is Trigram.");

    static const int ASSUMED_MIN_COUNT_FOR_UNIGRAMS;
    static const int ASSUMED_MIN_COUNT_FOR_BIGRAMS;
    static const int ASSUMED_MIN_COUNT_FOR_TRIGRAMS;

    static const int ENCODED_BACKOFF_WEIGHT_FOR_UNIGRAMS;
    static const int ENCODED_BACKOFF_WEIGHT_FOR_BIGRAMS;
    static const int ENCODED_BACKOFF_WEIGHT_FOR_TRIGRAMS;

    static const int DURATION_TO_DISCARD_ENTRY_IN_SECONDS;
};

} // namespace latinime
#endif /* LATINIME_DYNAMIC_LANGUAGE_MODEL_PROBABILITY_UTILS_H */
