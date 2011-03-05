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

#define LOG_TAG "LatinIME: proximity_info.cpp"

#include "proximity_info.h"

namespace latinime {
ProximityInfo::ProximityInfo(const int maxProximityCharsSize, const int keyboardWidth,
        const int keyboardHeight, const int gridWidth, const int gridHeight,
        const uint32_t *proximityCharsArray)
        : MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize), KEYBOARD_WIDTH(keyboardWidth),
          KEYBOARD_HEIGHT(keyboardHeight), GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight),
          CELL_WIDTH((keyboardWidth + gridWidth - 1) / gridWidth),
          CELL_HEIGHT((keyboardHeight + gridHeight - 1) / gridHeight) {
    const int len = GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE;
    mProximityCharsArray = new uint32_t[len];
    if (DEBUG_PROXIMITY_INFO) {
        LOGI("Create proximity info array %d", len);
    }
    memcpy(mProximityCharsArray, proximityCharsArray, len * sizeof(mProximityCharsArray[0]));
}

ProximityInfo::~ProximityInfo() {
    delete[] mProximityCharsArray;
}

inline int ProximityInfo::getStartIndexFromCoordinates(const int x, const int y) const {
    return (y / CELL_HEIGHT) * GRID_WIDTH + (x / CELL_WIDTH)
            * MAX_PROXIMITY_CHARS_SIZE;
}

bool ProximityInfo::hasSpaceProximity(const int x, const int y) const {
    const int startIndex = getStartIndexFromCoordinates(x, y);
    if (DEBUG_PROXIMITY_INFO) {
        LOGI("hasSpaceProximity: index %d", startIndex);
    }
    for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
        if (DEBUG_PROXIMITY_INFO) {
            LOGI("Index: %d", mProximityCharsArray[startIndex + i]);
        }
        if (mProximityCharsArray[startIndex + i] == KEYCODE_SPACE) {
            return true;
        }
    }
    return false;
}
}  // namespace latinime
