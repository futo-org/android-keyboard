/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <assert.h>
#include <stdio.h>
#include <string>

#define LOG_TAG "LatinIME: proximity_info.cpp"

#include "additional_proximity_chars.h"
#include "defines.h"
#include "dictionary.h"
#include "proximity_info.h"

namespace latinime {

inline void copyOrFillZero(void *to, const void *from, size_t size) {
    if (from) {
        memcpy(to, from, size);
    } else {
        memset(to, 0, size);
    }
}

ProximityInfo::ProximityInfo(const std::string localeStr, const int maxProximityCharsSize,
        const int keyboardWidth, const int keyboardHeight, const int gridWidth,
        const int gridHeight, const int mostCommonKeyWidth,
        const int32_t *proximityCharsArray, const int keyCount, const int32_t *keyXCoordinates,
        const int32_t *keyYCoordinates, const int32_t *keyWidths, const int32_t *keyHeights,
        const int32_t *keyCharCodes, const float *sweetSpotCenterXs, const float *sweetSpotCenterYs,
        const float *sweetSpotRadii)
        : MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize), KEYBOARD_WIDTH(keyboardWidth),
          KEYBOARD_HEIGHT(keyboardHeight), GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight),
          MOST_COMMON_KEY_WIDTH_SQUARE(mostCommonKeyWidth * mostCommonKeyWidth),
          CELL_WIDTH((keyboardWidth + gridWidth - 1) / gridWidth),
          CELL_HEIGHT((keyboardHeight + gridHeight - 1) / gridHeight),
          KEY_COUNT(min(keyCount, MAX_KEY_COUNT_IN_A_KEYBOARD)),
          HAS_TOUCH_POSITION_CORRECTION_DATA(keyCount > 0 && keyXCoordinates && keyYCoordinates
                  && keyWidths && keyHeights && keyCharCodes && sweetSpotCenterXs
                  && sweetSpotCenterYs && sweetSpotRadii),
          mLocaleStr(localeStr),
          mInputXCoordinates(0), mInputYCoordinates(0),
          mTouchPositionCorrectionEnabled(false) {
    const int proximityGridLength = GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE;
    mProximityCharsArray = new int32_t[proximityGridLength];
    mInputCodes = new int32_t[MAX_PROXIMITY_CHARS_SIZE * MAX_WORD_LENGTH_INTERNAL];
    if (DEBUG_PROXIMITY_INFO) {
        AKLOGI("Create proximity info array %d", proximityGridLength);
    }
    memcpy(mProximityCharsArray, proximityCharsArray,
            proximityGridLength * sizeof(mProximityCharsArray[0]));
    const int normalizedSquaredDistancesLength =
            MAX_PROXIMITY_CHARS_SIZE * MAX_WORD_LENGTH_INTERNAL;
    mNormalizedSquaredDistances = new int[normalizedSquaredDistancesLength];
    for (int i = 0; i < normalizedSquaredDistancesLength; ++i) {
        mNormalizedSquaredDistances[i] = NOT_A_DISTANCE;
    }

    copyOrFillZero(mKeyXCoordinates, keyXCoordinates, KEY_COUNT * sizeof(mKeyXCoordinates[0]));
    copyOrFillZero(mKeyYCoordinates, keyYCoordinates, KEY_COUNT * sizeof(mKeyYCoordinates[0]));
    copyOrFillZero(mKeyWidths, keyWidths, KEY_COUNT * sizeof(mKeyWidths[0]));
    copyOrFillZero(mKeyHeights, keyHeights, KEY_COUNT * sizeof(mKeyHeights[0]));
    copyOrFillZero(mKeyCharCodes, keyCharCodes, KEY_COUNT * sizeof(mKeyCharCodes[0]));
    copyOrFillZero(mSweetSpotCenterXs, sweetSpotCenterXs,
            KEY_COUNT * sizeof(mSweetSpotCenterXs[0]));
    copyOrFillZero(mSweetSpotCenterYs, sweetSpotCenterYs,
            KEY_COUNT * sizeof(mSweetSpotCenterYs[0]));
    copyOrFillZero(mSweetSpotRadii, sweetSpotRadii, KEY_COUNT * sizeof(mSweetSpotRadii[0]));

    initializeCodeToKeyIndex();
}

