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

#ifndef LATINIME_PROXIMITY_INFO_STATE_H
#define LATINIME_PROXIMITY_INFO_STATE_H

#include <assert.h>
#include <stdint.h>
#include <string>

#include "additional_proximity_chars.h"
#include "defines.h"

namespace latinime {

class ProximityInfo;

class ProximityInfoState {
 public:
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2 = 10;
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR =
            1 << NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
    // The max number of the keys in one keyboard layout
    static const int MAX_KEY_COUNT_IN_A_KEYBOARD = 64;
    // The upper limit of the char code in mCodeToKeyIndex
    static const int MAX_CHAR_CODE = 127;
    static const float NOT_A_DISTANCE_FLOAT = -1.0f;
    static const int NOT_A_CODE = -1;

    /////////////////////////////////////////
    // Defined in proximity_info_state.cpp //
    /////////////////////////////////////////
    void initInputParams(const int32_t* inputCodes, const int inputLength,
           const int* xCoordinates, const int* yCoordinates);

    /////////////////////////////////////////
    // Defined here                        //
    /////////////////////////////////////////
    // TODO: Move the constructor to initInputParams
    ProximityInfoState(ProximityInfo* proximityInfo, const int maxProximityCharsSize,
            const bool hasTouchPositionCorrectionData, const int mostCommonKeyWidthSquare,
            const std::string localeStr, const int keyCount, const int cellHeight,
            const int cellWidth, const int gridHeight, const int gridWidth)
            : mProximityInfo(proximityInfo),
              MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize),
              HAS_TOUCH_POSITION_CORRECTION_DATA(hasTouchPositionCorrectionData),
              MOST_COMMON_KEY_WIDTH_SQUARE(mostCommonKeyWidthSquare),
              LOCALE_STR(localeStr),
              KEY_COUNT(keyCount),
              CELL_HEIGHT(cellHeight),
              CELL_WIDTH(cellWidth),
              GRID_HEIGHT(gridHeight),
              GRID_WIDTH(gridWidth),
              mInputXCoordinates(0),
              mInputYCoordinates(0),
              mTouchPositionCorrectionEnabled(false) {
        const int normalizedSquaredDistancesLength =
                MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL;
        for (int i = 0; i < normalizedSquaredDistancesLength; ++i) {
            mNormalizedSquaredDistances[i] = NOT_A_DISTANCE;
        }
    }

    inline const int* getProximityCharsAt(const int index) const {
        return mInputCodes + (index * MAX_PROXIMITY_CHARS_SIZE);
    }

    inline unsigned short getPrimaryCharAt(const int index) const {
        return getProximityCharsAt(index)[0];
    }

    inline bool existsCharInProximityAt(const int index, const int c) const {
        const int *chars = getProximityCharsAt(index);
        int i = 0;
        while (chars[i] > 0 && i < MAX_PROXIMITY_CHARS_SIZE) {
            if (chars[i++] == c) {
                return true;
            }
        }
        return false;
    }

    inline bool existsAdjacentProximityChars(const int index) const {
        if (index < 0 || index >= mInputLength) return false;
        const int currentChar = getPrimaryCharAt(index);
        const int leftIndex = index - 1;
        if (leftIndex >= 0 && existsCharInProximityAt(leftIndex, currentChar)) {
            return true;
        }
        const int rightIndex = index + 1;
        if (rightIndex < mInputLength && existsCharInProximityAt(rightIndex, currentChar)) {
            return true;
        }
        return false;
    }

