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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.android.inputmethod.latin.R;

/**
 * A view that renders a virtual {@link MiniKeyboard}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public class PopupMiniKeyboardView extends KeyboardView implements PopupPanel {
    private final int[] mCoordinates = new int[2];
    private final boolean mConfigShowMiniKeyboardAtTouchedPoint;

    private int mOriginX;
    private int mOriginY;
    private int mTrackerId;
    private long mDownTime;

    public PopupMiniKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public PopupMiniKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        mConfigShowMiniKeyboardAtTouchedPoint = res.getBoolean(
                R.bool.config_show_mini_keyboard_at_touched_point);
        // Override default ProximityKeyDetector.
        mKeyDetector = new MiniKeyboardKeyDetector(res.getDimension(
                R.dimen.mini_keyboard_slide_allowance));
        // Remove gesture detector on mini-keyboard
        mGestureDetector = null;
        setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        // Mini keyboard needs no pop-up key preview displayed, so we pass always false with a
        // delay of 0. The delay does not matter actually since the popup is not shown anyway.
        super.setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    public void showPanel(KeyboardView parentKeyboardView, Key parentKey,
            PointerTracker tracker, int keyPreviewY, PopupWindow window) {
        final View container = (View)getParent();
        final MiniKeyboard miniKeyboard = (MiniKeyboard)getKeyboard();
        final Keyboard parentKeyboard = parentKeyboardView.getKeyboard();

        parentKeyboardView.getLocationInWindow(mCoordinates);
        final int pointX = (mConfigShowMiniKeyboardAtTouchedPoint) ? tracker.getLastX()
                : parentKey.mX + parentKey.mWidth / 2;
        final int pointY = parentKey.mY;
        final int miniKeyboardX = pointX - miniKeyboard.getDefaultCoordX()
                - container.getPaddingLeft()
                + parentKeyboardView.getPaddingLeft() + mCoordinates[0];
        final int miniKeyboardY = pointY - parentKeyboard.getVerticalGap()
                - (container.getMeasuredHeight() - container.getPaddingBottom())
                + parentKeyboardView.getPaddingTop() + mCoordinates[1];
        final int x = miniKeyboardX;
        final int y = parentKeyboardView.isKeyPreviewPopupEnabled() &&
                miniKeyboard.isOneRowKeyboard() && keyPreviewY >= 0 ? keyPreviewY : miniKeyboardY;

        if (miniKeyboard.setShifted(parentKeyboard.isShiftedOrShiftLocked())) {
            invalidateAllKeys();
        }
        window.setContentView(container);
        window.setWidth(container.getMeasuredWidth());
        window.setHeight(container.getMeasuredHeight());
        window.showAtLocation(parentKeyboardView, Gravity.NO_GRAVITY, x, y);

        mOriginX = x + container.getPaddingLeft() - mCoordinates[0];
        mOriginY = y + container.getPaddingTop() - mCoordinates[1];
        mTrackerId = tracker.mPointerId;
        mDownTime = SystemClock.uptimeMillis();

        // Inject down event on the key to mini keyboard.
        final MotionEvent downEvent = translateMotionEvent(MotionEvent.ACTION_DOWN, pointX,
                pointY + parentKey.mHeight / 2, mDownTime);
        onTouchEvent(downEvent);
        downEvent.recycle();
    }

    private MotionEvent translateMotionEvent(int action, float x, float y, long eventTime) {
        return MotionEvent.obtain(mDownTime, eventTime, action, x - mOriginX, y - mOriginY, 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        if (id == mTrackerId) {
            final MotionEvent translated = translateMotionEvent(me.getAction(), me.getX(index),
                    me.getY(index), me.getEventTime());
            super.onTouchEvent(translated);
            translated.recycle();
        }
        return true;
    }
}
