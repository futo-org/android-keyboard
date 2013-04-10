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

#include <cstring>
#include <cmath>

#define LOG_TAG "LatinIME: proximity_info.cpp"

#include "additional_proximity_chars.h"
#include "char_utils.h"
#include "defines.h"
#include "geometry_utils.h"
#include "jni.h"
#include "proximity_info.h"
#include "proximity_info_params.h"

namespace latinime {

static AK_FORCE_INLINE void safeGetOrFillZeroIntArrayRegion(JNIEnv *env, jintArray jArray,
        jsize len, jint *buffer) {
    if (jArray && buffer) {
        env->GetIntArrayRegion(jArray, 0, len, buffer);
    } else if (buffer) {
        memset(buffer, 0, len * sizeof(buffer[0]));
    }
}

static AK_FORCE_INLINE void safeGetOrFillZeroFloatArrayRegion(JNIEnv *env, jfloatArray jArray,
        jsize len, jfloat *buffer) {
    if (jArray && buffer) {
        env->GetFloatArrayRegion(jArray, 0, len, buffer);
    } else if (buffer) {
        memset(buffer, 0, len * sizeof(buffer[0]));
    }
}

ProximityInfo::ProximityInfo(JNIEnv *env, const jstring localeJStr,
        const int keyboardWidth, const int keyboardHeight, const int gridWidth,
        const int gridHeight, const int mostCommonKeyWidth, const int mostCommonKeyHeight,
        const jintArray proximityChars, const int keyCount, const jintArray keyXCoordinates,
        const jintArray keyYCoordinates, const jintArray keyWidths, const jintArray keyHeights,
        const jintArray keyCharCodes, const jfloatArray sweetSpotCenterXs,
        const jfloatArray sweetSpotCenterYs, const jfloatArray sweetSpotRadii)
        : GRID_WIDTH(gridWidth), GRID_HEIGHT(gridHeight), MOST_COMMON_KEY_WIDTH(mostCommonKeyWidth),
          MOST_COMMON_KEY_WIDTH_SQUARE(mostCommonKeyWidth * mostCommonKeyWidth),
          MOST_COMMON_KEY_HEIGHT(mostCommonKeyHeight),
          NORMALIZED_SQUARED_MOST_COMMON_KEY_HYPOTENUSE(1.0f +
                  SQUARE_FLOAT(static_cast<float>(mostCommonKeyHeight) /
                          static_cast<float>(mostCommonKeyWidth))),
          CELL_WIDTH((keyboardWidth + gridWidth - 1) / gridWidth),
          CELL_HEIGHT((keyboardHeight + gridHeight - 1) / gridHeight),
          KEY_COUNT(min(keyCount, MAX_KEY_COUNT_IN_A_KEYBOARD)),
          KEYBOARD_WIDTH(keyboardWidth), KEYBOARD_HEIGHT(keyboardHeight),
          KEYBOARD_HYPOTENUSE(hypotf(KEYBOARD_WIDTH, KEYBOARD_HEIGHT)),
          HAS_TOUCH_POSITION_CORRECTION_DATA(keyCount > 0 && keyXCoordinates && keyYCoordinates
                  && keyWidths && keyHeights && keyCharCodes && sweetSpotCenterXs
                  && sweetSpotCenterYs && sweetSpotRadii),
          mProximityCharsArray(new int[GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE
                  /* proximityCharsLength */]),
          mCodeToKeyMap() {
    /* Let's check the input array length here to make sure */
    const jsize proximityCharsLength = env->GetArrayLength(proximityChars);
    if (proximityCharsLength != GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY_CHARS_SIZE) {
        AKLOGE("Invalid proximityCharsLength: %d", proximityCharsLength);
        ASSERT(false);
        return;
    }
    if (DEBUG_PROXIMITY_INFO) {
        AKLOGI("Create proximity info array %d", proximityCharsLength);
    }
    const jsize localeCStrUtf8Length = env->GetStringUTFLength(localeJStr);
    if (localeCStrUtf8Length >= MAX_LOCALE_STRING_LENGTH) {
        AKLOGI("Locale string length too long: length=%d", localeCStrUtf8Length);
        ASSERT(false);
    }
    memset(mLocaleStr, 0, sizeof(mLocaleStr));
    env->GetStringUTFRegion(localeJStr, 0, env->GetStringLength(localeJStr), mLocaleStr);
    safeGetOrFillZeroIntArrayRegion(env, proximityChars, proximityCharsLength,
            mProximityCharsArray);
    safeGetOrFillZeroIntArrayRegion(env, keyXCoordinates, KEY_COUNT, mKeyXCoordinates);
    safeGetOrFillZeroIntArrayRegion(env, keyYCoordinates, KEY_COUNT, mKeyYCoordinates);
    safeGetOrFillZeroIntArrayRegion(env, keyWidths, KEY_COUNT, mKeyWidths);
    safeGetOrFillZeroIntArrayRegion(env, keyHeights, KEY_COUNT, mKeyHeights);
    safeGetOrFillZeroIntArrayRegion(env, keyCharCodes, KEY_COUNT, mKeyCodePoints);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotCenterXs, KEY_COUNT, mSweetSpotCenterXs);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotCenterYs, KEY_COUNT, mSweetSpotCenterYs);
    safeGetOrFillZeroFloatArrayRegion(env, sweetSpotRadii, KEY_COUNT, mSweetSpotRadii);
    initializeG();
}

