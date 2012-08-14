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

#include <cassert>
#include <cmath>
#include <cstring>

#define LOG_TAG "LatinIME: proximity_info.cpp"

#include "additional_proximity_chars.h"
#include "char_utils.h"
#include "defines.h"
#include "geometry_utils.h"
#include "jni.h"
#include "proximity_info.h"

namespace latinime {

static inline void safeGetOrFillZeroIntArrayRegion(JNIEnv *env, jintArray jArray, jsize len,
        jint *buffer) {
    if (jArray && buffer) {
        env->GetIntArrayRegion(jArray, 0, len, buffer);
    } else if (buffer) {
        memset(buffer, 0, len * sizeof(jint));
    }
}

static inline void safeGetOrFillZeroFloatArrayRegion(JNIEnv *env, jfloatArray jArray, jsize len,
        jfloat *buffer) {
    if (jArray && buffer) {
        env->GetFloatArrayRegion(jArray, 0, len, buffer);
    } else if (buffer) {
        memset(buffer, 0, len * sizeof(jfloat));
    }
}

ProximityInfo::ProximityInfo(JNIEnv *env, const jstring localeJStr, const int maxProximityCharsSize,
        const int keyboardWidth, const int keyboardHeight, const int gridWidth,
        const int gridHeight, const int mostCommonKeyWidth, const jintArray proximityChars,
        const int keyCount, const jintArray keyXCoordinates, const jintArray keyYCoordinates,
        const jintArray keyWidths, const jintArray keyHeights, const jintArray keyCharCodes,
        const jfloatArray sweetSpotCenterXs, const jfloatArray sweetSpotCenterYs,
        const jfloatArray sweetSpotRadii)
        : MAX_PROXIMITY_CHARS_SIZE(maxProximityCharsSize), KEYBOARD_WIDTH(keyboardWidth),
          KEYBOARD_HEIGHT(keyboardHeight), GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight),
          MOST_COMMON_KEY_WIDTH(mostCommonKeyWidth),
          MOST_COMMON_KEY_WIDTH_SQUARE(mostCommonKeyWidth * mostCommonKeyWidth),
          CELL_WIDTH((keyboardWidth + gridWidth - 1) / gridWidth),
          CELL_HEIGHT((keyboardHeight + gridHeight - 1) / gridHeight),
          KEY_COUNT(min(keyCount, MAX_KEY_COUNT_IN_A_KEYBOARD)),
          HAS_TOUCH_POSITION_CORRECTION_DATA(keyCount > 0 && keyXCoordinates && keyYCoordinates
                  && keyWidths && keyHeights && keyCharCodes && sweetSpotCenterXs
                  && sweetSpotCenterYs && sweetSpotRadii) {
    const int proximityGridLength = GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE;
    if (DEBUG_PROXIMITY_INFO) {
        AKLOGI("Create proximity info array %d", proximityGridLength);
    }
    const jsize localeCStrUtf8Length = env->GetStringUTFLength(localeJStr);
    if (localeCStrUtf8Length >= MAX_LOCALE_STRING_LENGTH) {
        AKLOGI("Locale string length too long: length=%d", localeCStrUtf8Length);
        assert(false);
    }
    memset(mLocaleStr, 0, sizeof(mLocaleStr));
    env->GetStringUTFRegion(localeJStr, 0, env->GetStringLength(localeJStr), mLocaleStr);
    mProximityCharsArray = new int32_t[proximityGridLength];
    safeGetOrFillZeroIntArrayRegion(env, proximityChars, proximityGridLength, mProximityCharsArray);
    safeGetOrFillZeroIntArrayRegion(env, keyXCoordinates, KEY_COUNT, mKeyXCoordinates);
    safeGetOrFillZeroIntArrayRegion(env, keyYCoordinates, KEY_COUNT, mKeyYCoordinates);
    safeGetOrFillZeroIntArrayRegion(env, keyWidths, KEY_COUNT, mKeyWidths);
    safeGetOrFillZeroIntArrayRegion(env, keyHeights, KEY_COUNT, mKeyHeights);
    safeGetOrFillZeroIntArrayRegion(env, keyCharCodes, KEY_COUNT, mKeyCharCodes);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotCenterXs, KEY_COUNT, mSweetSpotCenterXs);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotCenterYs, KEY_COUNT, mSweetSpotCenterYs);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotRadii, KEY_COUNT, mSweetSpotRadii);
    initializeCodeToKeyIndex();
    initializeG();
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
    delete[] mProximityCharsArray;
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
    int32_t *proximityCharsArray = mProximityCharsArray;
    for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
        if (DEBUG_PROXIMITY_INFO) {
            AKLOGI("Index: %d", mProximityCharsArray[startIndex + i]);
        }
        if (proximityCharsArray[startIndex + i] == KEYCODE_SPACE) {
            return true;
        }
    }
    return false;
}

