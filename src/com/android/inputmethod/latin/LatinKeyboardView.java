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

import android.content.Context;
import android.graphics.Canvas;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.List;

public class LatinKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;
    static final int KEYCODE_SHIFT_LONGPRESS = -101;

    private Keyboard mPhoneKeyboard;

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

    
    /****************************  INSTRUMENTATION  *******************************/

    static final boolean DEBUG_AUTO_PLAY = false;
    private static final int MSG_TOUCH_DOWN = 1;
    private static final int MSG_TOUCH_UP = 2;
    
    Handler mHandler2;
    
    private String mStringToPlay;
    private int mStringIndex;
    private boolean mDownDelivered;
    private Key[] mAsciiKeys = new Key[256];
    private boolean mPlaying;

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
    }
}
