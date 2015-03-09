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

package com.android.inputmethod.latin.utils;

import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {

    private static final String TAG = "ExecutorUtils";

    private static final ScheduledExecutorService sExecutorService =
            Executors.newSingleThreadScheduledExecutor(new ExecutorFactory());

    private static class ExecutorFactory implements ThreadFactory {
        @Override
        public Thread newThread(final Runnable runnable) {
            Thread thread = new Thread(runnable, TAG);
            thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    Log.w(TAG + "-" + runnable.getClass().getSimpleName(), ex);
                }
            });
            return thread;
        }
    }

    @UsedForTesting
    private static ScheduledExecutorService sExecutorServiceForTests;

    @UsedForTesting
    public static void setExecutorServiceForTests(
            final ScheduledExecutorService executorServiceForTests) {
        sExecutorServiceForTests = executorServiceForTests;
    }

    //
    // Public methods used to schedule a runnable for execution.
    //

    /**
     * @return scheduled executor service used to run background tasks
     */
    public static ScheduledExecutorService getBackgroundExecutor() {
        if (sExecutorServiceForTests != null) {
            return sExecutorServiceForTests;
        }
        return sExecutorService;
    }

    public static Runnable chain(final Runnable... runnables) {
        return new RunnableChain(runnables);
    }

    private static class RunnableChain implements Runnable {
        private final Runnable[] mRunnables;

        private RunnableChain(final Runnable... runnables) {
            if (runnables == null || runnables.length == 0) {
                throw new IllegalArgumentException("Attempting to construct an empty chain");
            }
            mRunnables = runnables;
        }

        @Override
        public void run() {
            for (Runnable runnable : mRunnables) {
                if (Thread.interrupted()) {
                    return;
                }
                runnable.run();
            }
        }
    }
}
