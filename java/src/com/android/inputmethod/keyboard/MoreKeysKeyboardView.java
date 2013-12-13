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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CoordinateUtils;

/**
 * A view that renders a virtual {@link MoreKeysKeyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 */
public class MoreKeysKeyboardView extends KeyboardView implements MoreKeysPanel {
    private final int[] mCoordinates = CoordinateUtils.newInstance();

    protected final KeyDetector mKeyDetector;
    private Controller mController = EMPTY_CONTROLLER;
    protected KeyboardActionListener mListener;
    private int mOriginX;
    private int mOriginY;
    private Key mCurrentKey;

    private int mActivePointerId;

    public MoreKeysKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.moreKeysKeyboardViewStyle);
    }

    public MoreKeysKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        mKeyDetector = new MoreKeysDetector(
                res.getDimension(R.dimen.more_keys_keyboard_slide_allowance));
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
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
    public void setKeyboard(final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, -getPaddingLeft(),
                -getPaddingTop() + getVerticalCorrection());
    }

    @Override
    public void showMoreKeysPanel(final View parentView, final Controller controller,
            final int pointX, final int pointY, final KeyboardActionListener listener) {
        mController = controller;
        mListener = listener;
        final View container = getContainerView();
        // The coordinates of panel's left-top corner in parentView's coordinate system.
        final int x = pointX - getDefaultCoordX() - container.getPaddingLeft();
        final int y = pointY - container.getMeasuredHeight() + container.getPaddingBottom();

        parentView.getLocationInWindow(mCoordinates);
        // Ensure the horizontal position of the panel does not extend past the screen edges.
        final int maxX = parentView.getMeasuredWidth() - container.getMeasuredWidth();
        final int panelX = Math.max(0, Math.min(maxX, x)) + CoordinateUtils.x(mCoordinates);
        final int panelY = y + CoordinateUtils.y(mCoordinates);
        container.setX(panelX);
        container.setY(panelY);

        mOriginX = x + container.getPaddingLeft();
        mOriginY = y + container.getPaddingTop();
        controller.onShowMoreKeysPanel(this);
    }

    /**
     * Returns the default x coordinate for showing this panel.
     */
    protected int getDefaultCoordX() {
        return ((MoreKeysKeyboard)getKeyboard()).getDefaultCoordX();
    }

    @Override
    public void onDownEvent(final int x, final int y, final int pointerId, final long eventTime) {
        mActivePointerId = pointerId;
        onMoveKeyInternal(x, y, pointerId);
    }

    @Override
    public void onMoveEvent(int x, int y, final int pointerId, long eventTime) {
        if (mActivePointerId != pointerId) {
            return;
        }
        final boolean hasOldKey = (mCurrentKey != null);
        onMoveKeyInternal(x, y, pointerId);
        if (hasOldKey && mCurrentKey == null) {
            // If the pointer has moved too far away from any target then cancel the panel.
            mController.onCancelMoreKeysPanel(this);
        }
    }

    @Override
    public void onUpEvent(final int x, final int y, final int pointerId, final long eventTime) {
        if (mCurrentKey != null && mActivePointerId == pointerId) {
            updateReleaseKeyGraphics(mCurrentKey);
            onCodeInput(mCurrentKey.getCode(), x, y);
            mCurrentKey = null;
        }
    }

    /**
     * Performs the specific action for this panel when the user presses a key on the panel.
     */
    protected void onCodeInput(final int code, final int x, final int y) {
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mListener.onTextInput(mCurrentKey.getOutputText());
        } else if (code != Constants.CODE_UNSPECIFIED) {
            mListener.onCodeInput(code, x, y);
        }
    }

    private void onMoveKeyInternal(int x, int y, int pointerId) {
        if (mActivePointerId != pointerId) {
            // Ignore old pointers when newer pointer is active.
            return;
        }
        final Key oldKey = mCurrentKey;
        final Key newKey = mKeyDetector.detectHitKey(x, y);
        if (newKey != oldKey) {
            mCurrentKey = newKey;
            invalidateKey(mCurrentKey);
            if (oldKey != null) {
                updateReleaseKeyGraphics(oldKey);
            }
            if (newKey != null) {
                updatePressKeyGraphics(newKey);
            }
        }
    }

    private void updateReleaseKeyGraphics(final Key key) {
        key.onReleased();
        invalidateKey(key);
    }

    private void updatePressKeyGraphics(final Key key) {
        key.onPressed();
        invalidateKey(key);
    }

    @Override
    public void dismissMoreKeysPanel() {
        if (!isShowingInParent()) {
            return;
        }
        mController.onDismissMoreKeysPanel(this);
    }

    @Override
    public int translateX(final int x) {
        return x - mOriginX;
    }

    @Override
    public int translateY(final int y) {
        return y - mOriginY;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        final int pointerId = me.getPointerId(index);
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, pointerId, eventTime);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, pointerId, eventTime);
            break;
        case MotionEvent.ACTION_MOVE:
            onMoveEvent(x, y, pointerId, eventTime);
            break;
        }
        return true;
    }

    @Override
    public View getContainerView() {
        return (View)getParent();
    }

    @Override
    public boolean isShowingInParent() {
        return (getContainerView().getParent() != null);
    }
}
