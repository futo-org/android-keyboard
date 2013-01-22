/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <vector>

#include "defines.h"
#include "geometry_utils.h"
#include "proximity_info.h"
#include "proximity_info_params.h"
#include "proximity_info_state_utils.h"

namespace latinime {
/* static */ int ProximityInfoStateUtils::updateTouchPoints(const int mostCommonKeyWidth,
        const ProximityInfo *const proximityInfo, const int maxPointToKeyLength,
        const int *const inputProximities, const int *const inputXCoordinates,
        const int *const inputYCoordinates, const int *const times, const int *const pointerIds,
        const int inputSize, const bool isGeometric, const int pointerId,
        const int pushTouchPointStartIndex, std::vector<int> *sampledInputXs,
        std::vector<int> *sampledInputYs, std::vector<int> *sampledInputTimes,
        std::vector<int> *sampledLengthCache, std::vector<int> *sampledInputIndice) {
    if (DEBUG_SAMPLING_POINTS) {
        if (times) {
            for (int i = 0; i < inputSize; ++i) {
                AKLOGI("(%d) x %d, y %d, time %d",
                        i, xCoordinates[i], yCoordinates[i], times[i]);
            }
        }
    }
#ifdef DO_ASSERT_TEST
    if (times) {
        for (int i = 0; i < inputSize; ++i) {
            if (i > 0) {
                ASSERT(times[i] >= times[i - 1]);
            }
        }
    }
#endif
    const bool proximityOnly = !isGeometric
            && (inputXCoordinates[0] < 0 || inputYCoordinates[0] < 0);
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
            const int c = isGeometric ?
                    NOT_A_COORDINATE : getPrimaryCodePointAt(inputProximities, i);
            const int x = proximityOnly ? NOT_A_COORDINATE : inputXCoordinates[i];
            const int y = proximityOnly ? NOT_A_COORDINATE : inputYCoordinates[i];
            const int time = times ? times[i] : -1;

            if (i > 1) {
                const float prevAngle = getAngle(
                        inputXCoordinates[i - 2], inputYCoordinates[i - 2],
                        inputXCoordinates[i - 1], inputYCoordinates[i - 1]);
                const float currentAngle =
                        getAngle(inputXCoordinates[i - 1], inputYCoordinates[i - 1], x, y);
                sumAngle += getAngleDiff(prevAngle, currentAngle);
            }

            if (pushTouchPoint(mostCommonKeyWidth, proximityInfo, maxPointToKeyLength,
                    i, c, x, y, time, isGeometric /* doSampling */,
                    i == lastInputIndex, sumAngle, currentNearKeysDistances,
                    prevNearKeysDistances, prevPrevNearKeysDistances,
                    sampledInputXs, sampledInputYs, sampledInputTimes, sampledLengthCache,
                    sampledInputIndice)) {
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
    return sampledInputXs->size();
}

/* static */ const int *ProximityInfoStateUtils::getProximityCodePointsAt(
        const int *const inputProximities, const int index) {
    return inputProximities + (index * MAX_PROXIMITY_CHARS_SIZE);
}

/* static */ int ProximityInfoStateUtils::getPrimaryCodePointAt(
        const int *const inputProximities, const int index) {
    return getProximityCodePointsAt(inputProximities, index)[0];
}

/* static */ void ProximityInfoStateUtils::popInputData(std::vector<int> *sampledInputXs,
        std::vector<int> *sampledInputYs, std::vector<int> *sampledInputTimes,
        std::vector<int> *sampledLengthCache, std::vector<int> *sampledInputIndice) {
    sampledInputXs->pop_back();
    sampledInputYs->pop_back();
    sampledInputTimes->pop_back();
    sampledLengthCache->pop_back();
    sampledInputIndice->pop_back();
}

/* static */ float ProximityInfoStateUtils::refreshSpeedRates(const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times,
        const int lastSavedInputSize, const int sampledInputSize,
        const std::vector<int> *const sampledInputXs,
        const std::vector<int> *const sampledInputYs,
        const std::vector<int> *const sampledInputTimes,
        const std::vector<int> *const sampledLengthCache,
        const std::vector<int> *const sampledInputIndice, std::vector<float> *sampledSpeedRates,
        std::vector<float> *sampledDirections) {
    // Relative speed calculation.
    const int sumDuration = sampledInputTimes->back() - sampledInputTimes->front();
    const int sumLength = sampledLengthCache->back() - sampledLengthCache->front();
    const float averageSpeed = static_cast<float>(sumLength) / static_cast<float>(sumDuration);
    sampledSpeedRates->resize(sampledInputSize);
    for (int i = lastSavedInputSize; i < sampledInputSize; ++i) {
        const int index = (*sampledInputIndice)[i];
        int length = 0;
        int duration = 0;

        // Calculate velocity by using distances and durations of
        // NUM_POINTS_FOR_SPEED_CALCULATION points for both forward and backward.
        static const int NUM_POINTS_FOR_SPEED_CALCULATION = 2;
        for (int j = index; j < min(inputSize - 1, index + NUM_POINTS_FOR_SPEED_CALCULATION);
                ++j) {
            if (i < sampledInputSize - 1 && j >= (*sampledInputIndice)[i + 1]) {
                break;
            }
            length += getDistanceInt(xCoordinates[j], yCoordinates[j],
                    xCoordinates[j + 1], yCoordinates[j + 1]);
            duration += times[j + 1] - times[j];
        }
        for (int j = index - 1; j >= max(0, index - NUM_POINTS_FOR_SPEED_CALCULATION); --j) {
            if (i > 0 && j < (*sampledInputIndice)[i - 1]) {
                break;
            }
            // TODO: use mLengthCache instead?
            length += getDistanceInt(xCoordinates[j], yCoordinates[j],
                    xCoordinates[j + 1], yCoordinates[j + 1]);
            duration += times[j + 1] - times[j];
        }
        if (duration == 0 || sumDuration == 0) {
            // Cannot calculate speed; thus, it gives an average value (1.0);
            (*sampledSpeedRates)[i] = 1.0f;
        } else {
            const float speed = static_cast<float>(length) / static_cast<float>(duration);
            (*sampledSpeedRates)[i] = speed / averageSpeed;
        }
    }

    // Direction calculation.
    sampledDirections->resize(sampledInputSize - 1);
    for (int i = max(0, lastSavedInputSize - 1); i < sampledInputSize - 1; ++i) {
        (*sampledDirections)[i] = getDirection(sampledInputXs, sampledInputYs, i, i + 1);
    }
    return averageSpeed;
}

/* static */ void ProximityInfoStateUtils::refreshBeelineSpeedRates(const int mostCommonKeyWidth,
        const float averageSpeed, const int inputSize, const int *const xCoordinates,
        const int *const yCoordinates, const int *times, const int sampledInputSize,
        const std::vector<int> *const sampledInputXs,
        const std::vector<int> *const sampledInputYs, const std::vector<int> *const inputIndice,
        std::vector<int> *beelineSpeedPercentiles) {
    if (DEBUG_SAMPLING_POINTS) {
        AKLOGI("--- refresh beeline speed rates");
    }
    beelineSpeedPercentiles->resize(sampledInputSize);
    for (int i = 0; i < sampledInputSize; ++i) {
        (*beelineSpeedPercentiles)[i] = static_cast<int>(calculateBeelineSpeedRate(
                mostCommonKeyWidth, averageSpeed, i, inputSize, xCoordinates, yCoordinates, times,
                sampledInputSize, sampledInputXs, sampledInputYs, inputIndice) * MAX_PERCENTILE);
    }
}

/* static */float ProximityInfoStateUtils::getDirection(
        const std::vector<int> *const sampledInputXs,
        const std::vector<int> *const sampledInputYs, const int index0, const int index1) {
    ASSERT(sampledInputXs && sampledInputYs);
    const int sampledInputSize =sampledInputXs->size();
    if (index0 < 0 || index0 > sampledInputSize - 1) {
        return 0.0f;
    }
    if (index1 < 0 || index1 > sampledInputSize - 1) {
        return 0.0f;
    }
    const int x1 = (*sampledInputXs)[index0];
    const int y1 = (*sampledInputYs)[index0];
    const int x2 = (*sampledInputXs)[index1];
    const int y2 = (*sampledInputYs)[index1];
    return getAngle(x1, y1, x2, y2);
}

// Calculating point to key distance for all near keys and returning the distance between
// the given point and the nearest key position.
/* static */ float ProximityInfoStateUtils::updateNearKeysDistances(
        const ProximityInfo *const proximityInfo, const float maxPointToKeyLength, const int x,
        const int y, NearKeysDistanceMap *const currentNearKeysDistances) {
    static const float NEAR_KEY_THRESHOLD = 2.0f;

    currentNearKeysDistances->clear();
    const int keyCount = proximityInfo->getKeyCount();
    float nearestKeyDistance = maxPointToKeyLength;
    for (int k = 0; k < keyCount; ++k) {
        const float dist = proximityInfo->getNormalizedSquaredDistanceFromCenterFloatG(k, x, y);
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
/* static */ bool ProximityInfoStateUtils::isPrevLocalMin(
        const NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances) {
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
/* static */ float ProximityInfoStateUtils::getPointScore(const int mostCommonKeyWidth,
        const int x, const int y, const int time, const bool lastPoint, const float nearest,
        const float sumAngle, const NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances,
        std::vector<int> *sampledInputXs, std::vector<int> *sampledInputYs) {
    static const int DISTANCE_BASE_SCALE = 100;
    static const float NEAR_KEY_THRESHOLD = 0.6f;
    static const int CORNER_CHECK_DISTANCE_THRESHOLD_SCALE = 25;
    static const float NOT_LOCALMIN_DISTANCE_SCORE = -1.0f;
    static const float LOCALMIN_DISTANCE_AND_NEAR_TO_KEY_SCORE = 1.0f;
    static const float CORNER_ANGLE_THRESHOLD = M_PI_F * 2.0f / 3.0f;
    static const float CORNER_SUM_ANGLE_THRESHOLD = M_PI_F / 4.0f;
    static const float CORNER_SCORE = 1.0f;

    const size_t size = sampledInputXs->size();
    // If there is only one point, add this point. Besides, if the previous point's distance map
    // is empty, we re-compute nearby keys distances from the current point.
    // Note that the current point is the first point in the incremental input that needs to
    // be re-computed.
    if (size <= 1 || prevNearKeysDistances->empty()) {
        return 0.0f;
    }

    const int baseSampleRate = mostCommonKeyWidth;
    const int distPrev = getDistanceInt(sampledInputXs->back(), sampledInputYs->back(),
            (*sampledInputXs)[size - 2], (*sampledInputYs)[size - 2]) * DISTANCE_BASE_SCALE;
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
    const float angle1 = getAngle(x, y, sampledInputXs->back(), sampledInputYs->back());
    const float angle2 = getAngle(sampledInputXs->back(), sampledInputYs->back(),
            (*sampledInputXs)[size - 2], (*sampledInputYs)[size - 2]);
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
/* static */ bool ProximityInfoStateUtils::pushTouchPoint(const int mostCommonKeyWidth,
        const ProximityInfo *const proximityInfo, const int maxPointToKeyLength,
        const int inputIndex, const int nodeCodePoint, int x, int y,
        const int time, const bool doSampling, const bool isLastPoint, const float sumAngle,
        NearKeysDistanceMap *const currentNearKeysDistances,
        const NearKeysDistanceMap *const prevNearKeysDistances,
        const NearKeysDistanceMap *const prevPrevNearKeysDistances,
        std::vector<int> *sampledInputXs, std::vector<int> *sampledInputYs,
        std::vector<int> *sampledInputTimes, std::vector<int> *sampledLengthCache,
        std::vector<int> *sampledInputIndice) {
    static const int LAST_POINT_SKIP_DISTANCE_SCALE = 4;

    size_t size = sampledInputXs->size();
    bool popped = false;
    if (nodeCodePoint < 0 && doSampling) {
        const float nearest = updateNearKeysDistances(
                proximityInfo, maxPointToKeyLength, x, y, currentNearKeysDistances);
        const float score = getPointScore(mostCommonKeyWidth, x, y, time, isLastPoint, nearest,
                sumAngle, currentNearKeysDistances, prevNearKeysDistances,
                prevPrevNearKeysDistances, sampledInputXs, sampledInputYs);
        if (score < 0) {
            // Pop previous point because it would be useless.
            popInputData(sampledInputXs, sampledInputYs, sampledInputTimes, sampledLengthCache,
                    sampledInputIndice);
            size = sampledInputXs->size();
            popped = true;
        } else {
            popped = false;
        }
        // Check if the last point should be skipped.
        if (isLastPoint && size > 0) {
            if (getDistanceInt(x, y, sampledInputXs->back(),
                    sampledInputYs->back()) * LAST_POINT_SKIP_DISTANCE_SCALE
                            < mostCommonKeyWidth) {
                // This point is not used because it's too close to the previous point.
                if (DEBUG_GEO_FULL) {
                    AKLOGI("p0: size = %zd, x = %d, y = %d, lx = %d, ly = %d, dist = %d, "
                           "width = %d", size, x, y, mSampledInputXs.back(),
                           mSampledInputYs.back(), ProximityInfoUtils::getDistanceInt(
                                   x, y, mSampledInputXs.back(), mSampledInputYs.back()),
                           mProximityInfo->getMostCommonKeyWidth()
                                   / LAST_POINT_SKIP_DISTANCE_SCALE);
                }
                return popped;
            }
        }
    }

    if (nodeCodePoint >= 0 && (x < 0 || y < 0)) {
        const int keyId = proximityInfo->getKeyIndexOf(nodeCodePoint);
        if (keyId >= 0) {
            x = proximityInfo->getKeyCenterXOfKeyIdG(keyId);
            y = proximityInfo->getKeyCenterYOfKeyIdG(keyId);
        }
    }

    // Pushing point information.
    if (size > 0) {
        sampledLengthCache->push_back(
                sampledLengthCache->back() + getDistanceInt(
                        x, y, sampledInputXs->back(), sampledInputYs->back()));
    } else {
        sampledLengthCache->push_back(0);
    }
    sampledInputXs->push_back(x);
    sampledInputYs->push_back(y);
    sampledInputTimes->push_back(time);
    sampledInputIndice->push_back(inputIndex);
    if (DEBUG_GEO_FULL) {
        AKLOGI("pushTouchPoint: x = %03d, y = %03d, time = %d, index = %d, popped ? %01d",
                x, y, time, inputIndex, popped);
    }
    return popped;
}

/* static */ float ProximityInfoStateUtils::calculateBeelineSpeedRate(const int mostCommonKeyWidth,
        const float averageSpeed, const int id, const int inputSize, const int *const xCoordinates,
        const int *const yCoordinates, const int *times, const int sampledInputSize,
        const std::vector<int> *const sampledInputXs,
        const std::vector<int> *const sampledInputYs, const std::vector<int> *const inputIndice) {
    if (sampledInputSize <= 0 || averageSpeed < 0.001f) {
        if (DEBUG_SAMPLING_POINTS) {
            AKLOGI("--- invalid state: cancel. size = %d, ave = %f",
                    mSampledInputSize, mAverageSpeed);
        }
        return 1.0f;
    }
    const int lookupRadius = mostCommonKeyWidth
            * ProximityInfoParams::LOOKUP_RADIUS_PERCENTILE / MAX_PERCENTILE;
    const int x0 = (*sampledInputXs)[id];
    const int y0 = (*sampledInputYs)[id];
    const int actualInputIndex = (*inputIndice)[id];
    int tempTime = 0;
    int tempBeelineDistance = 0;
    int start = actualInputIndex;
    // lookup forward
    while (start > 0 && tempBeelineDistance < lookupRadius) {
        tempTime += times[start] - times[start - 1];
        --start;
        tempBeelineDistance = getDistanceInt(x0, y0, xCoordinates[start], yCoordinates[start]);
    }
    // Exclusive unless this is an edge point
    if (start > 0 && start < actualInputIndex) {
        ++start;
    }
    tempTime= 0;
    tempBeelineDistance = 0;
    int end = actualInputIndex;
    // lookup backward
    while (end < (inputSize - 1) && tempBeelineDistance < lookupRadius) {
        tempTime += times[end + 1] - times[end];
        ++end;
        tempBeelineDistance = getDistanceInt(x0, y0, xCoordinates[end], yCoordinates[end]);
    }
    // Exclusive unless this is an edge point
    if (end > actualInputIndex && end < (inputSize - 1)) {
        --end;
    }

    if (start >= end) {
        if (DEBUG_DOUBLE_LETTER) {
            AKLOGI("--- double letter: start == end %d", start);
        }
        return 1.0f;
    }

    const int x2 = xCoordinates[start];
    const int y2 = yCoordinates[start];
    const int x3 = xCoordinates[end];
    const int y3 = yCoordinates[end];
    const int beelineDistance = getDistanceInt(x2, y2, x3, y3);
    int adjustedStartTime = times[start];
    if (start == 0 && actualInputIndex == 0 && inputSize > 1) {
        adjustedStartTime += ProximityInfoParams::FIRST_POINT_TIME_OFFSET_MILLIS;
    }
    int adjustedEndTime = times[end];
    if (end == (inputSize - 1) && inputSize > 1) {
        adjustedEndTime -= ProximityInfoParams::FIRST_POINT_TIME_OFFSET_MILLIS;
    }
    const int time = adjustedEndTime - adjustedStartTime;
    if (time <= 0) {
        return 1.0f;
    }

    if (time >= ProximityInfoParams::STRONG_DOUBLE_LETTER_TIME_MILLIS){
        return 0.0f;
    }
    if (DEBUG_DOUBLE_LETTER) {
        AKLOGI("--- (%d, %d) double letter: start = %d, end = %d, dist = %d, time = %d,"
                " speed = %f, ave = %f, val = %f, start time = %d, end time = %d",
                id, mInputIndice[id], start, end, beelineDistance, time,
                (static_cast<float>(beelineDistance) / static_cast<float>(time)), mAverageSpeed,
                ((static_cast<float>(beelineDistance) / static_cast<float>(time))
                        / mAverageSpeed), adjustedStartTime, adjustedEndTime);
    }
    // Offset 1%
    // TODO: Detect double letter more smartly
    return 0.01f + static_cast<float>(beelineDistance) / static_cast<float>(time) / averageSpeed;
}
} // namespace latinime
