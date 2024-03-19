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

#ifndef LATINIME_PROXIMITY_INFO_H
#define LATINIME_PROXIMITY_INFO_H

#include <unordered_map>
#include <vector>

#include "defines.h"
#include "jni.h"
#include "suggest/core/layout/proximity_info_utils.h"

// Thanks to https://stackoverflow.com/a/32698993
namespace insmat {
    AK_FORCE_INLINE float max(float a, float b) {
        return ((a) > (b)) ? (a) : (b);
    }

    AK_FORCE_INLINE float min(float a, float b) {
        return ((a) > (b)) ? (b) : (a);
    }

    AK_FORCE_INLINE float section(float h, float r = 1) // returns the positive root of intersection of line y = h with circle centered at the origin and radius r
    {
        ASSERT(r >= 0); // assume r is positive, leads to some simplifications in the formula below (can factor out r from the square root)
        return (h < r)? sqrt(r * r - h * h) : 0; // http://www.wolframalpha.com/input/?i=r+*+sin%28acos%28x+%2F+r%29%29+%3D+h
    }

    AK_FORCE_INLINE float g(float x, float h, float r = 1) // indefinite integral of circle segment
    {
        return .5f * (sqrt(1 - x * x / (r * r)) * x * r + r * r * asin(x / r) - 2 * h * x); // http://www.wolframalpha.com/input/?i=r+*+sin%28acos%28x+%2F+r%29%29+-+h
    }

    AK_FORCE_INLINE float area(float x0, float x1, float h, float r) // area of intersection of an infinitely tall box with left edge at x0, right edge at x1, bottom edge at h and top edge at infinity, with circle centered at the origin with radius r
    {
        if(x0 > x1)
            std::swap(x0, x1); // this must be sorted otherwise we get negative area
        float s = section(h, r);
        return g(max(-s, min(s, x1)), h, r) - g(max(-s, min(s, x0)), h, r); // integrate the area
    }

    AK_FORCE_INLINE float area(float x0, float x1, float y0, float y1, float r) // area of the intersection of a finite box with a circle centered at the origin with radius r
    {
        if(y0 > y1)
            std::swap(y0, y1); // this will simplify the reasoning
        if(y0 < 0) {
            if(y1 < 0)
                return area(x0, x1, -y0, -y1, r); // the box is completely under, just flip it above and try again
            else
                return area(x0, x1, 0, -y0, r) + area(x0, x1, 0, y1, r); // the box is both above and below, divide it to two boxes and go again
        } else {
            ASSERT(y1 >= 0); // y0 >= 0, which means that y1 >= 0 also (y1 >= y0) because of the swap at the beginning
            return area(x0, x1, y0, r) - area(x0, x1, y1, r); // area of the lower box minus area of the higher box
        }
    }

    AK_FORCE_INLINE float area(float x0, float x1, float y0, float y1, float cx, float cy, float r) // area of the intersection of a general box with a general circle
    {
        x0 -= cx; x1 -= cx;
        y0 -= cy; y1 -= cy;
        // get rid of the circle center

        return area(x0, x1, y0, y1, r);
    }
}

namespace latinime {

class ProximityInfo {
 public:
    ProximityInfo(JNIEnv *env, const int keyboardWidth, const int keyboardHeight,
            const int gridWidth, const int gridHeight,
            const int mostCommonKeyWidth, const int mostCommonKeyHeight,
            const jintArray proximityChars, const int keyCount, const jintArray keyXCoordinates,
            const jintArray keyYCoordinates, const jintArray keyWidths, const jintArray keyHeights,
            const jintArray keyCharCodes, const jfloatArray sweetSpotCenterXs,
            const jfloatArray sweetSpotCenterYs, const jfloatArray sweetSpotRadii);
    ~ProximityInfo();
    bool hasSpaceProximity(const int x, const int y) const;
    float getNormalizedSquaredDistanceFromCenterFloatG(
            const int keyId, const int x, const int y, const bool isGeometric) const;
    int getCodePointOf(const int keyIndex) const;
    int getOriginalCodePointOf(const int keyIndex) const;
    bool hasSweetSpotData(const int keyIndex) const {
        // When there are no calibration data for a key,
        // the radius of the key is assigned to zero.
        return mSweetSpotRadii[keyIndex] > 0.0f;
    }
    float getSweetSpotRadiiAt(int keyIndex) const { return mSweetSpotRadii[keyIndex]; }
    float getSweetSpotCenterXAt(int keyIndex) const { return mSweetSpotCenterXs[keyIndex]; }
    float getSweetSpotCenterYAt(int keyIndex) const { return mSweetSpotCenterYs[keyIndex]; }
    bool hasTouchPositionCorrectionData() const { return HAS_TOUCH_POSITION_CORRECTION_DATA; }
    int getMostCommonKeyWidth() const { return MOST_COMMON_KEY_WIDTH; }
    int getMostCommonKeyWidthSquare() const { return MOST_COMMON_KEY_WIDTH_SQUARE; }
    float getNormalizedSquaredMostCommonKeyHypotenuse() const {
        return NORMALIZED_SQUARED_MOST_COMMON_KEY_HYPOTENUSE;
    }
    int getKeyCount() const { return KEY_COUNT; }
    int getCellHeight() const { return CELL_HEIGHT; }
    int getCellWidth() const { return CELL_WIDTH; }
    int getGridWidth() const { return GRID_WIDTH; }
    int getGridHeight() const { return GRID_HEIGHT; }
    int getKeyboardWidth() const { return KEYBOARD_WIDTH; }
    int getKeyboardHeight() const { return KEYBOARD_HEIGHT; }
    float getKeyboardHypotenuse() const { return KEYBOARD_HYPOTENUSE; }

