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

package com.android.inputmethod.latin.setup;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.latin.R;

public final class SetupStartIndicatorView extends LinearLayout {
    public SetupStartIndicatorView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.setup_start_indicator_label, this);

        final LabelView labelView = (LabelView)findViewById(R.id.setup_start_label);
        labelView.setIndicatorView(findViewById(R.id.setup_start_indicator));
    }

    public static final class LabelView extends TextView {
        private View mIndicatorView;

        public LabelView(final Context context, final AttributeSet attrs) {
            super(context, attrs);
        }

        public void setIndicatorView(final View indicatorView) {
            mIndicatorView = indicatorView;
        }

        // TODO: Once we stop supporting ICS, uncomment {@link #setPressed(boolean)} method and
        // remove this method.
        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            for (final int state : getDrawableState()) {
                if (state == android.R.attr.state_pressed) {
                    updateIndicatorView(true /* pressed */);
                    return;
                }
            }
            updateIndicatorView(false /* pressed */);
        }

        // TODO: Once we stop supporting ICS, uncomment this method and remove
        // {@link #drawableStateChanged()} method.
//        @Override
//        public void setPressed(final boolean pressed) {
//            super.setPressed(pressed);
//            updateIndicatorView(pressed);
//        }

        private void updateIndicatorView(final boolean pressed) {
            if (mIndicatorView != null) {
                mIndicatorView.setPressed(pressed);
                mIndicatorView.invalidate();
            }
        }
    }

    public static final class IndicatorView extends View {
        private final Path mIndicatorPath = new Path();
        private final Paint mIndicatorPaint = new Paint();
        private final ColorStateList mIndicatorColor;

        public IndicatorView(final Context context, final AttributeSet attrs) {
            super(context, attrs);
            mIndicatorColor = getResources().getColorStateList(
                    R.color.setup_step_action_background);
            mIndicatorPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);
            final int layoutDirection = ViewCompat.getLayoutDirection(this);
            final int width = getWidth();
            final int height = getHeight();
            final float halfHeight = height / 2.0f;
            final Path path = mIndicatorPath;
            path.rewind();
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
                // Left arrow
                path.moveTo(width, 0.0f);
                path.lineTo(0.0f, halfHeight);
                path.lineTo(width, height);
            } else { // LAYOUT_DIRECTION_LTR
                // Right arrow
                path.moveTo(0.0f, 0.0f);
                path.lineTo(width, halfHeight);
                path.lineTo(0.0f, height);
            }
            path.close();
            final int[] stateSet = getDrawableState();
            final int color = mIndicatorColor.getColorForState(stateSet, 0);
            mIndicatorPaint.setColor(color);
            canvas.drawPath(path, mIndicatorPaint);
        }
    }
}
