/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.os.Message;
import android.os.SystemClock;
import android.view.ViewConfiguration;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.StaticInnerHandlerWrapper;

public final class MainKeyboardViewTimerHandler extends StaticInnerHandlerWrapper<MainKeyboardView>
        implements TimerProxy {
    private static final int MSG_TYPING_STATE_EXPIRED = 0;
    private static final int MSG_REPEAT_KEY = 1;
    private static final int MSG_LONGPRESS_KEY = 2;
    private static final int MSG_DOUBLE_TAP_SHIFT_KEY = 3;
    private static final int MSG_UPDATE_BATCH_INPUT = 4;

    private final int mIgnoreAltCodeKeyTimeout;
    private final int mGestureRecognitionUpdateTime;

    public MainKeyboardViewTimerHandler(final MainKeyboardView outerInstance,
            final TypedArray mainKeyboardViewAttr) {
        super(outerInstance);

        mIgnoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
        mGestureRecognitionUpdateTime = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0);
    }

    @Override
    public void handleMessage(final Message msg) {
        final MainKeyboardView keyboardView = getOuterInstance();
        if (keyboardView == null) {
            return;
        }
        final PointerTracker tracker = (PointerTracker) msg.obj;
        switch (msg.what) {
        case MSG_TYPING_STATE_EXPIRED:
            keyboardView.startWhileTypingFadeinAnimation();
            break;
        case MSG_REPEAT_KEY:
            tracker.onKeyRepeat(msg.arg1 /* code */, msg.arg2 /* repeatCount */);
            break;
        case MSG_LONGPRESS_KEY:
            keyboardView.onLongPress(tracker);
            break;
        case MSG_UPDATE_BATCH_INPUT:
            tracker.updateBatchInputByTimer(SystemClock.uptimeMillis());
            startUpdateBatchInputTimer(tracker);
            break;
        }
    }

    @Override
    public void startKeyRepeatTimer(final PointerTracker tracker, final int repeatCount,
            final int delay) {
        final Key key = tracker.getKey();
        if (key == null || delay == 0) {
            return;
        }
        sendMessageDelayed(
                obtainMessage(MSG_REPEAT_KEY, key.getCode(), repeatCount, tracker), delay);
    }

    public void cancelKeyRepeatTimer() {
        removeMessages(MSG_REPEAT_KEY);
    }

    // TODO: Suppress layout changes in key repeat mode
    public boolean isInKeyRepeat() {
        return hasMessages(MSG_REPEAT_KEY);
    }

    @Override
    public void startLongPressTimer(final PointerTracker tracker, final int delay) {
        cancelLongPressTimer();
        if (delay <= 0) return;
        sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, tracker), delay);
    }

    @Override
    public void cancelLongPressTimer() {
        removeMessages(MSG_LONGPRESS_KEY);
    }

    @Override
    public void startTypingStateTimer(final Key typedKey) {
        if (typedKey.isModifier() || typedKey.altCodeWhileTyping()) {
            return;
        }

        final boolean isTyping = isTypingState();
        removeMessages(MSG_TYPING_STATE_EXPIRED);
        final MainKeyboardView keyboardView = getOuterInstance();

        // When user hits the space or the enter key, just cancel the while-typing timer.
        final int typedCode = typedKey.getCode();
        if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
            if (isTyping) {
                keyboardView.startWhileTypingFadeinAnimation();
            }
            return;
        }

        sendMessageDelayed(
                obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout);
        if (isTyping) {
            return;
        }
        keyboardView.startWhileTypingFadeoutAnimation();
    }

    @Override
    public boolean isTypingState() {
        return hasMessages(MSG_TYPING_STATE_EXPIRED);
    }

    @Override
    public void startDoubleTapShiftKeyTimer() {
        sendMessageDelayed(obtainMessage(MSG_DOUBLE_TAP_SHIFT_KEY),
                ViewConfiguration.getDoubleTapTimeout());
    }

    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        removeMessages(MSG_DOUBLE_TAP_SHIFT_KEY);
    }

    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        return hasMessages(MSG_DOUBLE_TAP_SHIFT_KEY);
    }

    @Override
    public void cancelKeyTimers() {
        cancelKeyRepeatTimer();
        cancelLongPressTimer();
    }

    @Override
    public void startUpdateBatchInputTimer(final PointerTracker tracker) {
        if (mGestureRecognitionUpdateTime <= 0) {
            return;
        }
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker);
        sendMessageDelayed(obtainMessage(MSG_UPDATE_BATCH_INPUT, tracker),
                mGestureRecognitionUpdateTime);
    }

    @Override
    public void cancelUpdateBatchInputTimer(final PointerTracker tracker) {
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker);
    }

    @Override
    public void cancelAllUpdateBatchInputTimers() {
        removeMessages(MSG_UPDATE_BATCH_INPUT);
    }

    public void cancelAllMessages() {
        cancelKeyTimers();
        cancelAllUpdateBatchInputTimers();
    }
}
