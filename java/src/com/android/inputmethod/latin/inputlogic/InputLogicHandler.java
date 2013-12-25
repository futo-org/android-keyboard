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

package com.android.inputmethod.latin.inputlogic;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**
 * A helper to manage deferred tasks for the input logic.
 */
// TODO: Make this package private
public class InputLogicHandler implements Handler.Callback {
    final Handler mNonUIThreadHandler;

    public InputLogicHandler() {
        final HandlerThread handlerThread = new HandlerThread(
                InputLogicHandler.class.getSimpleName());
        handlerThread.start();
        mNonUIThreadHandler = new Handler(handlerThread.getLooper(), this);
    }

    public void destroy() {
        mNonUIThreadHandler.getLooper().quit();
    }

    /**
     * Handle a message.
     * @see android.os.Handler.Callback#handleMessage(android.os.Message)
     */
    @Override
    public boolean handleMessage(final Message msg) {
        return true;
    }
}
