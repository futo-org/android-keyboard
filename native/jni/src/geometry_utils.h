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

#define MAX_DISTANCE 10000000
#define MAX_PATHS 2

#define DEBUG_DECODER false

#define M_PI_F 3.14159265f

namespace latinime {

static inline float squareFloat(float x) {
    return x * x;
}

static inline float getSquaredDistanceFloat(float x1, float y1, float x2, float y2) {
    return squareFloat(x1 - x2) + squareFloat(y1 - y2);
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
    const float diff = fabsf(a1 - a2);
    if (diff > M_PI_F) {
        return 2.0f * M_PI_F - diff;
    }
    return diff;
}

// static float pointToLineSegSquaredDistanceFloat(
//         float x, float y, float x1, float y1, float x2, float y2) {
//     float A = x - x1;
//     float B = y - y1;
//     float C = x2 - x1;
//     float D = y2 - y1;
//     return fabsf(A * D - C * B) / sqrtf(C * C + D * D);
// }

static inline float pointToLineSegSquaredDistanceFloat(
        float x, float y, float x1, float y1, float x2, float y2) {
    const float ray1x = x - x1;
    const float ray1y = y - y1;
    const float ray2x = x2 - x1;
    const float ray2y = y2 - y1;

    const float dotProduct = ray1x * ray2x + ray1y * ray2y;
    const float lineLengthSqr = squareFloat(ray2x) + squareFloat(ray2y);
    const float projectionLengthSqr = dotProduct / lineLengthSqr;

    float projectionX;
    float projectionY;
    if (projectionLengthSqr < 0.0f) {
        projectionX = x1;
        projectionY = y1;
    } else if (projectionLengthSqr > 1.0f) {
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
