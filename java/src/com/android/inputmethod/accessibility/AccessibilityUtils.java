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
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.SettingsSecureCompatUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.utils.InputTypeUtils;

public final class AccessibilityUtils {
    private static final String TAG = AccessibilityUtils.class.getSimpleName();
    private static final String CLASS = AccessibilityUtils.class.getName();
    private static final String PACKAGE =
            AccessibilityUtils.class.getPackage().getName();

    private static final AccessibilityUtils sInstance = new AccessibilityUtils();

    private Context mContext;
    private AccessibilityManager mAccessibilityManager;
    private AudioManager mAudioManager;

    /** The most recent auto-correction. */
    private String mAutoCorrectionWord;

    /** The most recent typed word for auto-correction. */
    private String mTypedWord;

    /*
     * Setting this constant to {@code false} will disable all keyboard
     * accessibility code, regardless of whether Accessibility is turned on in
     * the system settings. It should ONLY be used in the event of an emergency.
     */
    private static final boolean ENABLE_ACCESSIBILITY = true;

    public static void init(final Context context) {
        if (!ENABLE_ACCESSIBILITY) return;

        // These only need to be initialized if the kill switch is off.
        sInstance.initInternal(context);
    }

    public static AccessibilityUtils getInstance() {
        return sInstance;
    }

    private AccessibilityUtils() {
        // This class is not publicly instantiable.
    }

    private void initInternal(final Context context) {
        mContext = context;
        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Returns {@code true} if accessibility is enabled. Currently, this means
     * that the kill switch is off and system accessibility is turned on.
     *
     * @return {@code true} if accessibility is enabled.
     */
    public boolean isAccessibilityEnabled() {
        return ENABLE_ACCESSIBILITY && mAccessibilityManager.isEnabled();
    }

    /**
     * Returns {@code true} if touch exploration is enabled. Currently, this
     * means that the kill switch is off, the device supports touch exploration,
     * and system accessibility is turned on.
     *
     * @return {@code true} if touch exploration is enabled.
     */
    public boolean isTouchExplorationEnabled() {
        return isAccessibilityEnabled() && mAccessibilityManager.isTouchExplorationEnabled();
    }

    /**
     * Returns {@true} if the provided event is a touch exploration (e.g. hover)
     * event. This is used to determine whether the event should be processed by
     * the touch exploration code within the keyboard.
     *
     * @param event The event to check.
     * @return {@true} is the event is a touch exploration event
     */
    public static boolean isTouchExplorationEvent(final MotionEvent event) {
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
    @SuppressWarnings("deprecation")
    public boolean shouldObscureInput(final EditorInfo editorInfo) {
        if (editorInfo == null) return false;

        // The user can optionally force speaking passwords.
        if (SettingsSecureCompatUtils.ACCESSIBILITY_SPEAK_PASSWORD != null) {
            final boolean speakPassword = Settings.Secure.getInt(mContext.getContentResolver(),
                    SettingsSecureCompatUtils.ACCESSIBILITY_SPEAK_PASSWORD, 0) != 0;
            if (speakPassword) return false;
        }

        // Always speak if the user is listening through headphones.
        if (mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn()) {
            return false;
        }

        // Don't speak if the IME is connected to a password field.
        return InputTypeUtils.isPasswordInputType(editorInfo.inputType);
    }

    /**
     * Sets the current auto-correction word and typed word. These may be used
     * to provide the user with a spoken description of what auto-correction
     * will occur when a key is typed.
     *
     * @param suggestedWords the list of suggested auto-correction words
     */
    public void setAutoCorrection(final SuggestedWords suggestedWords) {
        if (suggestedWords.mWillAutoCorrect) {
            mAutoCorrectionWord = suggestedWords.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION);
            final SuggestedWords.SuggestedWordInfo typedWordInfo = suggestedWords.mTypedWordInfo;
            if (null == typedWordInfo) {
                mTypedWord = null;
            } else {
                mTypedWord = typedWordInfo.mWord;
            }
        } else {
            mAutoCorrectionWord = null;
            mTypedWord = null;
        }
    }

    /**
     * Obtains a description for an auto-correction key, taking into account the
     * currently typed word and auto-correction.
     *
     * @param keyCodeDescription spoken description of the key that will insert
     *            an auto-correction
     * @param shouldObscure whether the key should be obscured
     * @return a description including a description of the auto-correction, if
     *         needed
     */
    public String getAutoCorrectionDescription(
            final String keyCodeDescription, final boolean shouldObscure) {
        if (!TextUtils.isEmpty(mAutoCorrectionWord)) {
            if (!TextUtils.equals(mAutoCorrectionWord, mTypedWord)) {
                if (shouldObscure) {
                    // This should never happen, but just in case...
                    return mContext.getString(R.string.spoken_auto_correct_obscured,
                            keyCodeDescription);
                }
                return mContext.getString(R.string.spoken_auto_correct, keyCodeDescription,
                        mTypedWord, mAutoCorrectionWord);
            }
        }

        return keyCodeDescription;
    }

    /**
     * Sends the specified text to the {@link AccessibilityManager} to be
     * spoken.
     *
     * @param view The source view.
     * @param text The text to speak.
     */
    public void announceForAccessibility(final View view, final CharSequence text) {
        if (!mAccessibilityManager.isEnabled()) {
            Log.e(TAG, "Attempted to speak when accessibility was disabled!");
            return;
        }

        // The following is a hack to avoid using the heavy-weight TextToSpeech
        // class. Instead, we're just forcing a fake AccessibilityEvent into
        // the screen reader to make it speak.
        final AccessibilityEvent event = AccessibilityEvent.obtain();

        event.setPackageName(PACKAGE);
        event.setClassName(CLASS);
        event.setEventTime(SystemClock.uptimeMillis());
        event.setEnabled(true);
        event.getText().add(text);

        // Platforms starting at SDK version 16 (Build.VERSION_CODES.JELLY_BEAN) should use
        // announce events.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            event.setEventType(AccessibilityEventCompat.TYPE_ANNOUNCEMENT);
        } else {
            event.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }

        final ViewParent viewParent = view.getParent();
        if ((viewParent == null) || !(viewParent instanceof ViewGroup)) {
            Log.e(TAG, "Failed to obtain ViewParent in announceForAccessibility");
            return;
        }

        viewParent.requestSendAccessibilityEvent(view, event);
    }

    /**
     * Handles speaking the "connect a headset to hear passwords" notification
     * when connecting to a password field.
     *
     * @param view The source view.
     * @param editorInfo The input connection's editor info attribute.
     * @param restarting Whether the connection is being restarted.
     */
    public void onStartInputViewInternal(final View view, final EditorInfo editorInfo,
            final boolean restarting) {
        if (shouldObscureInput(editorInfo)) {
            final CharSequence text = mContext.getText(R.string.spoken_use_headphones);
            announceForAccessibility(view, text);
        }
    }

    /**
     * Sends the specified {@link AccessibilityEvent} if accessibility is
     * enabled. No operation if accessibility is disabled.
     *
     * @param event The event to send.
     */
    public void requestSendAccessibilityEvent(final AccessibilityEvent event) {
        if (mAccessibilityManager.isEnabled()) {
            mAccessibilityManager.sendAccessibilityEvent(event);
        }
    }
}
