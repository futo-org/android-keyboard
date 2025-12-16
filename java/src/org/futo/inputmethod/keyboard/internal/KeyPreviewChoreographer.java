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

package org.futo.inputmethod.keyboard.internal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import org.futo.inputmethod.keyboard.Key;
import org.futo.inputmethod.latin.common.CoordinateUtils;
import org.futo.inputmethod.latin.uix.theme.KeyBackground;
import org.futo.inputmethod.latin.utils.ViewLayoutUtils;

import java.util.ArrayDeque;
import java.util.HashMap;

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
        placerView.addView(keyPreviewView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0));
        return keyPreviewView;
    }

    public boolean isShowingKeyPreview(final Key key) {
        return mShowingKeyPreviewViews.containsKey(key);
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
            if (tag instanceof KeyPreviewAnimators) {
                final KeyPreviewAnimators animators = (KeyPreviewAnimators)tag;
                animators.startDismiss();
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
                                       final ViewGroup placerView, final boolean withAnimation, KeyBackground background) {
        final KeyPreviewView keyPreviewView = getKeyPreviewView(key, placerView);
        keyPreviewView.setBackground(background.getBackground());
        placeKeyPreview(key, keyPreviewView, iconsSet, drawParams, keyboardViewWidth, keyboardOrigin, background.getForegroundColor());
        showKeyPreview(key, keyPreviewView, withAnimation);
    }

    public int getBottomPaddingForKey(final Context context, final Key key) {
        final float density = context.getResources().getDisplayMetrics().density;
        return Math.max(key.getHeight(), (int)(64.0f * density));
    }

    private void placeKeyPreview(final Key key, final KeyPreviewView keyPreviewView,
            final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams,
            final int keyboardViewWidth, final int[] originCoords, int foregroundColor) {
        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams, foregroundColor);
        keyPreviewView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mParams.setGeometry(keyPreviewView);

        final float density = keyPreviewView.getContext().getResources().getDisplayMetrics().density;

        final int bottomPadding = getBottomPaddingForKey(keyPreviewView.getContext(), key);
        final int topArea = Math.max(key.getHeight(), (int)(44.0f * density));

        final int previewWidth  = Math.min(Math.max(key.getWidth(), (int)(34.0f * density)), (int)(64.0f * density));
        int previewHeight = topArea + bottomPadding;
        keyPreviewView.setPadding(0, 0, 0, bottomPadding);

        final int keyDrawWidth = key.getDrawWidth();
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int keyPreviewPosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2
                + CoordinateUtils.x(originCoords);
        keyPreviewPosition = KeyPreviewView.POSITION_MIDDLE;
        final boolean hasMoreKeys = !key.getMoreKeys().isEmpty();
        keyPreviewView.setPreviewBackground(hasMoreKeys, keyPreviewPosition);
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        int previewY = key.getY() - previewHeight + mParams.mPreviewOffset
                + CoordinateUtils.y(originCoords) + key.getHeight();

        if(key.getHasFlick()) {
            previewHeight -= bottomPadding;
            keyPreviewView.setPadding(0, 0, 0, 0);
            keyPreviewView.setGravity(Gravity.CENTER);
            keyPreviewView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    Math.min(keyPreviewView.getWidth(), keyPreviewView.getHeight()) * 0.7f);
        } else {
            keyPreviewView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        }

        ViewLayoutUtils.placeViewAt(
                keyPreviewView, previewX, previewY, previewWidth, previewHeight);
        keyPreviewView.setPivotX(previewWidth / 2.0f);
        keyPreviewView.setPivotY(previewHeight);
    }

    void showKeyPreview(final Key key, final KeyPreviewView keyPreviewView,
            final boolean withAnimation) {
        if (!withAnimation) {
            keyPreviewView.setVisibility(View.VISIBLE);
            mShowingKeyPreviewViews.put(key, keyPreviewView);
            return;
        }

        // Show preview with animation.
        final Animator showUpAnimator = createShowUpAnimator(key, keyPreviewView);
        final Animator dismissAnimator = createDismissAnimator(key, keyPreviewView);
        final KeyPreviewAnimators animators = new KeyPreviewAnimators(
                showUpAnimator, dismissAnimator);
        keyPreviewView.setTag(animators);
        animators.startShowUp();
    }

    public Animator createShowUpAnimator(final Key key, final KeyPreviewView keyPreviewView) {
        final Animator showUpAnimator = mParams.createShowUpAnimator(keyPreviewView);
        showUpAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(final Animator animator) {
                showKeyPreview(key, keyPreviewView, false /* withAnimation */);
            }
        });
        return showUpAnimator;
    }

    private Animator createDismissAnimator(final Key key, final KeyPreviewView keyPreviewView) {
        final Animator dismissAnimator = mParams.createDismissAnimator(keyPreviewView);
        dismissAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animator) {
                dismissKeyPreview(key, false /* withAnimation */);
            }
        });
        return dismissAnimator;
    }

    private static class KeyPreviewAnimators extends AnimatorListenerAdapter {
        private final Animator mShowUpAnimator;
        private final Animator mDismissAnimator;

        public KeyPreviewAnimators(final Animator showUpAnimator, final Animator dismissAnimator) {
            mShowUpAnimator = showUpAnimator;
            mDismissAnimator = dismissAnimator;
        }

        public void startShowUp() {
            mShowUpAnimator.start();
        }

        public void startDismiss() {
            if (mShowUpAnimator.isRunning()) {
                mShowUpAnimator.addListener(this);
                return;
            }
            mDismissAnimator.start();
        }

        @Override
        public void onAnimationEnd(final Animator animator) {
            mDismissAnimator.start();
        }
    }
}
