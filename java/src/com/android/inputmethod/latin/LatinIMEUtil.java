/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.view.inputmethod.InputMethodManager;

import android.content.Context;
import android.os.AsyncTask;
import android.text.format.DateUtils;
import android.util.Log;

public class LatinIMEUtil {

    /**
     * Cancel an {@link AsyncTask}.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     */
    public static void cancelTask(AsyncTask<?, ?, ?> task, boolean mayInterruptIfRunning) {
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(mayInterruptIfRunning);
        }
    }

    public static class GCUtils {
        private static final String TAG = "GCUtils";
        public static final int GC_TRY_COUNT = 2;
        // GC_TRY_LOOP_MAX is used for the hard limit of GC wait,
        // GC_TRY_LOOP_MAX should be greater than GC_TRY_COUNT.
        public static final int GC_TRY_LOOP_MAX = 5;
        private static final long GC_INTERVAL = DateUtils.SECOND_IN_MILLIS;
        private static GCUtils sInstance = new GCUtils();
        private int mGCTryCount = 0;

        public static GCUtils getInstance() {
            return sInstance;
        }

        public void reset() {
            mGCTryCount = 0;
        }

        public boolean tryGCOrWait(String metaData, Throwable t) {
            if (mGCTryCount == 0) {
                System.gc();
            }
            if (++mGCTryCount > GC_TRY_COUNT) {
                LatinImeLogger.logOnException(metaData, t);
                return false;
            } else {
                try {
                    Thread.sleep(GC_INTERVAL);
                    return true;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Sleep was interrupted.");
                    LatinImeLogger.logOnException(metaData, t);
                    return false;
                }
            }
        }
    }

    public static boolean hasMultipleEnabledIMEs(Context context) {
        return ((InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE)).getEnabledInputMethodList().size() > 1;
    }
}