    // In the following function, c is the current character of the dictionary word
    // currently examined.
    // currentChars is an array containing the keys close to the character the
    // user actually typed at the same position. We want to see if c is in it: if so,
    // then the word contains at that position a character close to what the user
    // typed.
    // What the user typed is actually the first character of the array.
    // proximityIndex is a pointer to the variable where getMatchedProximityId returns
    // the index of c in the proximity chars of the input index.
    // Notice : accented characters do not have a proximity list, so they are alone
    // in their list. The non-accented version of the character should be considered
    // "close", but not the other keys close to the non-accented version.
    inline ProximityType getMatchedProximityId(const int index,
            const unsigned short c, const bool checkProximityChars, int *proximityIndex) const {
        const int *currentChars = getProximityCharsAt(index);
        const int firstChar = currentChars[0];
        const unsigned short baseLowerC = toBaseLowerCase(c);

        // The first char in the array is what user typed. If it matches right away,
        // that means the user typed that same char for this pos.
        if (firstChar == baseLowerC || firstChar == c) {
            return EQUIVALENT_CHAR;
        }

        if (!checkProximityChars) return UNRELATED_CHAR;

        // If the non-accented, lowercased version of that first character matches c,
        // then we have a non-accented version of the accented character the user
        // typed. Treat it as a close char.
        if (toBaseLowerCase(firstChar) == baseLowerC)
            return NEAR_PROXIMITY_CHAR;

        // Not an exact nor an accent-alike match: search the list of close keys
        int j = 1;
        while (j < MAX_PROXIMITY_CHARS_SIZE
                && currentChars[j] > ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
            const bool matched = (currentChars[j] == baseLowerC || currentChars[j] == c);
            if (matched) {
                if (proximityIndex) {
                    *proximityIndex = j;
                }
                return NEAR_PROXIMITY_CHAR;
            }
            ++j;
        }
        if (j < MAX_PROXIMITY_CHARS_SIZE
                && currentChars[j] == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
            ++j;
            while (j < MAX_PROXIMITY_CHARS_SIZE
                    && currentChars[j] > ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
                const bool matched = (currentChars[j] == baseLowerC || currentChars[j] == c);
                if (matched) {
                    if (proximityIndex) {
                        *proximityIndex = j;
                    }
                    return ADDITIONAL_PROXIMITY_CHAR;
                }
                ++j;
            }
        }

        // Was not included, signal this as an unrelated character.
        return UNRELATED_CHAR;
    }

    inline int getNormalizedSquaredDistance(
            const int inputIndex, const int proximityIndex) const {
        return mNormalizedSquaredDistances[inputIndex * MAX_PROXIMITY_CHARS_SIZE + proximityIndex];
    }

    inline const unsigned short* getPrimaryInputWord() const {
        return mPrimaryInputWord;
    }

    inline bool touchPositionCorrectionEnabled() const {
        return mTouchPositionCorrectionEnabled;
    }

 private:
    inline float square(const float x) const { return x * x; }

    float calculateNormalizedSquaredDistance(
            const int keyIndex, const int inputIndex) const {
        if (keyIndex == NOT_AN_INDEX) {
            return NOT_A_DISTANCE_FLOAT;
        }
        if (!mProximityInfo->hasSweetSpotData(keyIndex)) {
            return NOT_A_DISTANCE_FLOAT;
        }
        if (NOT_A_COORDINATE == mInputXCoordinates[inputIndex]) {
            return NOT_A_DISTANCE_FLOAT;
        }
        const float squaredDistance = calculateSquaredDistanceFromSweetSpotCenter(
                keyIndex, inputIndex);
        const float squaredRadius = square(mProximityInfo->getSweetSpotRadiiAt(keyIndex));
        return squaredDistance / squaredRadius;
    }

    bool hasInputCoordinates() const {
        return mInputXCoordinates && mInputYCoordinates;
    }

    float calculateSquaredDistanceFromSweetSpotCenter(
            const int keyIndex, const int inputIndex) const {
        const float sweetSpotCenterX = mProximityInfo->getSweetSpotCenterXAt(keyIndex);
        const float sweetSpotCenterY = mProximityInfo->getSweetSpotCenterYAt(keyIndex);
        const float inputX = (float)mInputXCoordinates[inputIndex];
        const float inputY = (float)mInputYCoordinates[inputIndex];
        return square(inputX - sweetSpotCenterX) + square(inputY - sweetSpotCenterY);
    }

    bool sameAsTyped(const unsigned short *word, int length) const {
        if (length != mInputLength) {
            return false;
        }
        const int *inputCodes = mInputCodes;
        while (length--) {
            if ((unsigned int) *inputCodes != (unsigned int) *word) {
                return false;
            }
            inputCodes += MAX_PROXIMITY_CHARS_SIZE;
            word++;
        }
        return true;
    }

    // TODO: const
    ProximityInfo *mProximityInfo;
    const int MAX_PROXIMITY_CHARS_SIZE;
    const bool HAS_TOUCH_POSITION_CORRECTION_DATA;
    const int MOST_COMMON_KEY_WIDTH_SQUARE;
    const std::string LOCALE_STR;
    const int KEY_COUNT;
    const int CELL_HEIGHT;
    const int CELL_WIDTH;
    const int GRID_HEIGHT;
    const int GRID_WIDTH;

    const int *mInputXCoordinates;
    const int *mInputYCoordinates;
    bool mTouchPositionCorrectionEnabled;
    int32_t mInputCodes[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mNormalizedSquaredDistances[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mInputLength;
    unsigned short mPrimaryInputWord[MAX_WORD_LENGTH_INTERNAL];
};

} // namespace latinime

#endif // LATINIME_PROXIMITY_INFO_STATE_H
