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

const int ProximityInfoState::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2 = 10;
const int ProximityInfoState::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR =
        1 << NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
const float ProximityInfoState::NOT_A_DISTANCE_FLOAT = -1.0f;
const int ProximityInfoState::NOT_A_CODE = -1;

void ProximityInfoState::initInputParams(const int pointerId, const float maxPointToKeyLength,
        const ProximityInfo *proximityInfo, const int32_t *const inputCodes, const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times,
        const int *const pointerIds, const bool isGeometric) {

    if (isGeometric) {
        mIsContinuationPossible = checkAndReturnIsContinuationPossible(
                inputSize, xCoordinates, yCoordinates, times);
    } else {
        mIsContinuationPossible = false;
    }

    mProximityInfo = proximityInfo;
    mHasTouchPositionCorrectionData = proximityInfo->hasTouchPositionCorrectionData();
    mMostCommonKeyWidthSquare = proximityInfo->getMostCommonKeyWidthSquare();
    mLocaleStr = proximityInfo->getLocaleStr();
    mKeyCount = proximityInfo->getKeyCount();
    mCellHeight = proximityInfo->getCellHeight();
    mCellWidth = proximityInfo->getCellWidth();
    mGridHeight = proximityInfo->getGridWidth();
    mGridWidth = proximityInfo->getGridHeight();

    memset(mInputCodes, 0, sizeof(mInputCodes));

    if (!isGeometric && pointerId == 0) {
        // Initialize
        // - mInputCodes
        // - mNormalizedSquaredDistances
        // TODO: Merge
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
    }

    ///////////////////////
    // Setup touch points
    int pushTouchPointStartIndex = 0;
    int lastSavedInputSize = 0;
    mMaxPointToKeyLength = maxPointToKeyLength;
    if (mIsContinuationPossible && mInputIndice.size() > 1) {
        // Just update difference.
        // Two points prior is never skipped. Thus, we pop 2 input point data here.
        pushTouchPointStartIndex = mInputIndice[mInputIndice.size() - 2];
        popInputData();
        popInputData();
        lastSavedInputSize = mInputXs.size();
    } else {
        // Clear all data.
        mInputXs.clear();
        mInputYs.clear();
        mTimes.clear();
        mInputIndice.clear();
        mLengthCache.clear();
        mDistanceCache.clear();
        mNearKeysVector.clear();
        mRelativeSpeeds.clear();
    }
    if (DEBUG_GEO_FULL) {
        AKLOGI("Init ProximityInfoState: reused points =  %d, last input size = %d",
                pushTouchPointStartIndex, lastSavedInputSize);
    }
    mInputSize = 0;

    if (xCoordinates && yCoordinates) {
        const bool proximityOnly = !isGeometric && (xCoordinates[0] < 0 || yCoordinates[0] < 0);
        int lastInputIndex = pushTouchPointStartIndex;
        for (int i = lastInputIndex; i < inputSize; ++i) {
            const int pid = pointerIds ? pointerIds[i] : 0;
            if (pointerId == pid) {
                lastInputIndex = i;
            }
        }
        if (DEBUG_GEO_FULL) {
            AKLOGI("Init ProximityInfoState: last input index = %d", lastInputIndex);
        }
        // Working space to save near keys distances for current, prev and prevprev input point.
        NearKeysDistanceMap nearKeysDistances[3];
        // These pointers are swapped for each inputs points.
        NearKeysDistanceMap *currentNearKeysDistances = &nearKeysDistances[0];
        NearKeysDistanceMap *prevNearKeysDistances = &nearKeysDistances[1];
        NearKeysDistanceMap *prevPrevNearKeysDistances = &nearKeysDistances[2];

        for (int i = pushTouchPointStartIndex; i <= lastInputIndex; ++i) {
            // Assuming pointerId == 0 if pointerIds is null.
            const int pid = pointerIds ? pointerIds[i] : 0;
            if (DEBUG_GEO_FULL) {
                AKLOGI("Init ProximityInfoState: (%d)PID = %d", i, pid);
            }
            if (pointerId == pid) {
                const int c = isGeometric ? NOT_A_COORDINATE : getPrimaryCharAt(i);
                const int x = proximityOnly ? NOT_A_COORDINATE : xCoordinates[i];
                const int y = proximityOnly ? NOT_A_COORDINATE : yCoordinates[i];
                const int time = times ? times[i] : -1;
                if (pushTouchPoint(i, c, x, y, time, isGeometric /* do sampling */,
                        i == lastInputIndex, currentNearKeysDistances, prevNearKeysDistances,
                        prevPrevNearKeysDistances)) {
                    // Previous point information was popped.
                    NearKeysDistanceMap *tmp = prevNearKeysDistances;
                    prevNearKeysDistances = currentNearKeysDistances;
                    currentNearKeysDistances = tmp;
                } else {
                    NearKeysDistanceMap *tmp = prevPrevNearKeysDistances;
                    prevPrevNearKeysDistances = prevNearKeysDistances;
                    prevNearKeysDistances = currentNearKeysDistances;
                    currentNearKeysDistances = tmp;
                }
            }
        }
        mInputSize = mInputXs.size();
    }

    if (mInputSize > 0 && isGeometric) {
        int sumDuration = mTimes.back() - mTimes.front();
        int sumLength = mLengthCache.back() - mLengthCache.front();
        float averageSpeed = static_cast<float>(sumLength) / static_cast<float>(sumDuration);
        mRelativeSpeeds.resize(mInputSize);
        for (int i = lastSavedInputSize; i < mInputSize; ++i) {
            const int index = mInputIndice[i];
            int length = 0;
            int duration = 0;
            if (index == 0 && index < inputSize - 1) {
                length = getDistanceInt(xCoordinates[index], yCoordinates[index],
                        xCoordinates[index + 1], yCoordinates[index + 1]);
                duration = times[index + 1] - times[index];
            } else if (index == inputSize - 1 && index > 0) {
                length = getDistanceInt(xCoordinates[index - 1], yCoordinates[index - 1],
                        xCoordinates[index], yCoordinates[index]);
                duration = times[index] - times[index - 1];
            } else if (0 < index && index < inputSize - 1) {
                length = getDistanceInt(xCoordinates[index - 1], yCoordinates[index - 1],
                        xCoordinates[index], yCoordinates[index])
                        + getDistanceInt(xCoordinates[index], yCoordinates[index],
                                xCoordinates[index + 1], yCoordinates[index + 1]);
                duration = times[index + 1] - times[index - 1];
            } else {
                length = 0;
                duration = 1;
            }
            const float speed = static_cast<float>(length) / static_cast<float>(duration);
            mRelativeSpeeds[i] = speed / averageSpeed;
        }
    }

    if (mInputSize > 0) {
        const int keyCount = mProximityInfo->getKeyCount();
        mNearKeysVector.resize(mInputSize);
        mDistanceCache.resize(mInputSize * keyCount);
        for (int i = lastSavedInputSize; i < mInputSize; ++i) {
            mNearKeysVector[i].reset();
            static const float NEAR_KEY_NORMALIZED_SQUARED_THRESHOLD = 4.0f;
            for (int k = 0; k < keyCount; ++k) {
                const int index = i * keyCount + k;
                const int x = mInputXs[i];
                const int y = mInputYs[i];
                const float normalizedSquaredDistance =
                        mProximityInfo->getNormalizedSquaredDistanceFromCenterFloatG(k, x, y);
                mDistanceCache[index] = normalizedSquaredDistance;
                if (normalizedSquaredDistance < NEAR_KEY_NORMALIZED_SQUARED_THRESHOLD) {
                    mNearKeysVector[i].set(k, 1);
                }
            }
        }

        static const float READ_FORWORD_LENGTH_SCALE = 0.95f;
        const int readForwordLength = static_cast<int>(
                hypotf(mProximityInfo->getKeyboardWidth(), mProximityInfo->getKeyboardHeight())
                * READ_FORWORD_LENGTH_SCALE);
        for (int i = 0; i < mInputSize; ++i) {
            if (DEBUG_GEO_FULL) {
                AKLOGI("Sampled(%d): x = %d, y = %d, time = %d", i, mInputXs[i], mInputYs[i],
                        mTimes[i]);
            }
            for (int j = max(i + 1, lastSavedInputSize); j < mInputSize; ++j) {
                if (mLengthCache[j] - mLengthCache[i] >= readForwordLength) {
                    break;
                }
                mNearKeysVector[i] |= mNearKeysVector[j];
            }
        }
    }

    // end
    ///////////////////////

    memset(mNormalizedSquaredDistances, NOT_A_DISTANCE, sizeof(mNormalizedSquaredDistances));
    memset(mPrimaryInputWord, 0, sizeof(mPrimaryInputWord));
    mTouchPositionCorrectionEnabled = mInputSize > 0 && mHasTouchPositionCorrectionData
            && xCoordinates && yCoordinates;
    if (!isGeometric && pointerId == 0) {
        for (int i = 0; i < inputSize; ++i) {
            mPrimaryInputWord[i] = getPrimaryCharAt(i);
        }

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
                                mProximityInfo->getKeyIndexOf(currentChar), i) :
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

    if (DEBUG_GEO_FULL) {
        AKLOGI("ProximityState init finished: %d points out of %d", mInputSize, inputSize);
    }
}

