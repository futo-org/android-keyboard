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

import android.os.Message;
import android.os.SystemClock;
import android.view.ViewConfiguration;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.keyboard.internal.TimerHandler.Callbacks;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;

// TODO: Separate this class into KeyTimerHandler and BatchInputTimerHandler or so.
public final class TimerHandler extends LeakGuardHandlerWrapper<Callbacks> implements TimerProxy {
    public interface Callbacks {
        public void startWhileTypingFadeinAnimation();
        public void startWhileTypingFadeoutAnimation();
        public void onLongPress(PointerTracker tracker);
    }

    private static final int MSG_TYPING_STATE_EXPIRED = 0;
    private static final int MSG_REPEAT_KEY = 1;
    private static final int MSG_LONGPRESS_KEY = 2;
    private static final int MSG_LONGPRESS_SHIFT_KEY = 3;
    private static final int MSG_DOUBLE_TAP_SHIFT_KEY = 4;
    private static final int MSG_UPDATE_BATCH_INPUT = 5;

    private final int mIgnoreAltCodeKeyTimeout;
    private final int mGestureRecognitionUpdateTime;

    public TimerHandler(final Callbacks ownerInstance, final int ignoreAltCodeKeyTimeout,
            final int gestureRecognitionUpdateTime) {
        super(ownerInstance);
        mIgnoreAltCodeKeyTimeout = ignoreAltCodeKeyTimeout;
        mGestureRecognitionUpdateTime = gestureRecognitionUpdateTime;
    }

    @Override
    public void handleMessage(final Message msg) {
        final Callbacks callbacks = getOwnerInstance();
        if (callbacks == null) {
            return;
        }
        final PointerTracker tracker = (PointerTracker) msg.obj;
        switch (msg.what) {
        case MSG_TYPING_STATE_EXPIRED:
            callbacks.startWhileTypingFadeinAnimation();
            break;
        case MSG_REPEAT_KEY:
            tracker.onKeyRepeat(msg.arg1 /* code */, msg.arg2 /* repeatCount */);
            break;
        case MSG_LONGPRESS_KEY:
        case MSG_LONGPRESS_SHIFT_KEY:
            cancelLongPressTimers();
            callbacks.onLongPress(tracker);
            break;
        case MSG_UPDATE_BATCH_INPUT:
            tracker.updateBatchInputByTimer(SystemClock.uptimeMillis());
            startUpdateBatchInputTimer(tracker);
            break;
        }
    }

    @Override
    public void startKeyRepeatTimerOf(final PointerTracker tracker, final int repeatCount,
            final int delay) {
        final Key key = tracker.getKey();
        if (key == null || delay == 0) {
            return;
        }
        sendMessageDelayed(
                obtainMessage(MSG_REPEAT_KEY, key.getCode(), repeatCount, tracker), delay);
    }

    private void cancelKeyRepeatTimerOf(final PointerTracker tracker) {
        removeMessages(MSG_REPEAT_KEY, tracker);
    }

    public void cancelKeyRepeatTimers() {
        removeMessages(MSG_REPEAT_KEY);
    }

    // TODO: Suppress layout changes in key repeat mode
    public boolean isInKeyRepeat() {
        return hasMessages(MSG_REPEAT_KEY);
    }

    @Override
    public void startLongPressTimerOf(final PointerTracker tracker, final int delay) {
        final Key key = tracker.getKey();
        if (key == null) {
            return;
        }
        // Use a separate message id for long pressing shift key, because long press shift key
        // timers should be canceled when other key is pressed.
        final int messageId = (key.getCode() == Constants.CODE_SHIFT)
                ? MSG_LONGPRESS_SHIFT_KEY : MSG_LONGPRESS_KEY;
        sendMessageDelayed(obtainMessage(messageId, tracker), delay);
    }

    @Override
    public void cancelLongPressTimerOf(final PointerTracker tracker) {
        removeMessages(MSG_LONGPRESS_KEY, tracker);
        removeMessages(MSG_LONGPRESS_SHIFT_KEY, tracker);
    }

    @Override
    public void cancelLongPressShiftKeyTimers() {
        removeMessages(MSG_LONGPRESS_SHIFT_KEY);
    }

    public void cancelLongPressTimers() {
        removeMessages(MSG_LONGPRESS_KEY);
        removeMessages(MSG_LONGPRESS_SHIFT_KEY);
    }

    @Override
    public void startTypingStateTimer(final Key typedKey) {
        if (typedKey.isModifier() || typedKey.altCodeWhileTyping()) {
            return;
        }

        final boolean isTyping = isTypingState();
        removeMessages(MSG_TYPING_STATE_EXPIRED);
        final Callbacks callbacks = getOwnerInstance();
        if (callbacks == null) {
            return;
        }

        // When user hits the space or the enter key, just cancel the while-typing timer.
        final int typedCode = typedKey.getCode();
        if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
            if (isTyping) {
                callbacks.startWhileTypingFadeinAnimation();
            }
            return;
        }

        sendMessageDelayed(
                obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout);
        if (isTyping) {
            return;
        }
        callbacks.startWhileTypingFadeoutAnimation();
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
    public void cancelKeyTimersOf(final PointerTracker tracker) {
        cancelKeyRepeatTimerOf(tracker);
        cancelLongPressTimerOf(tracker);
    }

    public void cancelAllKeyTimers() {
        cancelKeyRepeatTimers();
        cancelLongPressTimers();
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
        cancelAllKeyTimers();
        cancelAllUpdateBatchInputTimers();
    }
}
