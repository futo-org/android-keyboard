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
#include <sstream> // for debug prints
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
        mCharProbabilities.clear();
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
        const int sumDuration = mTimes.back() - mTimes.front();
        const int sumLength = mLengthCache.back() - mLengthCache.front();
        const float averageSpeed = static_cast<float>(sumLength) / static_cast<float>(sumDuration);
        mRelativeSpeeds.resize(mInputSize);
        for (int i = lastSavedInputSize; i < mInputSize; ++i) {
            const int index = mInputIndice[i];
            int length = 0;
            int duration = 0;

            // Calculate velocity by using distances and durations of
            // NUM_POINTS_FOR_SPEED_CALCULATION points for both forward and backward.
            static const int NUM_POINTS_FOR_SPEED_CALCULATION = 1;
            for (int j = index; j < min(inputSize - 1, index + NUM_POINTS_FOR_SPEED_CALCULATION);
                    ++j) {
                if (i < mInputSize - 1 && j >= mInputIndice[i + 1]) {
                    break;
                }
                length += getDistanceInt(xCoordinates[j], yCoordinates[j],
                        xCoordinates[j + 1], yCoordinates[j + 1]);
                duration += times[j + 1] - times[j];
            }
            for (int j = index - 1; j >= max(0, index - NUM_POINTS_FOR_SPEED_CALCULATION); --j) {
                if (i > 0 && j < mInputIndice[i - 1]) {
                    break;
                }
                length += getDistanceInt(xCoordinates[j], yCoordinates[j],
                        xCoordinates[j + 1], yCoordinates[j + 1]);
                duration += times[j + 1] - times[j];
            }
            if (duration == 0 || sumDuration == 0) {
                // Cannot calculate speed; thus, it gives an average value (1.0);
                mRelativeSpeeds[i] = 1.0f;
            } else {
                const float speed = static_cast<float>(length) / static_cast<float>(duration);
                mRelativeSpeeds[i] = speed / averageSpeed;
            }
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

    if (DEBUG_SAMPLING_POINTS) {
        std::stringstream originalX, originalY, sampledX, sampledY;
        for (int i = 0; i < inputSize; ++i) {
            originalX << xCoordinates[i];
            originalY << yCoordinates[i];
            if (i != inputSize - 1) {
                originalX << ";";
                originalY << ";";
            }
        }
        for (int i = 0; i < mInputSize; ++i) {
            sampledX << mInputXs[i];
            sampledY << mInputYs[i];
            if (i != mInputSize - 1) {
                sampledX << ";";
                sampledY << ";";
            }
        }
        AKLOGI("\n%s, %s,\n%s, %s,\n", originalX.str().c_str(), originalY.str().c_str(),
                sampledX.str().c_str(), sampledY.str().c_str());
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
    if (isGeometric && mInputSize > 0) {
        // updates probabilities of skipping or mapping each key for all points.
        updateAlignPointProbabilities();
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
    static const float NEAR_KEY_THRESHOLD = 1.7f;

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
    static const float MARGIN = 0.03f;

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
    static const int SAVE_DISTANCE_SCALE = 500;
    static const int SKIP_DISTANCE_SCALE = 10;
    static const float NEAR_KEY_THRESHOLD = 1.0f;
    static const int CHECK_LOCALMIN_DISTANCE_THRESHOLD_SCALE = 100;
    static const int STRAIGHT_SKIP_DISTANCE_THRESHOLD_SCALE = 200;
    static const int CORNER_CHECK_DISTANCE_THRESHOLD_SCALE = 20;
    static const float SAVE_DISTANCE_SCORE = 2.0f;
    static const float SKIP_DISTANCE_SCORE = -1.0f;
    static const float NOT_LOCALMIN_DISTANCE_SCORE = -1.0f;
    static const float LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE = 2.0f;
    static const float STRAIGHT_ANGLE_THRESHOLD = M_PI_F / 36.0f;
    static const float STRAIGHT_SKIP_NEAREST_DISTANCE_THRESHOLD = 0.5f;
    static const float STRAIGHT_SKIP_SCORE = -1.0f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F / 6.0f;
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
            score += NOT_LOCALMIN_DISTANCE_SCORE;
        } else if (nearest < NEAR_KEY_THRESHOLD) {
            // Promote points nearby keys
            score += LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE;
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
    static const int LAST_POINT_SKIP_DISTANCE_SCALE = 4;
    static const int LAST_AND_NOT_NEAREST_POINT_SKIP_DISTANCE_SCALE = 2;

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
        if (isLastPoint && size > 0) {
            const int lastPointsDistance = getDistanceInt(x, y, mInputXs.back(), mInputYs.back());
            if (lastPointsDistance * LAST_POINT_SKIP_DISTANCE_SCALE
                    < mProximityInfo->getMostCommonKeyWidth()) {
                // This point is not used because it's too close to the previous point.
                if (DEBUG_GEO_FULL) {
                    AKLOGI("p0: size = %zd, x = %d, y = %d, lx = %d, ly = %d, dist = %d, "
                           "width = %d", size, x, y, mInputXs.back(), mInputYs.back(),
                           getDistanceInt(x, y, mInputXs.back(), mInputYs.back()),
                           mProximityInfo->getMostCommonKeyWidth()
                                   / LAST_POINT_SKIP_DISTANCE_SCALE);
                }
                return popped;
            } else if (lastPointsDistance * LAST_AND_NOT_NEAREST_POINT_SKIP_DISTANCE_SCALE
                    < mProximityInfo->getMostCommonKeyWidth()) {
                int nearestChar = 0;
                float nearestCharDistance = mMaxPointToKeyLength;
                for (NearKeysDistanceMap::const_iterator it = currentNearKeysDistances->begin();
                        it != currentNearKeysDistances->end(); ++it) {
                    if (nearestCharDistance > it->second) {
                        nearestChar = it->first;
                        nearestCharDistance = it->second;
                    }
                }
                NearKeysDistanceMap::const_iterator itPP =
                        prevNearKeysDistances->find(nearestChar);
                if (itPP != prevNearKeysDistances->end() && nearestCharDistance > itPP->second) {
                    // The nearest key of the penultimate point is same as the nearest key of the
                    // last point. So, we don't need to use the last point.
                    if (DEBUG_GEO_FULL) {
                        AKLOGI("p1: char = %c, minDist = %f, prevNear key minDist = %f",
                                nearestChar, itPP->second, nearestCharDistance);
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

float ProximityInfoState::getPointToKeyLength(const int inputIndex, const int codePoint) const {
    if (isSkippableChar(codePoint)) {
        return 0.0f;
    }
    const int keyId = mProximityInfo->getKeyIndexOf(codePoint);
    return getPointToKeyByIdLength(inputIndex, keyId);
}

float ProximityInfoState::getPointToKeyByIdLength(const int inputIndex, const int keyId) const {
    if (keyId != NOT_AN_INDEX) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mDistanceCache[index], mMaxPointToKeyLength);
    }
    // If the char is not a key on the keyboard then return the max length.
    return static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
}

int ProximityInfoState::getSpaceY() const {
    const int keyId = mProximityInfo->getKeyIndexOf(KEYCODE_SPACE);
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

float ProximityInfoState::getPointAngle(const int index) const {
    if (index <= 0 || index >= mInputSize - 1) {
        return 0.0f;
    }
    const int x = mInputXs[index];
    const int y = mInputYs[index];
    const int nextX = mInputXs[index + 1];
    const int nextY = mInputYs[index + 1];
    const int previousX = mInputXs[index - 1];
    const int previousY = mInputYs[index - 1];
    const float previousDirection = getAngle(previousX, previousY, x, y);
    const float nextDirection = getAngle(x, y, nextX, nextY);
    const float directionDiff = getAngleDiff(previousDirection, nextDirection);
    return directionDiff;
}

float ProximityInfoState::getPointsAngle(
        const int index0, const int index1, const int index2) const {
    if (index0 < 0 || index0 > mInputSize - 1) {
        return 0.0f;
    }
    if (index1 < 0 || index1 > mInputSize - 1) {
        return 0.0f;
    }
    if (index2 < 0 || index2 > mInputSize - 1) {
        return 0.0f;
    }
    const int x0 = mInputXs[index0];
    const int y0 = mInputYs[index0];
    const int x1 = mInputXs[index1];
    const int y1 = mInputYs[index1];
    const int x2 = mInputXs[index2];
    const int y2 = mInputYs[index2];
    const float previousDirection = getAngle(x0, y0, x1, y1);
    const float nextDirection = getAngle(x1, y1, x2, y2);
    const float directionDiff = getAngleDiff(previousDirection, nextDirection);
    return directionDiff;
}

// Updates probabilities of aligning to some keys and skipping.
// Word suggestion should be based on this probabilities.
void ProximityInfoState::updateAlignPointProbabilities() {
    static const float MIN_PROBABILITY = 0.00001f;
    static const float SKIP_FIRST_POINT_PROBABILITY = 0.01f;
    static const float SKIP_LAST_POINT_PROBABILITY = 0.1f;
    static const float ANGLE_RATE = 0.8f;
    static const float DEEP_CORNER_ANGLE_THRESHOLD = M_PI_F * 0.5f;
    static const float SKIP_DEEP_CORNER_PROBABILITY = 0.3f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F * 35.0f / 180.0f;
    static const float STRAIGHT_ANGLE_THRESHOLD = M_PI_F * 15.0f / 180.0f;
    static const float SKIP_CORNER_PROBABILITY = 0.5f;
    static const float SLOW_STRAIGHT_WEIGHT = 0.8f;
    static const float CENTER_VALUE_OF_NORMALIZED_DISTRIBUTION = 0.0f;

    mCharProbabilities.resize(mInputSize);
    // Calculates probabilities of using a point as a correlated point with the character
    // for each point.
    for (int i = 0; i < mInputSize; ++i) {
        // First, calculates skip probability. Starts form 100%.
        // Note that all values that are multiplied to this probability should be in [0.0, 1.0];
        float skipProbability = 1.0f;
        const float speed = getRelativeSpeed(i);

        // Adjusts skip probability by a rate depending on speed.
        skipProbability *= min(1.0f, speed);
        if (i == 0) {
            skipProbability *= SKIP_FIRST_POINT_PROBABILITY;
        } else if (i == mInputSize - 1) {
            skipProbability *= SKIP_LAST_POINT_PROBABILITY;
        } else {
            const float currentAngle = getPointAngle(i);

            // Adjusts skip probability by a rate depending on angle.
            // ANGLE_RATE of skipProbability is adjusted by current angle.
            skipProbability *= max((M_PI_F - currentAngle) / M_PI_F, 0.0f) * ANGLE_RATE +
                    (1.0f - ANGLE_RATE);
            if (currentAngle > DEEP_CORNER_ANGLE_THRESHOLD) {
                skipProbability *= SKIP_DEEP_CORNER_PROBABILITY;
            }
            const float prevAngle = getPointsAngle(i, i - 1, i - 2);
            if (prevAngle < STRAIGHT_ANGLE_THRESHOLD && currentAngle > CORNER_ANGLE_THRESHOLD) {
                skipProbability *= SKIP_CORNER_PROBABILITY;
            }
            if (currentAngle < STRAIGHT_ANGLE_THRESHOLD) {
                // Adjusts skip probability by speed.
                skipProbability *= min(1.0f, speed * SLOW_STRAIGHT_WEIGHT);
            }
        }

        // probabilities must be in [0.0, 1.0];
        ASSERT(skipProbability >= 0.0f);
        ASSERT(skipProbability <= 1.0f);

        mCharProbabilities[i][NOT_AN_INDEX] = skipProbability;
        // Second, calculates key probabilities by dividing the rest probability
        // (1.0f - skipProbability).
        const float inputCharProbability = 1.0f - skipProbability;
        // Summing up probability densities of all near keys.
        float sumOfProbabilityDensityOfNearKeys = 0.0f;
        const float sigma = speed;
        NormalDistribution distribution(CENTER_VALUE_OF_NORMALIZED_DISTRIBUTION, sigma);
        for (int j = 0; j < mProximityInfo->getKeyCount(); ++j) {
            if (mNearKeysVector[i].test(j)) {
                const float distance = sqrtf(getPointToKeyByIdLength(i, j));
                sumOfProbabilityDensityOfNearKeys += distribution.getProbabilityDensity(distance);
            }
        }
        for (int j = 0; j < mProximityInfo->getKeyCount(); ++j) {
            if (mNearKeysVector[i].test(j)) {
                const float distance = sqrtf(getPointToKeyByIdLength(i, j));
                const float probabilityDessity = distribution.getProbabilityDensity(distance);
                // inputCharProbability divided to the probability for each near key.
                const float probability = inputCharProbability * probabilityDessity
                        / sumOfProbabilityDensityOfNearKeys;
                if (probability > MIN_PROBABILITY) {
                    mCharProbabilities[i][j] = probability;
                }
            }
        }
    }

    // Decrease key probabilities of points which don't have the highest probability of that key
    // among nearby points. Probabilities of the first point and the last point are not suppressed.
    for (int i = 1; i < mInputSize - 1; ++i) {
        // forward
        for (int j = i + 1; j < mInputSize; ++j) {
            if (suppressCharProbabilities(i, j)) {
                break;
            }
        }
        // backward
        for (int j = i - 1; j >= 0; --j) {
            if (suppressCharProbabilities(i, j)) {
                break;
            }
        }
    }

    if (DEBUG_POINTS_PROBABILITY) {
        for (int i = 0; i < mInputSize; ++i) {
            std::stringstream sstream;
            sstream << i << ", ";
            for (hash_map_compat<int, float>::iterator it = mCharProbabilities[i].begin();
                    it != mCharProbabilities[i].end(); ++it) {
                sstream << it->first
                        << "("
                        << static_cast<char>(mProximityInfo->getCodePointOf(it->first))
                        << "):"
                        << it->second
                        << ", ";
            }
            AKLOGI("%s", sstream.str().c_str());
        }
    }
}

// Decreases char probabilities of index0 by checking probabilities of a near point (index1).
bool ProximityInfoState::suppressCharProbabilities(const int index0, const int index1) {
    ASSERT(0 <= index0 && index0 < mInputSize);
    ASSERT(0 <= index1 && index1 < mInputSize);
    static const float SUPPRESSION_LENGTH_WEIGHT = 1.5f;
    const float keyWidthFloat = static_cast<float>(mProximityInfo->getMostCommonKeyWidth());
    const float diff = fabsf(static_cast<float>(mLengthCache[index0] - mLengthCache[index1]));
    if (diff > keyWidthFloat * SUPPRESSION_LENGTH_WEIGHT) {
        return false;
    }
    // Summing up decreased amount of probabilities from 0%.
    float sumOfAdjustedProbabilities = 0.0f;
    const float suppressionRate = diff / keyWidthFloat / SUPPRESSION_LENGTH_WEIGHT;
    for (hash_map_compat<int, float>::iterator it = mCharProbabilities[index0].begin();
            it != mCharProbabilities[index0].end(); ++it) {
        hash_map_compat<int, float>::const_iterator it2 =
                mCharProbabilities[index1].find(it->first);
        if (it2 != mCharProbabilities[index1].end() && it->second < it2->second) {
            const float newProbability = it->second * suppressionRate;
            sumOfAdjustedProbabilities += it->second - newProbability;
            it->second = newProbability;
        }
    }
    // All decreased amount of probabilities are added to the probability of skipping.
    mCharProbabilities[index0][NOT_AN_INDEX] += sumOfAdjustedProbabilities;
    return true;
}

// Get a word that is detected by tracing highest probability sequence into charBuf and returns
// probability of generating the word.
float ProximityInfoState::getHighestProbabilitySequence(uint16_t *const charBuf) const {
    int buf[mInputSize];
    // Maximum probabilities of each point are multiplied to 100%.
    float probability = 1.0f;
    // TODO: Current implementation is greedy algorithm. DP would be efficient for many cases.
    for (int i = 0; i < mInputSize; ++i) {
        float maxProbability = 0.0f;
        for (hash_map_compat<int, float>::const_iterator it = mCharProbabilities[i].begin();
                it != mCharProbabilities[i].end(); ++it) {
            if (it->second > maxProbability) {
                maxProbability = it->second;
                buf[i] = it->first;
            }
        }
        probability *= maxProbability;
    }
    int index = 0;
    for (int i = 0; i < mInputSize && index < MAX_WORD_LENGTH_INTERNAL - 1; ++i) {
        if (buf[i] != NOT_AN_INDEX) {
            charBuf[index] = mProximityInfo->getCodePointOf(buf[i]);
            index++;
        }
    }
    charBuf[index] = '\0';
    return probability;
}

} // namespace latinime