bool ProximityInfoState::checkAndReturnIsContinuationPossible(const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times) {
    for (int i = 0; i < mInputSize; ++i) {
        const int index = mInputIndice[i];
        if (index > inputSize || xCoordinates[index] != mInputXs[i] ||
                yCoordinates[index] != mInputYs[i] || times[index] != mTimes[i]) {
            return false;
        }
    }
    return true;
}

// Calculating point to key distance for all near keys and returning the distance between
// the given point and the nearest key position.
float ProximityInfoState::updateNearKeysDistances(const int x, const int y,
        NearKeysDistanceMap *const currentNearKeysDistances) {
    static const float NEAR_KEY_THRESHOLD = 4.0f;

    currentNearKeysDistances->clear();
    const int keyCount = mProximityInfo->getKeyCount();
    float nearestKeyDistance = mMaxPointToKeyLength;
    for (int k = 0; k < keyCount; ++k) {
        const float dist = mProximityInfo->getNormalizedSquaredDistanceFromCenterFloatG(k, x, y);
        if (dist < NEAR_KEY_THRESHOLD) {
            currentNearKeysDistances->insert(std::pair<int, float>(k, dist));
        }
        if (nearestKeyDistance > dist) {
            nearestKeyDistance = dist;
        }
    }
    return nearestKeyDistance;
}

