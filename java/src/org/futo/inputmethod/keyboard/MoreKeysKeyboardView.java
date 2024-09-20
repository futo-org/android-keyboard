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

package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.futo.inputmethod.accessibility.AccessibilityUtils;
import org.futo.inputmethod.accessibility.MoreKeysKeyboardAccessibilityDelegate;
import org.futo.inputmethod.keyboard.internal.KeyDrawParams;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.CoordinateUtils;

/**
 * A view that renders a virtual {@link MoreKeysKeyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 */
public class MoreKeysKeyboardView extends KeyboardView implements MoreKeysPanel {
    private final int[] mCoordinates = CoordinateUtils.newInstance();

    private final Drawable mDivider;
    protected final KeyDetector mKeyDetector;
    private Controller mController = EMPTY_CONTROLLER;
    protected KeyboardActionListener mListener;
    private int mOriginX;
    private int mOriginY;
    private Key mCurrentKey;

    private int mActivePointerId;

    protected MoreKeysKeyboardAccessibilityDelegate mAccessibilityDelegate;

    public MoreKeysKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.moreKeysKeyboardViewStyle);
    }

    public MoreKeysKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        final TypedArray moreKeysKeyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.MoreKeysKeyboardView, defStyle, R.style.MoreKeysKeyboardView);
        mDivider = moreKeysKeyboardViewAttr.getDrawable(R.styleable.MoreKeysKeyboardView_divider);
        if (mDivider != null) {
            // TODO: Drawable itself should have an alpha value.
            mDivider.setAlpha(128);
        }
        moreKeysKeyboardViewAttr.recycle();
        mKeyDetector = new MoreKeysDetector(getResources().getDimension(
                R.dimen.config_more_keys_keyboard_slide_allowance));
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
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        if (!key.isSpacer() || mDivider == null) {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
            return;
        }
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final int iconWidth = Math.min(mDivider.getIntrinsicWidth(), keyWidth);
        final int iconHeight = mDivider.getIntrinsicHeight();
        final int iconX = (keyWidth - iconWidth) / 2; // Align horizontally center
        final int iconY = (keyHeight - iconHeight) / 2; // Align vertically center
        drawIcon(canvas, mDivider, iconX, iconY, iconWidth, iconHeight);
    }

    @Override
    public void setKeyboard(final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + getVerticalCorrection());
        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new MoreKeysKeyboardAccessibilityDelegate(
                        this, mKeyDetector);
                mAccessibilityDelegate.setOpenAnnounce(R.string.spoken_open_more_keys_keyboard);
                mAccessibilityDelegate.setCloseAnnounce(R.string.spoken_close_more_keys_keyboard);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    @Override
    public void showMoreKeysPanel(final View parentView, final Controller controller,
            final int pointX, final int pointY, final KeyboardActionListener listener, final int[] touchOrigin) {
        mController = controller;
        mListener = listener;
        final View container = getContainerView();
        // The coordinates of panel's left-top corner in parentView's coordinate system.
        // We need to consider background drawable paddings.
        final int x = pointX - getDefaultCoordX() - container.getPaddingLeft() - getPaddingLeft();
        final int y = pointY - container.getMeasuredHeight() + container.getPaddingBottom()
                + getPaddingBottom();

        parentView.getLocationInWindow(mCoordinates);
        // Ensure the horizontal position of the panel does not extend past the parentView edges.
        final int maxX = parentView.getMeasuredWidth() - container.getMeasuredWidth();
        final int panelX = Math.max(0, Math.min(maxX, x)) + CoordinateUtils.x(mCoordinates);
        final int panelY = y + CoordinateUtils.y(mCoordinates);
        container.setX(panelX);
        container.setY(panelY);

        mOriginX = CoordinateUtils.x(touchOrigin) - getDefaultCoordX();
        mOriginY = CoordinateUtils.y(touchOrigin) - container.getMeasuredHeight();
        controller.onShowMoreKeysPanel(this);
        final MoreKeysKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            accessibilityDelegate.onShowMoreKeysKeyboard();
        }
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
        mCurrentKey = detectKey(x, y);
    }

    @Override
    public void onMoveEvent(final int x, final int y, final int pointerId, final long eventTime) {
        if (mActivePointerId != pointerId) {
            return;
        }
        final boolean hasOldKey = (mCurrentKey != null);
        mCurrentKey = detectKey(x, y);
        if (hasOldKey && mCurrentKey == null) {
            // A more keys keyboard is canceled when detecting no key.
            mController.onCancelMoreKeysPanel();
        }
    }

    @Override
    public void onUpEvent(final int x, final int y, final int pointerId, final long eventTime) {
        if (mActivePointerId != pointerId) {
            return;
        }
        // Calling {@link #detectKey(int,int,int)} here is harmless because the last move event and
        // the following up event share the same coordinates.
        mCurrentKey = detectKey(x, y);
        if (mCurrentKey != null) {
            updateReleaseKeyGraphics(mCurrentKey);
            onKeyInput(mCurrentKey, x, y);
            mCurrentKey = null;
        }
    }

    /**
     * Performs the specific action for this panel when the user presses a key on the panel.
     */
    protected void onKeyInput(final Key key, final int x, final int y) {
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mListener.onTextInput(mCurrentKey.getOutputText());
        } else if (code != Constants.CODE_UNSPECIFIED) {
            if (getKeyboard().hasProximityCharsCorrection(code)) {
                mListener.onCodeInput(code, x, y, false /* isKeyRepeat */);
            } else {
                mListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                        false /* isKeyRepeat */);
            }
        }
    }

    private Key detectKey(int x, int y) {
        final Key oldKey = mCurrentKey;
        final Key newKey = mKeyDetector.detectHitKey(x, y);
        if (newKey == oldKey) {
            return newKey;
        } else {
            if(mAccessibilityDelegate != null && AccessibilityUtils.getInstance().isAccessibilityEnabled() && newKey != null) {
                mAccessibilityDelegate.onKeyHovered(newKey);
            }
        }
        // A new key is detected.
        if (oldKey != null) {
            updateReleaseKeyGraphics(oldKey);
            invalidateKey(oldKey);
        }
        if (newKey != null) {
            updatePressKeyGraphics(newKey);
            invalidateKey(newKey);
        }
        return newKey;
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
        final MoreKeysKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            accessibilityDelegate.onDismissMoreKeysKeyboard();
        }
        mController.onDismissMoreKeysPanel();
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
    public boolean dispatchHoverEvent(MotionEvent event) {
        return (mAccessibilityDelegate != null && AccessibilityUtils.getInstance().isTouchExplorationEnabled() && mAccessibilityDelegate.dispatchHoverEvent(event))
                || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (mAccessibilityDelegate != null && AccessibilityUtils.getInstance().isTouchExplorationEnabled() && mAccessibilityDelegate.dispatchKeyEvent(event))
                || super.dispatchKeyEvent(event);
    }

    @Override
    public void onFocusChanged(boolean gainFocus, int direction,
                               Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if(mAccessibilityDelegate != null && AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            mAccessibilityDelegate.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        }
    }

    private View getContainerView() {
        return (View)getParent();
    }

    @Override
    public void showInParent(final ViewGroup parentView) {
        removeFromParent();
        parentView.addView(getContainerView());
    }

    @Override
    public void removeFromParent() {
        final View containerView = getContainerView();
        final ViewGroup currentParent = (ViewGroup)containerView.getParent();
        if (currentParent != null) {
            currentParent.removeView(containerView);
        }
    }

    @Override
    public boolean isShowingInParent() {
        return (getContainerView().getParent() != null);
    }
}
