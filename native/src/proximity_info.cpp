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

#include <stdio.h>
#include <string.h>

#define LOG_TAG "LatinIME: proximity_info.cpp"

#include "dictionary.h"
#include "proximity_info.h"

namespace latinime {

ProximityInfo::ProximityInfo(const int maxProximityCharsSize, const int keyboardWidth,
        const int keyboardHeight, const int gridWidth, const int gridHeight,
        const uint32_t *proximityCharsArray)
        : MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize), KEYBOARD_WIDTH(keyboardWidth),
          KEYBOARD_HEIGHT(keyboardHeight), GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight),
          CELL_WIDTH((keyboardWidth + gridWidth - 1) / gridWidth),
          CELL_HEIGHT((keyboardHeight + gridHeight - 1) / gridHeight) {
    const int len = GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE;
    mProximityCharsArray = new uint32_t[len];
    if (DEBUG_PROXIMITY_INFO) {
        LOGI("Create proximity info array %d", len);
    }
    memcpy(mProximityCharsArray, proximityCharsArray, len * sizeof(mProximityCharsArray[0]));
}

ProximityInfo::~ProximityInfo() {
    delete[] mProximityCharsArray;
}

inline int ProximityInfo::getStartIndexFromCoordinates(const int x, const int y) const {
    return ((y / CELL_HEIGHT) * GRID_WIDTH + (x / CELL_WIDTH))
            * MAX_PROXIMITY_CHARS_SIZE;
}

bool ProximityInfo::hasSpaceProximity(const int x, const int y) const {
    const int startIndex = getStartIndexFromCoordinates(x, y);
    if (DEBUG_PROXIMITY_INFO) {
        LOGI("hasSpaceProximity: index %d", startIndex);
    }
    for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
        if (DEBUG_PROXIMITY_INFO) {
            LOGI("Index: %d", mProximityCharsArray[startIndex + i]);
        }
        if (mProximityCharsArray[startIndex + i] == KEYCODE_SPACE) {
            return true;
        }
    }
    return false;
}

// TODO: Calculate nearby codes here.
void ProximityInfo::setInputParams(const int* inputCodes, const int inputLength) {
    mInputCodes = inputCodes;
    mInputLength = inputLength;
    for (int i = 0; i < inputLength; ++i) {
        mPrimaryInputWord[i] = getPrimaryCharAt(i);
    }
    mPrimaryInputWord[inputLength] = 0;
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
// Notice : accented characters do not have a proximity list, so they are alone
// in their list. The non-accented version of the character should be considered
// "close", but not the other keys close to the non-accented version.
ProximityInfo::ProximityType ProximityInfo::getMatchedProximityId(
        const int index, const unsigned short c, const bool checkProximityChars) const {
    const int *currentChars = getProximityCharsAt(index);
    const unsigned short baseLowerC = Dictionary::toBaseLowerCase(c);

    // The first char in the array is what user typed. If it matches right away,
    // that means the user typed that same char for this pos.
    if (currentChars[0] == baseLowerC || currentChars[0] == c)
        return SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR;

    if (!checkProximityChars) return UNRELATED_CHAR;

    // If the non-accented, lowercased version of that first character matches c,
    // then we have a non-accented version of the accented character the user
    // typed. Treat it as a close char.
    if (Dictionary::toBaseLowerCase(currentChars[0]) == baseLowerC)
        return NEAR_PROXIMITY_CHAR;

    // Not an exact nor an accent-alike match: search the list of close keys
    int j = 1;
    while (currentChars[j] > 0 && j < MAX_PROXIMITY_CHARS_SIZE) {
        const bool matched = (currentChars[j] == baseLowerC || currentChars[j] == c);
        if (matched) return NEAR_PROXIMITY_CHAR;
        ++j;
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

} // namespace latinime
