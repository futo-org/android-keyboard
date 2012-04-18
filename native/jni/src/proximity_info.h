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
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2 = 10;
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR =
            1 << NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;

    // Used as a return value for character comparison
    typedef enum {
        // Same char, possibly with different case or accent
        EQUIVALENT_CHAR,
        // It is a char located nearby on the keyboard
        NEAR_PROXIMITY_CHAR,
        // It is an unrelated char
        UNRELATED_CHAR,
        // Additional proximity char which can differ by language.
        ADDITIONAL_PROXIMITY_CHAR
    } ProximityType;

    ProximityInfo(const std::string localeStr, const int maxProximityCharsSize,
            const int keyboardWidth, const int keyboardHeight, const int gridWidth,
            const int gridHeight, const int mostCommonkeyWidth,
            const int32_t *proximityCharsArray, const int keyCount, const int32_t *keyXCoordinates,
            const int32_t *keyYCoordinates, const int32_t *keyWidths, const int32_t *keyHeights,
            const int32_t *keyCharCodes, const float *sweetSpotCenterXs,
            const float *sweetSpotCenterYs, const float *sweetSpotRadii);
    ~ProximityInfo();
    bool hasSpaceProximity(const int x, const int y) const;
    void setInputParams(const int32_t *inputCodes, const int inputLength,
            const int *xCoordinates, const int *yCoordinates);
    const int* getProximityCharsAt(const int index) const;
    unsigned short getPrimaryCharAt(const int index) const;
    bool existsCharInProximityAt(const int index, const int c) const;
    bool existsAdjacentProximityChars(const int index) const;
    ProximityType getMatchedProximityId(const int index, const unsigned short c,
            const bool checkProximityChars, int *proximityIndex = 0) const;
    int getNormalizedSquaredDistance(const int inputIndex, const int proximityIndex) const {
        return mNormalizedSquaredDistances[inputIndex * MAX_PROXIMITY_CHARS_SIZE + proximityIndex];
    }
    bool sameAsTyped(const unsigned short *word, int length) const;
    const unsigned short* getPrimaryInputWord() const {
        return mPrimaryInputWord;
    }
    bool touchPositionCorrectionEnabled() const {
        return mTouchPositionCorrectionEnabled;
    }

 private:
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
    int getKeyIndex(const int c) const;
    bool hasSweetSpotData(const int keyIndex) const {
        // When there are no calibration data for a key,
        // the radius of the key is assigned to zero.
        return mSweetSpotRadii[keyIndex] > 0.0;
    }
    bool isOnKey(const int keyId, const int x, const int y) const;
    int squaredDistanceToEdge(const int keyId, const int x, const int y) const;
    void calculateNearbyKeyCodes(
            const int x, const int y, const int32_t primaryKey, int *inputCodes) const;

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
    // TODO: remove this
    const int *mInputCodesFromJava;
    int32_t *mInputCodes;
    const int *mInputXCoordinates;
    const int *mInputYCoordinates;
    bool mTouchPositionCorrectionEnabled;
    int32_t *mProximityCharsArray;
    int *mNormalizedSquaredDistances;
    int32_t mKeyXCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyYCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyWidths[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyHeights[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int32_t mKeyCharCodes[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterXs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterYs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotRadii[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mInputLength;
    unsigned short mPrimaryInputWord[MAX_WORD_LENGTH_INTERNAL];
    int mCodeToKeyIndex[MAX_CHAR_CODE + 1];
};

} // namespace latinime

#endif // LATINIME_PROXIMITY_INFO_H
