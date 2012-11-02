/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_GEOMETRY_UTILS_H
#define LATINIME_GEOMETRY_UTILS_H

#include <cmath>

#define DEBUG_DECODER false

#define M_PI_F 3.14159265f
#define ROUND_FLOAT_10000(f) ((f) < 1000.0f && (f) > 0.001f) \
        ? (floorf((f) * 10000.0f) / 10000.0f) : (f)

namespace latinime {

static inline float SQUARE_FLOAT(const float x) { return x * x; }

static inline float getSquaredDistanceFloat(const float x1, const float y1, const float x2,
        const float y2) {
    return SQUARE_FLOAT(x1 - x2) + SQUARE_FLOAT(y1 - y2);
}

static inline float getNormalizedSquaredDistanceFloat(const float x1, const float y1,
        const float x2, const float y2, const float scale) {
    return getSquaredDistanceFloat(x1, y1, x2, y2) / SQUARE_FLOAT(scale);
}

static inline float getDistanceFloat(const float x1, const float y1, const float x2,
        const float y2) {
    return hypotf(x1 - x2, y1 - y2);
}

static inline int getDistanceInt(const int x1, const int y1, const int x2, const int y2) {
    return static_cast<int>(getDistanceFloat(static_cast<float>(x1), static_cast<float>(y1),
            static_cast<float>(x2), static_cast<float>(y2)));
}

static inline float getAngle(const int x1, const int y1, const int x2, const int y2) {
    const int dx = x1 - x2;
    const int dy = y1 - y2;
    if (dx == 0 && dy == 0) return 0;
    return atan2f(static_cast<float>(dy), static_cast<float>(dx));
}

static inline float getAngleDiff(const float a1, const float a2) {
    const float deltaA = fabsf(a1 - a2);
    const float diff = ROUND_FLOAT_10000(deltaA);
    if (diff > M_PI_F) {
        const float normalizedDiff = 2.0f * M_PI_F - diff;
        return ROUND_FLOAT_10000(normalizedDiff);
    }
    return diff;
}

static inline float pointToLineSegSquaredDistanceFloat(const float x, const float y, const float x1,
        const float y1, const float x2, const float y2, const bool extend) {
    const float ray1x = x - x1;
    const float ray1y = y - y1;
    const float ray2x = x2 - x1;
    const float ray2y = y2 - y1;

    const float dotProduct = ray1x * ray2x + ray1y * ray2y;
    const float lineLengthSqr = SQUARE_FLOAT(ray2x) + SQUARE_FLOAT(ray2y);
    const float projectionLengthSqr = dotProduct / lineLengthSqr;

    float projectionX;
    float projectionY;
    if (!extend && projectionLengthSqr < 0.0f) {
        projectionX = x1;
        projectionY = y1;
    } else if (!extend && projectionLengthSqr > 1.0f) {
        projectionX = x2;
        projectionY = y2;
    } else {
        projectionX = x1 + projectionLengthSqr * ray2x;
        projectionY = y1 + projectionLengthSqr * ray2y;
    }
    return getSquaredDistanceFloat(x, y, projectionX, projectionY);
}

// Normal distribution N(u, sigma^2).
struct NormalDistribution {
    NormalDistribution(const float u, const float sigma)
            : mU(u), mSigma(sigma),
              mPreComputedNonExpPart(1.0f / sqrtf(2.0f * M_PI_F * SQUARE_FLOAT(sigma))),
              mPreComputedExponentPart(-1.0f / (2.0f * SQUARE_FLOAT(sigma))) {}

    float getProbabilityDensity(const float x) const {
        const float shiftedX = x - mU;
        return mPreComputedNonExpPart * expf(mPreComputedExponentPart * SQUARE_FLOAT(shiftedX));
    }

private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(NormalDistribution);
    float mU; // mean value
    float mSigma; // standard deviation
    float mPreComputedNonExpPart; // = 1 / sqrt(2 * PI * sigma^2)
    float mPreComputedExponentPart; // = -1 / (2 * sigma^2)
}; // struct NormalDistribution
} // namespace latinime
#endif // LATINIME_GEOMETRY_UTILS_H
