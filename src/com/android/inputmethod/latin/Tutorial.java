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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.Drawable;
import android.opengl.Visibility;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class Tutorial {
    
    private List<Bubble> mBubbles = new ArrayList<Bubble>();
    private long mStartTime;
    private static final long MINIMUM_TIME = 6000;
    private static final long MAXIMUM_TIME = 20000;
    private View mInputView;
    private int[] mLocation = new int[2];
    private int mBubblePointerOffset;
    
    private static final int MSG_SHOW_BUBBLE = 0;
    private static final int MSG_HIDE_ALL = 1;
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_BUBBLE:
                    Bubble bubba = (Bubble) msg.obj;
                    bubba.show(mLocation[0], mLocation[1]);
                    break;
                case MSG_HIDE_ALL:
                    close(true);
            }
        }
    };

    class Bubble {
        Drawable bubbleBackground;
        int x;
        int y;
        int width;
        int gravity;
        String text;
        boolean dismissOnTouch;
        boolean dismissOnClose;
        PopupWindow window;
        TextView textView;
        View inputView;
        
        Bubble(Context context, View inputView,
                int backgroundResource, int bx, int by, int bw, int gravity, int textResource,
                boolean dismissOnTouch, boolean dismissOnClose) {
            bubbleBackground = context.getResources().getDrawable(backgroundResource);
            x = bx; 
            y = by;
            width = bw;
            this.gravity = gravity;
            text = context.getResources().getString(textResource);
            this.dismissOnTouch = dismissOnTouch;
            this.dismissOnClose = dismissOnClose;
            this.inputView = inputView;
            window = new PopupWindow(context);
            window.setBackgroundDrawable(null);
            LayoutInflater inflate =
                (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            textView = (TextView) inflate.inflate(R.layout.bubble_text, null);
            textView.setBackgroundDrawable(bubbleBackground);
            textView.setText(text);
            window.setContentView(textView);
            window.setFocusable(false);
            window.setTouchable(true);
            window.setOutsideTouchable(false);
            textView.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View view, MotionEvent me) {
                    Tutorial.this.touched();
                    return true;
                }
            });
        }

        private void chooseSize(PopupWindow pop, View parentView, CharSequence text, TextView tv) {
            int wid = tv.getPaddingLeft() + tv.getPaddingRight();
            int ht = tv.getPaddingTop() + tv.getPaddingBottom();

            /*
             * Figure out how big the text would be if we laid it out to the
             * full width of this view minus the border.
             */
            int cap = width - wid;

            Layout l = new StaticLayout(text, tv.getPaint(), cap,
                                        Layout.Alignment.ALIGN_NORMAL, 1, 0, true);
            float max = 0;
            for (int i = 0; i < l.getLineCount(); i++) {
                max = Math.max(max, l.getLineWidth(i));
            }

            /*
             * Now set the popup size to be big enough for the text plus the border.
             */
            pop.setWidth(width);
            pop.setHeight(ht + l.getHeight());
        }

        void show(int offx, int offy) {
            chooseSize(window, inputView, text, textView);
            if (inputView.getVisibility() == View.VISIBLE 
                    && inputView.getWindowVisibility() == View.VISIBLE) {
                try {
                    if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) offy -= window.getHeight();
                    if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) offx -= window.getWidth();
                    window.showAtLocation(inputView, Gravity.NO_GRAVITY, x + offx, y + offy);
                } catch (Exception e) {
                    // Input view is not valid
                }
            }
        }
        
        void hide() {
            textView.setOnTouchListener(null);
            if (window.isShowing()) {
                window.dismiss();
            }
        }
    }
    
    public Tutorial(LatinKeyboardView inputView) {
        Context context = inputView.getContext();
        int inputHeight = inputView.getHeight();
        int inputWidth = inputView.getWidth();
        mBubblePointerOffset = inputView.getContext().getResources()
            .getDimensionPixelOffset(R.dimen.bubble_pointer_offset);
        Bubble b0 = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step02, 0, 0, 
                inputWidth,
                Gravity.BOTTOM | Gravity.LEFT,
                R.string.tip_dismiss,
                false, true);
        mBubbles.add(b0);
//        Bubble b1 = new Bubble(context, inputView, 
//                R.drawable.dialog_bubble_step03, 
//                (int) (inputWidth * 0.85) + mBubblePointerOffset, inputHeight / 5, 
//                (int) (inputWidth * 0.45),
//                Gravity.TOP | Gravity.RIGHT,
//                R.string.tip_long_press,
//                true, false);
//        mBubbles.add(b1);
//        Bubble b2 = new Bubble(inputView.getContext(), inputView, 
//                R.drawable.dialog_bubble_step04, 
//                inputWidth / 10 - mBubblePointerOffset, inputHeight - inputHeight / 5,
//                (int) (inputWidth * 0.45),
//                Gravity.BOTTOM | Gravity.LEFT,
//                R.string.tip_access_symbols,
//                true, false);
//        mBubbles.add(b2);
        mInputView = inputView;
    }
    
    void start() {
        mInputView.getLocationInWindow(mLocation);
        long delayMillis = 0;
        for (int i = 0; i < mBubbles.size(); i++) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SHOW_BUBBLE, mBubbles.get(i)), delayMillis);
            delayMillis += 2000;
        }
        //mHandler.sendEmptyMessageDelayed(MSG_HIDE_ALL, MAXIMUM_TIME);
        mStartTime = SystemClock.uptimeMillis();
    }
    
    void touched() {
        if (SystemClock.uptimeMillis() - mStartTime < MINIMUM_TIME) {
            return;
        }
        for (int i = 0; i < mBubbles.size(); i++) {
            Bubble bubba = mBubbles.get(i);
            if (bubba.dismissOnTouch) {
                bubba.hide();
            }
        }
    }
    
    void close(boolean completed) {
        mHandler.removeMessages(MSG_SHOW_BUBBLE);
        for (int i = 0; i < mBubbles.size(); i++) {
            mBubbles.get(i).hide();
        }
        if (completed) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
                    mInputView.getContext());
            Editor editor = sp.edit();
            editor.putBoolean(LatinIME.PREF_TUTORIAL_RUN, true);
            editor.commit();
        }
    }
}
