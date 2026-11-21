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

package org.futo.inputmethod.keyboard;

public final class MoreKeysDetector extends KeyDetector {
    private final float mSlideAllowance;
    private final float mSlideAllowanceTop;

    public MoreKeysDetector(float slideAllowance) {
        super();
        mSlideAllowance = slideAllowance;
        // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
        mSlideAllowanceTop = (mSlideAllowance * 1.414f);
    }

    @Override
    public boolean alwaysAllowsKeySelectionByDraggingFinger() {
        return true;
    }

    private int mExtraAllowance = 0;
    public void setExtraAllowance(final int extraAllowance) {
        mExtraAllowance = extraAllowance;
    }

    // How far below the normal touch point the finger started (it pressed low on a tall key).
    // Touches in that band below the panel count as if they were on the bottom row, so the
    // key under the finger is still selected and sliding sideways still works.
    private int mVerticalShift = 0;
    public void setVerticalShift(final int verticalShift) {
        mVerticalShift = Math.max(0, verticalShift);
    }

    @Override
    public Key detectHitKey(final int x, final int y) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return null;
        }
        final int touchX = getTouchX(x);
        int touchY = getTouchY(y);
        final int bottom = keyboard.mOccupiedHeight;
        if (mVerticalShift > 0 && touchY >= bottom) {
            touchY = Math.max(bottom - 1, touchY - mVerticalShift);
        }

        Key nearestKey = null;
        float nearestDist = ((y < 0) ? mSlideAllowanceTop : mSlideAllowance) + mExtraAllowance;
        for (final Key key : keyboard.getSortedKeys()) {
            final float dist = key.distanceToEdge(touchX, touchY);
            if (dist < nearestDist) {
                nearestKey = key;
                nearestDist = dist;
            }
        }
        return nearestKey;
    }
}
