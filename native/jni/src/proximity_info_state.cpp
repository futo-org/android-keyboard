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

#include <cstring> // for memset()
#include <stdint.h>

#define LOG_TAG "LatinIME: proximity_info_state.cpp"

#include "defines.h"
#include "proximity_info.h"
#include "proximity_info_state.h"

namespace latinime {
void ProximityInfoState::initInputParams(
        const ProximityInfo *proximityInfo, const int32_t *inputCodes, const int inputLength,
        const int *xCoordinates, const int *yCoordinates) {
    mProximityInfo = proximityInfo;
    mHasTouchPositionCorrectionData = proximityInfo->hasTouchPositionCorrectionData();
    mMostCommonKeyWidthSquare = proximityInfo->getMostCommonKeyWidthSquare();
    mLocaleStr = proximityInfo->getLocaleStr();
    mKeyCount = proximityInfo->getKeyCount();
    mCellHeight = proximityInfo->getCellHeight();
    mCellWidth = proximityInfo->getCellWidth();
    mGridHeight = proximityInfo->getGridWidth();
    mGridWidth = proximityInfo->getGridHeight();
    const int normalizedSquaredDistancesLength =
            MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL;
    for (int i = 0; i < normalizedSquaredDistancesLength; ++i) {
        mNormalizedSquaredDistances[i] = NOT_A_DISTANCE;
    }

    memset(mInputCodes, 0,
            MAX_WORD_LENGTH_INTERNAL * MAX_PROXIMITY_CHARS_SIZE_INTERNAL * sizeof(mInputCodes[0]));

    for (int i = 0; i < inputLength; ++i) {
        const int32_t primaryKey = inputCodes[i];
        const int x = xCoordinates[i];
        const int y = yCoordinates[i];
        int *proximities = &mInputCodes[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL];
        mProximityInfo->calculateNearbyKeyCodes(x, y, primaryKey, proximities);
    }

    if (DEBUG_PROXIMITY_CHARS) {
        for (int i = 0; i < inputLength; ++i) {
            AKLOGI("---");
            for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL; ++j) {
                int icc = mInputCodes[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + j];
                int icfjc = inputCodes[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + j];
                icc += 0;
                icfjc += 0;
                AKLOGI("--- (%d)%c,%c", i, icc, icfjc); AKLOGI("--- A<%d>,B<%d>", icc, icfjc);
            }
        }
    }
    mInputXCoordinates = xCoordinates;
    mInputYCoordinates = yCoordinates;
    mTouchPositionCorrectionEnabled =
            mHasTouchPositionCorrectionData && xCoordinates && yCoordinates;
    mInputLength = inputLength;
    for (int i = 0; i < inputLength; ++i) {
        mPrimaryInputWord[i] = getPrimaryCharAt(i);
    }
    mPrimaryInputWord[inputLength] = 0;
    if (DEBUG_PROXIMITY_CHARS) {
        AKLOGI("--- initInputParams");
    }
    for (int i = 0; i < mInputLength; ++i) {
        const int *proximityChars = getProximityCharsAt(i);
        const int primaryKey = proximityChars[0];
        const int x = xCoordinates[i];
        const int y = yCoordinates[i];
        if (DEBUG_PROXIMITY_CHARS) {
            int a = x + y + primaryKey;
            a += 0;
            AKLOGI("--- Primary = %c, x = %d, y = %d", primaryKey, x, y);
        }
        for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL && proximityChars[j] > 0; ++j) {
            const int currentChar = proximityChars[j];
            const float squaredDistance =
                    hasInputCoordinates() ? calculateNormalizedSquaredDistance(
                            mProximityInfo->getKeyIndex(currentChar), i) :
                            NOT_A_DISTANCE_FLOAT;
            if (squaredDistance >= 0.0f) {
                mNormalizedSquaredDistances[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + j] =
                        (int) (squaredDistance * NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR);
            } else {
                mNormalizedSquaredDistances[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + j] =
                        (j == 0) ? EQUIVALENT_CHAR_WITHOUT_DISTANCE_INFO :
                                PROXIMITY_CHAR_WITHOUT_DISTANCE_INFO;
            }
            if (DEBUG_PROXIMITY_CHARS) {
                AKLOGI("--- Proximity (%d) = %c", j, currentChar);
            }
        }
    }
}

float ProximityInfoState::calculateNormalizedSquaredDistance(
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

float ProximityInfoState::calculateSquaredDistanceFromSweetSpotCenter(
        const int keyIndex, const int inputIndex) const {
    const float sweetSpotCenterX = mProximityInfo->getSweetSpotCenterXAt(keyIndex);
    const float sweetSpotCenterY = mProximityInfo->getSweetSpotCenterYAt(keyIndex);
    const float inputX = static_cast<float>(mInputXCoordinates[inputIndex]);
    const float inputY = static_cast<float>(mInputYCoordinates[inputIndex]);
    return square(inputX - sweetSpotCenterX) + square(inputY - sweetSpotCenterY);
}
} // namespace latinime
