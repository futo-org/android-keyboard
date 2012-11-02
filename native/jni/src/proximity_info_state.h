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

#include <bitset>
#include <cstring> // for memset()
#include <stdint.h>
#include <string>
#include <vector>

#include "char_utils.h"
#include "defines.h"
#include "hash_map_compat.h"

namespace latinime {

class ProximityInfo;

class ProximityInfoState {
 public:
    typedef std::bitset<MAX_KEY_COUNT_IN_A_KEYBOARD> NearKeycodesSet;
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
    static const int NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR;
    static const float NOT_A_DISTANCE_FLOAT;
    static const int NOT_A_CODE;

    /////////////////////////////////////////
    // Defined in proximity_info_state.cpp //
    /////////////////////////////////////////
    void initInputParams(const int pointerId, const float maxPointToKeyLength,
            const ProximityInfo *proximityInfo, const int *const inputCodes,
            const int inputSize, const int *xCoordinates, const int *yCoordinates,
            const int *const times, const int *const pointerIds, const bool isGeometric);

    /////////////////////////////////////////
    // Defined here                        //
    /////////////////////////////////////////
    ProximityInfoState()
            : mProximityInfo(0), mMaxPointToKeyLength(0),
              mHasTouchPositionCorrectionData(false), mMostCommonKeyWidthSquare(0), mLocaleStr(),
              mKeyCount(0), mCellHeight(0), mCellWidth(0), mGridHeight(0), mGridWidth(0),
              mIsContinuationPossible(false), mInputXs(), mInputYs(), mTimes(), mInputIndice(),
              mDistanceCache(), mLengthCache(), mRelativeSpeeds(), mDirections(),
              mCharProbabilities(), mNearKeysVector(), mSearchKeysVector(),
              mTouchPositionCorrectionEnabled(false), mInputSize(0) {
        memset(mInputCodes, 0, sizeof(mInputCodes));
        memset(mNormalizedSquaredDistances, 0, sizeof(mNormalizedSquaredDistances));
        memset(mPrimaryInputWord, 0, sizeof(mPrimaryInputWord));
    }

    virtual ~ProximityInfoState() {}

    inline int getPrimaryCodePointAt(const int index) const {
        return getProximityCodePointsAt(index)[0];
    }

    inline bool existsCodePointInProximityAt(const int index, const int c) const {
        const int *codePoints = getProximityCodePointsAt(index);
        int i = 0;
        while (codePoints[i] > 0 && i < MAX_PROXIMITY_CHARS_SIZE_INTERNAL) {
            if (codePoints[i++] == c) {
                return true;
            }
        }
        return false;
    }

    inline bool existsAdjacentProximityChars(const int index) const {
        if (index < 0 || index >= mInputSize) return false;
        const int currentCodePoint = getPrimaryCodePointAt(index);
        const int leftIndex = index - 1;
        if (leftIndex >= 0 && existsCodePointInProximityAt(leftIndex, currentCodePoint)) {
            return true;
        }
        const int rightIndex = index + 1;
        if (rightIndex < mInputSize && existsCodePointInProximityAt(rightIndex, currentCodePoint)) {
            return true;
        }
        return false;
    }

    inline int getNormalizedSquaredDistance(
            const int inputIndex, const int proximityIndex) const {
        return mNormalizedSquaredDistances[
                inputIndex * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + proximityIndex];
    }

    inline const int *getPrimaryInputWord() const {
        return mPrimaryInputWord;
    }

    inline bool touchPositionCorrectionEnabled() const {
        return mTouchPositionCorrectionEnabled;
    }

    inline bool sameAsTyped(const int *word, int length) const {
        if (length != mInputSize) {
            return false;
        }
        const int *inputCodes = mInputCodes;
        while (length--) {
            if (*inputCodes != *word) {
                return false;
            }
            inputCodes += MAX_PROXIMITY_CHARS_SIZE_INTERNAL;
            word++;
        }
        return true;
    }

    int getDuration(const int index) const;

    bool isUsed() const {
        return mInputSize > 0;
    }

    uint32_t size() const {
        return mInputSize;
    }

    int getInputX(const int index) const {
        return mInputXs[index];
    }

    int getInputY(const int index) const {
        return mInputYs[index];
    }

    int getLengthCache(const int index) const {
        return mLengthCache[index];
    }

    bool isContinuationPossible() const {
        return mIsContinuationPossible;
    }

