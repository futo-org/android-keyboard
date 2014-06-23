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
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.android.inputmethod.keyboard.Key;
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
    // Free {@link KeyPreviewView} pool that can be used for key preview.
    private final ArrayDeque<KeyPreviewView> mFreeKeyPreviewViews = new ArrayDeque<>();
    // Map from {@link Key} to {@link KeyPreviewView} that is currently being displayed as key
    // preview.
    private final HashMap<Key,KeyPreviewView> mShowingKeyPreviewViews = new HashMap<>();

    private final KeyPreviewDrawParams mParams;

    public KeyPreviewChoreographer(final KeyPreviewDrawParams params) {
        mParams = params;
    }

    public KeyPreviewView getKeyPreviewView(final Key key, final ViewGroup placerView) {
        KeyPreviewView keyPreviewView = mShowingKeyPreviewViews.remove(key);
        if (keyPreviewView != null) {
            return keyPreviewView;
        }
        keyPreviewView = mFreeKeyPreviewViews.poll();
        if (keyPreviewView != null) {
            return keyPreviewView;
        }
        final Context context = placerView.getContext();
        keyPreviewView = new KeyPreviewView(context, null /* attrs */);
        keyPreviewView.setBackgroundResource(mParams.mPreviewBackgroundResId);
        placerView.addView(keyPreviewView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0));
        return keyPreviewView;
    }

    public boolean isShowingKeyPreview(final Key key) {
        return mShowingKeyPreviewViews.containsKey(key);
    }

    public void dismissAllKeyPreviews() {
        for (final Key key : new HashSet<>(mShowingKeyPreviewViews.keySet())) {
            dismissKeyPreview(key, false /* withAnimation */);
        }
    }

    public void dismissKeyPreview(final Key key, final boolean withAnimation) {
        if (key == null) {
            return;
        }
        final KeyPreviewView keyPreviewView = mShowingKeyPreviewViews.get(key);
        if (keyPreviewView == null) {
            return;
        }
        final Object tag = keyPreviewView.getTag();
        if (withAnimation) {
            if (tag instanceof KeyPreviewAnimations) {
                final KeyPreviewAnimations animation = (KeyPreviewAnimations)tag;
                animation.startDismiss();
                return;
            }
        }
        // Dismiss preview without animation.
        mShowingKeyPreviewViews.remove(key);
        if (tag instanceof Animator) {
            ((Animator)tag).cancel();
        }
        keyPreviewView.setTag(null);
        keyPreviewView.setVisibility(View.INVISIBLE);
        mFreeKeyPreviewViews.add(keyPreviewView);
    }

    public void placeAndShowKeyPreview(final Key key, final KeyboardIconsSet iconsSet,
            final KeyDrawParams drawParams, final int keyboardViewWidth, final int[] keyboardOrigin,
            final ViewGroup placerView, final boolean withAnimation) {
        final KeyPreviewView keyPreviewView = getKeyPreviewView(key, placerView);
        placeKeyPreview(
                key, keyPreviewView, iconsSet, drawParams, keyboardViewWidth, keyboardOrigin);
        showKeyPreview(key, keyPreviewView, withAnimation);
    }

    private void placeKeyPreview(final Key key, final KeyPreviewView keyPreviewView,
            final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams,
            final int keyboardViewWidth, final int[] originCoords) {
        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams);
        keyPreviewView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mParams.setGeometry(keyPreviewView);
        final int previewWidth = keyPreviewView.getMeasuredWidth();
        final int previewHeight = mParams.mPreviewHeight;
        final int keyDrawWidth = key.getDrawWidth();
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int keyPreviewPosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2
                + CoordinateUtils.x(originCoords);
        if (previewX < 0) {
            previewX = 0;
            keyPreviewPosition = KeyPreviewView.POSITION_LEFT;
        } else if (previewX > keyboardViewWidth - previewWidth) {
            previewX = keyboardViewWidth - previewWidth;
            keyPreviewPosition = KeyPreviewView.POSITION_RIGHT;
        } else {
            keyPreviewPosition = KeyPreviewView.POSITION_MIDDLE;
        }
        final boolean hasMoreKeys = (key.getMoreKeys() != null);
        keyPreviewView.setPreviewBackground(hasMoreKeys, keyPreviewPosition);
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        final int previewY = key.getY() - previewHeight + mParams.mPreviewOffset
                + CoordinateUtils.y(originCoords);

        ViewLayoutUtils.placeViewAt(
                keyPreviewView, previewX, previewY, previewWidth, previewHeight);
        keyPreviewView.setPivotX(previewWidth / 2.0f);
        keyPreviewView.setPivotY(previewHeight);
    }

    private void showKeyPreview(final Key key, final KeyPreviewView keyPreviewView,
            final boolean withAnimation) {
        if (!withAnimation) {
            keyPreviewView.setVisibility(View.VISIBLE);
            mShowingKeyPreviewViews.put(key, keyPreviewView);
            return;
        }

        // Show preview with animation.
        final Animator showUpAnimation = createShowUpAniation(key, keyPreviewView);
        final Animator dismissAnimation = createDismissAnimation(key, keyPreviewView);
        final KeyPreviewAnimations animation = new KeyPreviewAnimations(
                showUpAnimation, dismissAnimation);
        keyPreviewView.setTag(animation);
        animation.startShowUp();
    }

    private static final float KEY_PREVIEW_SHOW_UP_END_SCALE = 1.0f;
    private static final AccelerateInterpolator ACCELERATE_INTERPOLATOR =
            new AccelerateInterpolator();
    private static final DecelerateInterpolator DECELERATE_INTERPOLATOR =
            new DecelerateInterpolator();

    private Animator createShowUpAniation(final Key key, final KeyPreviewView keyPreviewView) {
        // TODO: Optimization for no scale animation and no duration.
        final ObjectAnimator scaleXAnimation = ObjectAnimator.ofFloat(
                keyPreviewView, View.SCALE_X, mParams.getShowUpStartScale(),
                KEY_PREVIEW_SHOW_UP_END_SCALE);
        final ObjectAnimator scaleYAnimation = ObjectAnimator.ofFloat(
                keyPreviewView, View.SCALE_Y, mParams.getShowUpStartScale(),
                KEY_PREVIEW_SHOW_UP_END_SCALE);
        final AnimatorSet showUpAnimation = new AnimatorSet();
        showUpAnimation.play(scaleXAnimation).with(scaleYAnimation);
        showUpAnimation.setDuration(mParams.getShowUpDuration());
        showUpAnimation.setInterpolator(DECELERATE_INTERPOLATOR);
        showUpAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(final Animator animation) {
                showKeyPreview(key, keyPreviewView, false /* withAnimation */);
            }
        });
        return showUpAnimation;
    }

    private Animator createDismissAnimation(final Key key, final KeyPreviewView keyPreviewView) {
        // TODO: Optimization for no scale animation and no duration.
        final ObjectAnimator scaleXAnimation = ObjectAnimator.ofFloat(
                keyPreviewView, View.SCALE_X, mParams.getDismissEndScale());
        final ObjectAnimator scaleYAnimation = ObjectAnimator.ofFloat(
                keyPreviewView, View.SCALE_Y, mParams.getDismissEndScale());
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
