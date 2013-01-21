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

package com.android.inputmethod.keyboard.internal;

import android.graphics.Canvas;
import android.view.View;

import com.android.inputmethod.keyboard.PointerTracker;

/**
 * Abstract base class for previews that are drawn on PreviewPlacerView, e.g.,
 * GestureFloatingPrevewText, GestureTrail, and SlidingKeyInputPreview.
 */
public abstract class AbstractDrawingPreview {
    private final View mDrawingView;
    private boolean mPreviewEnabled;

    protected AbstractDrawingPreview(final View drawingView) {
        mDrawingView = drawingView;
    }

    public final View getDrawingView() {
        return mDrawingView;
    }

    public final void setPreviewEnabled(final boolean enabled) {
        mPreviewEnabled = enabled;
    }

    public boolean isPreviewEnabled() {
        return mPreviewEnabled;
    }

    public void setKeyboardGeometry(final int[] originCoords, final int width, final int height) {
        // Default implementation is empty.
    }

    public void onDetachFromWindow() {
        // Default implementation is empty.
    }

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    public abstract void drawPreview(final Canvas canvas);

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    public abstract void setPreviewPosition(final PointerTracker tracker);
}
