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
#include <string>

#include "defines.h"

namespace latinime {

class Correction;

class ProximityInfo {
 public:
    ProximityInfo(const char *localeCStr, const int maxProximityCharsSize,
            const int keyboardWidth, const int keyboardHeight, const int gridWidth,
            const int gridHeight, const int mostCommonkeyWidth,
            const int32_t *proximityCharsArray, const int keyCount, const int32_t *keyXCoordinates,
            const int32_t *keyYCoordinates, const int32_t *keyWidths, const int32_t *keyHeights,
            const int32_t *keyCharCodes, const float *sweetSpotCenterXs,
            const float *sweetSpotCenterYs, const float *sweetSpotRadii);
    ~ProximityInfo();
    bool hasSpaceProximity(const int x, const int y) const;
    int getNormalizedSquaredDistance(const int inputIndex, const int proximityIndex) const;
    bool sameAsTyped(const unsigned short *word, int length) const;
    int squaredDistanceToEdge(const int keyId, const int x, const int y) const;
    bool isOnKey(const int keyId, const int x, const int y) const {
        if (keyId < 0) return true; // NOT_A_ID is -1, but return whenever < 0 just in case
        const int left = mKeyXCoordinates[keyId];
        const int top = mKeyYCoordinates[keyId];
        const int right = left + mKeyWidths[keyId] + 1;
        const int bottom = top + mKeyHeights[keyId];
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }
    int getKeyIndex(const int c) const;
    bool hasSweetSpotData(const int keyIndex) const {
        // When there are no calibration data for a key,
        // the radius of the key is assigned to zero.
        return mSweetSpotRadii[keyIndex] > 0.0;
    }
    float getSweetSpotRadiiAt(int keyIndex) const {
        return mSweetSpotRadii[keyIndex];
    }
    float getSweetSpotCenterXAt(int keyIndex) const {
        return mSweetSpotCenterXs[keyIndex];
    }
    float getSweetSpotCenterYAt(int keyIndex) const {
        return mSweetSpotCenterYs[keyIndex];
    }
    void calculateNearbyKeyCodes(
            const int x, const int y, const int32_t primaryKey, int *inputCodes) const;

    bool hasTouchPositionCorrectionData() const {
        return HAS_TOUCH_POSITION_CORRECTION_DATA;
    }

    int getMostCommonKeyWidthSquare() const {
        return MOST_COMMON_KEY_WIDTH_SQUARE;
    }

    std::string getLocaleStr() const {
        return mLocaleStr;
    }

    int getKeyCount() const {
        return KEY_COUNT;
    }

    int getCellHeight() const {
        return CELL_HEIGHT;
    }

    int getCellWidth() const {
        return CELL_WIDTH;
    }

    int getGridWidth() const {
        return GRID_WIDTH;
    }

    int getGridHeight() const {
        return GRID_HEIGHT;
    }

    // Returns the keyboard key-center information.
    void getCenters(int *centersX, int *centersY, int *codeToKeyIndex, int *keyToCodeIndex,
            int *keyCount, int *keyWidth) const;

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProximityInfo);
    // The max number of the keys in one keyboard layout
    static const int MAX_KEY_COUNT_IN_A_KEYBOARD = 64;
    // The upper limit of the char code in mCodeToKeyIndex
    static const int MAX_CHAR_CODE = 127;
    static const float NOT_A_DISTANCE_FLOAT = -1.0f;
    static const int NOT_A_CODE = -1;

    int getStartIndexFromCoordinates(const int x, const int y) const;
    void initializeCodeToKeyIndex();
    float calculateNormalizedSquaredDistance(const int keyIndex, const int inputIndex) const;
    float calculateSquaredDistanceFromSweetSpotCenter(
            const int keyIndex, const int inputIndex) const;
    bool hasInputCoordinates() const;

    const int MAX_PROXIMITY_CHARS_SIZE;
    const int KEYBOARD_WIDTH;
    const int KEYBOARD_HEIGHT;
    const int GRID_WIDTH;
    const int GRID_HEIGHT;
    const int MOST_COMMON_KEY_WIDTH_SQUARE;
    const int CELL_WIDTH;
    const int CELL_HEIGHT;
    const int KEY_COUNT;
    const bool HAS_TOUCH_POSITION_CORRECTION_DATA;
    const std::string mLocaleStr;
    int32_t *mProximityCharsArray;
    int32_t mKeyXCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyYCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyWidths[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyHeights[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyCharCodes[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterXs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterYs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotRadii[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mCodeToKeyIndex[MAX_CHAR_CODE + 1];
    // TODO: move to correction.h
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_H
