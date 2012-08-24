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
#include "geometry_utils.h"
#include "proximity_info.h"
#include "proximity_info_state.h"

namespace latinime {
void ProximityInfoState::initInputParams(const int pointerId, const float maxLength,
        const ProximityInfo *proximityInfo, const int32_t *inputCodes, const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times,
        const int *const pointerIds, const bool isGeometric) {
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

    for (int i = 0; i < inputSize; ++i) {
        const int32_t primaryKey = inputCodes[i];
        const int x = xCoordinates[i];
        const int y = yCoordinates[i];
        int *proximities = &mInputCodes[i * MAX_PROXIMITY_CHARS_SIZE_INTERNAL];
        mProximityInfo->calculateNearbyKeyCodes(x, y, primaryKey, proximities);
    }

    if (DEBUG_PROXIMITY_CHARS) {
        for (int i = 0; i < inputSize; ++i) {
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

    mMaxPointToKeyLength = maxLength;
    ///////////////////////
    // Setup touch points
    mInputXs.clear();
    mInputYs.clear();
    mTimes.clear();
    mLengthCache.clear();
    mDistanceCache.clear();

    mInputSize = 0;
    if (xCoordinates && yCoordinates) {
        const bool proximityOnly = !isGeometric && (xCoordinates[0] < 0 || yCoordinates[0] < 0);
        for (int i = 0; i < inputSize; ++i) {
            ++mInputSize;
            // Assuming pointerId == 0 if pointerIds is null.
            const int pid = pointerIds ? pointerIds[i] : 0;
            if (pointerId == pid) {
                const int c = isGeometric ? NOT_A_COORDINATE : getPrimaryCharAt(i);
                const int x = proximityOnly ? NOT_A_COORDINATE : xCoordinates[i];
                const int y = proximityOnly ? NOT_A_COORDINATE : yCoordinates[i];
                const int time = times ? times[i] : -1;
                pushTouchPoint(c, x, y, time, isGeometric);
            }
        }
    }

    if (mInputSize > 0) {
        const int keyCount = mProximityInfo->getKeyCount();
        mDistanceCache.resize(mInputSize * keyCount);
        for (int i = 0; i < mInputSize; ++i) {
            for (int k = 0; k < keyCount; ++k) {
                const int index = i * keyCount + k;
                const int x = mInputXs[i];
                const int y = mInputYs[i];
                mDistanceCache[index] =
                        mProximityInfo->getNormalizedSquaredDistanceFromCenterFloat(k, x, y);
            }
        }
    }
    // end
    ///////////////////////

    for (int i = 0; i < inputSize; ++i) {
        mPrimaryInputWord[i] = getPrimaryCharAt(i);
    }
    mPrimaryInputWord[inputSize] = 0;

    mTouchPositionCorrectionEnabled =
            mHasTouchPositionCorrectionData && xCoordinates && yCoordinates && !isGeometric;
    for (int i = 0; i < mInputSize && mTouchPositionCorrectionEnabled; ++i) {
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

void ProximityInfoState::pushTouchPoint(const int nodeChar, int x, int y,
        const int time, const bool sample) {
    const uint32_t size = mInputXs.size();
    // TODO: Should have a const variable for 10
    const int sampleRate = mProximityInfo->getMostCommonKeyWidth() / 10;
    if (size > 0) {
        const int dist = getDistanceInt(x, y, mInputXs[size - 1], mInputYs[size - 1]);
        if (sample && dist < sampleRate) {
            return;
        }
        mLengthCache.push_back(mLengthCache[size - 1] + dist);
    } else {
        mLengthCache.push_back(0);
    }
    if (nodeChar >= 0 && (x < 0 || y < 0)) {
        const int keyId = mProximityInfo->getKeyIndex(nodeChar);
        if (keyId >= 0) {
            x = mProximityInfo->getKeyCenterXOfIdG(keyId);
            y = mProximityInfo->getKeyCenterYOfIdG(keyId);
        }
    }
    mInputXs.push_back(x);
    mInputYs.push_back(y);
    mTimes.push_back(time);
}

float ProximityInfoState::calculateNormalizedSquaredDistance(
        const int keyIndex, const int inputIndex) const {
    if (keyIndex == NOT_AN_INDEX) {
        return NOT_A_DISTANCE_FLOAT;
    }
    if (!mProximityInfo->hasSweetSpotData(keyIndex)) {
        return NOT_A_DISTANCE_FLOAT;
    }
    if (NOT_A_COORDINATE == mInputXs[inputIndex]) {
        return NOT_A_DISTANCE_FLOAT;
    }
    const float squaredDistance = calculateSquaredDistanceFromSweetSpotCenter(
            keyIndex, inputIndex);
    const float squaredRadius = square(mProximityInfo->getSweetSpotRadiiAt(keyIndex));
    return squaredDistance / squaredRadius;
}

int ProximityInfoState::getDuration(const int index) const {
    if (mTimes.size() == 0 || index <= 0 || index >= static_cast<int>(mInputSize) - 1) {
        return 0;
    }
    return mTimes[index + 1] - mTimes[index - 1];
}

float ProximityInfoState::getPointToKeyLength(int inputIndex, int charCode, float scale) {
    const int keyId = mProximityInfo->getKeyIndex(charCode);
    if (keyId >= 0) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mDistanceCache[index] * scale, mMaxPointToKeyLength);
    }
    return 0;
}

int ProximityInfoState::getKeyKeyDistance(int key0, int key1) {
    return mProximityInfo->getKeyKeyDistanceG(key0, key1);
}

int ProximityInfoState::getSpaceY() {
    const int keyId = mProximityInfo->getKeyIndex(' ');
    return mProximityInfo->getKeyCenterYOfIdG(keyId);
}

float ProximityInfoState::calculateSquaredDistanceFromSweetSpotCenter(
        const int keyIndex, const int inputIndex) const {
    const float sweetSpotCenterX = mProximityInfo->getSweetSpotCenterXAt(keyIndex);
    const float sweetSpotCenterY = mProximityInfo->getSweetSpotCenterYAt(keyIndex);
    const float inputX = static_cast<float>(mInputXs[inputIndex]);
    const float inputY = static_cast<float>(mInputYs[inputIndex]);
    return square(inputX - sweetSpotCenterX) + square(inputY - sweetSpotCenterY);
}
} // namespace latinime
