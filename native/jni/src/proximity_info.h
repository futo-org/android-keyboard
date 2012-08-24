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
#include "jni.h"

namespace latinime {

class Correction;

class ProximityInfo {
 public:
    ProximityInfo(JNIEnv *env, const jstring localeJStr, const int maxProximityCharsSize,
            const int keyboardWidth, const int keyboardHeight, const int gridWidth,
            const int gridHeight, const int mostCommonKeyWidth, const jintArray proximityChars,
            const int keyCount, const jintArray keyXCoordinates, const jintArray keyYCoordinates,
            const jintArray keyWidths, const jintArray keyHeights, const jintArray keyCharCodes,
            const jfloatArray sweetSpotCenterXs, const jfloatArray sweetSpotCenterYs,
            const jfloatArray sweetSpotRadii);
    ~ProximityInfo();
    bool hasSpaceProximity(const int x, const int y) const;
    int getNormalizedSquaredDistance(const int inputIndex, const int proximityIndex) const;
    float getNormalizedSquaredDistanceFromCenterFloat(
            const int keyId, const int x, const int y) const;
    bool sameAsTyped(const unsigned short *word, int length) const;
    int getKeyIndex(const int c) const;
    int getKeyCode(const int keyIndex) const;
    bool hasSweetSpotData(const int keyIndex) const {
        // When there are no calibration data for a key,
        // the radius of the key is assigned to zero.
        return mSweetSpotRadii[keyIndex] > 0.0f;
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

    int getMostCommonKeyWidth() const {
        return MOST_COMMON_KEY_WIDTH;
    }

    int getMostCommonKeyWidthSquare() const {
        return MOST_COMMON_KEY_WIDTH_SQUARE;
    }

    const char *getLocaleStr() const {
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

    float getKeyCenterXOfCharG(int charCode) const;
    float getKeyCenterYOfCharG(int charCode) const;
    float getKeyCenterXOfIdG(int keyId) const;
    float getKeyCenterYOfIdG(int keyId) const;
    int getKeyKeyDistanceG(int key0, int key1) const;

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProximityInfo);
    // The max number of the keys in one keyboard layout
    static const int MAX_KEY_COUNT_IN_A_KEYBOARD = 64;
    // The upper limit of the char code in mCodeToKeyIndex
    static const int MAX_CHAR_CODE = 127;
    static const int NOT_A_CODE;
    static const float NOT_A_DISTANCE_FLOAT;

    int getStartIndexFromCoordinates(const int x, const int y) const;
    void initializeCodeToKeyIndex();
    void initializeG();
    float calculateNormalizedSquaredDistance(const int keyIndex, const int inputIndex) const;
    float calculateSquaredDistanceFromSweetSpotCenter(
            const int keyIndex, const int inputIndex) const;
    bool hasInputCoordinates() const;
    int squaredDistanceToEdge(const int keyId, const int x, const int y) const;
    bool isOnKey(const int keyId, const int x, const int y) const {
        if (keyId < 0) return true; // NOT_A_ID is -1, but return whenever < 0 just in case
        const int left = mKeyXCoordinates[keyId];
        const int top = mKeyYCoordinates[keyId];
        const int right = left + mKeyWidths[keyId] + 1;
        const int bottom = top + mKeyHeights[keyId];
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    const int MAX_PROXIMITY_CHARS_SIZE;
    const int GRID_WIDTH;
    const int GRID_HEIGHT;
    const int MOST_COMMON_KEY_WIDTH;
    const int MOST_COMMON_KEY_WIDTH_SQUARE;
    const int CELL_WIDTH;
    const int CELL_HEIGHT;
    const int KEY_COUNT;
    const bool HAS_TOUCH_POSITION_CORRECTION_DATA;
    char mLocaleStr[MAX_LOCALE_STRING_LENGTH];
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

    int mKeyToCodeIndexG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mCenterXsG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mCenterYsG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyKeyDistancesG[MAX_KEY_COUNT_IN_A_KEYBOARD][MAX_KEY_COUNT_IN_A_KEYBOARD];
    // TODO: move to correction.h
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_H