// Check if previous point is at local minimum position to near keys.
bool ProximityInfoState::isPrevLocalMin(const NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) const {
    static const float MARGIN = 0.01f;

    for (NearKeysDistanceMap::const_iterator it = prevNearKeysDistances->begin();
        it != prevNearKeysDistances->end(); ++it) {
        NearKeysDistanceMap::const_iterator itPP = prevPrevNearKeysDistances->find(it->first);
        NearKeysDistanceMap::const_iterator itC = currentNearKeysDistances->find(it->first);
        if ((itPP == prevPrevNearKeysDistances->end() || itPP->second > it->second + MARGIN)
                && (itC == currentNearKeysDistances->end() || itC->second > it->second + MARGIN)) {
            return true;
        }
    }
    return false;
}

// Calculating a point score that indicates usefulness of the point.
float ProximityInfoState::getPointScore(
        const int x, const int y, const int time, const bool lastPoint, const float nearest,
        const NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) const {
    static const int DISTANCE_BASE_SCALE = 100;
    static const int SAVE_DISTANCE_SCALE = 200;
    static const int SKIP_DISTANCE_SCALE = 25;
    static const int CHECK_LOCALMIN_DISTANCE_THRESHOLD_SCALE = 40;
    static const int STRAIGHT_SKIP_DISTANCE_THRESHOLD_SCALE = 50;
    static const int CORNER_CHECK_DISTANCE_THRESHOLD_SCALE = 27;
    static const float SAVE_DISTANCE_SCORE = 2.0f;
    static const float SKIP_DISTANCE_SCORE = -1.0f;
    static const float CHECK_LOCALMIN_DISTANCE_SCORE = -1.0f;
    static const float STRAIGHT_ANGLE_THRESHOLD = M_PI_F / 36.0f;
    static const float STRAIGHT_SKIP_NEAREST_DISTANCE_THRESHOLD = 0.5f;
    static const float STRAIGHT_SKIP_SCORE = -1.0f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F / 2.0f;
    static const float CORNER_SCORE = 1.0f;

    const std::size_t size = mInputXs.size();
    if (size <= 1) {
        return 0.0f;
    }
    const int baseSampleRate = mProximityInfo->getMostCommonKeyWidth();
    const int distNext = getDistanceInt(x, y, mInputXs.back(), mInputYs.back())
            * DISTANCE_BASE_SCALE;
    const int distPrev = getDistanceInt(mInputXs.back(), mInputYs.back(),
            mInputXs[size - 2], mInputYs[size - 2]) * DISTANCE_BASE_SCALE;
    float score = 0.0f;

    // Sum of distances
    if (distPrev + distNext > baseSampleRate * SAVE_DISTANCE_SCALE) {
        score +=  SAVE_DISTANCE_SCORE;
    }
    // Distance
    if (distPrev < baseSampleRate * SKIP_DISTANCE_SCALE) {
        score += SKIP_DISTANCE_SCORE;
    }
    // Location
    if (distPrev < baseSampleRate * CHECK_LOCALMIN_DISTANCE_THRESHOLD_SCALE) {
        if (!isPrevLocalMin(currentNearKeysDistances, prevNearKeysDistances,
            prevPrevNearKeysDistances)) {
            score += CHECK_LOCALMIN_DISTANCE_SCORE;
        }
    }
    // Angle
    const float angle1 = getAngle(x, y, mInputXs.back(), mInputYs.back());
    const float angle2 = getAngle(mInputXs.back(), mInputYs.back(),
            mInputXs[size - 2], mInputYs[size - 2]);
    const float angleDiff = getAngleDiff(angle1, angle2);
    // Skip straight
    if (nearest > STRAIGHT_SKIP_NEAREST_DISTANCE_THRESHOLD
            && distPrev < baseSampleRate * STRAIGHT_SKIP_DISTANCE_THRESHOLD_SCALE
            && angleDiff < STRAIGHT_ANGLE_THRESHOLD) {
        score += STRAIGHT_SKIP_SCORE;
    }
    // Save corner
    if (distPrev > baseSampleRate * CORNER_CHECK_DISTANCE_THRESHOLD_SCALE
            && angleDiff > CORNER_ANGLE_THRESHOLD) {
        score += CORNER_SCORE;
    }
    return score;
}

