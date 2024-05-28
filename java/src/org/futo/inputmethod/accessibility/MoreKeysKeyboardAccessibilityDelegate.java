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

package org.futo.inputmethod.accessibility;

import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;

import org.futo.inputmethod.keyboard.Key;
import org.futo.inputmethod.keyboard.KeyDetector;
import org.futo.inputmethod.keyboard.MoreKeysKeyboardView;
import org.futo.inputmethod.keyboard.PointerTracker;

/**
 * This class represents a delegate that can be registered in {@link MoreKeysKeyboardView} to
 * enhance accessibility support via composition rather via inheritance.
 */
public class MoreKeysKeyboardAccessibilityDelegate
        extends KeyboardAccessibilityDelegate<MoreKeysKeyboardView> {
    private static final String TAG = MoreKeysKeyboardAccessibilityDelegate.class.getSimpleName();

    private final Rect mMoreKeysKeyboardValidBounds = new Rect();
    private static final int CLOSING_INSET_IN_PIXEL = 1;
    private int mOpenAnnounceResId;
    private int mCloseAnnounceResId;

    public MoreKeysKeyboardAccessibilityDelegate(final MoreKeysKeyboardView moreKeysKeyboardView,
            final KeyDetector keyDetector) {
        super(moreKeysKeyboardView, keyDetector);
    }

    public void setOpenAnnounce(final int resId) {
        mOpenAnnounceResId = resId;
    }

    public void setCloseAnnounce(final int resId) {
        mCloseAnnounceResId = resId;
    }

    public void onShowMoreKeysKeyboard() {
        sendWindowStateChanged(mOpenAnnounceResId);
    }

    public void onDismissMoreKeysKeyboard() {
        sendWindowStateChanged(mCloseAnnounceResId);
    }

    public AccessibilityEvent createAccessibilityEvent(final Key key, final int eventType) {
        final int virtualViewId = getVirtualViewIdOf(key);
        final String keyDescription = getKeyDescription(key);
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mKeyboardView.getContext().getPackageName());
        event.setClassName(key.getClass().getName());
        event.setContentDescription(keyDescription);
        event.setEnabled(true);
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        record.setSource(mKeyboardView, virtualViewId);
        return event;
    }

    public void onKeyHovered(Key k) {
        AccessibilityEvent event = createAccessibilityEvent(k, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        AccessibilityUtils.getInstance().requestSendAccessibilityEvent(event);
    }
}
