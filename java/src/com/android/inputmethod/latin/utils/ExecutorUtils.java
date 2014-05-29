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

import com.android.inputmethod.annotations.UsedForTesting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {
    private static final ConcurrentHashMap<String, ExecutorService> sExecutorMap =
            new ConcurrentHashMap<>();

    private static class ThreadFactoryWithId implements ThreadFactory {
        private final String mId;

        public ThreadFactoryWithId(final String id) {
            mId = id;
        }

        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(r, "Executor - " + mId);
        }
    }

    /**
     * Gets the executor for the given id.
     */
    public static ExecutorService getExecutor(final String id) {
        ExecutorService executor = sExecutorMap.get(id);
        if (executor == null) {
            synchronized(sExecutorMap) {
                executor = sExecutorMap.get(id);
                if (executor == null) {
                    executor = Executors.newSingleThreadExecutor(new ThreadFactoryWithId(id));
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
        synchronized(sExecutorMap) {
            for (final ExecutorService executor : sExecutorMap.values()) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        executor.shutdown();
                        sExecutorMap.remove(executor);
                    }
                });
            }
        }
    }
}