// Sampling touch point and pushing information to vectors.
// Returning if previous point is popped or not.
bool ProximityInfoState::pushTouchPoint(const int inputIndex, const int nodeChar, int x, int y,
        const int time, const bool sample, const bool isLastPoint,
        NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) {
    static const float LAST_POINT_SKIP_DISTANCE_SCALE = 0.25f;

    size_t size = mInputXs.size();
    bool popped = false;
    if (nodeChar < 0 && sample) {
        const float nearest = updateNearKeysDistances(x, y, currentNearKeysDistances);
        const float score = getPointScore(x, y, time, isLastPoint, nearest,
                currentNearKeysDistances, prevNearKeysDistances, prevPrevNearKeysDistances);
        if (score < 0) {
            // Pop previous point because it would be useless.
            popInputData();
            size = mInputXs.size();
            popped = true;
        } else {
            popped = false;
        }
        // Check if the last point should be skipped.
        if (isLastPoint) {
            if (size > 0 && getDistanceFloat(x, y, mInputXs.back(), mInputYs.back())
                    < mProximityInfo->getMostCommonKeyWidth() * LAST_POINT_SKIP_DISTANCE_SCALE) {
                if (DEBUG_GEO_FULL) {
                    AKLOGI("p0: size = %zd, x = %d, y = %d, lx = %d, ly = %d, dist = %f, "
                           "width = %f", size, x, y, mInputXs.back(), mInputYs.back(),
                           getDistanceFloat(x, y, mInputXs.back(), mInputYs.back()),
                           mProximityInfo->getMostCommonKeyWidth()
                                   * LAST_POINT_SKIP_DISTANCE_SCALE);
                }
                return popped;
            } else if (size > 1) {
                int minChar = 0;
                float minDist = mMaxPointToKeyLength;
                for (NearKeysDistanceMap::const_iterator it = currentNearKeysDistances->begin();
                        it != currentNearKeysDistances->end(); ++it) {
                    if (minDist > it->second) {
                        minChar = it->first;
                        minDist = it->second;
                    }
                }
                NearKeysDistanceMap::const_iterator itPP =
                        prevNearKeysDistances->find(minChar);
                if (itPP != prevNearKeysDistances->end() && minDist > itPP->second) {
                    if (DEBUG_GEO_FULL) {
                        AKLOGI("p1: char = %c, minDist = %f, prevNear key minDist = %f",
                                minChar, itPP->second, minDist);
                    }
                    return popped;
                }
            }
        }
    }

    if (nodeChar >= 0 && (x < 0 || y < 0)) {
        const int keyId = mProximityInfo->getKeyIndexOf(nodeChar);
        if (keyId >= 0) {
            x = mProximityInfo->getKeyCenterXOfKeyIdG(keyId);
            y = mProximityInfo->getKeyCenterYOfKeyIdG(keyId);
        }
    }

    // Pushing point information.
    if (size > 0) {
        mLengthCache.push_back(
                mLengthCache.back() + getDistanceInt(x, y, mInputXs.back(), mInputYs.back()));
    } else {
        mLengthCache.push_back(0);
    }
    mInputXs.push_back(x);
    mInputYs.push_back(y);
    mTimes.push_back(time);
    mInputIndice.push_back(inputIndex);
    if (DEBUG_GEO_FULL) {
        AKLOGI("pushTouchPoint: x = %03d, y = %03d, time = %d, index = %d, popped ? %01d",
                x, y, time, inputIndex, popped);
    }
    return popped;
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
    if (index >= 0 && index < mInputSize - 1) {
        return mTimes[index + 1] - mTimes[index];
    }
    return 0;
}

