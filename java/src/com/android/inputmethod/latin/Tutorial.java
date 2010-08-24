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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class Tutorial implements OnTouchListener {
    
    private List<Bubble> mBubbles = new ArrayList<Bubble>();
    private View mInputView;
    private LatinIME mIme;
    private int[] mLocation = new int[2];
    
    private static final int MSG_SHOW_BUBBLE = 0;
    
    private int mBubbleIndex;
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_BUBBLE:
                    Bubble bubba = (Bubble) msg.obj;
                    bubba.show(mLocation[0], mLocation[1]);
                    break;
            }
        }
    };

    class Bubble {
        Drawable bubbleBackground;
        int x;
        int y;
        int width;
        int gravity;
        CharSequence text;
        boolean dismissOnTouch;
        boolean dismissOnClose;
        PopupWindow window;
        TextView textView;
        View inputView;
        
        Bubble(Context context, View inputView,
                int backgroundResource, int bx, int by, int textResource1, int textResource2) {
            bubbleBackground = context.getResources().getDrawable(backgroundResource);
            x = bx;
            y = by;
            width = (int) (inputView.getWidth() * 0.9);
            this.gravity = Gravity.TOP | Gravity.LEFT;
            text = new SpannableStringBuilder()
                .append(context.getResources().getText(textResource1))
                .append("\n") 
                .append(context.getResources().getText(textResource2));
            this.dismissOnTouch = true;
            this.dismissOnClose = false;
            this.inputView = inputView;
            window = new PopupWindow(context);
            window.setBackgroundDrawable(null);
            LayoutInflater inflate =
                (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            textView = (TextView) inflate.inflate(R.layout.bubble_text, null);
            textView.setBackgroundDrawable(bubbleBackground);
            textView.setText(text);
            //textView.setText(textResource1);
            window.setContentView(textView);
            window.setFocusable(false);
            window.setTouchable(true);
            window.setOutsideTouchable(false);
        }

        private int chooseSize(PopupWindow pop, View parentView, CharSequence text, TextView tv) {
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
            return l.getHeight();
        }

        void show(int offx, int offy) {
            int textHeight = chooseSize(window, inputView, text, textView);
            offy -= textView.getPaddingTop() + textHeight;
            if (inputView.getVisibility() == View.VISIBLE 
                    && inputView.getWindowVisibility() == View.VISIBLE) {
                try {
                    if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) offy -= window.getHeight();
                    if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) offx -= window.getWidth();
                    textView.setOnTouchListener(new View.OnTouchListener() {
                        public boolean onTouch(View view, MotionEvent me) {
                            Tutorial.this.next();
                            return true;
                        }
                    });
                    window.showAtLocation(inputView, Gravity.NO_GRAVITY, x + offx, y + offy);
                } catch (Exception e) {
                    // Input view is not valid
                }
            }
        }
        
        void hide() {
            if (window.isShowing()) {
                textView.setOnTouchListener(null);
                window.dismiss();
            }
        }
        
        boolean isShowing() {
            return window.isShowing();
        }
    }
    
    public Tutorial(LatinIME ime, LatinKeyboardView inputView) {
        Context context = inputView.getContext();
        mIme = ime;
        int inputWidth = inputView.getWidth();
        final int x = inputWidth / 20; // Half of 1/10th
        Bubble bWelcome = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step02, x, 0, 
                R.string.tip_to_open_keyboard, R.string.touch_to_continue);
        mBubbles.add(bWelcome);
        Bubble bAccents = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step02, x, 0, 
                R.string.tip_to_view_accents, R.string.touch_to_continue);
        mBubbles.add(bAccents);
        Bubble b123 = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step07, x, 0, 
                R.string.tip_to_open_symbols, R.string.touch_to_continue);
        mBubbles.add(b123);
        Bubble bABC = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step07, x, 0, 
                R.string.tip_to_close_symbols, R.string.touch_to_continue);
        mBubbles.add(bABC);
        Bubble bSettings = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step07, x, 0, 
                R.string.tip_to_launch_settings, R.string.touch_to_continue);
        mBubbles.add(bSettings);
        Bubble bDone = new Bubble(context, inputView, 
                R.drawable.dialog_bubble_step02, x, 0, 
                R.string.tip_to_start_typing, R.string.touch_to_finish);
        mBubbles.add(bDone);
        mInputView = inputView;
    }
    
    void start() {
        mInputView.getLocationInWindow(mLocation);
        mBubbleIndex = -1;
        mInputView.setOnTouchListener(this);
        next();
    }

    boolean next() {
        if (mBubbleIndex >= 0) {
            // If the bubble is not yet showing, don't move to the next.
            if (!mBubbles.get(mBubbleIndex).isShowing()) {
                return true;
            }
            // Hide all previous bubbles as well, as they may have had a delayed show
            for (int i = 0; i <= mBubbleIndex; i++) {
                mBubbles.get(i).hide();
            }
        }
        mBubbleIndex++;
        if (mBubbleIndex >= mBubbles.size()) {
            mInputView.setOnTouchListener(null);
            mIme.sendDownUpKeyEvents(-1); // Inform the setupwizard that tutorial is in last bubble
            mIme.tutorialDone();
            return false;
        }
        if (mBubbleIndex == 3 || mBubbleIndex == 4) {
            mIme.mKeyboardSwitcher.toggleSymbols();
        }
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(MSG_SHOW_BUBBLE, mBubbles.get(mBubbleIndex)), 500);
        return true;
    }
    
    void hide() {
        for (int i = 0; i < mBubbles.size(); i++) {
            mBubbles.get(i).hide();
        }
        mInputView.setOnTouchListener(null);
    }

    boolean close() {
        mHandler.removeMessages(MSG_SHOW_BUBBLE);
        hide();
        return true;
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            next();
        }
        return true;
    }
}
