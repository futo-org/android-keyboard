/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import com.android.inputmethod.latin.R;

/**
 * Used as the UI component of {@link TextDecorator}.
 */
public final class TextDecoratorUi implements TextDecoratorUiOperator {
    private static final boolean VISUAL_DEBUG = false;
    private static final int VISUAL_DEBUG_HIT_AREA_COLOR = 0x80ff8000;

    private final RelativeLayout mLocalRootView;
    private final CommitIndicatorView mCommitIndicatorView;
    private final AddToDictionaryIndicatorView mAddToDictionaryIndicatorView;
    private final PopupWindow mTouchEventWindow;
    private final View mTouchEventWindowClickListenerView;
    private final float mHitAreaMarginInPixels;

    /**
     * This constructor is designed to be called from {@link InputMethodService#setInputView(View)}.
     * Other usages are not supported.
     *
     * @param context the context of the input method.
     * @param inputView the view that is passed to {@link InputMethodService#setInputView(View)}.
     */
    public TextDecoratorUi(final Context context, final View inputView) {
        final Resources resources = context.getResources();
        final int hitAreaMarginInDP = resources.getInteger(
                R.integer.text_decorator_hit_area_margin_in_dp);
        mHitAreaMarginInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                hitAreaMarginInDP, resources.getDisplayMetrics());

        mLocalRootView = new RelativeLayout(context);
        mLocalRootView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        // TODO: Use #setBackground(null) for API Level >= 16.
        mLocalRootView.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        final ViewGroup contentView = getContentView(inputView);
        mCommitIndicatorView = new CommitIndicatorView(context);
        mAddToDictionaryIndicatorView = new AddToDictionaryIndicatorView(context);
        mLocalRootView.addView(mCommitIndicatorView);
        mLocalRootView.addView(mAddToDictionaryIndicatorView);
        if (contentView != null) {
            contentView.addView(mLocalRootView);
        }

