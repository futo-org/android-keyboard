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

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {
    private static final ConcurrentHashMap<String, PrioritizedSerialExecutor> sExecutorMap =
            new ConcurrentHashMap<>();

    /**
     * Gets the executor for the given dictionary name.
     */
    public static PrioritizedSerialExecutor getExecutor(final String dictName) {
        PrioritizedSerialExecutor executor = sExecutorMap.get(dictName);
        if (executor == null) {
            synchronized(sExecutorMap) {
                executor = new PrioritizedSerialExecutor();
                sExecutorMap.put(dictName, executor);
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
            for (final PrioritizedSerialExecutor executor : sExecutorMap.values()) {
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
