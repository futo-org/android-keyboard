/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <stdio.h>
#include <string.h>

#include "proximity_info.h"

namespace latinime {
ProximityInfo::ProximityInfo(int maxProximityCharsSize, int displayWidth, int displayHeight,
        int gridWidth, int gridHeight, uint32_t const *proximityCharsArray)
        : MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize), DISPLAY_WIDTH(displayWidth),
          DISPLAY_HEIGHT(displayHeight), GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight) {
    mProximityCharsArray = new uint32_t[GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE];
    memcpy(mProximityCharsArray, proximityCharsArray, sizeof(mProximityCharsArray));
}

ProximityInfo::~ProximityInfo() {
    delete[] mProximityCharsArray;
}
}
