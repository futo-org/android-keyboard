/*
 * Copyright (C) 2008-2009 Google Inc.
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

    private boolean mExtensionVisible;
    private LatinKeyboardView mExtension;
    private PopupWindow mExtensionPopup;
    private boolean mFirstEvent;

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

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        LatinKeyboard keyboard = (LatinKeyboard) getKeyboard();
        if (DEBUG_LINE) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            invalidate();
        }
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
        if (me.getY() < 0) {
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

    private boolean openExtension() {
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
            mExtension.setOnKeyboardActionListener((LatinIME) getContext());
            mExtension.setPopupParent(this);
            mExtension.setPopupOffset(0, -windowLocation[1]);
            Keyboard keyboard;
            mExtension.setKeyboard(keyboard = new LatinKeyboard(getContext(),
                    ((LatinKeyboard) getKeyboard()).getExtension()));
            mExtensionPopup.setContentView(mExtension);
            mExtensionPopup.setWidth(getWidth());
            mExtensionPopup.setHeight(keyboard.getHeight());
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
        mExtension.setVisibility(INVISIBLE);
        mExtension.closing();
        mExtensionVisible = false;
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

    @Override
    public void setKeyboard(Keyboard k) {
        super.setKeyboard(k);
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
