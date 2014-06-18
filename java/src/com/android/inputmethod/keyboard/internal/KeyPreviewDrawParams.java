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

import android.content.res.TypedArray;
import android.view.View;

import com.android.inputmethod.latin.R;

public final class KeyPreviewDrawParams {
    // XML attributes of {@link MainKeyboardView}.
    public final int mPreviewOffset;
    public final int mPreviewHeight;
    public final int mPreviewBackgroundResId;
    private int mShowUpDuration;
    private int mDismissDuration;
    private float mShowUpStartScale;
    private float mDismissEndScale;
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

    public void setAnimationParams(final float showUpStartScale, final int showUpDuration,
            final float dismissEndScale, final int dismissDuration) {
        mShowUpStartScale = showUpStartScale;
        mShowUpDuration = showUpDuration;
        mDismissEndScale = dismissEndScale;
        mDismissDuration = dismissDuration;
    }

    public float getShowUpStartScale() {
        return mShowUpStartScale;
    }

    public int getShowUpDuration() {
        return mShowUpDuration;
    }

    public float getDismissEndScale() {
        return mDismissEndScale;
    }

    public int getDismissDuration() {
        return mDismissDuration;
    }
}