float ProximityInfoState::getPointToKeyLength(const int inputIndex, const int codePoint,
        const float scale) const {
    const int keyId = mProximityInfo->getKeyIndexOf(codePoint);
    if (keyId != NOT_AN_INDEX) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mDistanceCache[index] * scale, mMaxPointToKeyLength);
    }
    if (isSkippableChar(codePoint)) {
        return 0.0f;
    }
    // If the char is not a key on the keyboard then return the max length.
    return MAX_POINT_TO_KEY_LENGTH;
}

int ProximityInfoState::getSpaceY() const {
    const int keyId = mProximityInfo->getKeyIndexOf(' ');
    return mProximityInfo->getKeyCenterYOfKeyIdG(keyId);
}

float ProximityInfoState::calculateSquaredDistanceFromSweetSpotCenter(
        const int keyIndex, const int inputIndex) const {
    const float sweetSpotCenterX = mProximityInfo->getSweetSpotCenterXAt(keyIndex);
    const float sweetSpotCenterY = mProximityInfo->getSweetSpotCenterYAt(keyIndex);
    const float inputX = static_cast<float>(mInputXs[inputIndex]);
    const float inputY = static_cast<float>(mInputYs[inputIndex]);
    return square(inputX - sweetSpotCenterX) + square(inputY - sweetSpotCenterY);
}

// Puts possible characters into filter and returns new filter size.
int32_t ProximityInfoState::getAllPossibleChars(
        const size_t index, int32_t *const filter, const int32_t filterSize) const {
    if (index >= mInputXs.size()) {
        return filterSize;
    }
    int newFilterSize = filterSize;
    for (int j = 0; j < mProximityInfo->getKeyCount(); ++j) {
        if (mNearKeysVector[index].test(j)) {
            const int32_t keyCodePoint = mProximityInfo->getCodePointOf(j);
            bool insert = true;
            // TODO: Avoid linear search
            for (int k = 0; k < filterSize; ++k) {
                if (filter[k] == keyCodePoint) {
                    insert = false;
                    break;
                }
            }
            if (insert) {
                filter[newFilterSize++] = keyCodePoint;
            }
        }
    }
    return newFilterSize;
}

void ProximityInfoState::popInputData() {
    mInputXs.pop_back();
    mInputYs.pop_back();
    mTimes.pop_back();
    mLengthCache.pop_back();
    mInputIndice.pop_back();
}

} // namespace latinime
