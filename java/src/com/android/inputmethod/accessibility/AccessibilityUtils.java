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

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.android.inputmethod.compat.AccessibilityEventCompatUtils;
import com.android.inputmethod.compat.AccessibilityManagerCompatWrapper;
import com.android.inputmethod.compat.MotionEventCompatUtils;

public class AccessibilityUtils {
    private static final String TAG = AccessibilityUtils.class.getSimpleName();
    private static final String CLASS = AccessibilityUtils.class.getClass().getName();
    private static final String PACKAGE = AccessibilityUtils.class.getClass().getPackage()
            .getName();

    private static final AccessibilityUtils sInstance = new AccessibilityUtils();

    private AccessibilityManager mAccessibilityManager;
    private AccessibilityManagerCompatWrapper mCompatManager;

    /*
     * Setting this constant to {@code false} will disable all keyboard
     * accessibility code, regardless of whether Accessibility is turned on in
     * the system settings. It should ONLY be used in the event of an emergency.
     */
    private static final boolean ENABLE_ACCESSIBILITY = true;

    public static void init(InputMethodService inputMethod, SharedPreferences prefs) {
        if (!ENABLE_ACCESSIBILITY)
            return;

        // These only need to be initialized if the kill switch is off.
        sInstance.initInternal(inputMethod, prefs);
        KeyCodeDescriptionMapper.init(inputMethod, prefs);
        AccessibleInputMethodServiceProxy.init(inputMethod, prefs);
        AccessibleKeyboardViewProxy.init(inputMethod, prefs);
    }

    public static AccessibilityUtils getInstance() {
        return sInstance;
    }

    private AccessibilityUtils() {
        // This class is not publicly instantiable.
    }

    private void initInternal(Context context, SharedPreferences prefs) {
        mAccessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        mCompatManager = new AccessibilityManagerCompatWrapper(mAccessibilityManager);
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
                && AccessibilityEventCompatUtils.supportsTouchExploration()
                && mAccessibilityManager.isEnabled()
                && !mCompatManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_SPOKEN).isEmpty();
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

        return action == MotionEventCompatUtils.ACTION_HOVER_ENTER
                || action == MotionEventCompatUtils.ACTION_HOVER_EXIT
                || action == MotionEventCompatUtils.ACTION_HOVER_MOVE;
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
                .obtain(AccessibilityEventCompatUtils.TYPE_VIEW_HOVER_ENTER);

        event.setPackageName(PACKAGE);
        event.setClassName(CLASS);
        event.setEventTime(SystemClock.uptimeMillis());
        event.setEnabled(true);
        event.getText().add(text);

        mAccessibilityManager.sendAccessibilityEvent(event);
    }
}
