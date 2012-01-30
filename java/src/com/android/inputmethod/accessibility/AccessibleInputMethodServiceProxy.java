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
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.KeyEvent;

public class AccessibleInputMethodServiceProxy implements AccessibleKeyboardActionListener {
    private static final AccessibleInputMethodServiceProxy sInstance =
            new AccessibleInputMethodServiceProxy();

    /**
     * Duration of the key click vibration in milliseconds.
     */
    private static final long VIBRATE_KEY_CLICK = 50;

    private static final float FX_VOLUME = -1.0f;

    private InputMethodService mInputMethod;
    private Vibrator mVibrator;
    private AudioManager mAudioManager;

    public static void init(InputMethodService inputMethod) {
        sInstance.initInternal(inputMethod);
    }

    public static AccessibleInputMethodServiceProxy getInstance() {
        return sInstance;
    }

    private AccessibleInputMethodServiceProxy() {
        // Not publicly instantiable.
    }

    private void initInternal(InputMethodService inputMethod) {
        mInputMethod = inputMethod;
        mVibrator = (Vibrator) inputMethod.getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) inputMethod.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Handle flick gestures by mapping them to directional pad keys.
     */
    @Override
    public void onFlickGesture(int direction) {
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
}
