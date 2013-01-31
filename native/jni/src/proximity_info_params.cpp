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

#include "proximity_info_params.h"

namespace latinime {
const int ProximityInfoParams::LOOKUP_RADIUS_PERCENTILE = 50;
const int ProximityInfoParams::FIRST_POINT_TIME_OFFSET_MILLIS = 150;
const int ProximityInfoParams::STRONG_DOUBLE_LETTER_TIME_MILLIS = 600;
const int ProximityInfoParams::MIN_DOUBLE_LETTER_BEELINE_SPEED_PERCENTILE = 5;
const int ProximityInfoParams::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2 = 10;
const int ProximityInfoParams::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR =
        1 << NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
const float ProximityInfoParams::NOT_A_DISTANCE_FLOAT = -1.0f;

// Per method constants
const float ProximityInfoParams::NEAR_KEY_NORMALIZED_SQUARED_THRESHOLD = 4.0f;
const float ProximityInfoParams::NEAR_KEY_THRESHOLD_FOR_DISTANCE = 2.0f;
const float ProximityInfoParams::MARGIN_FOR_PREV_LOCAL_MIN = 0.01f;
const int ProximityInfoParams::DISTANCE_BASE_SCALE = 100;
const float ProximityInfoParams::NEAR_KEY_THRESHOLD_FOR_POINT_SCORE = 0.6f;
const int ProximityInfoParams::CORNER_CHECK_DISTANCE_THRESHOLD_SCALE = 25;
const float ProximityInfoParams::NOT_LOCALMIN_DISTANCE_SCORE = -1.0f;
const float ProximityInfoParams::LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE = 1.0f;
const float ProximityInfoParams::CORNER_ANGLE_THRESHOLD_FOR_POINT_SCORE = M_PI_F * 2.0f / 3.0f;
const float ProximityInfoParams::CORNER_SUM_ANGLE_THRESHOLD = M_PI_F / 4.0f;
const float ProximityInfoParams::CORNER_SCORE = 1.0f;

// TODO: Investigate if this is required
const float ProximityInfoParams::SEARCH_KEY_RADIUS_RATIO = 0.95f;
} // namespace latinime
