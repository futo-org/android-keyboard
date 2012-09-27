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
#define SQUARE_FLOAT(x) ((x) * (x))

namespace latinime {

static inline float getSquaredDistanceFloat(float x1, float y1, float x2, float y2) {
    const float deltaX = x1 - x2;
    const float deltaY = y1 - y2;
    return SQUARE_FLOAT(deltaX) + SQUARE_FLOAT(deltaY);
}

static inline float getDistanceFloat(float x1, float y1, float x2, float y2) {
    return hypotf(x1 - x2, y1 - y2);
}

static inline int getDistanceInt(int x1, int y1, int x2, int y2) {
    return static_cast<int>(getDistanceFloat(static_cast<float>(x1), static_cast<float>(y1),
            static_cast<float>(x2), static_cast<float>(y2)));
}

static inline float getAngle(int x1, int y1, int x2, int y2) {
    const int dx = x1 - x2;
    const int dy = y1 - y2;
    if (dx == 0 && dy == 0) return 0;
    return atan2f(static_cast<float>(dy), static_cast<float>(dx));
}

static inline float getAngleDiff(float a1, float a2) {
    const float deltaA = fabsf(a1 - a2);
    const float diff = ROUND_FLOAT_10000(deltaA);
    if (diff > M_PI_F) {
        const float normalizedDiff = 2.0f * M_PI_F - diff;
        return ROUND_FLOAT_10000(normalizedDiff);
    }
    return diff;
}

static inline float pointToLineSegSquaredDistanceFloat(
        float x, float y, float x1, float y1, float x2, float y2, bool extend) {
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
} // namespace latinime
#endif // LATINIME_GEOMETRY_UTILS_H