// Build the reversed look up table from the char code to the index in mKeyXCoordinates,
// mKeyYCoordinates, mKeyWidths, mKeyHeights, mKeyCharCodes.
void ProximityInfo::initializeCodeToKeyIndex() {
    memset(mCodeToKeyIndex, -1, (MAX_CHAR_CODE + 1) * sizeof(mCodeToKeyIndex[0]));
    for (int i = 0; i < KEY_COUNT; ++i) {
        const int code = mKeyCharCodes[i];
        if (0 <= code && code <= MAX_CHAR_CODE) {
            mCodeToKeyIndex[code] = i;
        }
    }
}

ProximityInfo::~ProximityInfo() {
    delete[] mNormalizedSquaredDistances;
    delete[] mProximityCharsArray;
    delete[] mInputCodes;
}

inline int ProximityInfo::getStartIndexFromCoordinates(const int x, const int y) const {
    return ((y / CELL_HEIGHT) * GRID_WIDTH + (x / CELL_WIDTH))
            * MAX_PROXIMITY_CHARS_SIZE;
}

bool ProximityInfo::hasSpaceProximity(const int x, const int y) const {
    if (x < 0 || y < 0) {
        if (DEBUG_DICT) {
            AKLOGI("HasSpaceProximity: Illegal coordinates (%d, %d)", x, y);
            assert(false);
        }
        return false;
    }

    const int startIndex = getStartIndexFromCoordinates(x, y);
    if (DEBUG_PROXIMITY_INFO) {
        AKLOGI("hasSpaceProximity: index %d, %d, %d", startIndex, x, y);
    }
    for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
        if (DEBUG_PROXIMITY_INFO) {
            AKLOGI("Index: %d", mProximityCharsArray[startIndex + i]);
        }
        if (mProximityCharsArray[startIndex + i] == KEYCODE_SPACE) {
            return true;
        }
    }
    return false;
}

bool ProximityInfo::isOnKey(const int keyId, const int x, const int y) const {
    if (keyId < 0) return true; // NOT_A_ID is -1, but return whenever < 0 just in case
    const int left = mKeyXCoordinates[keyId];
    const int top = mKeyYCoordinates[keyId];
    const int right = left + mKeyWidths[keyId] + 1;
    const int bottom = top + mKeyHeights[keyId];
    return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
}

int ProximityInfo::squaredDistanceToEdge(const int keyId, const int x, const int y) const {
    if (keyId < 0) return true; // NOT_A_ID is -1, but return whenever < 0 just in case
    const int left = mKeyXCoordinates[keyId];
    const int top = mKeyYCoordinates[keyId];
    const int right = left + mKeyWidths[keyId];
    const int bottom = top + mKeyHeights[keyId];
    const int edgeX = x < left ? left : (x > right ? right : x);
    const int edgeY = y < top ? top : (y > bottom ? bottom : y);
    const int dx = x - edgeX;
    const int dy = y - edgeY;
    return dx * dx + dy * dy;
}

void ProximityInfo::calculateNearbyKeyCodes(
        const int x, const int y, const int32_t primaryKey, int *inputCodes) const {
    int insertPos = 0;
    inputCodes[insertPos++] = primaryKey;
    const int startIndex = getStartIndexFromCoordinates(x, y);
    if (startIndex >= 0) {
        for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
            const int32_t c = mProximityCharsArray[startIndex + i];
            if (c < KEYCODE_SPACE || c == primaryKey) {
                continue;
            }
            const int keyIndex = getKeyIndex(c);
            const bool onKey = isOnKey(keyIndex, x, y);
            const int distance = squaredDistanceToEdge(keyIndex, x, y);
            if (onKey || distance < MOST_COMMON_KEY_WIDTH_SQUARE) {
                inputCodes[insertPos++] = c;
                if (insertPos >= MAX_PROXIMITY_CHARS_SIZE) {
                    if (DEBUG_DICT) {
                        assert(false);
                    }
                    return;
                }
            }
        }
        const int additionalProximitySize =
                AdditionalProximityChars::getAdditionalCharsSize(&mLocaleStr, primaryKey);
        if (additionalProximitySize > 0) {
            inputCodes[insertPos++] = ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE;
            if (insertPos >= MAX_PROXIMITY_CHARS_SIZE) {
                if (DEBUG_DICT) {
                    assert(false);
                }
                return;
            }

            const int32_t* additionalProximityChars =
                    AdditionalProximityChars::getAdditionalChars(&mLocaleStr, primaryKey);
            for (int j = 0; j < additionalProximitySize; ++j) {
                const int32_t ac = additionalProximityChars[j];
                int k = 0;
                for (; k < insertPos; ++k) {
                    if ((int)ac == inputCodes[k]) {
                        break;
                    }
                }
                if (k < insertPos) {
                    continue;
                }
                inputCodes[insertPos++] = ac;
                if (insertPos >= MAX_PROXIMITY_CHARS_SIZE) {
                    if (DEBUG_DICT) {
                        assert(false);
                    }
                    return;
                }
            }
        }
    }
    // Add a delimiter for the proximity characters
    for (int i = insertPos; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
        inputCodes[i] = NOT_A_CODE;
    }
}

