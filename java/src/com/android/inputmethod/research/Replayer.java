/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.MotionEventReader.ReplayData;

/**
 * Replays a sequence of motion events in realtime on the screen.
 *
 * Useful for user inspection of logged data.
 */
public class Replayer {
    private static final String TAG = Replayer.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    private static final long START_TIME_DELAY_MS = 500;

    private boolean mIsReplaying = false;
    private KeyboardSwitcher mKeyboardSwitcher;

    private Replayer() {
    }

    private static final Replayer sInstance = new Replayer();
    public static Replayer getInstance() {
        return sInstance;
    }

    public void setKeyboardSwitcher(final KeyboardSwitcher keyboardSwitcher) {
        mKeyboardSwitcher = keyboardSwitcher;
    }

    private static final int MSG_MOTION_EVENT = 0;
    private static final int MSG_DONE = 1;
    private static final int COMPLETION_TIME_MS = 500;

    // TODO: Support historical events and multi-touch.
    public void replay(final ReplayData replayData, final Runnable callback) {
        if (mIsReplaying) {
            return;
        }
        mIsReplaying = true;
        final int numActions = replayData.mActions.size();
        if (DEBUG) {
            Log.d(TAG, "replaying " + numActions + " actions");
        }
        if (numActions == 0) {
            mIsReplaying = false;
            return;
        }
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();

        // The reference time relative to the times stored in events.
        final long origStartTime = replayData.mTimes.get(0);
        // The reference time relative to which events are replayed in the present.
        final long currentStartTime = SystemClock.uptimeMillis() + START_TIME_DELAY_MS;
        // The adjustment needed to translate times from the original recorded time to the current
        // time.
        final long timeAdjustment = currentStartTime - origStartTime;
        final Handler handler = new Handler(Looper.getMainLooper()) {
            // Track the time of the most recent DOWN event, to be passed as a parameter when
            // constructing a MotionEvent.  It's initialized here to the origStartTime, but this is
            // only a precaution.  The value should be overwritten by the first ACTION_DOWN event
            // before the first use of the variable.  Note that this may cause the first few events
            // to have incorrect {@code downTime}s.
            private long mOrigDownTime = origStartTime;

            @Override
            public void handleMessage(final Message msg) {
                switch (msg.what) {
                case MSG_MOTION_EVENT:
                    final int index = msg.arg1;
                    final int action = replayData.mActions.get(index);
                    final PointerProperties[] pointerPropertiesArray =
                            replayData.mPointerPropertiesArrays.get(index);
                    final PointerCoords[] pointerCoordsArray =
                            replayData.mPointerCoordsArrays.get(index);
                    final long origTime = replayData.mTimes.get(index);
                    if (action == MotionEvent.ACTION_DOWN) {
                        mOrigDownTime = origTime;
                    }

                    final MotionEvent me = MotionEvent.obtain(mOrigDownTime + timeAdjustment,
                            origTime + timeAdjustment, action,
                            pointerPropertiesArray.length, pointerPropertiesArray,
                            pointerCoordsArray, 0, 0, 1.0f, 1.0f, 0, 0, 0, 0);
                    mainKeyboardView.processMotionEvent(me);
                    me.recycle();
                    break;
                case MSG_DONE:
                    mIsReplaying = false;
                    ResearchLogger.getInstance().requestIndicatorRedraw();
                    break;
                }
            }
        };

        handler.post(new Runnable() {
            @Override
            public void run() {
                ResearchLogger.getInstance().requestIndicatorRedraw();
            }
        });
        for (int i = 0; i < numActions; i++) {
            final Message msg = Message.obtain(handler, MSG_MOTION_EVENT, i, 0);
            final long msgTime = replayData.mTimes.get(i) + timeAdjustment;
            handler.sendMessageAtTime(msg, msgTime);
            if (DEBUG) {
                Log.d(TAG, "queuing event at " + msgTime);
            }
        }

        final long presentDoneTime = replayData.mTimes.get(numActions - 1) + timeAdjustment
                + COMPLETION_TIME_MS;
        handler.sendMessageAtTime(Message.obtain(handler, MSG_DONE), presentDoneTime);
        if (callback != null) {
            handler.postAtTime(callback, presentDoneTime + 1);
        }
    }

    public boolean isReplaying() {
        return mIsReplaying;
    }
}
