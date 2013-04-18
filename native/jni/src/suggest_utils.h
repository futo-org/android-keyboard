/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LATINIME_SUGGEST_UTILS_H
#define LATINIME_SUGGEST_UTILS_H

#include "defines.h"
#include "proximity_info_params.h"

namespace latinime {
class SuggestUtils {
 public:
    // TODO: (OLD) Remove
    static float getLengthScalingFactor(const float normalizedSquaredDistance) {
        // Promote or demote the score according to the distance from the sweet spot
        static const float A = ZERO_DISTANCE_PROMOTION_RATE / 100.0f;
        static const float B = 1.0f;
        static const float C = 0.5f;
        static const float MIN = 0.3f;
        static const float R1 = NEUTRAL_SCORE_SQUARED_RADIUS;
        static const float R2 = HALF_SCORE_SQUARED_RADIUS;
        const float x = normalizedSquaredDistance / static_cast<float>(
                ProximityInfoParams::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR);
        const float factor = max((x < R1)
                ? (A * (R1 - x) + B * x) / R1
                : (B * (R2 - x) + C * (x - R1)) / (R2 - R1), MIN);
        // factor is a piecewise linear function like:
        // A -_                  .
        //     ^-_               .
        // B      \              .
        //         \_            .
        // C         ------------.
        //                       .
        // 0   R1 R2             .
        return factor;
    }

    static float getSweetSpotFactor(const bool isTouchPositionCorrectionEnabled,
            const float normalizedSquaredDistance) {
        // Promote or demote the score according to the distance from the sweet spot
        static const float A = 0.0f;
        static const float B = 0.24f;
        static const float C = 1.20f;
        static const float R0 = 0.0f;
        static const float R1 = 0.25f; // Sweet spot
        static const float R2 = 1.0f;
        const float x = normalizedSquaredDistance;
        if (!isTouchPositionCorrectionEnabled) {
            return min(C, x);
        }

        // factor is a piecewise linear function like:
        // C        -------------.
        //         /             .
        // B      /              .
        //      -/               .
        // A _-^                 .
        //                       .
        //   R0 R1 R2            .

        if (x < R0) {
            return A;
        } else if (x < R1) {
            return (A * (R1 - x) + B * (x - R0)) / (R1 - R0);
        } else if (x < R2) {
            return (B * (R2 - x) + C * (x - R1)) / (R2 - R1);
        } else {
            return C;
        }
    }
 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(SuggestUtils);
};
} // namespace latinime
#endif // LATINIME_SUGGEST_UTILS_H
