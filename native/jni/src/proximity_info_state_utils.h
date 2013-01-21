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

#ifndef LATINIME_PROXIMITY_INFO_STATE_UTILS_H
#define LATINIME_PROXIMITY_INFO_STATE_UTILS_H

#include <vector>

#include "defines.h"
#include "geometry_utils.h"
#include "hash_map_compat.h"
#include "proximity_info.h"

namespace latinime {
class ProximityInfoStateUtils {
 public:
    static int updateTouchPoints(const int mostCommonKeyWidth,
            const ProximityInfo *const proximityInfo, const int maxPointToKeyLength,
            const int *const inputProximities,
            const int *const inputXCoordinates, const int *const inputYCoordinates,
            const int *const times, const int *const pointerIds, const int inputSize,
            const bool isGeometric, const int pointerId, const int pushTouchPointStartIndex,
            std::vector<int> *sampledInputXs, std::vector<int> *sampledInputYs,
            std::vector<int> *sampledInputTimes, std::vector<int> *sampledLengthCache,
            std::vector<int> *sampledInputIndice) {
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
                        i, c, x, y, time, isGeometric /* do sampling */,
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

    static const int *getProximityCodePointsAt(
            const int *const inputProximities, const int index) {
        return inputProximities + (index * MAX_PROXIMITY_CHARS_SIZE_INTERNAL);
    }

    static int getPrimaryCodePointAt(const int *const inputProximities, const int index) {
        return getProximityCodePointsAt(inputProximities, index)[0];
    }

    static void popInputData(std::vector<int> *sampledInputXs, std::vector<int> *sampledInputYs,
            std::vector<int> *sampledInputTimes, std::vector<int> *sampledLengthCache,
            std::vector<int> *sampledInputIndice) {
        sampledInputXs->pop_back();
        sampledInputYs->pop_back();
        sampledInputTimes->pop_back();
        sampledLengthCache->pop_back();
        sampledInputIndice->pop_back();
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProximityInfoStateUtils);

    typedef hash_map_compat<int, float> NearKeysDistanceMap;

    // Calculating point to key distance for all near keys and returning the distance between
    // the given point and the nearest key position.
    static float updateNearKeysDistances(const ProximityInfo *const proximityInfo,
            const float maxPointToKeyLength, const int x, const int y,
            NearKeysDistanceMap *const currentNearKeysDistances) {
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
    static bool isPrevLocalMin(const NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances) {
        static const float MARGIN = 0.01f;

        for (NearKeysDistanceMap::const_iterator it = prevNearKeysDistances->begin();
            it != prevNearKeysDistances->end(); ++it) {
            NearKeysDistanceMap::const_iterator itPP = prevPrevNearKeysDistances->find(it->first);
            NearKeysDistanceMap::const_iterator itC = currentNearKeysDistances->find(it->first);
            if ((itPP == prevPrevNearKeysDistances->end() || itPP->second > it->second + MARGIN)
                    && (itC == currentNearKeysDistances->end()
                            || itC->second > it->second + MARGIN)) {
                return true;
            }
        }
        return false;
    }

    // Calculating a point score that indicates usefulness of the point.
    static float getPointScore(const int mostCommonKeyWidth,
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
        const int distPrev = getDistanceInt(
                sampledInputXs->back(), sampledInputYs->back(),
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
    static bool pushTouchPoint(const int mostCommonKeyWidth,
            const ProximityInfo *const proximityInfo, const int maxPointToKeyLength,
            const int inputIndex, const int nodeCodePoint, int x, int y,
            const int time, const bool sample, const bool isLastPoint, const float sumAngle,
            NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances,
            std::vector<int> *sampledInputXs, std::vector<int> *sampledInputYs,
            std::vector<int> *sampledInputTimes, std::vector<int> *sampledLengthCache,
            std::vector<int> *sampledInputIndice) {
        static const int LAST_POINT_SKIP_DISTANCE_SCALE = 4;

        size_t size = sampledInputXs->size();
        bool popped = false;
        if (nodeCodePoint < 0 && sample) {
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
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_STATE_UTILS_H
