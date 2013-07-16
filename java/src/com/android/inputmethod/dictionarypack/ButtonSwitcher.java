/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.Button;
import android.widget.FrameLayout;

import com.android.inputmethod.latin.R;

/**
 * A view that handles buttons inside it according to a status.
 */
public class ButtonSwitcher extends FrameLayout {
    public static final int NOT_INITIALIZED = -1;
    public static final int STATUS_NO_BUTTON = 0;
    public static final int STATUS_INSTALL = 1;
    public static final int STATUS_CANCEL = 2;
    public static final int STATUS_DELETE = 3;
    // One of the above
    private int mStatus = NOT_INITIALIZED;
    private int mAnimateToStatus = NOT_INITIALIZED;

    // Animation directions
    public static final int ANIMATION_IN = 1;
    public static final int ANIMATION_OUT = 2;

    private Button mInstallButton;
    private Button mCancelButton;
    private Button mDeleteButton;
    private DictionaryListInterfaceState mInterfaceState;
    private OnClickListener mOnClickListener;

    public ButtonSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonSwitcher(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void reset(final DictionaryListInterfaceState interfaceState) {
        mStatus = NOT_INITIALIZED;
        mAnimateToStatus = NOT_INITIALIZED;
        mInterfaceState = interfaceState;
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mInstallButton = (Button)findViewById(R.id.dict_install_button);
        mCancelButton = (Button)findViewById(R.id.dict_cancel_button);
        mDeleteButton = (Button)findViewById(R.id.dict_delete_button);
        setInternalOnClickListener(mOnClickListener);
        setButtonPositionWithoutAnimation(mStatus);
        if (mAnimateToStatus != NOT_INITIALIZED) {
            // We have been asked to animate before we were ready, so we took a note of it.
            // We are now ready: launch the animation.
            animateButtonPosition(mStatus, mAnimateToStatus);
            mStatus = mAnimateToStatus;
            mAnimateToStatus = NOT_INITIALIZED;
        }
    }

    private Button getButton(final int status) {
        switch(status) {
        case STATUS_INSTALL:
            return mInstallButton;
        case STATUS_CANCEL:
            return mCancelButton;
        case STATUS_DELETE:
            return mDeleteButton;
        default:
            return null;
        }
    }

    public void setStatusAndUpdateVisuals(final int status) {
        if (mStatus == NOT_INITIALIZED) {
            setButtonPositionWithoutAnimation(status);
            mStatus = status;
        } else {
            if (null == mInstallButton) {
                // We may come here before we have been layout. In this case we don't know our
                // size yet so we can't start animations so we need to remember what animation to
                // start once layout has gone through.
                mAnimateToStatus = status;
            } else {
                animateButtonPosition(mStatus, status);
                mStatus = status;
            }
        }
    }

    private void setButtonPositionWithoutAnimation(final int status) {
        // This may be called by setStatus() before the layout has come yet.
        if (null == mInstallButton) return;
        final int width = getWidth();
        // Set to out of the screen if that's not the currently displayed status
        mInstallButton.setTranslationX(STATUS_INSTALL == status ? 0 : width);
        mCancelButton.setTranslationX(STATUS_CANCEL == status ? 0 : width);
        mDeleteButton.setTranslationX(STATUS_DELETE == status ? 0 : width);
    }

    private void animateButtonPosition(final int oldStatus, final int newStatus) {
        final View oldButton = getButton(oldStatus);
        final View newButton = getButton(newStatus);
        if (null != oldButton && null != newButton) {
            // Transition between two buttons : animate out, then in
            animateButton(oldButton, ANIMATION_OUT).setListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(final Animator animation) {
                            if (newStatus != mStatus) return;
                            animateButton(newButton, ANIMATION_IN);
                        }
                    });
        } else if (null != oldButton) {
            animateButton(oldButton, ANIMATION_OUT);
        } else if (null != newButton) {
            animateButton(newButton, ANIMATION_IN);
        }
    }

    public void setInternalOnClickListener(final OnClickListener listener) {
        mOnClickListener = listener;
        if (null != mInstallButton) {
            // Already laid out : do it now
            mInstallButton.setOnClickListener(mOnClickListener);
            mCancelButton.setOnClickListener(mOnClickListener);
            mDeleteButton.setOnClickListener(mOnClickListener);
        }
    }

    private ViewPropertyAnimator animateButton(final View button, final int direction) {
        final float outerX = getWidth();
        final float innerX = button.getX() - button.getTranslationX();
        mInterfaceState.removeFromCache((View)getParent());
        if (ANIMATION_IN == direction) {
            button.setClickable(true);
            return button.animate().translationX(0);
        } else {
            button.setClickable(false);
            return button.animate().translationX(outerX - innerX);
        }
    }
}
