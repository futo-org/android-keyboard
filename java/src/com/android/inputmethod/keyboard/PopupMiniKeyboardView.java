/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.latin.R;

import java.util.List;

/**
 * A view that renders a virtual {@link MiniKeyboard}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public class PopupMiniKeyboardView extends KeyboardView implements PopupPanel {
    private final int[] mCoordinates = new int[2];

    private final KeyDetector mKeyDetector;
    private final int mVerticalCorrection;

    private Controller mController;
    private KeyboardActionListener mListener;
    private int mOriginX;
    private int mOriginY;

    private static class MiniKeyboardKeyDetector extends KeyDetector {
        private final int mSlideAllowanceSquare;
        private final int mSlideAllowanceSquareTop;

        public MiniKeyboardKeyDetector(float slideAllowance) {
            super(/* keyHysteresisDistance */0);
            mSlideAllowanceSquare = (int)(slideAllowance * slideAllowance);
            // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
            mSlideAllowanceSquareTop = mSlideAllowanceSquare * 2;
        }

        @Override
        public boolean alwaysAllowsSlidingInput() {
            return true;
        }

        @Override
        protected int getMaxNearbyKeys() {
            // No nearby key will be returned.
            return 1;
        }

        @Override
        public int getKeyIndexAndNearbyCodes(int x, int y, final int[] allCodes) {
            final List<Key> keys = getKeyboard().mKeys;
            final int touchX = getTouchX(x);
            final int touchY = getTouchY(y);

            int nearestIndex = NOT_A_KEY;
            int nearestDist = (y < 0) ? mSlideAllowanceSquareTop : mSlideAllowanceSquare;
            final int keyCount = keys.size();
            for (int index = 0; index < keyCount; index++) {
                final int dist = keys.get(index).squaredDistanceToEdge(touchX, touchY);
                if (dist < nearestDist) {
                    nearestIndex = index;
                    nearestDist = dist;
                }
            }

            if (allCodes != null && nearestIndex != NOT_A_KEY)
                allCodes[0] = keys.get(nearestIndex).mCode;
            return nearestIndex;
        }
    }

    private static final TimerProxy EMPTY_TIMER_PROXY = new TimerProxy() {
        @Override
        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {}
        @Override
        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {}
        @Override
        public void cancelLongPressTimer() {}
        @Override
        public void cancelKeyTimers() {}
    };

    private final KeyboardActionListener mMiniKeyboardListener =
            new KeyboardActionListener.Adapter() {
        @Override
        public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
            mListener.onCodeInput(primaryCode, keyCodes, x, y);
        }

        @Override
        public void onTextInput(CharSequence text) {
            mListener.onTextInput(text);
        }

        @Override
        public void onCancelInput() {
            mListener.onCancelInput();
        }

        @Override
        public void onPress(int primaryCode, boolean withSliding) {
            mListener.onPress(primaryCode, withSliding);
        }
        @Override
        public void onRelease(int primaryCode, boolean withSliding) {
            mListener.onRelease(primaryCode, withSliding);
        }
    };

    public PopupMiniKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.popupMiniKeyboardViewStyle);
    }

    public PopupMiniKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mVerticalCorrection = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_verticalCorrection, 0);
        a.recycle();

        final Resources res = context.getResources();
        // Override default ProximityKeyDetector.
        mKeyDetector = new MiniKeyboardKeyDetector(res.getDimension(
                R.dimen.mini_keyboard_slide_allowance));
        // Remove gesture detector on mini-keyboard
        setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard != null) {
            final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
            final int height = keyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void setKeyboard(Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, -getPaddingLeft(),
                -getPaddingTop() + mVerticalCorrection);
    }

    @Override
    public KeyDetector getKeyDetector() {
        return mKeyDetector;
    }

    @Override
    public KeyboardActionListener getKeyboardActionListener() {
        return mMiniKeyboardListener;
    }

    @Override
    public DrawingProxy getDrawingProxy() {
        return  this;
    }

    @Override
    public TimerProxy getTimerProxy() {
        return EMPTY_TIMER_PROXY;
    }

    @Override
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        // Mini keyboard needs no pop-up key preview displayed, so we pass always false with a
        // delay of 0. The delay does not matter actually since the popup is not shown anyway.
        super.setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    public void setShifted(boolean shifted) {
        final MiniKeyboard miniKeyboard = (MiniKeyboard)getKeyboard();
        if (miniKeyboard.setShifted(shifted)) {
            invalidateAllKeys();
        }
    }

    @Override
    public void showPopupPanel(View parentView, Controller controller, int pointX, int pointY,
            PopupWindow window, KeyboardActionListener listener) {
        mController = controller;
        mListener = listener;
        final View container = (View)getParent();
        final MiniKeyboard miniKeyboard = (MiniKeyboard)getKeyboard();

        parentView.getLocationInWindow(mCoordinates);
        final int miniKeyboardLeft = pointX - miniKeyboard.getDefaultCoordX()
                + parentView.getPaddingLeft();
        final int x = wrapUp(Math.max(0, Math.min(miniKeyboardLeft,
                parentView.getWidth() - miniKeyboard.mOccupiedWidth))
                - container.getPaddingLeft() + mCoordinates[0],
                container.getMeasuredWidth(), 0, parentView.getWidth());
        final int y = pointY
                - (container.getMeasuredHeight() - container.getPaddingBottom())
                + parentView.getPaddingTop() + mCoordinates[1];

        window.setContentView(container);
        window.setWidth(container.getMeasuredWidth());
        window.setHeight(container.getMeasuredHeight());
        window.showAtLocation(parentView, Gravity.NO_GRAVITY, x, y);

        mOriginX = x + container.getPaddingLeft() - mCoordinates[0];
        mOriginY = y + container.getPaddingTop() - mCoordinates[1];
    }

    private static int wrapUp(int x, int width, int left, int right) {
        if (x < left)
            return left;
        if (x + width > right)
            return right - width;
        return x;
    }

    @Override
    public boolean dismissPopupPanel() {
        return mController.dismissPopupPanel();
    }

    @Override
    public int translateX(int x) {
        return x - mOriginX;
    }

    @Override
    public int translateY(int y) {
        return y - mOriginY;
    }
}
