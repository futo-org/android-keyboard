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

#ifndef LATINIME_PROXIMITY_INFO_PARAMS_H
#define LATINIME_PROXIMITY_INFO_PARAMS_H

#include "defines.h"

namespace latinime {

class ProximityInfoParams {
 public:
    static const int LOOKUP_RADIUS_PERCENTILE;
    static const int FIRST_POINT_TIME_OFFSET_MILLIS;
    static const int STRONG_DOUBLE_LETTER_TIME_MILLIS;
    static const int MIN_DOUBLE_LETTER_BEELINE_SPEED_PERCENTILE;
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR;
    static const float NOT_A_DISTANCE_FLOAT;
    static const float SEARCH_KEY_RADIUS_RATIO;
 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProximityInfoParams);
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_PARAMS_H
