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
        const ProximityInfo *proximityInfo, const int *const inputCodes, const int inputSize,
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
            const int primaryKey = inputCodes[i];
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
        mSearchKeysVector.clear();
        mRelativeSpeeds.clear();
        mCharProbabilities.clear();
        mDirections.clear();
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
        // "sumAngle" is accumulated by each angle of input points. And when "sumAngle" exceeds
        // the threshold we save that point, reset sumAngle. This aims to keep the figure of
        // the curve.
        float sumAngle = 0.0f;

        for (int i = pushTouchPointStartIndex; i <= lastInputIndex; ++i) {
            // Assuming pointerId == 0 if pointerIds is null.
            const int pid = pointerIds ? pointerIds[i] : 0;
            if (DEBUG_GEO_FULL) {
                AKLOGI("Init ProximityInfoState: (%d)PID = %d", i, pid);
            }
            if (pointerId == pid) {
                const int c = isGeometric ? NOT_A_COORDINATE : getPrimaryCodePointAt(i);
                const int x = proximityOnly ? NOT_A_COORDINATE : xCoordinates[i];
                const int y = proximityOnly ? NOT_A_COORDINATE : yCoordinates[i];
                const int time = times ? times[i] : -1;

                if (i > 1) {
                    const float prevAngle = getAngle(xCoordinates[i - 2], yCoordinates[i - 2],
                            xCoordinates[i - 1], yCoordinates[i - 1]);
                    const float currentAngle =
                            getAngle(xCoordinates[i - 1], yCoordinates[i - 1], x, y);
                    sumAngle += getAngleDiff(prevAngle, currentAngle);
                }

                if (pushTouchPoint(i, c, x, y, time, isGeometric /* do sampling */,
                        i == lastInputIndex, sumAngle, currentNearKeysDistances,
                        prevNearKeysDistances, prevPrevNearKeysDistances)) {
                    // Previous point information was popped.
                    NearKeysDistanceMap *tmp = prevNearKeysDistances;
                    prevNearKeysDistances = currentNearKeysDistances;
                    currentNearKeysDistances = tmp;
                } else {
                    NearKeysDistanceMap *tmp = prevPrevNearKeysDistances;
                    prevPrevNearKeysDistances = prevNearKeysDistances;
                    prevNearKeysDistances = currentNearKeysDistances;
                    currentNearKeysDistances = tmp;
                    sumAngle = 0.0f;
                }
            }
        }
        mInputSize = mInputXs.size();
    }

    if (mInputSize > 0 && isGeometric) {
        // Relative speed calculation.
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
            static const int NUM_POINTS_FOR_SPEED_CALCULATION = 2;
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

        // Direction calculation.
        mDirections.resize(mInputSize - 1);
        for (int i = max(0, lastSavedInputSize - 1); i < mInputSize - 1; ++i) {
            mDirections[i] = getDirection(i, i + 1);
        }

    }

    if (DEBUG_GEO_FULL) {
        for (int i = 0; i < mInputSize; ++i) {
            AKLOGI("Sampled(%d): x = %d, y = %d, time = %d", i, mInputXs[i], mInputYs[i],
                    mTimes[i]);
        }
    }

    if (mInputSize > 0) {
        const int keyCount = mProximityInfo->getKeyCount();
        mNearKeysVector.resize(mInputSize);
        mSearchKeysVector.resize(mInputSize);
        mDistanceCache.resize(mInputSize * keyCount);
        for (int i = lastSavedInputSize; i < mInputSize; ++i) {
            mNearKeysVector[i].reset();
            mSearchKeysVector[i].reset();
            static const float NEAR_KEY_NORMALIZED_SQUARED_THRESHOLD = 4.0f;
            for (int k = 0; k < keyCount; ++k) {
                const int index = i * keyCount + k;
                const int x = mInputXs[i];
                const int y = mInputYs[i];
                const float normalizedSquaredDistance =
                        mProximityInfo->getNormalizedSquaredDistanceFromCenterFloatG(k, x, y);
                mDistanceCache[index] = normalizedSquaredDistance;
                if (normalizedSquaredDistance < NEAR_KEY_NORMALIZED_SQUARED_THRESHOLD) {
                    mNearKeysVector[i][k] = true;
                }
            }
        }
        if (isGeometric) {
            // updates probabilities of skipping or mapping each key for all points.
            updateAlignPointProbabilities(lastSavedInputSize);

            static const float READ_FORWORD_LENGTH_SCALE = 0.95f;
            const int readForwordLength = static_cast<int>(
                    hypotf(mProximityInfo->getKeyboardWidth(), mProximityInfo->getKeyboardHeight())
                            * READ_FORWORD_LENGTH_SCALE);
            for (int i = 0; i < mInputSize; ++i) {
                if (i >= lastSavedInputSize) {
                    mSearchKeysVector[i].reset();
                }
                for (int j = max(i, lastSavedInputSize); j < mInputSize; ++j) {
                    if (mLengthCache[j] - mLengthCache[i] >= readForwordLength) {
                        break;
                    }
                    mSearchKeysVector[i] |= mNearKeysVector[j];
                }
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
            mPrimaryInputWord[i] = getPrimaryCodePointAt(i);
        }

        for (int i = 0; i < mInputSize && mTouchPositionCorrectionEnabled; ++i) {
            const int *proximityCodePoints = getProximityCodePointsAt(i);
            const int primaryKey = proximityCodePoints[0];
            const int x = xCoordinates[i];
            const int y = yCoordinates[i];
            if (DEBUG_PROXIMITY_CHARS) {
                int a = x + y + primaryKey;
                a += 0;
                AKLOGI("--- Primary = %c, x = %d, y = %d", primaryKey, x, y);
            }
            for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL && proximityCodePoints[j] > 0;
                    ++j) {
                const int currentCodePoint = proximityCodePoints[j];
                const float squaredDistance =
                        hasInputCoordinates() ? calculateNormalizedSquaredDistance(
                                mProximityInfo->getKeyIndexOf(currentCodePoint), i) :
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
                    AKLOGI("--- Proximity (%d) = %c", j, currentCodePoint);
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
    static const float NEAR_KEY_THRESHOLD = 2.0f;

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
        const float sumAngle, const NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) const {
    static const int DISTANCE_BASE_SCALE = 100;
    static const float NEAR_KEY_THRESHOLD = 0.6f;
    static const int CORNER_CHECK_DISTANCE_THRESHOLD_SCALE = 25;
    static const float NOT_LOCALMIN_DISTANCE_SCORE = -1.0f;
    static const float LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE = 1.0f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F * 2.0f / 3.0f;
    static const float CORNER_SUM_ANGLE_THRESHOLD = M_PI_F / 4.0f;
    static const float CORNER_SCORE = 1.0f;

    const size_t size = mInputXs.size();
    // If there is only one point, add this point. Besides, if the previous point's distance map
    // is empty, we re-compute nearby keys distances from the current point.
    // Note that the current point is the first point in the incremental input that needs to
    // be re-computed.
    if (size <= 1 || prevNearKeysDistances->empty()) {
        return 0.0f;
    }

    const int baseSampleRate = mProximityInfo->getMostCommonKeyWidth();
    const int distPrev = getDistanceInt(mInputXs.back(), mInputYs.back(),
            mInputXs[size - 2], mInputYs[size - 2]) * DISTANCE_BASE_SCALE;
    float score = 0.0f;

    // Location
    if (!isPrevLocalMin(currentNearKeysDistances, prevNearKeysDistances,
        prevPrevNearKeysDistances)) {
        score += NOT_LOCALMIN_DISTANCE_SCORE;
    } else if (nearest < NEAR_KEY_THRESHOLD) {
        // Promote points nearby keys
        score += LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE;
    }
    // Angle
    const float angle1 = getAngle(x, y, mInputXs.back(), mInputYs.back());
    const float angle2 = getAngle(mInputXs.back(), mInputYs.back(),
            mInputXs[size - 2], mInputYs[size - 2]);
    const float angleDiff = getAngleDiff(angle1, angle2);

    // Save corner
    if (distPrev > baseSampleRate * CORNER_CHECK_DISTANCE_THRESHOLD_SCALE
            && (sumAngle > CORNER_SUM_ANGLE_THRESHOLD || angleDiff > CORNER_ANGLE_THRESHOLD)) {
        score += CORNER_SCORE;
    }
    return score;
}

// Sampling touch point and pushing information to vectors.
// Returning if previous point is popped or not.
bool ProximityInfoState::pushTouchPoint(const int inputIndex, const int nodeCodePoint, int x, int y,
        const int time, const bool sample, const bool isLastPoint, const float sumAngle,
        NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) {
    static const int LAST_POINT_SKIP_DISTANCE_SCALE = 4;

    size_t size = mInputXs.size();
    bool popped = false;
    if (nodeCodePoint < 0 && sample) {
        const float nearest = updateNearKeysDistances(x, y, currentNearKeysDistances);
        const float score = getPointScore(x, y, time, isLastPoint, nearest, sumAngle,
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
            if (getDistanceInt(x, y, mInputXs.back(), mInputYs.back())
                    * LAST_POINT_SKIP_DISTANCE_SCALE < mProximityInfo->getMostCommonKeyWidth()) {
                // This point is not used because it's too close to the previous point.
                if (DEBUG_GEO_FULL) {
                    AKLOGI("p0: size = %zd, x = %d, y = %d, lx = %d, ly = %d, dist = %d, "
                           "width = %d", size, x, y, mInputXs.back(), mInputYs.back(),
                           getDistanceInt(x, y, mInputXs.back(), mInputYs.back()),
                           mProximityInfo->getMostCommonKeyWidth()
                                   / LAST_POINT_SKIP_DISTANCE_SCALE);
                }
                return popped;
            }
        }
    }

    if (nodeCodePoint >= 0 && (x < 0 || y < 0)) {
        const int keyId = mProximityInfo->getKeyIndexOf(nodeCodePoint);
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
    const int keyId = mProximityInfo->getKeyIndexOf(codePoint);
    if (keyId != NOT_AN_INDEX) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mDistanceCache[index], mMaxPointToKeyLength);
    }
    if (isSkippableCodePoint(codePoint)) {
        return 0.0f;
    }
    // If the char is not a key on the keyboard then return the max length.
    return MAX_POINT_TO_KEY_LENGTH;
}

float ProximityInfoState::getPointToKeyByIdLength(const int inputIndex, const int keyId) const {
    if (keyId != NOT_AN_INDEX) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mDistanceCache[index], mMaxPointToKeyLength);
    }
    // If the char is not a key on the keyboard then return the max length.
    return static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
}

