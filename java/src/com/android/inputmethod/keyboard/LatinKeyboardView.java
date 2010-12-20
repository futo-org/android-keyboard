/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.voice.VoiceIMEConnector;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;

// TODO: We should remove this class
public class LatinKeyboardView extends KeyboardView {

    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents;
    /**
     * Whether multi-touch disambiguation needs to be disabled if a real multi-touch event has
     * occured
     */
    private boolean mDisableDisambiguation;
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    /** The y coordinate of the last row */
    private int mLastRowY;
    private int mLastX;
    private int mLastY;

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setPreviewEnabled(boolean previewEnabled) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null
                && (latinKeyboard.isPhoneKeyboard() || latinKeyboard.isNumberKeyboard())) {
            // Phone and number keyboard never shows popup preview (except language switch).
            super.setPreviewEnabled(false);
        } else {
            super.setPreviewEnabled(previewEnabled);
        }
    }

    public void setLatinKeyboard(LatinKeyboard k) {
        super.setKeyboard(k);
        // One-seventh of the keyboard width seems like a reasonable threshold
        mJumpThresholdSquare = k.getMinWidth() / 7;
        mJumpThresholdSquare *= mJumpThresholdSquare;
        // Assuming there are 4 rows, this is the coordinate of the last row
        mLastRowY = (k.getHeight() * 3) / 4;
    }

    public LatinKeyboard getLatinKeyboard() {
        Keyboard keyboard = getKeyboard();
        if (keyboard instanceof LatinKeyboard) {
            return (LatinKeyboard)keyboard;
        } else {
            return null;
        }
    }

    @Override
    protected boolean onLongPress(Key key) {
        int primaryCode = key.mCode;
        if (primaryCode == Keyboard.CODE_SETTINGS) {
            return invokeOnKey(Keyboard.CODE_SETTINGS_LONGPRESS);
        } else if (primaryCode == '0' && getLatinKeyboard().isPhoneKeyboard()) {
            // Long pressing on 0 in phone number keypad gives you a '+'.
            return invokeOnKey('+');
        } else {
            return super.onLongPress(key);
        }
    }

    private boolean invokeOnKey(int primaryCode) {
        getOnKeyboardActionListener().onCodeInput(primaryCode, null,
                KeyboardView.NOT_A_TOUCH_COORDINATE,
                KeyboardView.NOT_A_TOUCH_COORDINATE);
        return true;
    }

    @Override
    protected CharSequence adjustCase(CharSequence label) {
        LatinKeyboard keyboard = getLatinKeyboard();
        if (keyboard.isAlphaKeyboard()
                && keyboard.isShiftedOrShiftLocked()
                && !TextUtils.isEmpty(label) && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            return label.toString().toUpperCase();
        }
        return label;
    }

    /**
     * This function checks to see if we need to handle any sudden jumps in the pointer location
     * that could be due to a multi-touch being treated as a move by the firmware or hardware.
     * Once a sudden jump is detected, all subsequent move events are discarded
     * until an UP is received.<P>
     * When a sudden jump is detected, an UP event is simulated at the last position and when
     * the sudden moves subside, a DOWN event is simulated for the second key.
     * @param me the motion event
     * @return true if the event was consumed, so that it doesn't continue to be handled by
     * KeyboardView.
     */
    private boolean handleSuddenJump(MotionEvent me) {
        final int action = me.getAction();
        final int x = (int) me.getX();
        final int y = (int) me.getY();
        boolean result = false;

        // Real multi-touch event? Stop looking for sudden jumps
        if (me.getPointerCount() > 1) {
            mDisableDisambiguation = true;
        }
        if (mDisableDisambiguation) {
            // If UP, reset the multi-touch flag
            if (action == MotionEvent.ACTION_UP) mDisableDisambiguation = false;
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Reset the "session"
            mDroppingEvents = false;
            mDisableDisambiguation = false;
            break;
        case MotionEvent.ACTION_MOVE:
            // Is this a big jump?
            final int distanceSquare = (mLastX - x) * (mLastX - x) + (mLastY - y) * (mLastY - y);
            // Check the distance and also if the move is not entirely within the bottom row
            // If it's only in the bottom row, it might be an intentional slide gesture
            // for language switching
            if (distanceSquare > mJumpThresholdSquare
                    && (mLastY < mLastRowY || y < mLastRowY)) {
                // If we're not yet dropping events, start dropping and send an UP event
                if (!mDroppingEvents) {
                    mDroppingEvents = true;
                    // Send an up event
                    MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                            MotionEvent.ACTION_UP,
                            mLastX, mLastY, me.getMetaState());
                    super.onTouchEvent(translated);
                    translated.recycle();
                }
                result = true;
            } else if (mDroppingEvents) {
                // If moves are small and we're already dropping events, continue dropping
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mDroppingEvents) {
                // Send a down event first, as we dropped a bunch of sudden jumps and assume that
                // the user is releasing the touch on the second key.
                MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        x, y, me.getMetaState());
                super.onTouchEvent(translated);
                translated.recycle();
                mDroppingEvents = false;
                // Let the up event get processed as well, result = false
            }
            break;
        }
        // Track the previous coordinate
        mLastX = x;
        mLastY = y;
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        LatinKeyboard keyboard = getLatinKeyboard();

        // If there was a sudden jump, return without processing the actual motion event.
        if (handleSuddenJump(me))
            return true;

        // Reset any bounding box controls in the keyboard
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            keyboard.keyReleased();
        }

        if (me.getAction() == MotionEvent.ACTION_UP) {
            int languageDirection = keyboard.getLanguageChangeDirection();
            if (languageDirection != 0) {
                getOnKeyboardActionListener().onCodeInput(
                        languageDirection == 1
                        ? Keyboard.CODE_NEXT_LANGUAGE : Keyboard.CODE_PREV_LANGUAGE,
                        null, mLastX, mLastY);
                me.setAction(MotionEvent.ACTION_CANCEL);
                keyboard.keyReleased();
                return super.onTouchEvent(me);
            }
        }

        return super.onTouchEvent(me);
    }

    @Override
    public void draw(Canvas c) {
        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                super.draw(c);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait("LatinKeyboardView", e);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        // Token is available from here.
        VoiceIMEConnector.getInstance().onAttachedToWindow();
    }
}
