/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.PointerTracker;

import javax.annotation.Nonnull;

public interface TimerProxy {
    /**
     * Start a timer to detect if a user is typing keys.
     * @param typedKey the key that is typed.
     */
    public void startTypingStateTimer(@Nonnull Key typedKey);

    /**
     * Check if a user is key typing.
     * @return true if a user is in typing.
     */
    public boolean isTypingState();

    /**
     * Start a timer to simulate repeated key presses while a user keep pressing a key.
     * @param tracker the {@link PointerTracker} that points the key to be repeated.
     * @param repeatCount the number of times that the key is repeating. Starting from 1.
     * @param delay the interval delay to the next key repeat, in millisecond.
     */
    public void startKeyRepeatTimerOf(@Nonnull PointerTracker tracker, int repeatCount, int delay);

    /**
     * Start a timer to detect a long pressed key.
     * If a key pointed by <code>tracker</code> is a shift key, start another timer to detect
     * long pressed shift key.
     * @param tracker the {@link PointerTracker} that starts long pressing.
     * @param delay the delay to fire the long press timer, in millisecond.
     */
    public void startLongPressTimerOf(@Nonnull PointerTracker tracker, int delay);

    /**
     * Cancel timers for detecting a long pressed key and a long press shift key.
     * @param tracker cancel long press timers of this {@link PointerTracker}.
     */
    public void cancelLongPressTimersOf(@Nonnull PointerTracker tracker);

    /**
     * Cancel a timer for detecting a long pressed shift key.
     */
    public void cancelLongPressShiftKeyTimer();

    /**
     * Cancel timers for detecting repeated key press, long pressed key, and long pressed shift key.
     * @param tracker the {@link PointerTracker} that starts timers to be canceled.
     */
    public void cancelKeyTimersOf(@Nonnull PointerTracker tracker);

    /**
     * Start a timer to detect double tapped shift key.
     */
    public void startDoubleTapShiftKeyTimer();

    /**
     * Cancel a timer of detecting double tapped shift key.
     */
    public void cancelDoubleTapShiftKeyTimer();

    /**
     * Check if a timer of detecting double tapped shift key is running.
     * @return true if detecting double tapped shift key is on going.
     */
    public boolean isInDoubleTapShiftKeyTimeout();

    /**
     * Start a timer to fire updating batch input while <code>tracker</code> is on hold.
     * @param tracker the {@link PointerTracker} that stops moving.
     */
    public void startUpdateBatchInputTimer(@Nonnull PointerTracker tracker);

    /**
     * Cancel a timer of firing updating batch input.
     * @param tracker the {@link PointerTracker} that resumes moving or ends gesture input.
     */
    public void cancelUpdateBatchInputTimer(@Nonnull PointerTracker tracker);

    /**
     * Cancel all timers of firing updating batch input.
     */
    public void cancelAllUpdateBatchInputTimers();

    public static class Adapter implements TimerProxy {
        @Override
        public void startTypingStateTimer(@Nonnull Key typedKey) {}
        @Override
        public boolean isTypingState() { return false; }
        @Override
        public void startKeyRepeatTimerOf(@Nonnull PointerTracker tracker, int repeatCount,
                int delay) {}
        @Override
        public void startLongPressTimerOf(@Nonnull PointerTracker tracker, int delay) {}
        @Override
        public void cancelLongPressTimersOf(@Nonnull PointerTracker tracker) {}
        @Override
        public void cancelLongPressShiftKeyTimer() {}
        @Override
        public void cancelKeyTimersOf(@Nonnull PointerTracker tracker) {}
        @Override
        public void startDoubleTapShiftKeyTimer() {}
        @Override
        public void cancelDoubleTapShiftKeyTimer() {}
        @Override
        public boolean isInDoubleTapShiftKeyTimeout() { return false; }
        @Override
        public void startUpdateBatchInputTimer(@Nonnull PointerTracker tracker) {}
        @Override
        public void cancelUpdateBatchInputTimer(@Nonnull PointerTracker tracker) {}
        @Override
        public void cancelAllUpdateBatchInputTimers() {}
    }
}
