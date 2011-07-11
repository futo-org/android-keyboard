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

/**
 * A view that renders a virtual {@link MiniKeyboard}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public class PopupMiniKeyboardView extends KeyboardView implements PopupPanel {
    private final int[] mCoordinates = new int[2];
    private final boolean mConfigShowMiniKeyboardAtTouchedPoint;

    private final KeyDetector mKeyDetector;
    private final int mVerticalCorrection;

    private LatinKeyboardBaseView mParentKeyboardView;
    private int mOriginX;
    private int mOriginY;

    private static final TimerProxy EMPTY_TIMER_PROXY = new TimerProxy() {
        @Override
        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {}
        @Override
        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {}
        @Override
        public void startLongPressShiftTimer(long delay, int keyIndex, PointerTracker tracker) {}
        @Override
        public void cancelLongPressTimers() {}
        @Override
        public void cancelKeyTimers() {}
    };

    private final KeyboardActionListener mListner = new KeyboardActionListener() {
        @Override
        public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
            mParentKeyboardView.getKeyboardActionListener()
                    .onCodeInput(primaryCode, keyCodes, x, y);
            mParentKeyboardView.dismissMiniKeyboard();
        }

        @Override
        public void onTextInput(CharSequence text) {
            mParentKeyboardView.getKeyboardActionListener().onTextInput(text);
            mParentKeyboardView.dismissMiniKeyboard();
        }

        @Override
        public void onCancelInput() {
            mParentKeyboardView.getKeyboardActionListener().onCancelInput();
            mParentKeyboardView.dismissMiniKeyboard();
        }

        @Override
        public void onPress(int primaryCode, boolean withSliding) {
            mParentKeyboardView.getKeyboardActionListener().onPress(primaryCode, withSliding);
        }
        @Override
        public void onRelease(int primaryCode, boolean withSliding) {
            mParentKeyboardView.getKeyboardActionListener().onRelease(primaryCode, withSliding);
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
        mConfigShowMiniKeyboardAtTouchedPoint = res.getBoolean(
                R.bool.config_show_mini_keyboard_at_touched_point);
        // Override default ProximityKeyDetector.
        mKeyDetector = new MiniKeyboardKeyDetector(res.getDimension(
                R.dimen.mini_keyboard_slide_allowance));
        // Remove gesture detector on mini-keyboard
        setKeyPreviewPopupEnabled(false, 0);
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
        return mListner;
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Do nothing for the mini keyboard.
    }

    @Override
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        // Mini keyboard needs no pop-up key preview displayed, so we pass always false with a
        // delay of 0. The delay does not matter actually since the popup is not shown anyway.
        super.setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    public void showPanel(LatinKeyboardBaseView parentKeyboardView, Key parentKey,
            PointerTracker tracker, PopupWindow window) {
        mParentKeyboardView = parentKeyboardView;
        final View container = (View)getParent();
        final MiniKeyboard miniKeyboard = (MiniKeyboard)getKeyboard();
        final Keyboard parentKeyboard = parentKeyboardView.getKeyboard();

        parentKeyboardView.getLocationInWindow(mCoordinates);
        final int pointX = (mConfigShowMiniKeyboardAtTouchedPoint) ? tracker.getLastX()
                : parentKey.mX + parentKey.mWidth / 2;
        final int pointY = parentKey.mY;
        final int miniKeyboardLeft = pointX - miniKeyboard.getDefaultCoordX()
                + parentKeyboardView.getPaddingLeft();
        final int x = Math.max(0, Math.min(miniKeyboardLeft,
                parentKeyboardView.getWidth() - miniKeyboard.getMinWidth()))
                - container.getPaddingLeft() + mCoordinates[0];
        final int y = pointY - parentKeyboard.getVerticalGap()
                - (container.getMeasuredHeight() - container.getPaddingBottom())
                + parentKeyboardView.getPaddingTop() + mCoordinates[1];

        if (miniKeyboard.setShifted(parentKeyboard.isShiftedOrShiftLocked())) {
            invalidateAllKeys();
        }
        window.setContentView(container);
        window.setWidth(container.getMeasuredWidth());
        window.setHeight(container.getMeasuredHeight());
        window.showAtLocation(parentKeyboardView, Gravity.NO_GRAVITY, x, y);

        mOriginX = x + container.getPaddingLeft() - mCoordinates[0];
        mOriginY = y + container.getPaddingTop() - mCoordinates[1];
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
