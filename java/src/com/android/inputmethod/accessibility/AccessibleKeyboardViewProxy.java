/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.AccessibilityEventCompatUtils;
import com.android.inputmethod.compat.MotionEventCompatUtils;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.LatinKeyboardView;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.R;

public class AccessibleKeyboardViewProxy {
    private static final String TAG = AccessibleKeyboardViewProxy.class.getSimpleName();
    private static final AccessibleKeyboardViewProxy sInstance = new AccessibleKeyboardViewProxy();

    private InputMethodService mInputMethod;
    private FlickGestureDetector mGestureDetector;
    private LatinKeyboardView mView;
    private AccessibleKeyboardActionListener mListener;

    private Key mLastHoverKey = null;

    public static void init(InputMethodService inputMethod) {
        sInstance.initInternal(inputMethod);
        sInstance.mListener = AccessibleInputMethodServiceProxy.getInstance();
    }

    public static AccessibleKeyboardViewProxy getInstance() {
        return sInstance;
    }

    public static void setView(LatinKeyboardView view) {
        sInstance.mView = view;
    }

    private AccessibleKeyboardViewProxy() {
        // Not publicly instantiable.
    }

    private void initInternal(InputMethodService inputMethod) {
        final Paint paint = new Paint();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(14.0f);
        paint.setAntiAlias(true);
        paint.setColor(Color.YELLOW);

        mInputMethod = inputMethod;
        mGestureDetector = new KeyboardFlickGestureDetector(inputMethod);
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mView == null) {
            Log.e(TAG, "No keyboard view set!");
            return false;
        }

        switch (event.getEventType()) {
        case AccessibilityEventCompatUtils.TYPE_VIEW_HOVER_ENTER:
            final Key key = mLastHoverKey;

            if (key == null)
                break;

            final EditorInfo info = mInputMethod.getCurrentInputEditorInfo();
            final boolean shouldObscure = AccessibilityUtils.getInstance().shouldObscureInput(info);
            final CharSequence description = KeyCodeDescriptionMapper.getInstance()
                    .getDescriptionForKey(mView.getContext(), mView.getKeyboard(), key,
                            shouldObscure);

            if (description == null)
                return false;

            event.getText().add(description);

            break;
        }

        return true;
    }

    /**
     * Receives hover events when accessibility is turned on in SDK versions ICS
     * and higher.
     *
     * @param event The hover event.
     * @return {@code true} if the event is handled
     */
    public boolean dispatchHoverEvent(MotionEvent event, PointerTracker tracker) {
        if (mGestureDetector.onHoverEvent(event, this, tracker))
            return true;

        return onHoverEventInternal(event, tracker);
    }

    /**
     * Handles touch exploration events when Accessibility is turned on.
     *
     * @param event The touch exploration hover event.
     * @return {@code true} if the event was handled
     */
    /*package*/ boolean onHoverEventInternal(MotionEvent event, PointerTracker tracker) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();

        switch (event.getAction()) {
        case MotionEventCompatUtils.ACTION_HOVER_ENTER:
        case MotionEventCompatUtils.ACTION_HOVER_MOVE:
            final Key key = tracker.getKeyOn(x, y);

            if (key != mLastHoverKey) {
                fireKeyHoverEvent(mLastHoverKey, false);
                mLastHoverKey = key;
                fireKeyHoverEvent(mLastHoverKey, true);
            }

            return true;
        }

        return false;
    }

    private void fireKeyHoverEvent(Key key, boolean entering) {
        if (mListener == null) {
            Log.e(TAG, "No accessible keyboard action listener set!");
            return;
        }

        if (mView == null) {
            Log.e(TAG, "No keyboard view set!");
            return;
        }

        if (key == null)
            return;

        if (entering) {
            mView.sendAccessibilityEvent(AccessibilityEventCompatUtils.TYPE_VIEW_HOVER_ENTER);
        } else {
            mView.sendAccessibilityEvent(AccessibilityEventCompatUtils.TYPE_VIEW_HOVER_EXIT);
        }
    }

    private class KeyboardFlickGestureDetector extends FlickGestureDetector {
        public KeyboardFlickGestureDetector(Context context) {
            super(context);
        }

        @Override
        public boolean onFlick(MotionEvent e1, MotionEvent e2, int direction) {
            if (mListener != null) {
                mListener.onFlickGesture(direction);
            }
            return true;
        }
    }

    /**
     * Notifies the user of changes in the keyboard shift state.
     */
    public void notifyShiftState() {
        final Keyboard keyboard = mView.getKeyboard();
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final Context context = mView.getContext();
        final CharSequence text;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            text = context.getText(R.string.spoken_description_shiftmode_locked);
            break;
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            text = context.getText(R.string.spoken_description_shiftmode_on);
            break;
        default:
            text = context.getText(R.string.spoken_description_shiftmode_off);
        }

        AccessibilityUtils.getInstance().speak(text);
    }

    /**
     * Notifies the user of changes in the keyboard symbols state.
     */
    public void notifySymbolsState() {
        final Keyboard keyboard = mView.getKeyboard();
        final Context context = mView.getContext();
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final int resId;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET:
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_mode_alpha;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_mode_symbol;
            break;
        case KeyboardId.ELEMENT_PHONE:
            resId = R.string.spoken_description_mode_phone;
            break;
        case KeyboardId.ELEMENT_PHONE_SYMBOLS:
            resId = R.string.spoken_description_mode_phone_shift;
            break;
        default:
            resId = -1;
        }

        if (resId < 0) {
            return;
        }

        final String text = context.getString(resId);
        AccessibilityUtils.getInstance().speak(text);
    }
}
