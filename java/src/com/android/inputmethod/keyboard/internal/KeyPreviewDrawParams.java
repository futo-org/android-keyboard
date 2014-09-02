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

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.TypedArray;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.android.inputmethod.latin.R;

public final class KeyPreviewDrawParams {
    // XML attributes of {@link MainKeyboardView}.
    public final int mPreviewOffset;
    public final int mPreviewHeight;
    public final int mPreviewBackgroundResId;
    private final int mShowUpAnimatorResId;
    private final int mDismissAnimatorResId;
    private boolean mHasCustomAnimationParams;
    private int mShowUpDuration;
    private int mDismissDuration;
    private float mShowUpStartXScale;
    private float mShowUpStartYScale;
    private float mDismissEndXScale;
    private float mDismissEndYScale;
    private int mLingerTimeout;
    private boolean mShowPopup = true;

    // The graphical geometry of the key preview.
    // <-width->
    // +-------+   ^
    // |       |   |
    // |preview| height (visible)
    // |       |   |
    // +       + ^ v
    //  \     /  |offset
    // +-\   /-+ v
    // |  +-+  |
    // |parent |
    // |    key|
    // +-------+
    // The background of a {@link TextView} being used for a key preview may have invisible
    // paddings. To align the more keys keyboard panel's visible part with the visible part of
    // the background, we need to record the width and height of key preview that don't include
    // invisible paddings.
    private int mVisibleWidth;
    private int mVisibleHeight;
    // The key preview may have an arbitrary offset and its background that may have a bottom
    // padding. To align the more keys keyboard and the key preview we also need to record the
    // offset between the top edge of parent key and the bottom of the visible part of key
    // preview background.
    private int mVisibleOffset;

    public KeyPreviewDrawParams(final TypedArray mainKeyboardViewAttr) {
        mPreviewOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
                R.styleable.MainKeyboardView_keyPreviewOffset, 0);
        mPreviewHeight = mainKeyboardViewAttr.getDimensionPixelSize(
                R.styleable.MainKeyboardView_keyPreviewHeight, 0);
        mPreviewBackgroundResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_keyPreviewBackground, 0);
        mLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_keyPreviewLingerTimeout, 0);
        mShowUpAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_keyPreviewShowUpAnimator, 0);
        mDismissAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_keyPreviewDismissAnimator, 0);
    }

    public void setVisibleOffset(final int previewVisibleOffset) {
        mVisibleOffset = previewVisibleOffset;
    }

    public int getVisibleOffset() {
        return mVisibleOffset;
    }

    public void setGeometry(final View previewTextView) {
        final int previewWidth = previewTextView.getMeasuredWidth();
        final int previewHeight = mPreviewHeight;
        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        mVisibleWidth = previewWidth - previewTextView.getPaddingLeft()
                - previewTextView.getPaddingRight();
        mVisibleHeight = previewHeight - previewTextView.getPaddingTop()
                - previewTextView.getPaddingBottom();
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        setVisibleOffset(mPreviewOffset - previewTextView.getPaddingBottom());
    }

    public int getVisibleWidth() {
        return mVisibleWidth;
    }

    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    public void setPopupEnabled(final boolean enabled, final int lingerTimeout) {
        mShowPopup = enabled;
        mLingerTimeout = lingerTimeout;
    }

    public boolean isPopupEnabled() {
        return mShowPopup;
    }

    public int getLingerTimeout() {
        return mLingerTimeout;
    }

    public void setAnimationParams(final boolean hasCustomAnimationParams,
            final float showUpStartXScale, final float showUpStartYScale, final int showUpDuration,
            final float dismissEndXScale, final float dismissEndYScale, final int dismissDuration) {
        mHasCustomAnimationParams = hasCustomAnimationParams;
        mShowUpStartXScale = showUpStartXScale;
        mShowUpStartYScale = showUpStartYScale;
        mShowUpDuration = showUpDuration;
        mDismissEndXScale = dismissEndXScale;
        mDismissEndYScale = dismissEndYScale;
        mDismissDuration = dismissDuration;
    }

    private static final float KEY_PREVIEW_SHOW_UP_END_SCALE = 1.0f;
    private static final AccelerateInterpolator ACCELERATE_INTERPOLATOR =
            new AccelerateInterpolator();
    private static final DecelerateInterpolator DECELERATE_INTERPOLATOR =
            new DecelerateInterpolator();

    public Animator createShowUpAnimator(final View target) {
        if (mHasCustomAnimationParams) {
            final ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(
                    target, View.SCALE_X, mShowUpStartXScale,
                    KEY_PREVIEW_SHOW_UP_END_SCALE);
            final ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(
                    target, View.SCALE_Y, mShowUpStartYScale,
                    KEY_PREVIEW_SHOW_UP_END_SCALE);
            final AnimatorSet showUpAnimator = new AnimatorSet();
            showUpAnimator.play(scaleXAnimator).with(scaleYAnimator);
            showUpAnimator.setDuration(mShowUpDuration);
            showUpAnimator.setInterpolator(DECELERATE_INTERPOLATOR);
            return showUpAnimator;
        }
        final Animator animator = AnimatorInflater.loadAnimator(
                target.getContext(), mShowUpAnimatorResId);
        animator.setTarget(target);
        animator.setInterpolator(DECELERATE_INTERPOLATOR);
        return animator;
    }

    public Animator createDismissAnimator(final View target) {
        if (mHasCustomAnimationParams) {
            final ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(
                    target, View.SCALE_X, mDismissEndXScale);
            final ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(
                    target, View.SCALE_Y, mDismissEndYScale);
            final AnimatorSet dismissAnimator = new AnimatorSet();
            dismissAnimator.play(scaleXAnimator).with(scaleYAnimator);
            final int dismissDuration = Math.min(mDismissDuration, mLingerTimeout);
            dismissAnimator.setDuration(dismissDuration);
            dismissAnimator.setInterpolator(ACCELERATE_INTERPOLATOR);
            return dismissAnimator;
        }
        final Animator animator = AnimatorInflater.loadAnimator(
                target.getContext(), mDismissAnimatorResId);
        animator.setTarget(target);
        animator.setInterpolator(ACCELERATE_INTERPOLATOR);
        return animator;
    }
}
