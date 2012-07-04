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
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.AudioManagerCompatWrapper;
import com.android.inputmethod.compat.SettingsSecureCompatUtils;
import com.android.inputmethod.latin.InputTypeUtils;
import com.android.inputmethod.latin.R;

public class AccessibilityUtils {
    private static final String TAG = AccessibilityUtils.class.getSimpleName();
    private static final String CLASS = AccessibilityUtils.class.getClass().getName();
    private static final String PACKAGE = AccessibilityUtils.class.getClass().getPackage()
            .getName();

    private static final AccessibilityUtils sInstance = new AccessibilityUtils();

    private Context mContext;
    private AccessibilityManager mAccessibilityManager;
    private AudioManagerCompatWrapper mAudioManager;

    /*
     * Setting this constant to {@code false} will disable all keyboard
     * accessibility code, regardless of whether Accessibility is turned on in
     * the system settings. It should ONLY be used in the event of an emergency.
     */
    private static final boolean ENABLE_ACCESSIBILITY = true;

    public static void init(InputMethodService inputMethod) {
        if (!ENABLE_ACCESSIBILITY)
            return;

        // These only need to be initialized if the kill switch is off.
        sInstance.initInternal(inputMethod);
        KeyCodeDescriptionMapper.init();
        AccessibleKeyboardViewProxy.init(inputMethod);
    }

    public static AccessibilityUtils getInstance() {
        return sInstance;
    }

    private AccessibilityUtils() {
        // This class is not publicly instantiable.
    }

    private void initInternal(Context context) {
        mContext = context;
        mAccessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);

        final AudioManager audioManager = (AudioManager) context
                .getSystemService(Context.AUDIO_SERVICE);
        mAudioManager = new AudioManagerCompatWrapper(audioManager);
    }

    /**
     * Returns {@code true} if touch exploration is enabled. Currently, this
     * means that the kill switch is off, the device supports touch exploration,
     * and a spoken feedback service is turned on.
     *
     * @return {@code true} if touch exploration is enabled.
     */
    public boolean isTouchExplorationEnabled() {
        return ENABLE_ACCESSIBILITY
                && mAccessibilityManager.isEnabled()
                && mAccessibilityManager.isTouchExplorationEnabled();
    }

    /**
     * Returns {@true} if the provided event is a touch exploration (e.g. hover)
     * event. This is used to determine whether the event should be processed by
     * the touch exploration code within the keyboard.
     *
     * @param event The event to check.
     * @return {@true} is the event is a touch exploration event
     */
    public boolean isTouchExplorationEvent(MotionEvent event) {
        final int action = event.getAction();

        return action == MotionEvent.ACTION_HOVER_ENTER
                || action == MotionEvent.ACTION_HOVER_EXIT
                || action == MotionEvent.ACTION_HOVER_MOVE;
    }

    /**
     * Returns whether the device should obscure typed password characters.
     * Typically this means speaking "dot" in place of non-control characters.
     *
     * @return {@code true} if the device should obscure password characters.
     */
    public boolean shouldObscureInput(EditorInfo editorInfo) {
        if (editorInfo == null)
            return false;

        // The user can optionally force speaking passwords.
        if (SettingsSecureCompatUtils.ACCESSIBILITY_SPEAK_PASSWORD != null) {
            final boolean speakPassword = Settings.Secure.getInt(mContext.getContentResolver(),
                    SettingsSecureCompatUtils.ACCESSIBILITY_SPEAK_PASSWORD, 0) != 0;
            if (speakPassword)
                return false;
        }

        // Always speak if the user is listening through headphones.
        if (mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn())
            return false;

        // Don't speak if the IME is connected to a password field.
        return InputTypeUtils.isPasswordInputType(editorInfo.inputType);
    }

    /**
     * Sends the specified text to the {@link AccessibilityManager} to be
     * spoken.
     *
     * @param text the text to speak
     */
    public void speak(CharSequence text) {
        if (!mAccessibilityManager.isEnabled()) {
            Log.e(TAG, "Attempted to speak when accessibility was disabled!");
            return;
        }

        // The following is a hack to avoid using the heavy-weight TextToSpeech
        // class. Instead, we're just forcing a fake AccessibilityEvent into
        // the screen reader to make it speak.
        final AccessibilityEvent event = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        event.setPackageName(PACKAGE);
        event.setClassName(CLASS);
        event.setEventTime(SystemClock.uptimeMillis());
        event.setEnabled(true);
        event.getText().add(text);

        mAccessibilityManager.sendAccessibilityEvent(event);
    }

    /**
     * Handles speaking the "connect a headset to hear passwords" notification
     * when connecting to a password field.
     *
     * @param editorInfo The input connection's editor info attribute.
     * @param restarting Whether the connection is being restarted.
     */
    public void onStartInputViewInternal(EditorInfo editorInfo, boolean restarting) {
        if (shouldObscureInput(editorInfo)) {
            final CharSequence text = mContext.getText(R.string.spoken_use_headphones);
            speak(text);
        }
    }

    /**
     * Sends the specified {@link AccessibilityEvent} if accessibility is
     * enabled. No operation if accessibility is disabled.
     *
     * @param event The event to send.
     */
    public void requestSendAccessibilityEvent(AccessibilityEvent event) {
        if (mAccessibilityManager.isEnabled()) {
            mAccessibilityManager.sendAccessibilityEvent(event);
        }
    }
}
