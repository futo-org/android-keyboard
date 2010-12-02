/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.inputmethod.keyboard;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;

public class LatinKey extends Key {

    // functional normal state (with properties)
    private final int[] KEY_STATE_FUNCTIONAL_NORMAL = {
            android.R.attr.state_single
    };

    // functional pressed state (with properties)
    private final int[] KEY_STATE_FUNCTIONAL_PRESSED = {
            android.R.attr.state_single,
            android.R.attr.state_pressed
    };

    private boolean mShiftLockEnabled;

    public LatinKey(Resources res, Row parent, int x, int y,
            XmlResourceParser parser, KeyStyles keyStyles) {
        super(res, parent, x, y, parser, keyStyles);
        if (popupCharacters != null && popupCharacters.length() == 0) {
            // If there is a keyboard with no keys specified in popupCharacters
            popupResId = 0;
        }
    }

    void enableShiftLock() {
        mShiftLockEnabled = true;
    }

    // sticky is used for shift key.  If a key is not sticky and is modifier,
    // the key will be treated as functional.
    private boolean isFunctionalKey() {
        return !sticky && modifier;
    }

    @Override
    public void onReleased(boolean inside) {
        if (!mShiftLockEnabled) {
            super.onReleased(inside);
        } else {
            pressed = !pressed;
        }
    }

    /**
     * Overriding this method so that we can reduce the target area for certain keys.
     */
    @Override
    public boolean isInside(int x, int y) {
        boolean result = (keyboard instanceof LatinKeyboard)
                && ((LatinKeyboard)keyboard).isInside(this, x, y);
        return result;
    }

    boolean isInsideSuper(int x, int y) {
        return super.isInside(x, y);
    }

    @Override
    public int[] getCurrentDrawableState() {
        if (isFunctionalKey()) {
            if (pressed) {
                return KEY_STATE_FUNCTIONAL_PRESSED;
            } else {
                return KEY_STATE_FUNCTIONAL_NORMAL;
            }
        }
        return super.getCurrentDrawableState();
    }
}
