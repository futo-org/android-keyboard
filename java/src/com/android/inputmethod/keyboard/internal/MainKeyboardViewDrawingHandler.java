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
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.utils.StaticInnerHandlerWrapper;

public class MainKeyboardViewDrawingHandler extends StaticInnerHandlerWrapper<MainKeyboardView> {
    private static final int MSG_DISMISS_KEY_PREVIEW = 0;
    private static final int MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;

    public MainKeyboardViewDrawingHandler(final MainKeyboardView outerInstance) {
        super(outerInstance);
    }

    @Override
    public void handleMessage(final Message msg) {
        final MainKeyboardView mainKeyboardView = getOuterInstance();
        if (mainKeyboardView == null) return;
        switch (msg.what) {
        case MSG_DISMISS_KEY_PREVIEW:
            mainKeyboardView.dismissKeyPreviewWithoutDelay((Key)msg.obj);
            break;
        case MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT:
            mainKeyboardView.showGestureFloatingPreviewText(SuggestedWords.EMPTY);
            break;
        }
    }

    public void dismissKeyPreview(final long delay, final Key key) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, key), delay);
    }

    private void cancelAllDismissKeyPreviews() {
        removeMessages(MSG_DISMISS_KEY_PREVIEW);
        final MainKeyboardView mainKeyboardView = getOuterInstance();
        if (mainKeyboardView == null) return;
        mainKeyboardView.dismissAllKeyPreviews();
    }

    public void dismissGestureFloatingPreviewText(final long delay) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT), delay);
    }

    public void cancelAllMessages() {
        cancelAllDismissKeyPreviews();
    }
}
