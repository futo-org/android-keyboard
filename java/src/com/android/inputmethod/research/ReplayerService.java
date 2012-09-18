/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.android.inputmethod.research.MotionEventReader.ReplayData;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Provide a mechanism to invoke the replayer from outside.
 *
 * In particular, makes access from a host possible through {@code adb am startservice}.
 */
public class ReplayerService extends IntentService {
    private static final String TAG = ReplayerService.class.getSimpleName();
    private static final String EXTRA_FILENAME = "com.android.inputmethod.research.extra.FILENAME";
    private static final long MAX_REPLAY_TIME = TimeUnit.SECONDS.toMillis(60);

    public ReplayerService() {
        super(ReplayerService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final String filename = intent.getStringExtra(EXTRA_FILENAME);
        if (filename == null) return;

        final ReplayData replayData = new MotionEventReader().readMotionEventData(
                new File(filename));
        synchronized (this) {
            Replayer.getInstance().replay(replayData, new Runnable() {
                @Override
                public void run() {
                    synchronized (ReplayerService.this) {
                        ReplayerService.this.notify();
                    }
                }
            });
            try {
                wait(MAX_REPLAY_TIME);
            } catch (InterruptedException e) {
                Log.e(TAG, "Timeout while replaying.", e);
            }
        }
    }
}
