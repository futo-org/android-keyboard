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

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.internal.DrawingHandler.Callbacks;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;

// TODO: Separate this class into KeyPreviewHandler and BatchInputPreviewHandler or so.
public class DrawingHandler extends LeakGuardHandlerWrapper<Callbacks> {
    public interface Callbacks {
        public void dismissKeyPreviewWithoutDelay(Key key);
        public void dismissAllKeyPreviews();
        public void showGestureFloatingPreviewText(SuggestedWords suggestedWords);
    }

    private static final int MSG_DISMISS_KEY_PREVIEW = 0;
    private static final int MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;

    public DrawingHandler(final Callbacks ownerInstance) {
        super(ownerInstance);
    }

    @Override
    public void handleMessage(final Message msg) {
        final Callbacks callbacks = getOwnerInstance();
        if (callbacks == null) {
            return;
        }
        switch (msg.what) {
        case MSG_DISMISS_KEY_PREVIEW:
            callbacks.dismissKeyPreviewWithoutDelay((Key)msg.obj);
            break;
        case MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT:
            callbacks.showGestureFloatingPreviewText(SuggestedWords.EMPTY);
            break;
        }
    }

    public void dismissKeyPreview(final long delay, final Key key) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, key), delay);
    }

    private void cancelAllDismissKeyPreviews() {
        removeMessages(MSG_DISMISS_KEY_PREVIEW);
        final Callbacks callbacks = getOwnerInstance();
        if (callbacks == null) {
            return;
        }
        callbacks.dismissAllKeyPreviews();
    }

    public void dismissGestureFloatingPreviewText(final long delay) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT), delay);
    }

    public void cancelAllMessages() {
        cancelAllDismissKeyPreviews();
    }
}
