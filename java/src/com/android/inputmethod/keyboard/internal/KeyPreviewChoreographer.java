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

package com.android.inputmethod.keyboard.internal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.ViewLayoutUtils;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This class controls pop up key previews. This class decides:
 * - what kind of key previews should be shown.
 * - where key previews should be placed.
 * - how key previews should be shown and dismissed.
 */
public final class KeyPreviewChoreographer {
    // Free {@link TextView} pool that can be used for key preview.
    private final ArrayDeque<TextView> mFreeKeyPreviewTextViews = new ArrayDeque<>();
    // Map from {@link Key} to {@link TextView} that is currently being displayed as key preview.
    private final HashMap<Key,TextView> mShowingKeyPreviewTextViews = new HashMap<>();

    private final KeyPreviewDrawParams mParams;

    public KeyPreviewChoreographer(final KeyPreviewDrawParams params) {
        mParams = params;
    }

    public TextView getKeyPreviewTextView(final Key key, final ViewGroup placerView) {
        TextView previewTextView = mShowingKeyPreviewTextViews.remove(key);
        if (previewTextView != null) {
            return previewTextView;
        }
        previewTextView = mFreeKeyPreviewTextViews.poll();
        if (previewTextView != null) {
            return previewTextView;
        }
        final Context context = placerView.getContext();
        if (mParams.mLayoutId != 0) {
            previewTextView = (TextView)LayoutInflater.from(context)
                    .inflate(mParams.mLayoutId, null);
        } else {
            previewTextView = new TextView(context);
        }
        placerView.addView(previewTextView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0));
        return previewTextView;
    }

    public boolean isShowingKeyPreview(final Key key) {
        return mShowingKeyPreviewTextViews.containsKey(key);
    }

    public void dismissAllKeyPreviews() {
        for (final Key key : new HashSet<>(mShowingKeyPreviewTextViews.keySet())) {
            dismissKeyPreview(key, false /* withAnimation */);
        }
    }

    public void dismissKeyPreview(final Key key, final boolean withAnimation) {
        if (key == null) {
            return;
        }
        final TextView previewTextView = mShowingKeyPreviewTextViews.get(key);
        if (previewTextView == null) {
            return;
        }
        final Object tag = previewTextView.getTag();
        if (withAnimation) {
            if (tag instanceof KeyPreviewAnimations) {
                final KeyPreviewAnimations animation = (KeyPreviewAnimations)tag;
                animation.startDismiss();
                return;
            }
        }
        // Dismiss preview without animation.
        mShowingKeyPreviewTextViews.remove(key);
        if (tag instanceof Animator) {
            ((Animator)tag).cancel();
        }
        previewTextView.setTag(null);
        previewTextView.setVisibility(View.INVISIBLE);
        mFreeKeyPreviewTextViews.add(previewTextView);
    }

    // Background state set
    private static final int[][][] KEY_PREVIEW_BACKGROUND_STATE_TABLE = {
        { // STATE_MIDDLE
            {},
            { R.attr.state_has_morekeys }
        },
        { // STATE_LEFT
            { R.attr.state_left_edge },
            { R.attr.state_left_edge, R.attr.state_has_morekeys }
        },
        { // STATE_RIGHT
            { R.attr.state_right_edge },
            { R.attr.state_right_edge, R.attr.state_has_morekeys }
        }
    };
    private static final int STATE_MIDDLE = 0;
    private static final int STATE_LEFT = 1;
    private static final int STATE_RIGHT = 2;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_HAS_MOREKEYS = 1;

    public void placeKeyPreview(final Key key, final TextView previewTextView,
            final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams,
            final int keyboardViewWidth, final int[] originCoords) {
        previewTextView.setTextColor(drawParams.mPreviewTextColor);
        final Drawable background = previewTextView.getBackground();
        final String label = key.getPreviewLabel();
        // What we show as preview should match what we show on a key top in onDraw().
        if (label != null) {
            // TODO Should take care of temporaryShiftLabel here.
            previewTextView.setCompoundDrawables(null, null, null, null);
            previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    key.selectPreviewTextSize(drawParams));
            previewTextView.setTypeface(key.selectPreviewTypeface(drawParams));
            previewTextView.setText(label);
        } else {
            previewTextView.setCompoundDrawables(null, null, null, key.getPreviewIcon(iconsSet));
            previewTextView.setText(null);
        }

        previewTextView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mParams.setGeometry(previewTextView);
        final int previewWidth = previewTextView.getMeasuredWidth();
        final int previewHeight = mParams.mPreviewHeight;
        final int keyDrawWidth = key.getDrawWidth();
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int statePosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2
                + CoordinateUtils.x(originCoords);
        if (previewX < 0) {
            previewX = 0;
            statePosition = STATE_LEFT;
        } else if (previewX > keyboardViewWidth - previewWidth) {
            previewX = keyboardViewWidth - previewWidth;
            statePosition = STATE_RIGHT;
        } else {
            statePosition = STATE_MIDDLE;
        }
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        final int previewY = key.getY() - previewHeight + mParams.mPreviewOffset
                + CoordinateUtils.y(originCoords);

        if (background != null) {
            final int hasMoreKeys = (key.getMoreKeys() != null) ? STATE_HAS_MOREKEYS : STATE_NORMAL;
            background.setState(KEY_PREVIEW_BACKGROUND_STATE_TABLE[statePosition][hasMoreKeys]);
        }
        ViewLayoutUtils.placeViewAt(
                previewTextView, previewX, previewY, previewWidth, previewHeight);
        previewTextView.setPivotX(previewWidth / 2.0f);
        previewTextView.setPivotY(previewHeight);
    }

    public void showKeyPreview(final Key key, final TextView previewTextView,
            final boolean withAnimation) {
        if (!withAnimation) {
            previewTextView.setVisibility(View.VISIBLE);
            mShowingKeyPreviewTextViews.put(key, previewTextView);
            return;
        }

        // Show preview with animation.
        final Animator showUpAnimation = createShowUpAniation(key, previewTextView);
        final Animator dismissAnimation = createDismissAnimation(key, previewTextView);
        final KeyPreviewAnimations animation = new KeyPreviewAnimations(
                showUpAnimation, dismissAnimation);
        previewTextView.setTag(animation);
        animation.startShowUp();
    }

    private static final float KEY_PREVIEW_SHOW_UP_END_SCALE = 1.0f;
    private static final AccelerateInterpolator ACCELERATE_INTERPOLATOR =
            new AccelerateInterpolator();
    private static final DecelerateInterpolator DECELERATE_INTERPOLATOR =
            new DecelerateInterpolator();

    private Animator createShowUpAniation(final Key key, final TextView previewTextView) {
        // TODO: Optimization for no scale animation and no duration.
        final ObjectAnimator scaleXAnimation = ObjectAnimator.ofFloat(
                previewTextView, View.SCALE_X, mParams.getShowUpStartScale(),
                KEY_PREVIEW_SHOW_UP_END_SCALE);
        final ObjectAnimator scaleYAnimation = ObjectAnimator.ofFloat(
                previewTextView, View.SCALE_Y, mParams.getShowUpStartScale(),
                KEY_PREVIEW_SHOW_UP_END_SCALE);
        final AnimatorSet showUpAnimation = new AnimatorSet();
        showUpAnimation.play(scaleXAnimation).with(scaleYAnimation);
        showUpAnimation.setDuration(mParams.getShowUpDuration());
        showUpAnimation.setInterpolator(DECELERATE_INTERPOLATOR);
        showUpAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(final Animator animation) {
                showKeyPreview(key, previewTextView, false /* withAnimation */);
            }
        });
        return showUpAnimation;
    }

    private Animator createDismissAnimation(final Key key, final TextView previewTextView) {
        // TODO: Optimization for no scale animation and no duration.
        final ObjectAnimator scaleXAnimation = ObjectAnimator.ofFloat(
                previewTextView, View.SCALE_X, mParams.getDismissEndScale());
        final ObjectAnimator scaleYAnimation = ObjectAnimator.ofFloat(
                previewTextView, View.SCALE_Y, mParams.getDismissEndScale());
        final AnimatorSet dismissAnimation = new AnimatorSet();
        dismissAnimation.play(scaleXAnimation).with(scaleYAnimation);
        final int dismissDuration = Math.min(
                mParams.getDismissDuration(), mParams.getLingerTimeout());
        dismissAnimation.setDuration(dismissDuration);
        dismissAnimation.setInterpolator(ACCELERATE_INTERPOLATOR);
        dismissAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                dismissKeyPreview(key, false /* withAnimation */);
            }
        });
        return dismissAnimation;
    }

    private static class KeyPreviewAnimations extends AnimatorListenerAdapter {
        private final Animator mShowUpAnimation;
        private final Animator mDismissAnimation;

        public KeyPreviewAnimations(final Animator showUpAnimation,
                final Animator dismissAnimation) {
            mShowUpAnimation = showUpAnimation;
            mDismissAnimation = dismissAnimation;
        }

        public void startShowUp() {
            mShowUpAnimation.start();
        }

        public void startDismiss() {
            if (mShowUpAnimation.isRunning()) {
                mShowUpAnimation.addListener(this);
                return;
            }
            mDismissAnimation.start();
        }

        @Override
        public void onAnimationEnd(final Animator animation) {
            mDismissAnimation.start();
        }
    }
}