static inline float getNormalizedSquaredDistanceFloat(float x1, float y1, float x2, float y2,
        float scale) {
    return squareFloat((x1 - x2) / scale) + squareFloat((y1 - y2) / scale);
}

float ProximityInfo::getNormalizedSquaredDistanceFromCenterFloat(
        const int keyId, const int x, const int y) const {
    const float centerX = static_cast<float>(getKeyCenterXOfIdG(keyId));
    const float centerY = static_cast<float>(getKeyCenterYOfIdG(keyId));
    const float touchX = static_cast<float>(x);
    const float touchY = static_cast<float>(y);
    const float keyWidth = static_cast<float>(getMostCommonKeyWidth());
    return getNormalizedSquaredDistanceFloat(centerX, centerY, touchX, touchY, keyWidth);
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
    int32_t *proximityCharsArray = mProximityCharsArray;
    int insertPos = 0;
    inputCodes[insertPos++] = primaryKey;
    const int startIndex = getStartIndexFromCoordinates(x, y);
    if (startIndex >= 0) {
        for (int i = 0; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
            const int32_t c = proximityCharsArray[startIndex + i];
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
                AdditionalProximityChars::getAdditionalCharsSize(mLocaleStr, primaryKey);
        if (additionalProximitySize > 0) {
            inputCodes[insertPos++] = ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE;
            if (insertPos >= MAX_PROXIMITY_CHARS_SIZE) {
                if (DEBUG_DICT) {
                    assert(false);
                }
                return;
            }

            const int32_t *additionalProximityChars =
                    AdditionalProximityChars::getAdditionalChars(mLocaleStr, primaryKey);
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

int ProximityInfo::getKeyCode(const int keyIndex) const {
    if (keyIndex < 0 || keyIndex >= KEY_COUNT) {
        return NOT_AN_INDEX;
    }
    return mKeyToCodeIndexG[keyIndex];
}

void ProximityInfo::initializeG() {
    // TODO: Optimize
    for (int i = 0; i < KEY_COUNT; ++i) {
        const int code = mKeyCharCodes[i];
        const int lowerCode = toBaseLowerCase(code);
        mCenterXsG[i] = mKeyXCoordinates[i] + mKeyWidths[i] / 2;
        mCenterYsG[i] = mKeyYCoordinates[i] + mKeyHeights[i] / 2;
        if (code != lowerCode && lowerCode >= 0 && lowerCode <= MAX_CHAR_CODE) {
            mCodeToKeyIndex[lowerCode] = i;
            mKeyToCodeIndexG[i] = lowerCode;
        } else {
            mKeyToCodeIndexG[i] = code;
        }
    }
    for (int i = 0; i < KEY_COUNT; i++) {
        mKeyKeyDistancesG[i][i] = 0;
        for (int j = i + 1; j < KEY_COUNT; j++) {
            mKeyKeyDistancesG[i][j] = getDistanceInt(
                    mCenterXsG[i], mCenterYsG[i], mCenterXsG[j], mCenterYsG[j]);
            mKeyKeyDistancesG[j][i] = mKeyKeyDistancesG[i][j];
        }
    }
}

float ProximityInfo::getKeyCenterXOfCharG(int charCode) const {
    return getKeyCenterXOfIdG(getKeyIndex(charCode));
}

float ProximityInfo::getKeyCenterYOfCharG(int charCode) const {
    return getKeyCenterYOfIdG(getKeyIndex(charCode));
}

float ProximityInfo::getKeyCenterXOfIdG(int keyId) const {
    if (keyId >= 0) {
        return mCenterXsG[keyId];
    }
    return 0;
}

float ProximityInfo::getKeyCenterYOfIdG(int keyId) const {
    if (keyId >= 0) {
        return mCenterYsG[keyId];
    }
    return 0;
}

int ProximityInfo::getKeyKeyDistanceG(int key0, int key1) const {
    const int keyId0 = getKeyIndex(key0);
    const int keyId1 = getKeyIndex(key1);
    if (keyId0 >= 0 && keyId1 >= 0) {
        return mKeyKeyDistancesG[keyId0][keyId1];
    }
    return 0;
}
} // namespace latinime