// In the following function, c is the current character of the dictionary word currently examined.
// currentChars is an array containing the keys close to the character the user actually typed at
// the same position. We want to see if c is in it: if so, then the word contains at that position
// a character close to what the user typed.
// What the user typed is actually the first character of the array.
// proximityIndex is a pointer to the variable where getMatchedProximityId returns the index of c
// in the proximity chars of the input index.
// Notice : accented characters do not have a proximity list, so they are alone in their list. The
// non-accented version of the character should be considered "close", but not the other keys close
// to the non-accented version.
ProximityType ProximityInfoState::getMatchedProximityId(const int index, const int c,
        const bool checkProximityChars, int *proximityIndex) const {
    const int *currentCodePoints = getProximityCodePointsAt(index);
    const int firstCodePoint = currentCodePoints[0];
    const int baseLowerC = toBaseLowerCase(c);

    // The first char in the array is what user typed. If it matches right away, that means the
    // user typed that same char for this pos.
    if (firstCodePoint == baseLowerC || firstCodePoint == c) {
        return EQUIVALENT_CHAR;
    }

    if (!checkProximityChars) return UNRELATED_CHAR;

    // If the non-accented, lowercased version of that first character matches c, then we have a
    // non-accented version of the accented character the user typed. Treat it as a close char.
    if (toBaseLowerCase(firstCodePoint) == baseLowerC) {
        return NEAR_PROXIMITY_CHAR;
    }

    // Not an exact nor an accent-alike match: search the list of close keys
    int j = 1;
    while (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
            && currentCodePoints[j] > ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
        const bool matched = (currentCodePoints[j] == baseLowerC || currentCodePoints[j] == c);
        if (matched) {
            if (proximityIndex) {
                *proximityIndex = j;
            }
            return NEAR_PROXIMITY_CHAR;
        }
        ++j;
    }
    if (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
            && currentCodePoints[j] == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
        ++j;
        while (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
                && currentCodePoints[j] > ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
            const bool matched = (currentCodePoints[j] == baseLowerC || currentCodePoints[j] == c);
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
    const int keyCount = mProximityInfo->getKeyCount();
    for (int j = 0; j < keyCount; ++j) {
        if (mSearchKeysVector[index].test(j)) {
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

bool ProximityInfoState::isKeyInSerchKeysAfterIndex(const int index, const int keyId) const {
    ASSERT(keyId >= 0);
    ASSERT(index >= 0 && index < mInputSize);
    return mSearchKeysVector[index].test(keyId);
}

void ProximityInfoState::popInputData() {
    mInputXs.pop_back();
    mInputYs.pop_back();
    mTimes.pop_back();
    mLengthCache.pop_back();
    mInputIndice.pop_back();
}

float ProximityInfoState::getDirection(const int index0, const int index1) const {
    if (index0 < 0 || index0 > mInputSize - 1) {
        return 0.0f;
    }
    if (index1 < 0 || index1 > mInputSize - 1) {
        return 0.0f;
    }
    const int x1 = mInputXs[index0];
    const int y1 = mInputYs[index0];
    const int x2 = mInputXs[index1];
    const int y2 = mInputYs[index1];
    return getAngle(x1, y1, x2, y2);
}

float ProximityInfoState::getPointAngle(const int index) const {
    if (index <= 0 || index >= mInputSize - 1) {
        return 0.0f;
    }
    const float previousDirection = getDirection(index - 1, index);
    const float nextDirection = getDirection(index, index + 1);
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
    const float previousDirection = getDirection(index0, index1);
    const float nextDirection = getDirection(index1, index2);
    return getAngleDiff(previousDirection, nextDirection);
}

float ProximityInfoState::getLineToKeyDistance(
        const int from, const int to, const int keyId, const bool extend) const {
    if (from < 0 || from > mInputSize - 1) {
        return 0.0f;
    }
    if (to < 0 || to > mInputSize - 1) {
        return 0.0f;
    }
    const int x0 = mInputXs[from];
    const int y0 = mInputYs[from];
    const int x1 = mInputXs[to];
    const int y1 = mInputYs[to];

    const int keyX = mProximityInfo->getKeyCenterXOfKeyIdG(keyId);
    const int keyY = mProximityInfo->getKeyCenterYOfKeyIdG(keyId);

    return pointToLineSegSquaredDistanceFloat(keyX, keyY, x0, y0, x1, y1, extend);
}

// Updates probabilities of aligning to some keys and skipping.
// Word suggestion should be based on this probabilities.
void ProximityInfoState::updateAlignPointProbabilities(const int start) {
    static const float MIN_PROBABILITY = 0.000001f;
    static const float MAX_SKIP_PROBABILITY = 0.95f;
    static const float SKIP_FIRST_POINT_PROBABILITY = 0.01f;
    static const float SKIP_LAST_POINT_PROBABILITY = 0.1f;
    static const float MIN_SPEED_RATE_FOR_SKIP_PROBABILITY = 0.15f;
    static const float SPEED_WEIGHT_FOR_SKIP_PROBABILITY = 0.9f;
    static const float SLOW_STRAIGHT_WEIGHT_FOR_SKIP_PROBABILITY = 0.6f;
    static const float NEAREST_DISTANCE_WEIGHT = 0.5f;
    static const float NEAREST_DISTANCE_BIAS = 0.5f;
    static const float NEAREST_DISTANCE_WEIGHT_FOR_LAST = 0.6f;
    static const float NEAREST_DISTANCE_BIAS_FOR_LAST = 0.4f;

    static const float ANGLE_WEIGHT = 0.90f;
    static const float DEEP_CORNER_ANGLE_THRESHOLD = M_PI_F * 60.0f / 180.0f;
    static const float SKIP_DEEP_CORNER_PROBABILITY = 0.1f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F * 30.0f / 180.0f;
    static const float STRAIGHT_ANGLE_THRESHOLD = M_PI_F * 15.0f / 180.0f;
    static const float SKIP_CORNER_PROBABILITY = 0.4f;
    static const float SPEED_MARGIN = 0.1f;
    static const float CENTER_VALUE_OF_NORMALIZED_DISTRIBUTION = 0.0f;

    const int keyCount = mProximityInfo->getKeyCount();
    mCharProbabilities.resize(mInputSize);
    // Calculates probabilities of using a point as a correlated point with the character
    // for each point.
    for (int i = start; i < mInputSize; ++i) {
        mCharProbabilities[i].clear();
        // First, calculates skip probability. Starts form MIN_SKIP_PROBABILITY.
        // Note that all values that are multiplied to this probability should be in [0.0, 1.0];
        float skipProbability = MAX_SKIP_PROBABILITY;

        const float currentAngle = getPointAngle(i);
        const float relativeSpeed = getRelativeSpeed(i);

        float nearestKeyDistance = static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
        for (int j = 0; j < keyCount; ++j) {
            if (mNearKeysVector[i].test(j)) {
                const float distance = getPointToKeyByIdLength(i, j);
                if (distance < nearestKeyDistance) {
                    nearestKeyDistance = distance;
                }
            }
        }

        if (i == 0) {
            skipProbability *= min(1.0f, nearestKeyDistance * NEAREST_DISTANCE_WEIGHT
                    + NEAREST_DISTANCE_BIAS);
            // Promote the first point
            skipProbability *= SKIP_FIRST_POINT_PROBABILITY;
        } else if (i == mInputSize - 1) {
            skipProbability *= min(1.0f, nearestKeyDistance * NEAREST_DISTANCE_WEIGHT_FOR_LAST
                    + NEAREST_DISTANCE_BIAS_FOR_LAST);
            // Promote the last point
            skipProbability *= SKIP_LAST_POINT_PROBABILITY;
        } else {
            // If the current speed is relatively slower than adjacent keys, we promote this point.
            if (getRelativeSpeed(i - 1) - SPEED_MARGIN > relativeSpeed
                    && relativeSpeed < getRelativeSpeed(i + 1) - SPEED_MARGIN) {
                if (currentAngle < CORNER_ANGLE_THRESHOLD) {
                    skipProbability *= min(1.0f, relativeSpeed
                            * SLOW_STRAIGHT_WEIGHT_FOR_SKIP_PROBABILITY);
                } else {
                    // If the angle is small enough, we promote this point more. (e.g. pit vs put)
                    skipProbability *= min(1.0f, relativeSpeed * SPEED_WEIGHT_FOR_SKIP_PROBABILITY
                            + MIN_SPEED_RATE_FOR_SKIP_PROBABILITY);
                }
            }

            skipProbability *= min(1.0f, relativeSpeed * nearestKeyDistance *
                    NEAREST_DISTANCE_WEIGHT + NEAREST_DISTANCE_BIAS);

            // Adjusts skip probability by a rate depending on angle.
            // ANGLE_RATE of skipProbability is adjusted by current angle.
            skipProbability *= (M_PI_F - currentAngle) / M_PI_F * ANGLE_WEIGHT
                    + (1.0f - ANGLE_WEIGHT);
            if (currentAngle > DEEP_CORNER_ANGLE_THRESHOLD) {
                skipProbability *= SKIP_DEEP_CORNER_PROBABILITY;
            }
            // We assume the angle of this point is the angle for point[i], point[i - 2]
            // and point[i - 3]. The reason why we don't use the angle for point[i], point[i - 1]
            // and point[i - 2] is this angle can be more affected by the noise.
            const float prevAngle = getPointsAngle(i, i - 2, i - 3);
            if (i >= 3 && prevAngle < STRAIGHT_ANGLE_THRESHOLD
                    && currentAngle > CORNER_ANGLE_THRESHOLD) {
                skipProbability *= SKIP_CORNER_PROBABILITY;
            }
        }

        // probabilities must be in [0.0, MAX_SKIP_PROBABILITY];
        ASSERT(skipProbability >= 0.0f);
        ASSERT(skipProbability <= MAX_SKIP_PROBABILITY);
        mCharProbabilities[i][NOT_AN_INDEX] = skipProbability;

        // Second, calculates key probabilities by dividing the rest probability
        // (1.0f - skipProbability).
        const float inputCharProbability = 1.0f - skipProbability;

        // TODO: The variance is critical for accuracy; thus, adjusting these parameter by machine
        // learning or something would be efficient.
        static const float SPEEDxANGLE_WEIGHT_FOR_STANDARD_DIVIATION = 0.3f;
        static const float MAX_SPEEDxANGLE_RATE_FOR_STANDERD_DIVIATION = 0.25f;
        static const float SPEEDxNEAREST_WEIGHT_FOR_STANDARD_DIVIATION = 0.5f;
        static const float MAX_SPEEDxNEAREST_RATE_FOR_STANDERD_DIVIATION = 0.15f;
        static const float MIN_STANDERD_DIVIATION = 0.37f;

        const float speedxAngleRate = min(relativeSpeed * currentAngle / M_PI_F
                * SPEEDxANGLE_WEIGHT_FOR_STANDARD_DIVIATION,
                        MAX_SPEEDxANGLE_RATE_FOR_STANDERD_DIVIATION);
        const float speedxNearestKeyDistanceRate = min(relativeSpeed * nearestKeyDistance
                * SPEEDxNEAREST_WEIGHT_FOR_STANDARD_DIVIATION,
                        MAX_SPEEDxNEAREST_RATE_FOR_STANDERD_DIVIATION);
        const float sigma = speedxAngleRate + speedxNearestKeyDistanceRate + MIN_STANDERD_DIVIATION;

        NormalDistribution distribution(CENTER_VALUE_OF_NORMALIZED_DISTRIBUTION, sigma);
        static const float PREV_DISTANCE_WEIGHT = 0.5f;
        static const float NEXT_DISTANCE_WEIGHT = 0.6f;
        // Summing up probability densities of all near keys.
        float sumOfProbabilityDensities = 0.0f;
        for (int j = 0; j < keyCount; ++j) {
            if (mNearKeysVector[i].test(j)) {
                float distance = sqrtf(getPointToKeyByIdLength(i, j));
                if (i == 0 && i != mInputSize - 1) {
                    // For the first point, weighted average of distances from first point and the
                    // next point to the key is used as a point to key distance.
                    const float nextDistance = sqrtf(getPointToKeyByIdLength(i + 1, j));
                    if (nextDistance < distance) {
                        // The distance of the first point tends to bigger than continuing
                        // points because the first touch by the user can be sloppy.
                        // So we promote the first point if the distance of that point is larger
                        // than the distance of the next point.
                        distance = (distance + nextDistance * NEXT_DISTANCE_WEIGHT)
                                / (1.0f + NEXT_DISTANCE_WEIGHT);
                    }
                } else if (i != 0 && i == mInputSize - 1) {
                    // For the first point, weighted average of distances from last point and
                    // the previous point to the key is used as a point to key distance.
                    const float previousDistance = sqrtf(getPointToKeyByIdLength(i - 1, j));
                    if (previousDistance < distance) {
                        // The distance of the last point tends to bigger than continuing points
                        // because the last touch by the user can be sloppy. So we promote the
                        // last point if the distance of that point is larger than the distance of
                        // the previous point.
                        distance = (distance + previousDistance * PREV_DISTANCE_WEIGHT)
                                / (1.0f + PREV_DISTANCE_WEIGHT);
                    }
                }
                // TODO: Promote the first point when the extended line from the next input is near
                // from a key. Also, promote the last point as well.
                sumOfProbabilityDensities += distribution.getProbabilityDensity(distance);
            }
        }

        // Split the probability of an input point to keys that are close to the input point.
        for (int j = 0; j < keyCount; ++j) {
            if (mNearKeysVector[i].test(j)) {
                float distance = sqrtf(getPointToKeyByIdLength(i, j));
                if (i == 0 && i != mInputSize - 1) {
                    // For the first point, weighted average of distances from the first point and
                    // the next point to the key is used as a point to key distance.
                    const float prevDistance = sqrtf(getPointToKeyByIdLength(i + 1, j));
                    if (prevDistance < distance) {
                        distance = (distance + prevDistance * NEXT_DISTANCE_WEIGHT)
                                / (1.0f + NEXT_DISTANCE_WEIGHT);
                    }
                } else if (i != 0 && i == mInputSize - 1) {
                    // For the first point, weighted average of distances from last point and
                    // the previous point to the key is used as a point to key distance.
                    const float prevDistance = sqrtf(getPointToKeyByIdLength(i - 1, j));
                    if (prevDistance < distance) {
                        distance = (distance + prevDistance * PREV_DISTANCE_WEIGHT)
                                / (1.0f + PREV_DISTANCE_WEIGHT);
                    }
                }
                const float probabilityDensity = distribution.getProbabilityDensity(distance);
                const float probability = inputCharProbability * probabilityDensity
                        / sumOfProbabilityDensities;
                mCharProbabilities[i][j] = probability;
            }
        }
    }


    if (DEBUG_POINTS_PROBABILITY) {
        for (int i = 0; i < mInputSize; ++i) {
            std::stringstream sstream;
            sstream << i << ", ";
            sstream << "("<< mInputXs[i] << ", ";
            sstream << ", "<< mInputYs[i] << "), ";
            sstream << "Speed: "<< getRelativeSpeed(i) << ", ";
            sstream << "Angle: "<< getPointAngle(i) << ", \n";

            for (hash_map_compat<int, float>::iterator it = mCharProbabilities[i].begin();
                    it != mCharProbabilities[i].end(); ++it) {
                if (it->first == NOT_AN_INDEX) {
                    sstream << it->first
                            << "(skip):"
                            << it->second
                            << "\n";
                } else {
                    sstream << it->first
                            << "("
                            << static_cast<char>(mProximityInfo->getCodePointOf(it->first))
                            << "):"
                            << it->second
                            << "\n";
                }
            }
            AKLOGI("%s", sstream.str().c_str());
        }
    }

    // Decrease key probabilities of points which don't have the highest probability of that key
    // among nearby points. Probabilities of the first point and the last point are not suppressed.
    for (int i = max(start, 1); i < mInputSize; ++i) {
        for (int j = i + 1; j < mInputSize; ++j) {
            if (!suppressCharProbabilities(i, j)) {
                break;
            }
        }
        for (int j = i - 1; j >= max(start, 0); --j) {
            if (!suppressCharProbabilities(i, j)) {
                break;
            }
        }
    }

    // Converting from raw probabilities to log probabilities to calculate spatial distance.
    for (int i = start; i < mInputSize; ++i) {
        for (int j = 0; j < keyCount; ++j) {
            hash_map_compat<int, float>::iterator it = mCharProbabilities[i].find(j);
            if (it == mCharProbabilities[i].end()){
                mNearKeysVector[i].reset(j);
            } else if(it->second < MIN_PROBABILITY) {
                // Erases from near keys vector because it has very low probability.
                mNearKeysVector[i].reset(j);
                mCharProbabilities[i].erase(j);
            } else {
                it->second = -logf(it->second);
            }
        }
        mCharProbabilities[i][NOT_AN_INDEX] = -logf(mCharProbabilities[i][NOT_AN_INDEX]);
    }
}

// Decreases char probabilities of index0 by checking probabilities of a near point (index1) and
// increases char probabilities of index1 by checking probabilities of index0.
bool ProximityInfoState::suppressCharProbabilities(const int index0, const int index1) {
    ASSERT(0 <= index0 && index0 < mInputSize);
    ASSERT(0 <= index1 && index1 < mInputSize);

    static const float SUPPRESSION_LENGTH_WEIGHT = 1.5f;
    static const float MIN_SUPPRESSION_RATE = 0.1f;
    static const float SUPPRESSION_WEIGHT = 0.5f;
    static const float SUPPRESSION_WEIGHT_FOR_PROBABILITY_GAIN = 0.1f;
    static const float SKIP_PROBABALITY_WEIGHT_FOR_PROBABILITY_GAIN = 0.3f;

    const float keyWidthFloat = static_cast<float>(mProximityInfo->getMostCommonKeyWidth());
    const float diff = fabsf(static_cast<float>(mLengthCache[index0] - mLengthCache[index1]));
    if (diff > keyWidthFloat * SUPPRESSION_LENGTH_WEIGHT) {
        return false;
    }
    const float suppressionRate = MIN_SUPPRESSION_RATE
            + diff / keyWidthFloat / SUPPRESSION_LENGTH_WEIGHT * SUPPRESSION_WEIGHT;
    for (hash_map_compat<int, float>::iterator it = mCharProbabilities[index0].begin();
            it != mCharProbabilities[index0].end(); ++it) {
        hash_map_compat<int, float>::iterator it2 =  mCharProbabilities[index1].find(it->first);
        if (it2 != mCharProbabilities[index1].end() && it->second < it2->second) {
            const float newProbability = it->second * suppressionRate;
            const float suppression = it->second - newProbability;
            it->second = newProbability;
            // mCharProbabilities[index0][NOT_AN_INDEX] is the probability of skipping this point.
            mCharProbabilities[index0][NOT_AN_INDEX] += suppression;

            // Add the probability of the same key nearby index1
            const float probabilityGain = min(suppression * SUPPRESSION_WEIGHT_FOR_PROBABILITY_GAIN,
                    mCharProbabilities[index1][NOT_AN_INDEX]
                            * SKIP_PROBABALITY_WEIGHT_FOR_PROBABILITY_GAIN);
            it2->second += probabilityGain;
            mCharProbabilities[index1][NOT_AN_INDEX] -= probabilityGain;
        }
    }
    return true;
}

// Get a word that is detected by tracing highest probability sequence into codePointBuf and
// returns probability of generating the word.
float ProximityInfoState::getHighestProbabilitySequence(int *const codePointBuf) const {
    static const float DEMOTION_LOG_PROBABILITY = 0.3f;
    int index = 0;
    float sumLogProbability = 0.0f;
    // TODO: Current implementation is greedy algorithm. DP would be efficient for many cases.
    for (int i = 0; i < mInputSize && index < MAX_WORD_LENGTH_INTERNAL - 1; ++i) {
        float minLogProbability = static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
        int character = NOT_AN_INDEX;
        for (hash_map_compat<int, float>::const_iterator it = mCharProbabilities[i].begin();
                it != mCharProbabilities[i].end(); ++it) {
            const float logProbability = (it->first != NOT_AN_INDEX)
                    ? it->second + DEMOTION_LOG_PROBABILITY : it->second;
            if (logProbability < minLogProbability) {
                minLogProbability = logProbability;
                character = it->first;
            }
        }
        if (character != NOT_AN_INDEX) {
            codePointBuf[index] = mProximityInfo->getCodePointOf(character);
            index++;
        }
        sumLogProbability += minLogProbability;
    }
    codePointBuf[index] = '\0';
    return sumLogProbability;
}

// Returns a probability of mapping index to keyIndex.
float ProximityInfoState::getProbability(const int index, const int keyIndex) const {
    ASSERT(0 <= index && index < mInputSize);
    hash_map_compat<int, float>::const_iterator it = mCharProbabilities[index].find(keyIndex);
    if (it != mCharProbabilities[index].end()) {
        return it->second;
    }
    return static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
}

} // namespace latinime