    float getPointToKeyLength(const int inputIndex, const int charCode) const;
    float getPointToKeyByIdLength(const int inputIndex, const int keyId) const;

    ProximityType getMatchedProximityId(const int index, const int c,
            const bool checkProximityChars, int *proximityIndex = 0) const;

    int getSpaceY() const;

    int32_t getAllPossibleChars(
            const size_t startIndex, int32_t *const filter, const int32_t filterSize) const;

    float getRelativeSpeed(const int index) const {
        return mRelativeSpeeds[index];
    }

    float getDirection(const int index) const {
        return mDirections[index];
    }
    // get xy direction
    float getDirection(const int x, const int y) const;

    float getPointAngle(const int index) const;
    // Returns angle of three points. x, y, and z are indices.
    float getPointsAngle(const int index0, const int index1, const int index2) const;

    float getHighestProbabilitySequence(int *const codePointBuf) const;

    float getProbability(const int index, const int charCode) const;

    float getLineToKeyDistance(
            const int from, const int to, const int keyId, const bool extend) const;

    bool isKeyInSerchKeysAfterIndex(const int index, const int keyId) const;
 private:
    DISALLOW_COPY_AND_ASSIGN(ProximityInfoState);
    typedef hash_map_compat<int, float> NearKeysDistanceMap;
    /////////////////////////////////////////
    // Defined in proximity_info_state.cpp //
    /////////////////////////////////////////
    float calculateNormalizedSquaredDistance(const int keyIndex, const int inputIndex) const;

    float calculateSquaredDistanceFromSweetSpotCenter(
            const int keyIndex, const int inputIndex) const;

    bool pushTouchPoint(const int inputIndex, const int nodeCodePoint, int x, int y, const int time,
            const bool sample, const bool isLastPoint, const float sumAngle,
            NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances);
    /////////////////////////////////////////
    // Defined here                        //
    /////////////////////////////////////////
    inline float square(const float x) const { return x * x; }

    bool hasInputCoordinates() const {
        return mInputXs.size() > 0 && mInputYs.size() > 0;
    }

    inline const int *getProximityCodePointsAt(const int index) const {
        return mInputCodes + (index * MAX_PROXIMITY_CHARS_SIZE_INTERNAL);
    }

    float updateNearKeysDistances(const int x, const int y,
            NearKeysDistanceMap *const currentNearKeysDistances);
    bool isPrevLocalMin(const NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances) const;
    float getPointScore(
            const int x, const int y, const int time, const bool last, const float nearest,
            const float sumAngle, const NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances) const;
    bool checkAndReturnIsContinuationPossible(const int inputSize, const int *const xCoordinates,
            const int *const yCoordinates, const int *const times);
    void popInputData();
    void updateAlignPointProbabilities(const int start);
    bool suppressCharProbabilities(const int index1, const int index2);

    // const
    const ProximityInfo *mProximityInfo;
    float mMaxPointToKeyLength;
    bool mHasTouchPositionCorrectionData;
    int mMostCommonKeyWidthSquare;
    std::string mLocaleStr;
    int mKeyCount;
    int mCellHeight;
    int mCellWidth;
    int mGridHeight;
    int mGridWidth;
    bool mIsContinuationPossible;

    std::vector<int> mInputXs;
    std::vector<int> mInputYs;
    std::vector<int> mTimes;
    std::vector<int> mInputIndice;
    std::vector<float> mDistanceCache;
    std::vector<int>  mLengthCache;
    std::vector<float> mRelativeSpeeds;
    std::vector<float> mDirections;
    // probabilities of skipping or mapping to a key for each point.
    std::vector<hash_map_compat<int, float> > mCharProbabilities;
    // The vector for the key code set which holds nearby keys for each sampled input point
    // 1. Used to calculate the probability of the key
    // 2. Used to calculate mSearchKeysVector
    std::vector<NearKeycodesSet> mNearKeysVector;
    // The vector for the key code set which holds nearby keys of some trailing sampled input points
    // for each sampled input point. These nearby keys contain the next characters which can be in
    // the dictionary. Specifically, currently we are looking for keys nearby trailing sampled
    // inputs including the current input point.
    std::vector<NearKeycodesSet> mSearchKeysVector;
    bool mTouchPositionCorrectionEnabled;
    int mInputCodes[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mNormalizedSquaredDistances[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mInputSize;
    int mPrimaryInputWord[MAX_WORD_LENGTH_INTERNAL];
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_STATE_H
