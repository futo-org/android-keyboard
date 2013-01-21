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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.CoordinateUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;

public final class PreviewPlacerView extends RelativeLayout {
    private final int[] mKeyboardViewOrigin = CoordinateUtils.newInstance();

    // TODO: Move these AbstractDrawingPvreiew objects to MainKeyboardView.
    private final GestureFloatingPreviewText mGestureFloatingPreviewText;
    private final GestureTrailsPreview mGestureTrailsPreview;
    private final SlidingKeyInputPreview mSlidingKeyInputPreview;

    public PreviewPlacerView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public PreviewPlacerView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context);
        setWillNotDraw(false);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        // TODO: mGestureFloatingPreviewText could be an instance of GestureFloatingPreviewText or
        // MultiGesturePreviewText, depending on the user's choice in the settings.
        mGestureFloatingPreviewText = new GestureFloatingPreviewText(this, mainKeyboardViewAttr);
        mGestureTrailsPreview = new GestureTrailsPreview(this, mainKeyboardViewAttr);
        mSlidingKeyInputPreview = new SlidingKeyInputPreview(this, mainKeyboardViewAttr);
        mainKeyboardViewAttr.recycle();

        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    public void setKeyboardViewGeometry(final int[] originCoords, final int width,
            final int height) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords);
        mGestureFloatingPreviewText.setKeyboardGeometry(originCoords, width, height);
        mGestureTrailsPreview.setKeyboardGeometry(originCoords, width, height);
        mSlidingKeyInputPreview.setKeyboardGeometry(originCoords, width, height);
    }

    // TODO: Move this method to MainKeyboardView
    public void setGesturePreviewMode(final boolean drawsGesturePreviewTrail,
            final boolean drawsGestureFloatingPreviewText) {
        mGestureFloatingPreviewText.setPreviewEnabled(drawsGestureFloatingPreviewText);
        mGestureTrailsPreview.setPreviewEnabled(drawsGesturePreviewTrail);
    }

    // TODO: Move this method to MainKeyboardView
    public void invalidatePointer(final PointerTracker tracker) {
        mGestureFloatingPreviewText.setPreviewPosition(tracker);
        mGestureTrailsPreview.setPreviewPosition(tracker);
    }

    // TODO: Move this method to MainKeyboardView
    public void showSlidingKeyInputPreview(final PointerTracker tracker) {
        mSlidingKeyInputPreview.setPreviewPosition(tracker);
    }

    // TODO: Move this method to MainKeyboardView
    public void dismissSlidingKeyInputPreview() {
        mSlidingKeyInputPreview.dismissSlidingKeyInputPreview();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mGestureFloatingPreviewText.onDetachFromWindow();
        mGestureTrailsPreview.onDetachFromWindow();
        mSlidingKeyInputPreview.onDetachFromWindow();
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final int originX = CoordinateUtils.x(mKeyboardViewOrigin);
        final int originY = CoordinateUtils.y(mKeyboardViewOrigin);
        canvas.translate(originX, originY);
        mGestureFloatingPreviewText.drawPreview(canvas);
        mGestureTrailsPreview.drawPreview(canvas);
        mSlidingKeyInputPreview.drawPreview(canvas);
        canvas.translate(-originX, -originY);
    }

    // TODO: Move this method to MainKeyboardView.
    public void setGestureFloatingPreviewText(final SuggestedWords suggestedWords) {
        mGestureFloatingPreviewText.setSuggetedWords(suggestedWords);
    }
}
