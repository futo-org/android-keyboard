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

package com.android.inputmethod.keyboard;

public final class MoreKeysDetector extends KeyDetector {
    private final int mSlideAllowanceSquare;
    private final int mSlideAllowanceSquareTop;

    public MoreKeysDetector(float slideAllowance) {
        super(/* keyHysteresisDistance */0);
        mSlideAllowanceSquare = (int)(slideAllowance * slideAllowance);
        // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
        mSlideAllowanceSquareTop = mSlideAllowanceSquare * 2;
    }

    @Override
    public boolean alwaysAllowsSlidingInput() {
        return true;
    }

    @Override
    public Key detectHitKey(int x, int y) {
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        Key nearestKey = null;
        int nearestDist = (y < 0) ? mSlideAllowanceSquareTop : mSlideAllowanceSquare;
        for (final Key key : getKeyboard().getKeys()) {
            final int dist = key.squaredDistanceToEdge(touchX, touchY);
            if (dist < nearestDist) {
                nearestKey = key;
                nearestDist = dist;
            }
        }
        return nearestKey;
    }
}