void ProximityInfo::setInputParams(const int32_t* inputCodes, const int inputLength,
        const int* xCoordinates, const int* yCoordinates) {
    memset(mInputCodes, 0,
            MAX_WORD_LENGTH_INTERNAL * MAX_PROXIMITY_CHARS_SIZE * sizeof(mInputCodes[0]));

    for (int i = 0; i < inputLength; ++i) {
        const int32_t primaryKey = inputCodes[i];
        const int x = xCoordinates[i];
        const int y = yCoordinates[i];
        int *proximities = &mInputCodes[i * MAX_PROXIMITY_CHARS_SIZE];
        calculateNearbyKeyCodes(x, y, primaryKey, proximities);
    }

    if (DEBUG_PROXIMITY_CHARS) {
        for (int i = 0; i < inputLength; ++i) {
            AKLOGI("---");
            for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE; ++j) {
                int icc = mInputCodes[i * MAX_PROXIMITY_CHARS_SIZE + j];
                int icfjc = inputCodes[i * MAX_PROXIMITY_CHARS_SIZE + j];
                icc+= 0;
                icfjc += 0;
                AKLOGI("--- (%d)%c,%c", i, icc, icfjc);
                AKLOGI("---             A<%d>,B<%d>", icc, icfjc);
            }
        }
    }
    //Keep for debug, sorry
    //for (int i = 0; i < MAX_WORD_LENGTH_INTERNAL * MAX_PROXIMITY_CHARS_SIZE; ++i) {
    //if (i < inputLength * MAX_PROXIMITY_CHARS_SIZE) {
    //mInputCodes[i] = mInputCodesFromJava[i];
    //} else {
    // mInputCodes[i] = 0;
    // }
    //}
    mInputXCoordinates = xCoordinates;
    mInputYCoordinates = yCoordinates;
    mTouchPositionCorrectionEnabled =
            HAS_TOUCH_POSITION_CORRECTION_DATA && xCoordinates && yCoordinates;
    mInputLength = inputLength;
    for (int i = 0; i < inputLength; ++i) {
        mPrimaryInputWord[i] = getPrimaryCharAt(i);
    }
    mPrimaryInputWord[inputLength] = 0;
    if (DEBUG_PROXIMITY_CHARS) {
        AKLOGI("--- setInputParams");
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
            // Keep debug code just in case
            //int proximities[50];
            //for (int m = 0; m < 50; ++m) {
            //proximities[m] = 0;
            //}
            //calculateNearbyKeyCodes(x, y, primaryKey, proximities);
            //for (int l = 0; l < 50 && proximities[l] > 0; ++l) {
            //if (DEBUG_PROXIMITY_CHARS) {
            //AKLOGI("--- native Proximity (%d) = %c", l, proximities[l]);
            //}
            //}
        }
        for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE && proximityChars[j] > 0; ++j) {
            const int currentChar = proximityChars[j];
            const float squaredDistance = hasInputCoordinates()
                    ? calculateNormalizedSquaredDistance(getKeyIndex(currentChar), i)
                    : NOT_A_DISTANCE_FLOAT;
            if (squaredDistance >= 0.0f) {
                mNormalizedSquaredDistances[i * MAX_PROXIMITY_CHARS_SIZE + j] =
                        (int)(squaredDistance * NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR);
            } else {
                mNormalizedSquaredDistances[i * MAX_PROXIMITY_CHARS_SIZE + j] = (j == 0)
                        ? EQUIVALENT_CHAR_WITHOUT_DISTANCE_INFO
                        : PROXIMITY_CHAR_WITHOUT_DISTANCE_INFO;
            }
            if (DEBUG_PROXIMITY_CHARS) {
                AKLOGI("--- Proximity (%d) = %c", j, currentChar);
            }
        }
    }
}

