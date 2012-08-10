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

#ifndef LATINIME_INCREMENTAL_GEOMETRY_UTILS_H
#define LATINIME_INCREMENTAL_GEOMETRY_UTILS_H

#include <cmath>

#define MAX_DISTANCE 10000000
#define KEY_NUM 27
#define SPACE_KEY 26
#define MAX_PATHS 2

#define DEBUG_DECODER false

namespace latinime {

static inline float sqr(float x) {
    return x * x;
}

static inline float getNormalizedSqrDistance(int x1, int y1, int x2, int y2, int scale) {
    return sqr((x1 - x2) * 1.0 / scale) + sqr((y1 - y2) * 1.0 / scale);
}

static inline int getDistance(int x1, int y1, int x2, int y2) {
    return (int) sqrt(sqr(x2 - x1) + sqr(y2 - y1));
}

static inline float getDistanceSq(float x1, float y1, float x2, float y2) {
    return sqr(x2 - x1) + sqr(y2 - y1);
}

static inline float getAngle(int x1, int y1, int x2, int y2) {
    float dx = x1 - x2;
    float dy = y1 - y2;
    if (dx == 0 && dy == 0)
        return 0;
    return atan2(dy, dx);
}

static inline float angleDiff(float a1, float a2) {
    float diff = a1 - a2;
    if (diff < 0) {
        diff = -diff;
    }
    if (diff > M_PI) {
        return 2 * M_PI - diff;
    }
    return diff;
}

//static float pointToLineDistanceSq(float x, float y, float x1, float y1, float x2, float y2) {
//    float A = x - x1;
//    float B = y - y1;
//    float C = x2 - x1;
//    float D = y2 - y1;
//    return abs(A * D - C * B) / sqrt(C * C + D * D);
//}

static inline float pointToLineSegDistanceSq(
        float x, float y, float x1, float y1, float x2, float y2) {
    float ray1x = x - x1;
    float ray1y = y - y1;
    float ray2x = x2 - x1;
    float ray2y = y2 - y1;

    float dotProduct = ray1x * ray2x + ray1y * ray2y;
    float lineLengthSq = ray2x * ray2x + ray2y * ray2y;
    float projectionLengthSq = dotProduct / lineLengthSq;

    float projectionX, projectionY;
    if (projectionLengthSq < 0) {
        projectionX = x1;
        projectionY = y1;
    } else if (projectionLengthSq > 1) {
        projectionX = x2;
        projectionY = y2;
    } else {
        projectionX = x1 + projectionLengthSq * ray2x;
        projectionY = y1 + projectionLengthSq * ray2y;
    }

    float dist = getDistanceSq(x, y, projectionX, projectionY);
    return dist;
}
} // namespace latinime
#endif // LATINIME_INCREMENTAL_GEOMETRY_UTILS_H
