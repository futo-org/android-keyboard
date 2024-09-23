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

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.util.SparseIntArray;

import org.futo.inputmethod.keyboard.Key;
import org.futo.inputmethod.keyboard.KeyDetector;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.KeyboardId;
import org.futo.inputmethod.keyboard.MainKeyboardView;
import org.futo.inputmethod.keyboard.PointerTracker;
import org.futo.inputmethod.latin.R;

/**
 * This class represents a delegate that can be registered in {@link MainKeyboardView} to enhance
 * accessibility support via composition rather via inheritance.
 */
public final class MainKeyboardAccessibilityDelegate
        extends KeyboardAccessibilityDelegate<MainKeyboardView>
        implements AccessibilityLongPressTimer.LongPressTimerCallback {
    private static final String TAG = MainKeyboardAccessibilityDelegate.class.getSimpleName();

    /** Map of keyboard modes to resource IDs. */
    private static final SparseIntArray KEYBOARD_MODE_RES_IDS = new SparseIntArray();

    static {
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATE, R.string.keyboard_mode_date);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATETIME, R.string.keyboard_mode_date_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_EMAIL, R.string.keyboard_mode_email);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_IM, R.string.keyboard_mode_im);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_NUMBER, R.string.keyboard_mode_number);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_PHONE, R.string.keyboard_mode_phone);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TEXT, R.string.keyboard_mode_text);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TIME, R.string.keyboard_mode_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_URL, R.string.keyboard_mode_url);
    }

    /** The most recently set keyboard mode. */
    private int mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    private static final int KEYBOARD_IS_HIDDEN = -1;
    // The rectangle region to ignore hover events.
    private final Rect mBoundsToIgnoreHoverEvent = new Rect();


    public MainKeyboardAccessibilityDelegate(final MainKeyboardView mainKeyboardView,
            final KeyDetector keyDetector) {
        super(mainKeyboardView, keyDetector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        if (keyboard == null) {
            return;
        }
        final Keyboard lastKeyboard = getKeyboard();
        super.setKeyboard(keyboard);
        final int lastKeyboardMode = mLastKeyboardMode;
        mLastKeyboardMode = keyboard.mId.mMode;

        // Since this method is called even when accessibility is off, make sure
        // to check the state before announcing anything.
        if (!AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            return;
        }
        // Announce the language name only when the language is changed.
        if (lastKeyboard == null || !keyboard.mId.mLocale.equals(lastKeyboard.mId.mLocale)) {
            announceKeyboardLanguage(keyboard);
            return;
        }
        // Announce the mode only when the mode is changed.
        if (keyboard.mId.mMode != lastKeyboardMode) {
            announceKeyboardMode(keyboard);
            return;
        }
        // Announce the keyboard type only when the type is changed.
        if (keyboard.mId.mElementId != lastKeyboard.mId.mElementId) {
            announceKeyboardType(keyboard, lastKeyboard);
            return;
        }
    }

    /**
     * Called when the keyboard is hidden and accessibility is enabled.
     */
    public void onHideWindow() {
        if (mLastKeyboardMode != KEYBOARD_IS_HIDDEN) {
            announceKeyboardHidden();
        }
        mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    }

    /**
     * Announces which language of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardLanguage(final Keyboard keyboard) {
        final String languageText = keyboard.mId.mLocale.getDisplayName(); // TODO: Verify this is correct
        sendWindowStateChanged(languageText);
    }

    /**
     * Announces which type of keyboard is being displayed.
     * If the keyboard type is unknown, no announcement is made.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardMode(final Keyboard keyboard) {
        final Context context = mKeyboardView.getContext();
        final int modeTextResId = KEYBOARD_MODE_RES_IDS.get(keyboard.mId.mMode);
        if (modeTextResId == 0) {
            return;
        }
        final String modeText = context.getString(modeTextResId);
        final String text = context.getString(R.string.announce_keyboard_mode, modeText);
        sendWindowStateChanged(text);
    }

    /**
     * Announces which type of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     * @param lastKeyboard The last keyboard.
     */
    private void announceKeyboardType(final Keyboard keyboard, final Keyboard lastKeyboard) {
        final int lastElementId = lastKeyboard.mId.mElementId;
        final int resId;
        switch (keyboard.mId.mElementId) {
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET:
            if (lastElementId == KeyboardId.ELEMENT_ALPHABET
                    || lastElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
                // Transition between alphabet mode and automatic shifted mode should be silently
                // ignored because it can be determined by each key's talk back announce.
                return;
            }
            resId = R.string.spoken_description_mode_alpha;
            break;
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
            if (lastElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
                // Resetting automatic shifted mode by pressing the shift key causes the transition
                // from automatic shifted to manual shifted that should be silently ignored.
                return;
            }
            resId = R.string.spoken_description_shiftmode_on;
            break;
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
            if (lastElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED) {
                // Resetting caps locked mode by pressing the shift key causes the transition
                // from shift locked to shift lock shifted that should be silently ignored.
                return;
            }
            resId = R.string.spoken_description_shiftmode_locked;
            break;
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_shiftmode_locked;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
            resId = R.string.spoken_description_mode_symbol;
            break;
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_mode_symbol_shift;
            break;
        case KeyboardId.ELEMENT_PHONE:
            resId = R.string.spoken_description_mode_phone;
            break;
        case KeyboardId.ELEMENT_PHONE_SYMBOLS:
            resId = R.string.spoken_description_mode_phone_shift;
            break;
        case KeyboardId.ELEMENT_NUMBER:
            resId = R.string.spoken_description_mode_digits;
            break;
        default:
            return;
        }
        sendWindowStateChanged(resId);
    }

    /**
     * Announces that the keyboard has been hidden.
     */
    private void announceKeyboardHidden() {
        sendWindowStateChanged(R.string.announce_keyboard_hidden);
    }

    @Override
    public void performClickOn(final Key key) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        if (DEBUG_HOVER) {
            Log.d(TAG, "performClickOn: key=" + key
                    + " inIgnoreBounds=" + mBoundsToIgnoreHoverEvent.contains(x, y));
        }
        if (mBoundsToIgnoreHoverEvent.contains(x, y)) {
            // This hover exit event points to the key that should be ignored.
            // Clear the ignoring region to handle further hover events.
            mBoundsToIgnoreHoverEvent.setEmpty();
            return;
        }
        super.performClickOn(key);
    }

    @Override
    public void performLongClickOn(final Key key) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "performLongClickOn: key=" + key);
        }
        final PointerTracker tracker = PointerTracker.getPointerTracker(HOVER_EVENT_POINTER_ID);
        mKeyboardView.showMoreKeysKeyboard(key, tracker);
    }
}
