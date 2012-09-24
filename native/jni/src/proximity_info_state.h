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
            const ProximityInfo *proximityInfo, const int32_t *const inputCodes,
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
              mDistanceCache(), mLengthCache(), mRelativeSpeeds(), mNearKeysVector(),
              mTouchPositionCorrectionEnabled(false), mInputSize(0) {
        memset(mInputCodes, 0, sizeof(mInputCodes));
        memset(mNormalizedSquaredDistances, 0, sizeof(mNormalizedSquaredDistances));
        memset(mPrimaryInputWord, 0, sizeof(mPrimaryInputWord));
    }

    virtual ~ProximityInfoState() {}

    inline unsigned short getPrimaryCharAt(const int index) const {
        return getProximityCharsAt(index)[0];
    }

    inline bool existsCharInProximityAt(const int index, const int c) const {
        const int *chars = getProximityCharsAt(index);
        int i = 0;
        while (chars[i] > 0 && i < MAX_PROXIMITY_CHARS_SIZE_INTERNAL) {
            if (chars[i++] == c) {
                return true;
            }
        }
        return false;
    }

    inline bool existsAdjacentProximityChars(const int index) const {
        if (index < 0 || index >= mInputSize) return false;
        const int currentChar = getPrimaryCharAt(index);
        const int leftIndex = index - 1;
        if (leftIndex >= 0 && existsCharInProximityAt(leftIndex, currentChar)) {
            return true;
        }
        const int rightIndex = index + 1;
        if (rightIndex < mInputSize && existsCharInProximityAt(rightIndex, currentChar)) {
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
            const unsigned short c, const bool checkProximityChars, int *proximityIndex = 0) const {
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
        while (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
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
        if (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
                && currentChars[j] == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
            ++j;
            while (j < MAX_PROXIMITY_CHARS_SIZE_INTERNAL
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
        return mNormalizedSquaredDistances[
                inputIndex * MAX_PROXIMITY_CHARS_SIZE_INTERNAL + proximityIndex];
    }

    inline const unsigned short *getPrimaryInputWord() const {
        return mPrimaryInputWord;
    }

    inline bool touchPositionCorrectionEnabled() const {
        return mTouchPositionCorrectionEnabled;
    }

    inline bool sameAsTyped(const unsigned short *word, int length) const {
        if (length != mInputSize) {
            return false;
        }
        const int *inputCodes = mInputCodes;
        while (length--) {
            if (static_cast<unsigned int>(*inputCodes) != static_cast<unsigned int>(*word)) {
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

    float getPointToKeyLength(const int inputIndex, const int charCode, const float scale) const;

    int getSpaceY() const;

    int32_t getAllPossibleChars(
            const size_t startIndex, int32_t *const filter, const int32_t filterSize) const;

    float getRelativeSpeed(const int index) const {
        return mRelativeSpeeds[index];
    }
 private:
    DISALLOW_COPY_AND_ASSIGN(ProximityInfoState);
    typedef hash_map_compat<int, float> NearKeysDistanceMap;
    /////////////////////////////////////////
    // Defined in proximity_info_state.cpp //
    /////////////////////////////////////////
    float calculateNormalizedSquaredDistance(const int keyIndex, const int inputIndex) const;

    float calculateSquaredDistanceFromSweetSpotCenter(
            const int keyIndex, const int inputIndex) const;

    bool pushTouchPoint(const int inputIndex, const int nodeChar, int x, int y, const int time,
            const bool sample, const bool isLastPoint,
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

    inline const int *getProximityCharsAt(const int index) const {
        return mInputCodes + (index * MAX_PROXIMITY_CHARS_SIZE_INTERNAL);
    }

    float updateNearKeysDistances(const int x, const int y,
            NearKeysDistanceMap *const currentNearKeysDistances);
    bool isPrevLocalMin(const NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances) const;
    float getPointScore(
            const int x, const int y, const int time, const bool last, const float nearest,
            const NearKeysDistanceMap *const currentNearKeysDistances,
            const NearKeysDistanceMap *const prevNearKeysDistances,
            const NearKeysDistanceMap *const prevPrevNearKeysDistances) const;
    bool checkAndReturnIsContinuationPossible(const int inputSize, const int *const xCoordinates,
            const int *const yCoordinates, const int *const times);
    void popInputData();

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
    std::vector<NearKeycodesSet> mNearKeysVector;
    bool mTouchPositionCorrectionEnabled;
    int32_t mInputCodes[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mNormalizedSquaredDistances[MAX_PROXIMITY_CHARS_SIZE_INTERNAL * MAX_WORD_LENGTH_INTERNAL];
    int mInputSize;
    unsigned short mPrimaryInputWord[MAX_WORD_LENGTH_INTERNAL];
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_STATE_H