ProximityInfo::~ProximityInfo() {
    delete[] mProximityCharsArray;
}

bool ProximityInfo::hasSpaceProximity(const int x, const int y) const {
    if (x < 0 || y < 0) {
        if (DEBUG_DICT) {
            AKLOGI("HasSpaceProximity: Illegal coordinates (%d, %d)", x, y);
            // TODO: Enable this assertion.
            //ASSERT(false);
        }
        return false;
    }

    const int startIndex = ProximityInfoUtils::getStartIndexFromCoordinates(x, y,
            CELL_HEIGHT, CELL_WIDTH, GRID_WIDTH);
    if (DEBUG_PROXIMITY_INFO) {
        AKLOGI("hasSpaceProximity: index %d, %d, %d", startIndex, x, y);
    }
    int *proximityCharsArray = mProximityCharsArray;
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

float ProximityInfo::getNormalizedSquaredDistanceFromCenterFloatG(
        const int keyId, const int x, const int y, const float verticalScale) const {
    const bool correctTouchPosition = hasTouchPositionCorrectionData();
    const float centerX = static_cast<float>(correctTouchPosition ? getSweetSpotCenterXAt(keyId)
            : getKeyCenterXOfKeyIdG(keyId));
    const float visualKeyCenterY = static_cast<float>(getKeyCenterYOfKeyIdG(keyId));
    float centerY;
    if (correctTouchPosition) {
        const float sweetSpotCenterY = static_cast<float>(getSweetSpotCenterYAt(keyId));
        const float gapY = sweetSpotCenterY - visualKeyCenterY;
        centerY = visualKeyCenterY + gapY * verticalScale;
    } else {
        centerY = visualKeyCenterY;
    }
    const float touchX = static_cast<float>(x);
    const float touchY = static_cast<float>(y);
    const float keyWidth = static_cast<float>(getMostCommonKeyWidth());
    return ProximityInfoUtils::getSquaredDistanceFloat(centerX, centerY, touchX, touchY)
            / SQUARE_FLOAT(keyWidth);
}

int ProximityInfo::getCodePointOf(const int keyIndex) const {
    if (keyIndex < 0 || keyIndex >= KEY_COUNT) {
        return NOT_A_CODE_POINT;
    }
    return mKeyIndexToCodePointG[keyIndex];
}

void ProximityInfo::initializeG() {
    // TODO: Optimize
    for (int i = 0; i < KEY_COUNT; ++i) {
        const int code = mKeyCodePoints[i];
        const int lowerCode = toLowerCase(code);
        mCenterXsG[i] = mKeyXCoordinates[i] + mKeyWidths[i] / 2;
        mCenterYsG[i] = mKeyYCoordinates[i] + mKeyHeights[i] / 2;
        mCodeToKeyMap[lowerCode] = i;
        mKeyIndexToCodePointG[i] = lowerCode;
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

int ProximityInfo::getKeyCenterXOfCodePointG(int charCode) const {
    return getKeyCenterXOfKeyIdG(
            ProximityInfoUtils::getKeyIndexOf(KEY_COUNT, charCode, &mCodeToKeyMap));
}

int ProximityInfo::getKeyCenterYOfCodePointG(int charCode) const {
    return getKeyCenterYOfKeyIdG(
            ProximityInfoUtils::getKeyIndexOf(KEY_COUNT, charCode, &mCodeToKeyMap));
}

int ProximityInfo::getKeyCenterXOfKeyIdG(int keyId) const {
    if (keyId >= 0) {
        return mCenterXsG[keyId];
    }
    return 0;
}

int ProximityInfo::getKeyCenterYOfKeyIdG(int keyId) const {
    if (keyId >= 0) {
        return mCenterYsG[keyId];
    }
    return 0;
}

int ProximityInfo::getKeyKeyDistanceG(const int keyId0, const int keyId1) const {
    if (keyId0 >= 0 && keyId1 >= 0) {
        return mKeyKeyDistancesG[keyId0][keyId1];
    }
    return MAX_VALUE_FOR_WEIGHTING;
}
} // namespace latinime
