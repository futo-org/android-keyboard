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

#define LOG_TAG "LatinIME: proximity_info_state.cpp"

#include "defines.h"
#include "geometry_utils.h"
#include "proximity_info.h"
#include "proximity_info_state.h"
#include "proximity_info_state_utils.h"

namespace latinime {

const int ProximityInfoState::NOT_A_CODE = -1;

void ProximityInfoState::initInputParams(const int pointerId, const float maxPointToKeyLength,
        const ProximityInfo *proximityInfo, const int *const inputCodes, const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times,
        const int *const pointerIds, const bool isGeometric) {
    mIsContinuationPossible = checkAndReturnIsContinuationPossible(
            inputSize, xCoordinates, yCoordinates, times, isGeometric);

    mProximityInfo = proximityInfo;
    mHasTouchPositionCorrectionData = proximityInfo->hasTouchPositionCorrectionData();
    mMostCommonKeyWidthSquare = proximityInfo->getMostCommonKeyWidthSquare();
    mKeyCount = proximityInfo->getKeyCount();
    mCellHeight = proximityInfo->getCellHeight();
    mCellWidth = proximityInfo->getCellWidth();
    mGridHeight = proximityInfo->getGridWidth();
    mGridWidth = proximityInfo->getGridHeight();

    memset(mInputProximities, 0, sizeof(mInputProximities));

    if (!isGeometric && pointerId == 0) {
        mProximityInfo->initializeProximities(inputCodes, xCoordinates, yCoordinates,
                inputSize, mInputProximities);
    }

    ///////////////////////
    // Setup touch points
    int pushTouchPointStartIndex = 0;
    int lastSavedInputSize = 0;
    mMaxPointToKeyLength = maxPointToKeyLength;
    if (mIsContinuationPossible && mSampledInputIndice.size() > 1) {
        // Just update difference.
        // Two points prior is never skipped. Thus, we pop 2 input point data here.
        pushTouchPointStartIndex = mSampledInputIndice[mSampledInputIndice.size() - 2];
        popInputData();
        popInputData();
        lastSavedInputSize = mSampledInputXs.size();
    } else {
        // Clear all data.
        mSampledInputXs.clear();
        mSampledInputYs.clear();
        mSampledTimes.clear();
        mSampledInputIndice.clear();
        mSampledLengthCache.clear();
        mSampledDistanceCache_G.clear();
        mSampledNearKeysVector.clear();
        mSampledSearchKeysVector.clear();
        mSpeedRates.clear();
        mBeelineSpeedPercentiles.clear();
        mCharProbabilities.clear();
        mDirections.clear();
    }
    if (DEBUG_GEO_FULL) {
        AKLOGI("Init ProximityInfoState: reused points =  %d, last input size = %d",
                pushTouchPointStartIndex, lastSavedInputSize);
    }
    mSampledInputSize = 0;

    if (xCoordinates && yCoordinates) {
        mSampledInputSize = ProximityInfoStateUtils::updateTouchPoints(
                mProximityInfo->getMostCommonKeyWidth(), mProximityInfo, mMaxPointToKeyLength,
                mInputProximities, xCoordinates, yCoordinates, times, pointerIds, inputSize,
                isGeometric, pointerId, pushTouchPointStartIndex, &mSampledInputXs,
                &mSampledInputYs, &mSampledTimes, &mSampledLengthCache, &mSampledInputIndice);
    }

    if (mSampledInputSize > 0 && isGeometric) {
        mAverageSpeed = ProximityInfoStateUtils::refreshSpeedRates(
                inputSize, xCoordinates, yCoordinates, times, lastSavedInputSize,
                mSampledInputSize, &mSampledInputXs, &mSampledInputYs, &mSampledTimes,
                &mSampledLengthCache, &mSampledInputIndice, &mSpeedRates, &mDirections);
        ProximityInfoStateUtils::refreshBeelineSpeedRates(
                mProximityInfo->getMostCommonKeyWidth(), mAverageSpeed, inputSize,
                xCoordinates, yCoordinates, times, mSampledInputSize, &mSampledInputXs,
                &mSampledInputYs, &mSampledInputIndice, &mBeelineSpeedPercentiles);
    }

    if (mSampledInputSize > 0) {
        ProximityInfoStateUtils::initGeometricDistanceInfos(
                mProximityInfo, mProximityInfo->getKeyCount(),
                mSampledInputSize, lastSavedInputSize, &mSampledInputXs, &mSampledInputYs,
                &mSampledNearKeysVector, &mSampledDistanceCache_G);
        if (isGeometric) {
            // updates probabilities of skipping or mapping each key for all points.
            ProximityInfoStateUtils::updateAlignPointProbabilities(
                    mMaxPointToKeyLength, mProximityInfo->getMostCommonKeyWidth(),
                    mProximityInfo->getKeyCount(), lastSavedInputSize, mSampledInputSize,
                    &mSampledInputXs, &mSampledInputYs, &mSpeedRates, &mSampledLengthCache,
                    &mSampledDistanceCache_G, &mSampledNearKeysVector, &mCharProbabilities);
            ProximityInfoStateUtils::updateSampledSearchKeysVector(mProximityInfo,
                    mSampledInputSize, lastSavedInputSize, &mSampledLengthCache,
                    &mSampledNearKeysVector, &mSampledSearchKeysVector);
        }
    }

    if (DEBUG_SAMPLING_POINTS) {
        ProximityInfoStateUtils::dump(isGeometric, inputSize, xCoordinates, yCoordinates,
                mSampledInputSize, &mSampledInputXs, &mSampledInputYs, &mSampledTimes, &mSpeedRates,
                &mBeelineSpeedPercentiles);
    }
    // end
    ///////////////////////

    memset(mNormalizedSquaredDistances, NOT_A_DISTANCE, sizeof(mNormalizedSquaredDistances));
    memset(mPrimaryInputWord, 0, sizeof(mPrimaryInputWord));
    mTouchPositionCorrectionEnabled = mSampledInputSize > 0 && mHasTouchPositionCorrectionData
            && xCoordinates && yCoordinates;
    if (!isGeometric && pointerId == 0) {
        ProximityInfoStateUtils::initPrimaryInputWord(
                inputSize, mInputProximities, mPrimaryInputWord);
        if (mTouchPositionCorrectionEnabled) {
            ProximityInfoStateUtils::initNormalizedSquaredDistances(
                    mProximityInfo, inputSize, xCoordinates, yCoordinates, mInputProximities,
                    hasInputCoordinates(), &mSampledInputXs, &mSampledInputYs,
                    mNormalizedSquaredDistances);
        }
    }
    if (DEBUG_GEO_FULL) {
        AKLOGI("ProximityState init finished: %d points out of %d", mSampledInputSize, inputSize);
    }
}

bool ProximityInfoState::checkAndReturnIsContinuationPossible(const int inputSize,
        const int *const xCoordinates, const int *const yCoordinates, const int *const times,
        const bool isGeometric) const {
    if (isGeometric) {
        for (int i = 0; i < mSampledInputSize; ++i) {
            const int index = mSampledInputIndice[i];
            if (index > inputSize || xCoordinates[index] != mSampledInputXs[i] ||
                    yCoordinates[index] != mSampledInputYs[i] || times[index] != mSampledTimes[i]) {
                return false;
            }
        }
    } else {
        if (inputSize < mSampledInputSize) {
            // Assuming the cache is invalid if the previous input size is larger than the new one.
            return false;
        }
        for (int i = 0; i < mSampledInputSize && i < MAX_WORD_LENGTH; ++i) {
            if (xCoordinates[i] != mSampledInputXs[i]
                    || yCoordinates[i] != mSampledInputYs[i]) {
                return false;
            }
        }
    }
    return true;
}

int ProximityInfoState::getDuration(const int index) const {
    if (index >= 0 && index < mSampledInputSize - 1) {
        return mSampledTimes[index + 1] - mSampledTimes[index];
    }
    return 0;
}

// TODO: Remove the "scale" parameter
// This function basically converts from a length to an edit distance. Accordingly, it's obviously
// wrong to compare with mMaxPointToKeyLength.
float ProximityInfoState::getPointToKeyLength(
        const int inputIndex, const int codePoint, const float scale) const {
    const int keyId = mProximityInfo->getKeyIndexOf(codePoint);
    if (keyId != NOT_AN_INDEX) {
        const int index = inputIndex * mProximityInfo->getKeyCount() + keyId;
        return min(mSampledDistanceCache_G[index] * scale, mMaxPointToKeyLength);
    }
    if (isSkippableCodePoint(codePoint)) {
        return 0.0f;
    }
    // If the char is not a key on the keyboard then return the max length.
    return MAX_POINT_TO_KEY_LENGTH;
}

float ProximityInfoState::getPointToKeyLength_G(const int inputIndex, const int codePoint) const {
    return getPointToKeyLength(inputIndex, codePoint, 1.0f);
}

// TODO: Remove the "scale" parameter
float ProximityInfoState::getPointToKeyByIdLength(
        const int inputIndex, const int keyId, const float scale) const {
    return ProximityInfoStateUtils::getPointToKeyByIdLength(mMaxPointToKeyLength,
            &mSampledDistanceCache_G, mProximityInfo->getKeyCount(), inputIndex, keyId, scale);
}

float ProximityInfoState::getPointToKeyByIdLength(const int inputIndex, const int keyId) const {
    return getPointToKeyByIdLength(inputIndex, keyId, 1.0f);
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
    while (j < MAX_PROXIMITY_CHARS_SIZE
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
    if (j < MAX_PROXIMITY_CHARS_SIZE
            && currentCodePoints[j] == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
        ++j;
        while (j < MAX_PROXIMITY_CHARS_SIZE
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

// Puts possible characters into filter and returns new filter size.
int ProximityInfoState::getAllPossibleChars(
        const size_t index, int *const filter, const int filterSize) const {
    if (index >= mSampledInputXs.size()) {
        return filterSize;
    }
    int newFilterSize = filterSize;
    const int keyCount = mProximityInfo->getKeyCount();
    for (int j = 0; j < keyCount; ++j) {
        if (mSampledSearchKeysVector[index].test(j)) {
            const int keyCodePoint = mProximityInfo->getCodePointOf(j);
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
    ASSERT(index >= 0 && index < mSampledInputSize);
    return mSampledSearchKeysVector[index].test(keyId);
}

void ProximityInfoState::popInputData() {
    ProximityInfoStateUtils::popInputData(&mSampledInputXs, &mSampledInputYs, &mSampledTimes,
            &mSampledLengthCache, &mSampledInputIndice);
}

float ProximityInfoState::getDirection(const int index0, const int index1) const {
    return ProximityInfoStateUtils::getDirection(
            &mSampledInputXs, &mSampledInputYs, index0, index1);
}

float ProximityInfoState::getLineToKeyDistance(
        const int from, const int to, const int keyId, const bool extend) const {
    if (from < 0 || from > mSampledInputSize - 1) {
        return 0.0f;
    }
    if (to < 0 || to > mSampledInputSize - 1) {
        return 0.0f;
    }
    const int x0 = mSampledInputXs[from];
    const int y0 = mSampledInputYs[from];
    const int x1 = mSampledInputXs[to];
    const int y1 = mSampledInputYs[to];

    const int keyX = mProximityInfo->getKeyCenterXOfKeyIdG(keyId);
    const int keyY = mProximityInfo->getKeyCenterYOfKeyIdG(keyId);

    return ProximityInfoUtils::pointToLineSegSquaredDistanceFloat(
            keyX, keyY, x0, y0, x1, y1, extend);
}

// Get a word that is detected by tracing the most probable string into codePointBuf and
// returns probability of generating the word.
float ProximityInfoState::getMostProbableString(int *const codePointBuf) const {
    static const float DEMOTION_LOG_PROBABILITY = 0.3f;
    int index = 0;
    float sumLogProbability = 0.0f;
    // TODO: Current implementation is greedy algorithm. DP would be efficient for many cases.
    for (int i = 0; i < mSampledInputSize && index < MAX_WORD_LENGTH - 1; ++i) {
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

bool ProximityInfoState::hasSpaceProximity(const int index) const {
    ASSERT(0 <= index && index < mSampledInputSize);
    return mProximityInfo->hasSpaceProximity(getInputX(index), getInputY(index));
}

// Returns a probability of mapping index to keyIndex.
float ProximityInfoState::getProbability(const int index, const int keyIndex) const {
    ASSERT(0 <= index && index < mSampledInputSize);
    hash_map_compat<int, float>::const_iterator it = mCharProbabilities[index].find(keyIndex);
    if (it != mCharProbabilities[index].end()) {
        return it->second;
    }
    return static_cast<float>(MAX_POINT_TO_KEY_LENGTH);
}
} // namespace latinime
