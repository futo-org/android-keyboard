/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.view.View;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.CoordinateUtils;

/**
 * Draw rubber band preview graphics during sliding key input.
 */
public final class SlidingKeyInputPreview extends AbstractDrawingPreview {
    private boolean mShowSlidingKeyInputPreview;
    private final int[] mRubberBandFrom = CoordinateUtils.newInstance();
    private final int[] mRubberBandTo = CoordinateUtils.newInstance();

    public SlidingKeyInputPreview(final View drawingView, final TypedArray mainKeyboardViewAttr) {
        super(drawingView);
    }

    public void dismissSlidingKeyInputPreview() {
        mShowSlidingKeyInputPreview = false;
    }

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    @Override
    public void drawPreview(final Canvas canvas) {
        if (!isPreviewEnabled() || mShowSlidingKeyInputPreview == false) {
            return;
        }
        // TODO: Implement rubber band preview
    }

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    @Override
    public void setPreviewPosition(final PointerTracker tracker) {
        if (!tracker.isInSlidingKeyInputFromModifier()) {
            mShowSlidingKeyInputPreview = false;
            return;
        }
        tracker.getDownCoordinates(mRubberBandFrom);
        tracker.getLastCoordinates(mRubberBandTo);
        mShowSlidingKeyInputPreview = true;
        getDrawingView().invalidate();
    }
}
