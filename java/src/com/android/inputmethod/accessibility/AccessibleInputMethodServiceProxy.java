/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

public class AccessibleInputMethodServiceProxy implements AccessibleKeyboardActionListener {
    private static final AccessibleInputMethodServiceProxy sInstance =
            new AccessibleInputMethodServiceProxy();

    /*
     * Delay for the handler event that's fired when Accessibility is on and the
     * user hovers outside of any valid keys. This is used to let the user know
     * that if they lift their finger, nothing will be typed.
     */
    private static final long DELAY_NO_HOVER_SELECTION = 250;

    /**
     * Duration of the key click vibration in milliseconds.
     */
    private static final long VIBRATE_KEY_CLICK = 50;

    private static final float FX_VOLUME = -1.0f;

    private InputMethodService mInputMethod;
    private Vibrator mVibrator;
    private AudioManager mAudioManager;
    private AccessibilityHandler mAccessibilityHandler;

    private static class AccessibilityHandler
            extends StaticInnerHandlerWrapper<AccessibleInputMethodServiceProxy> {
        private static final int MSG_NO_HOVER_SELECTION = 0;

        public AccessibilityHandler(AccessibleInputMethodServiceProxy outerInstance,
                Looper looper) {
            super(outerInstance, looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_NO_HOVER_SELECTION:
                getOuterInstance().notifyNoHoverSelection();
                break;
            }
        }

        public void postNoHoverSelection() {
            removeMessages(MSG_NO_HOVER_SELECTION);
            sendEmptyMessageDelayed(MSG_NO_HOVER_SELECTION, DELAY_NO_HOVER_SELECTION);
        }

        public void cancelNoHoverSelection() {
            removeMessages(MSG_NO_HOVER_SELECTION);
        }
    }

    public static void init(InputMethodService inputMethod, SharedPreferences prefs) {
        sInstance.initInternal(inputMethod, prefs);
    }

    public static AccessibleInputMethodServiceProxy getInstance() {
        return sInstance;
    }

    private AccessibleInputMethodServiceProxy() {
        // Not publicly instantiable.
    }

    private void initInternal(InputMethodService inputMethod, SharedPreferences prefs) {
        mInputMethod = inputMethod;
        mVibrator = (Vibrator) inputMethod.getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) inputMethod.getSystemService(Context.AUDIO_SERVICE);
        mAccessibilityHandler = new AccessibilityHandler(this, inputMethod.getMainLooper());
    }

    /**
     * If touch exploration is enabled, cancels the event sent by
     * {@link AccessibleInputMethodServiceProxy#onHoverExit(int)} because the
     * user is currently hovering above a key.
     */
    @Override
    public void onHoverEnter(int primaryCode) {
        mAccessibilityHandler.cancelNoHoverSelection();
    }

    /**
     * If touch exploration is enabled, sends a delayed event to notify the user
     * that they are not currently hovering above a key.
     */
    @Override
    public void onHoverExit(int primaryCode) {
        mAccessibilityHandler.postNoHoverSelection();
    }

    /**
     * Handle flick gestures by mapping them to directional pad keys.
     */
    @Override
    public void onFlickGesture(int direction) {
        final int keyEventCode;

        switch (direction) {
        case FlickGestureDetector.FLICK_LEFT:
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
            break;
        case FlickGestureDetector.FLICK_RIGHT:
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
            break;
        }
    }

    /**
     * Provide haptic feedback and send the specified keyCode to the input
     * connection as a pair of down/up events.
     *
     * @param keyCode
     */
    private void sendDownUpKeyEvents(int keyCode) {
        mVibrator.vibrate(VIBRATE_KEY_CLICK);
        mAudioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, FX_VOLUME);
        mInputMethod.sendDownUpKeyEvents(keyCode);
    }

    /**
     * When Accessibility is turned on, notifies the user that they are not
     * currently hovering above a key. By default this will speak the currently
     * entered text.
     */
    private void notifyNoHoverSelection() {
        final ExtractedText extracted = mInputMethod.getCurrentInputConnection().getExtractedText(
                new ExtractedTextRequest(), 0);

        if (extracted == null)
            return;

        final CharSequence text;

        if (TextUtils.isEmpty(extracted.text)) {
            text = mInputMethod.getString(R.string.spoken_no_text_entered);
        } else {
            text = mInputMethod.getString(R.string.spoken_current_text_is, extracted.text);
        }

        AccessibilityUtils.getInstance().speak(text);
    }
}
