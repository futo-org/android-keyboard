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

package com.android.inputmethod.latin;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.Keyboard.Key;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.PopupWindow;

public class LatinKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;
    static final int KEYCODE_SHIFT_LONGPRESS = -101;
    static final int KEYCODE_VOICE = -102;
    static final int KEYCODE_F1 = -103;
    static final int KEYCODE_NEXT_LANGUAGE = -104;
    static final int KEYCODE_PREV_LANGUAGE = -105;

    private Keyboard mPhoneKeyboard;

    /** Whether the extension of this keyboard is visible */
    private boolean mExtensionVisible;
    /** The view that is shown as an extension of this keyboard view */
    private LatinKeyboardView mExtension;
    /** The popup window that contains the extension of this keyboard */
    private PopupWindow mExtensionPopup;
    /** Whether this view is an extension of another keyboard */
    private boolean mIsExtensionType;
    private boolean mFirstEvent;
    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents;
    /** 
     * Whether multi-touch disambiguation needs to be disabled for any reason. There are 2 reasons
     * for this to happen - (1) if a real multi-touch event has occured and (2) we've opened an 
     * extension keyboard.
     */
    private boolean mDisableDisambiguation;
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    /** The y coordinate of the last row */
    private int mLastRowY;

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setPhoneKeyboard(Keyboard phoneKeyboard) {
        mPhoneKeyboard = phoneKeyboard;
    }

    @Override
    public void setKeyboard(Keyboard k) {
        super.setKeyboard(k);
        // One-seventh of the keyboard width seems like a reasonable threshold
        mJumpThresholdSquare = k.getMinWidth() / 7;
        mJumpThresholdSquare *= mJumpThresholdSquare;
        // Assuming there are 4 rows, this is the coordinate of the last row
        mLastRowY = (k.getHeight() * 3) / 4;
        setKeyboardLocal(k);
    }

    @Override
    protected boolean onLongPress(Key key) {
        if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        } else if (key.codes[0] == Keyboard.KEYCODE_SHIFT) {
            getOnKeyboardActionListener().onKey(KEYCODE_SHIFT_LONGPRESS, null);
            invalidateAllKeys();
            return true;
        } else if (key.codes[0] == '0' && getKeyboard() == mPhoneKeyboard) {
            // Long pressing on 0 in phone number keypad gives you a '+'.
            getOnKeyboardActionListener().onKey('+', null);
            return true;
        } else {
            return super.onLongPress(key);
        }
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
        LatinKeyboard keyboard = (LatinKeyboard) getKeyboard();
        if (DEBUG_LINE) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            invalidate();
        }

        // If an extension keyboard is visible or this is an extension keyboard, don't look
        // for sudden jumps. Otherwise, if there was a sudden jump, return without processing the
        // actual motion event.
        if (!mExtensionVisible && !mIsExtensionType
                && handleSuddenJump(me)) return true;
        // Reset any bounding box controls in the keyboard
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            keyboard.keyReleased();
        }

        if (me.getAction() == MotionEvent.ACTION_UP) {
            int languageDirection = keyboard.getLanguageChangeDirection();
            if (languageDirection != 0) {
                getOnKeyboardActionListener().onKey(
                        languageDirection == 1 ? KEYCODE_NEXT_LANGUAGE : KEYCODE_PREV_LANGUAGE,
                        null);
                me.setAction(MotionEvent.ACTION_CANCEL);
                keyboard.keyReleased();
                return super.onTouchEvent(me);
            }
        }

        // If we don't have an extension keyboard, don't go any further.
        if (keyboard.getExtension() == 0) {
            return super.onTouchEvent(me);
        }
        // If the motion event is above the keyboard and it's not an UP event coming
        // even before the first MOVE event into the extension area
        if (me.getY() < 0 && (mExtensionVisible || me.getAction() != MotionEvent.ACTION_UP)) {
            if (mExtensionVisible) {
                int action = me.getAction();
                if (mFirstEvent) action = MotionEvent.ACTION_DOWN;
                mFirstEvent = false;
                MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        action,
                        me.getX(), me.getY() + mExtension.getHeight(), me.getMetaState());
                boolean result = mExtension.onTouchEvent(translated);
                translated.recycle();
                if (me.getAction() == MotionEvent.ACTION_UP
                        || me.getAction() == MotionEvent.ACTION_CANCEL) {
                    closeExtension();
                }
                return result;
            } else {
                if (openExtension()) {
                    MotionEvent cancel = MotionEvent.obtain(me.getDownTime(), me.getEventTime(),
                            MotionEvent.ACTION_CANCEL, me.getX() - 100, me.getY() - 100, 0);
                    super.onTouchEvent(cancel);
                    cancel.recycle();
                    if (mExtension.getHeight() > 0) {
                        MotionEvent translated = MotionEvent.obtain(me.getEventTime(),
                                me.getEventTime(),
                                MotionEvent.ACTION_DOWN,
                                me.getX(), me.getY() + mExtension.getHeight(),
                                me.getMetaState());
                        mExtension.onTouchEvent(translated);
                        translated.recycle();
                    } else {
                        mFirstEvent = true;
                    }
                    // Stop processing multi-touch errors
                    mDisableDisambiguation  = true;
                }
                return true;
            }
        } else if (mExtensionVisible) {
            closeExtension();
            // Send a down event into the main keyboard first
            MotionEvent down = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                    MotionEvent.ACTION_DOWN,
                    me.getX(), me.getY(), me.getMetaState());
            super.onTouchEvent(down);
            down.recycle();
            // Send the actual event
            return super.onTouchEvent(me);
        } else {
            return super.onTouchEvent(me);
        }
    }

    private void setExtensionType(boolean isExtensionType) {
        mIsExtensionType = isExtensionType;
    }

    private boolean openExtension() {
        // If the current keyboard is not visible, don't show the popup
        if (!isShown()) {
            return false;
        }
        if (((LatinKeyboard) getKeyboard()).getExtension() == 0) return false;
        makePopupWindow();
        mExtensionVisible = true;
        return true;
    }

    private void makePopupWindow() {
        if (mExtensionPopup == null) {
            int[] windowLocation = new int[2];
            mExtensionPopup = new PopupWindow(getContext());
            mExtensionPopup.setBackgroundDrawable(null);
            LayoutInflater li = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            mExtension = (LatinKeyboardView) li.inflate(R.layout.input_trans, null);
            mExtension.setExtensionType(true);
            mExtension.setOnKeyboardActionListener(
                    new ExtensionKeyboardListener(getOnKeyboardActionListener()));
            mExtension.setPopupParent(this);
            mExtension.setPopupOffset(0, -windowLocation[1]);
            Keyboard keyboard;
            mExtension.setKeyboard(keyboard = new LatinKeyboard(getContext(),
                    ((LatinKeyboard) getKeyboard()).getExtension()));
            mExtensionPopup.setContentView(mExtension);
            mExtensionPopup.setWidth(getWidth());
            mExtensionPopup.setHeight(keyboard.getHeight());
            mExtensionPopup.setAnimationStyle(-1);
            getLocationInWindow(windowLocation);
            // TODO: Fix the "- 30". 
            mExtension.setPopupOffset(0, -windowLocation[1] - 30);
            mExtensionPopup.showAtLocation(this, 0, 0, -keyboard.getHeight()
                    + windowLocation[1]);
        } else {
            mExtension.setVisibility(VISIBLE);
        }
    }

    @Override
    public void closing() {
        super.closing();
        if (mExtensionPopup != null && mExtensionPopup.isShowing()) {
            mExtensionPopup.dismiss();
            mExtensionPopup = null;
        }
    }

    private void closeExtension() {
        mExtension.closing();
        mExtension.setVisibility(INVISIBLE);
        mExtensionVisible = false;
    }

    private static class ExtensionKeyboardListener implements OnKeyboardActionListener {
        private OnKeyboardActionListener mTarget;
        ExtensionKeyboardListener(OnKeyboardActionListener target) {
            mTarget = target;
        }
        public void onKey(int primaryCode, int[] keyCodes) {
            mTarget.onKey(primaryCode, keyCodes);
        }
        public void onPress(int primaryCode) {
            mTarget.onPress(primaryCode);
        }
        public void onRelease(int primaryCode) {
            mTarget.onRelease(primaryCode);
        }
        public void onText(CharSequence text) {
            mTarget.onText(text);
        }
        public void swipeDown() {
            // Don't pass through
        }
        public void swipeLeft() {
            // Don't pass through
        }
        public void swipeRight() {
            // Don't pass through
        }
        public void swipeUp() {
            // Don't pass through
        }
    }

    /****************************  INSTRUMENTATION  *******************************/

    static final boolean DEBUG_AUTO_PLAY = false;
    static final boolean DEBUG_LINE = false;
    private static final int MSG_TOUCH_DOWN = 1;
    private static final int MSG_TOUCH_UP = 2;
    
    Handler mHandler2;
    
    private String mStringToPlay;
    private int mStringIndex;
    private boolean mDownDelivered;
    private Key[] mAsciiKeys = new Key[256];
    private boolean mPlaying;
    private int mLastX;
    private int mLastY;
    private Paint mPaint;

    private void setKeyboardLocal(Keyboard k) {
        if (DEBUG_AUTO_PLAY) {
            findKeys();
            if (mHandler2 == null) {
                mHandler2 = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        removeMessages(MSG_TOUCH_DOWN);
                        removeMessages(MSG_TOUCH_UP);
                        if (mPlaying == false) return;
                        
                        switch (msg.what) {
                            case MSG_TOUCH_DOWN:
                                if (mStringIndex >= mStringToPlay.length()) {
                                    mPlaying = false;
                                    return;
                                }
                                char c = mStringToPlay.charAt(mStringIndex);
                                while (c > 255 || mAsciiKeys[(int) c] == null) {
                                    mStringIndex++;
                                    if (mStringIndex >= mStringToPlay.length()) {
                                        mPlaying = false;
                                        return;
                                    }
                                    c = mStringToPlay.charAt(mStringIndex);
                                }
                                int x = mAsciiKeys[c].x + 10;
                                int y = mAsciiKeys[c].y + 26;
                                MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(), 
                                        SystemClock.uptimeMillis(), 
                                        MotionEvent.ACTION_DOWN, x, y, 0);
                                LatinKeyboardView.this.dispatchTouchEvent(me);
                                me.recycle();
                                sendEmptyMessageDelayed(MSG_TOUCH_UP, 500); // Deliver up in 500ms if nothing else
                                // happens
                                mDownDelivered = true;
                                break;
                            case MSG_TOUCH_UP:
                                char cUp = mStringToPlay.charAt(mStringIndex);
                                int x2 = mAsciiKeys[cUp].x + 10;
                                int y2 = mAsciiKeys[cUp].y + 26;
                                mStringIndex++;
                                
                                MotionEvent me2 = MotionEvent.obtain(SystemClock.uptimeMillis(), 
                                        SystemClock.uptimeMillis(), 
                                        MotionEvent.ACTION_UP, x2, y2, 0);
                                LatinKeyboardView.this.dispatchTouchEvent(me2);
                                me2.recycle();
                                sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 500); // Deliver up in 500ms if nothing else
                                // happens
                                mDownDelivered = false;
                                break;
                        }
                    }
                };

            }
        }
    }

    private void findKeys() {
        List<Key> keys = getKeyboard().getKeys();
        // Get the keys on this keyboard
        for (int i = 0; i < keys.size(); i++) {
            int code = keys.get(i).codes[0];
            if (code >= 0 && code <= 255) { 
                mAsciiKeys[code] = keys.get(i);
            }
        }
    }
    
    void startPlaying(String s) {
        if (!DEBUG_AUTO_PLAY) return;
        if (s == null) return;
        mStringToPlay = s.toLowerCase();
        mPlaying = true;
        mDownDelivered = false;
        mStringIndex = 0;
        mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 10);
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);
        if (DEBUG_AUTO_PLAY && mPlaying) {
            mHandler2.removeMessages(MSG_TOUCH_DOWN);
            mHandler2.removeMessages(MSG_TOUCH_UP);
            if (mDownDelivered) {
                mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_UP, 20);
            } else {
                mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 20);
            }
        }
        if (DEBUG_LINE) {
            if (mPaint == null) {
                mPaint = new Paint();
                mPaint.setColor(0x80FFFFFF);
                mPaint.setAntiAlias(false);
            }
            c.drawLine(mLastX, 0, mLastX, getHeight(), mPaint);
            c.drawLine(0, mLastY, getWidth(), mLastY, mPaint);
        }
    }
}
