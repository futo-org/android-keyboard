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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {

    private static final String STATIC_LANGUAGE_MODEL_UPDATE = "StaticLanguageModelUpdate";
    private static final String DYNAMIC_LANGUAGE_MODEL_UPDATE = "DynamicLanguageModelUpdate";

    private static final ConcurrentHashMap<String, ScheduledExecutorService> sExecutorMap =
            new ConcurrentHashMap<>();

    @UsedForTesting
    private static ScheduledExecutorService sExecutorServiceForTests;

    @UsedForTesting
    public static void setExecutorServiceForTests(
            final ScheduledExecutorService executorServiceForTests) {
        sExecutorServiceForTests = executorServiceForTests;
    }

    /**
     * @return scheduled executor service used to update static language models
     */
    public static ScheduledExecutorService getExecutorForStaticLanguageModelUpdate() {
        return getExecutor(STATIC_LANGUAGE_MODEL_UPDATE);
    }

    /**
     * @return scheduled executor service used to update dynamic language models
     */
    public static ScheduledExecutorService getExecutorForDynamicLanguageModelUpdate() {
        return getExecutor(DYNAMIC_LANGUAGE_MODEL_UPDATE);
    }

    /**
     * Gets the executor for the given id.
     */
    private static ScheduledExecutorService getExecutor(final String id) {
        if (sExecutorServiceForTests != null) {
            return sExecutorServiceForTests;
        }
        ScheduledExecutorService executor = sExecutorMap.get(id);
        if (executor == null) {
            synchronized (sExecutorMap) {
                executor = sExecutorMap.get(id);
                if (executor == null) {
                    executor = Executors.newSingleThreadScheduledExecutor(new ExecutorFactory(id));
                    sExecutorMap.put(id, executor);
                }
            }
        }
        return executor;
    }

    /**
     * Shutdowns all executors and removes all executors from the executor map for testing.
     */
    @UsedForTesting
    public static void shutdownAllExecutors() {
        synchronized (sExecutorMap) {
            for (final ScheduledExecutorService executor : sExecutorMap.values()) {
                executor.execute(new ExecutorShutdown(executor));
            }
            sExecutorMap.clear();
        }
    }

    private static class ExecutorFactory implements ThreadFactory {
        private final String mThreadName;

        public ExecutorFactory(final String threadName) {
            mThreadName = threadName;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            Thread thread = new Thread(runnable, mThreadName);
            thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    Log.w(mThreadName + "-" + runnable.getClass().getSimpleName(), ex);
                }
            });
            return thread;
        }
    }

    private static class ExecutorShutdown implements Runnable {
        private final ScheduledExecutorService mExecutor;

        public ExecutorShutdown(final ScheduledExecutorService executor) {
            mExecutor = executor;
        }

        @Override
        public void run() {
            mExecutor.shutdown();
        }
    }
}
