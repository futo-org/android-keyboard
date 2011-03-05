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

#ifndef LATINIME_PROXIMITY_INFO_H
#define LATINIME_PROXIMITY_INFO_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

class ProximityInfo {
public:
    ProximityInfo(const int maxProximityCharsSize, const int keyboardWidth,
            const int keybaordHeight, const int gridWidth, const int gridHeight,
            const uint32_t *proximityCharsArray);
    ~ProximityInfo();
    bool hasSpaceProximity(const int x, const int y) const;
private:
    int getStartIndexFromCoordinates(const int x, const int y) const;
    const int CELL_WIDTH;
    const int CELL_HEIGHT;
    const int KEYBOARD_WIDTH;
    const int KEYBOARD_HEIGHT;
    const int GRID_WIDTH;
    const int GRID_HEIGHT;
    const int MAX_PROXIMITY_CHARS_SIZE;
    uint32_t *mProximityCharsArray;
};
}; // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_H