    int getKeyCenterXOfKeyIdG(
            const int keyId, const int referencePointX, const bool isGeometric) const;
    int getKeyCenterYOfKeyIdG(
            const int keyId, const int referencePointY, const bool isGeometric) const;
    int getKeyKeyDistanceG(int keyId0, int keyId1) const;

    AK_FORCE_INLINE void initializeProximities(const int *const inputCodes,
            const int *const inputXCoordinates, const int *const inputYCoordinates,
            const int inputSize, int *allInputCodes, const std::vector<int> *locale) const {
        ProximityInfoUtils::initializeProximities(inputCodes, inputXCoordinates, inputYCoordinates,
                inputSize, mKeyXCoordinates, mKeyYCoordinates, mKeyWidths, mKeyHeights,
                mProximityCharsArray, CELL_HEIGHT, CELL_WIDTH, GRID_WIDTH, MOST_COMMON_KEY_WIDTH,
                KEY_COUNT, locale, &mLowerCodePointToKeyMap, allInputCodes);
    }

    AK_FORCE_INLINE int getKeyIndexOf(const int c) const {
        return ProximityInfoUtils::getKeyIndexOf(KEY_COUNT, c, &mLowerCodePointToKeyMap);
    }

    AK_FORCE_INLINE bool isCodePointOnKeyboard(const int codePoint) const {
        return getKeyIndexOf(codePoint) != NOT_AN_INDEX;
    }

    AK_FORCE_INLINE std::vector<float> decomposeTapPosition(const int tapX, const int tapY) const {
        std::vector<float> percentages(KEY_COUNT, 0.0f);

        float tapRadius = MOST_COMMON_KEY_WIDTH * 0.292f;
        float totalArea = M_PI * ((float)(tapRadius * tapRadius));

        bool anySet = false;
        for(int key = 0; key < KEY_COUNT; key++) {
            const int left = mKeyXCoordinates[key];
            const int top = mKeyYCoordinates[key];
            const int right = left + mKeyWidths[key] + 1;
            const int bottom = top + mKeyHeights[key];

            percentages[key] = insmat::area(left, right, bottom, top, tapX, tapY, tapRadius) / totalArea;

            if(percentages[key] > 0.05f) {
                anySet = true;
            }
        }

        if(!anySet) {
            // Fallback - have to pick the closest key
            AKLOGE("FALLBACK - Have to pick closest key");
            int closestKey = -1;
            int minDistance = 1000000;
            for(int key = 0; key < KEY_COUNT; key++) {
                const int keyX = mKeyXCoordinates[key];
                const int keyY = mKeyYCoordinates[key];

                const int distance = (keyX - tapX) * (keyX - tapX) + (keyY - tapY) * (keyY - tapY);
                if(distance < minDistance) {
                    minDistance = distance;
                    closestKey = key;
                }
            }

            if(closestKey != -1) {
                percentages[closestKey] = 1.0f;
            } else {
                AKLOGE("Failed to find even the closest key!");
            }
        }

        return percentages;
    }

    AK_FORCE_INLINE int getKeyCodePoint(const int key) const {
        return mKeyCodePoints[key];
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ProximityInfo);

    void initializeG();

    const int GRID_WIDTH;
    const int GRID_HEIGHT;
    const int MOST_COMMON_KEY_WIDTH;
    const int MOST_COMMON_KEY_WIDTH_SQUARE;
    const float NORMALIZED_SQUARED_MOST_COMMON_KEY_HYPOTENUSE;
    const int CELL_WIDTH;
    const int CELL_HEIGHT;
    const int KEY_COUNT;
    const int KEYBOARD_WIDTH;
    const int KEYBOARD_HEIGHT;
    const float KEYBOARD_HYPOTENUSE;
    const bool HAS_TOUCH_POSITION_CORRECTION_DATA;
    int *mProximityCharsArray;
    int mKeyXCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyYCoordinates[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyWidths[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyHeights[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyCodePoints[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterXs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotCenterYs[MAX_KEY_COUNT_IN_A_KEYBOARD];
    // Sweet spots for geometric input. Note that we have extra sweet spots only for Y coordinates.
    float mSweetSpotCenterYsG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    float mSweetSpotRadii[MAX_KEY_COUNT_IN_A_KEYBOARD];
    std::unordered_map<int, int> mLowerCodePointToKeyMap;
    int mKeyIndexToOriginalCodePoint[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyIndexToLowerCodePointG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mCenterXsG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mCenterYsG[MAX_KEY_COUNT_IN_A_KEYBOARD];
    int mKeyKeyDistancesG[MAX_KEY_COUNT_IN_A_KEYBOARD][MAX_KEY_COUNT_IN_A_KEYBOARD];
};
} // namespace latinime
#endif // LATINIME_PROXIMITY_INFO_H