inline float square(const float x) { return x * x; }

float ProximityInfo::calculateNormalizedSquaredDistance(
        const int keyIndex, const int inputIndex) const {
    if (keyIndex == NOT_AN_INDEX) {
        return NOT_A_DISTANCE_FLOAT;
    }
    if (!hasSweetSpotData(keyIndex)) {
        return NOT_A_DISTANCE_FLOAT;
    }
    if (NOT_A_COORDINATE == mInputXCoordinates[inputIndex]) {
        return NOT_A_DISTANCE_FLOAT;
    }
    const float squaredDistance = calculateSquaredDistanceFromSweetSpotCenter(keyIndex, inputIndex);
    const float squaredRadius = square(mSweetSpotRadii[keyIndex]);
    return squaredDistance / squaredRadius;
}

bool ProximityInfo::hasInputCoordinates() const {
    return mInputXCoordinates && mInputYCoordinates;
}

int ProximityInfo::getKeyIndex(const int c) const {
    if (KEY_COUNT == 0) {
        // We do not have the coordinate data
        return NOT_AN_INDEX;
    }
    const unsigned short baseLowerC = toBaseLowerCase(c);
    if (baseLowerC > MAX_CHAR_CODE) {
        return NOT_AN_INDEX;
    }
    return mCodeToKeyIndex[baseLowerC];
}

float ProximityInfo::calculateSquaredDistanceFromSweetSpotCenter(
        const int keyIndex, const int inputIndex) const {
    const float sweetSpotCenterX = mSweetSpotCenterXs[keyIndex];
    const float sweetSpotCenterY = mSweetSpotCenterYs[keyIndex];
    const float inputX = (float)mInputXCoordinates[inputIndex];
    const float inputY = (float)mInputYCoordinates[inputIndex];
    return square(inputX - sweetSpotCenterX) + square(inputY - sweetSpotCenterY);
}

inline const int* ProximityInfo::getProximityCharsAt(const int index) const {
    return mInputCodes + (index * MAX_PROXIMITY_CHARS_SIZE);
}

unsigned short ProximityInfo::getPrimaryCharAt(const int index) const {
    return getProximityCharsAt(index)[0];
}

inline bool ProximityInfo::existsCharInProximityAt(const int index, const int c) const {
    const int *chars = getProximityCharsAt(index);
    int i = 0;
    while (chars[i] > 0 && i < MAX_PROXIMITY_CHARS_SIZE) {
        if (chars[i++] == c) {
            return true;
        }
    }
    return false;
}

bool ProximityInfo::existsAdjacentProximityChars(const int index) const {
    if (index < 0 || index >= mInputLength) return false;
    const int currentChar = getPrimaryCharAt(index);
    const int leftIndex = index - 1;
    if (leftIndex >= 0 && existsCharInProximityAt(leftIndex, currentChar)) {
        return true;
    }
    const int rightIndex = index + 1;
    if (rightIndex < mInputLength && existsCharInProximityAt(rightIndex, currentChar)) {
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
ProximityInfo::ProximityType ProximityInfo::getMatchedProximityId(const int index,
        const unsigned short c, const bool checkProximityChars, int *proximityIndex) const {
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
    while (j < MAX_PROXIMITY_CHARS_SIZE
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
    if (j < MAX_PROXIMITY_CHARS_SIZE
            && currentChars[j] == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
        ++j;
        while (j < MAX_PROXIMITY_CHARS_SIZE
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

bool ProximityInfo::sameAsTyped(const unsigned short *word, int length) const {
    if (length != mInputLength) {
        return false;
    }
    const int *inputCodes = mInputCodes;
    while (length--) {
        if ((unsigned int) *inputCodes != (unsigned int) *word) {
            return false;
        }
        inputCodes += MAX_PROXIMITY_CHARS_SIZE;
        word++;
    }
    return true;
}

const int ProximityInfo::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR_LOG_2;
const int ProximityInfo::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR;
const int ProximityInfo::MAX_KEY_COUNT_IN_A_KEYBOARD;
const int ProximityInfo::MAX_CHAR_CODE;

} // namespace latinime
