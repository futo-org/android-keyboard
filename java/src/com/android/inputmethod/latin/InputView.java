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

package com.android.inputmethod.latin;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public final class InputView extends LinearLayout {
    private View mSuggestionStripView;
    private View mKeyboardView;
    private int mKeyboardTopPadding;

    private boolean mIsForwardingEvent;
    private final Rect mInputViewRect = new Rect();
    private final Rect mEventForwardingRect = new Rect();
    private final Rect mEventReceivingRect = new Rect();

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public void setKeyboardGeometry(final int keyboardTopPadding) {
        mKeyboardTopPadding = keyboardTopPadding;
    }

    @Override
    protected void onFinishInflate() {
        mSuggestionStripView = findViewById(R.id.suggestion_strip_view);
        mKeyboardView = findViewById(R.id.keyboard_view);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        if (mSuggestionStripView.getVisibility() != VISIBLE
                || mKeyboardView.getVisibility() != VISIBLE) {
            return super.dispatchTouchEvent(me);
        }

        // The touch events that hit the top padding of keyboard should be forwarded to
        // {@link SuggestionStripView}.
        final Rect rect = mInputViewRect;
        this.getGlobalVisibleRect(rect);
        final int x = (int)me.getX() + rect.left;
        final int y = (int)me.getY() + rect.top;

        final Rect forwardingRect = mEventForwardingRect;
        mKeyboardView.getGlobalVisibleRect(forwardingRect);
        if (!mIsForwardingEvent && !forwardingRect.contains(x, y)) {
            return super.dispatchTouchEvent(me);
        }

        final int forwardingLimitY = forwardingRect.top + mKeyboardTopPadding;
        boolean sendToTarget = false;

        switch (me.getAction()) {
        case MotionEvent.ACTION_DOWN:
            if (y < forwardingLimitY) {
                // This down event and further move and up events should be forwarded to the target.
                mIsForwardingEvent = true;
                sendToTarget = true;
            }
            break;
        case MotionEvent.ACTION_MOVE:
            sendToTarget = mIsForwardingEvent;
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            sendToTarget = mIsForwardingEvent;
            mIsForwardingEvent = false;
            break;
        }

        if (!sendToTarget) {
            return super.dispatchTouchEvent(me);
        }

        final Rect receivingRect = mEventReceivingRect;
        mSuggestionStripView.getGlobalVisibleRect(receivingRect);
        final int translatedX = x - receivingRect.left;
        final int translatedY;
        if (y < forwardingLimitY) {
            // The forwarded event should have coordinates that are inside of the target.
            translatedY = Math.min(y - receivingRect.top, receivingRect.height() - 1);
        } else {
            translatedY = y - receivingRect.top;
        }
        me.setLocation(translatedX, translatedY);
        mSuggestionStripView.dispatchTouchEvent(me);
        return true;
    }
}