        // This popup window is used to avoid the limitation that the input method is not able to
        // observe the touch events happening outside of InputMethodService.Insets#touchableRegion.
        // We don't use this popup window for rendering the UI for performance reasons though.
        mTouchEventWindow = new PopupWindow(context);
        if (VISUAL_DEBUG) {
            mTouchEventWindow.setBackgroundDrawable(new ColorDrawable(VISUAL_DEBUG_HIT_AREA_COLOR));
        } else {
            mTouchEventWindow.setBackgroundDrawable(null);
        }
        mTouchEventWindowClickListenerView = new View(context);
        mTouchEventWindow.setContentView(mTouchEventWindowClickListenerView);
    }

    @Override
    public void disposeUi() {
        if (mLocalRootView != null) {
            final ViewParent parent = mLocalRootView.getParent();
            if (parent != null && parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mLocalRootView);
            }
            mLocalRootView.removeAllViews();
        }
        if (mTouchEventWindow != null) {
            mTouchEventWindow.dismiss();
        }
    }

    @Override
    public void hideUi() {
        mCommitIndicatorView.setVisibility(View.GONE);
        mAddToDictionaryIndicatorView.setVisibility(View.GONE);
        mTouchEventWindow.dismiss();
    }

    @Override
    public void layoutUi(final boolean isCommitMode, final Matrix matrix,
            final RectF indicatorBounds, final RectF composingTextBounds) {
        final RectF indicatorBoundsInScreenCoordinates = new RectF();
        matrix.mapRect(indicatorBoundsInScreenCoordinates, indicatorBounds);
        mCommitIndicatorView.setBounds(indicatorBoundsInScreenCoordinates);
        mAddToDictionaryIndicatorView.setBounds(indicatorBoundsInScreenCoordinates);

        final RectF hitAreaBounds = new RectF(composingTextBounds);
        hitAreaBounds.union(indicatorBounds);
        final RectF hitAreaBoundsInScreenCoordinates = new RectF();
        matrix.mapRect(hitAreaBoundsInScreenCoordinates, hitAreaBounds);
        hitAreaBoundsInScreenCoordinates.inset(-mHitAreaMarginInPixels, -mHitAreaMarginInPixels);

        final int[] originScreen = new int[2];
        mLocalRootView.getLocationOnScreen(originScreen);
        final int viewOriginX = originScreen[0];
        final int viewOriginY = originScreen[1];

        final View toBeShown;
        final View toBeHidden;
        if (isCommitMode) {
            toBeShown = mCommitIndicatorView;
            toBeHidden = mAddToDictionaryIndicatorView;
        } else {
            toBeShown = mAddToDictionaryIndicatorView;
            toBeHidden = mCommitIndicatorView;
        }
        toBeShown.setX(indicatorBoundsInScreenCoordinates.left - viewOriginX);
        toBeShown.setY(indicatorBoundsInScreenCoordinates.top - viewOriginY);
        toBeShown.setVisibility(View.VISIBLE);
        toBeHidden.setVisibility(View.GONE);

        if (mTouchEventWindow.isShowing()) {
            mTouchEventWindow.update((int)hitAreaBoundsInScreenCoordinates.left - viewOriginX,
                    (int)hitAreaBoundsInScreenCoordinates.top - viewOriginY,
                    (int)hitAreaBoundsInScreenCoordinates.width(),
                    (int)hitAreaBoundsInScreenCoordinates.height());
        } else {
            mTouchEventWindow.setWidth((int)hitAreaBoundsInScreenCoordinates.width());
            mTouchEventWindow.setHeight((int)hitAreaBoundsInScreenCoordinates.height());
            mTouchEventWindow.showAtLocation(mLocalRootView, Gravity.NO_GRAVITY,
                    (int)hitAreaBoundsInScreenCoordinates.left - viewOriginX,
                    (int)hitAreaBoundsInScreenCoordinates.top - viewOriginY);
        }
    }

    @Override
    public void setOnClickListener(final Runnable listener) {
        mTouchEventWindowClickListenerView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View arg0) {
                listener.run();
            }
        });
    }

    private static class IndicatorView extends View {
        private final Path mPath;
        private final Path mTmpPath = new Path();
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix mMatrix = new Matrix();
        private final int mBackgroundColor;
        private final int mForegroundColor;
        private final RectF mBounds = new RectF();
        public IndicatorView(Context context, final int pathResourceId,
                final int sizeResourceId, final int backgroundColorResourceId,
                final int foregroundColroResourceId) {
            super(context);
            final Resources resources = context.getResources();
            mPath = createPath(resources, pathResourceId, sizeResourceId);
            mBackgroundColor = resources.getColor(backgroundColorResourceId);
            mForegroundColor = resources.getColor(foregroundColroResourceId);
        }

        public void setBounds(final RectF rect) {
            mBounds.set(rect);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            mPaint.setColor(mBackgroundColor);
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0.0f, 0.0f, mBounds.width(), mBounds.height(), mPaint);

            mMatrix.reset();
            mMatrix.postScale(mBounds.width(), mBounds.height());
            mPath.transform(mMatrix, mTmpPath);
            mPaint.setColor(mForegroundColor);
            canvas.drawPath(mTmpPath, mPaint);
        }

        private static Path createPath(final Resources resources, final int pathResourceId,
                final int sizeResourceId) {
            final int size = resources.getInteger(sizeResourceId);
            final float normalizationFactor = 1.0f / size;
            final int[] array = resources.getIntArray(pathResourceId);

            final Path path = new Path();
            for (int i = 0; i < array.length; i += 2) {
                if (i == 0) {
                    path.moveTo(array[i] * normalizationFactor, array[i + 1] * normalizationFactor);
                } else {
                    path.lineTo(array[i] * normalizationFactor, array[i + 1] * normalizationFactor);
                }
            }
            path.close();
            return path;
        }
    }

    private static ViewGroup getContentView(final View view) {
        final View rootView = view.getRootView();
        if (rootView == null) {
            return null;
        }

        final ViewGroup windowContentView = (ViewGroup)rootView.findViewById(android.R.id.content);
        if (windowContentView == null) {
            return null;
        }
        return windowContentView;
    }

    private static final class CommitIndicatorView extends TextDecoratorUi.IndicatorView {
        public CommitIndicatorView(final Context context) {
            super(context, R.array.text_decorator_commit_indicator_path,
                    R.integer.text_decorator_commit_indicator_path_size,
                    R.color.text_decorator_commit_indicator_background_color,
                    R.color.text_decorator_commit_indicator_foreground_color);
        }
    }

    private static final class AddToDictionaryIndicatorView extends TextDecoratorUi.IndicatorView {
        public AddToDictionaryIndicatorView(final Context context) {
            super(context, R.array.text_decorator_add_to_dictionary_indicator_path,
                    R.integer.text_decorator_add_to_dictionary_indicator_path_size,
                    R.color.text_decorator_add_to_dictionary_indicator_background_color,
                    R.color.text_decorator_add_to_dictionary_indicator_foreground_color);
        }
    }
}