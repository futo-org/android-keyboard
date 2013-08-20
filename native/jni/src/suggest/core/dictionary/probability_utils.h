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

#ifndef LATINIME_PROBABILITY_UTILS_H
#define LATINIME_PROBABILITY_UTILS_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

class ProbabilityUtils {
 public:
    static AK_FORCE_INLINE int backoff(const int unigramProbability) {
        return unigramProbability;
        // For some reason, applying the backoff weight gives bad results in tests. To apply the
        // backoff weight, we divide the probability by 2, which in our storing format means
        // decreasing the score by 8.
        // TODO: figure out what's wrong with this.
        // return unigramProbability > 8 ?
        //         unigramProbability - 8 : (0 == unigramProbability ? 0 : 8);
    }

    static AK_FORCE_INLINE int computeProbabilityForBigram(
            const int unigramProbability, const int bigramProbability) {
        // We divide the range [unigramProbability..255] in 16.5 steps - in other words, we want
        // the unigram probability to be the median value of the 17th step from the top. A value of
        // 0 for the bigram probability represents the middle of the 16th step from the top,
        // while a value of 15 represents the middle of the top step.
        // See makedict.BinaryDictEncoder#makeBigramFlags for details.
        const float stepSize = static_cast<float>(MAX_PROBABILITY - unigramProbability)
                / (1.5f + MAX_BIGRAM_ENCODED_PROBABILITY);
        return unigramProbability
                + static_cast<int>(static_cast<float>(bigramProbability + 1) * stepSize);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProbabilityUtils);
};
}
#endif /* LATINIME_PROBABILITY_UTILS_H */
