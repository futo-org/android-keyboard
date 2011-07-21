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

package com.android.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;

public class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    public static final int ICON_UNDEFINED = 0;

    // This should be aligned with Keyboard.keyIcon enum.
    private static final int ICON_SHIFT_KEY = 1;
    private static final int ICON_TO_SYMBOL_KEY = 2;
    private static final int ICON_TO_SYMBOL_KEY_WITH_SHORTCUT = 3;
    private static final int ICON_DELETE_KEY = 4;
    private static final int ICON_SETTINGS_KEY = 5; // This is also represented as "@icon/5" in xml.
    private static final int ICON_SHORTCUT_KEY = 6;
    private static final int ICON_SPACE_KEY = 7;
    private static final int ICON_RETURN_KEY = 8;
    private static final int ICON_SEARCH_KEY = 9;
    private static final int ICON_TAB_KEY = 10;
    // This should be aligned with Keyboard.keyIconShifted enum.
    private static final int ICON_SHIFTED_SHIFT_KEY = 11;
    // This should be aligned with Keyboard.keyIconPreview enum.
    private static final int ICON_PREVIEW_TAB_KEY = 12;
    private static final int ICON_PREVIEW_SETTINGS_KEY = 13;
    private static final int ICON_PREVIEW_SHORTCUT_KEY = 14;

    private static final int ICON_LAST = 14;

    private final Drawable mIcons[] = new Drawable[ICON_LAST + 1];

    private static final int getIconId(int attrIndex) {
        switch (attrIndex) {
        case R.styleable.Keyboard_iconShiftKey:
            return ICON_SHIFT_KEY;
        case R.styleable.Keyboard_iconToSymbolKey:
            return ICON_TO_SYMBOL_KEY;
        case R.styleable.Keyboard_iconToSymbolKeyWithShortcut:
            return ICON_TO_SYMBOL_KEY_WITH_SHORTCUT;
        case R.styleable.Keyboard_iconDeleteKey:
            return ICON_DELETE_KEY;
        case R.styleable.Keyboard_iconSettingsKey:
            return ICON_SETTINGS_KEY;
        case R.styleable.Keyboard_iconShortcutKey:
            return ICON_SHORTCUT_KEY;
        case R.styleable.Keyboard_iconSpaceKey:
            return ICON_SPACE_KEY;
        case R.styleable.Keyboard_iconReturnKey:
            return ICON_RETURN_KEY;
        case R.styleable.Keyboard_iconSearchKey:
            return ICON_SEARCH_KEY;
        case R.styleable.Keyboard_iconTabKey:
            return ICON_TAB_KEY;
        case R.styleable.Keyboard_iconShiftedShiftKey:
            return ICON_SHIFTED_SHIFT_KEY;
        case R.styleable.Keyboard_iconPreviewTabKey:
            return ICON_PREVIEW_TAB_KEY;
        case R.styleable.Keyboard_iconPreviewSettingsKey:
            return ICON_PREVIEW_SETTINGS_KEY;
        case R.styleable.Keyboard_iconPreviewShortcutKey:
            return ICON_PREVIEW_SHORTCUT_KEY;
        default:
            return ICON_UNDEFINED;
        }
    }

    public void loadIcons(TypedArray keyboardAttrs) {
        final int count = keyboardAttrs.getIndexCount();
        for (int i = 0; i < count; i++) {
            final int attrIndex = keyboardAttrs.getIndex(i);
            final int iconId = getIconId(attrIndex);
            if (iconId != ICON_UNDEFINED) {
                try {
                    final Drawable icon = keyboardAttrs.getDrawable(attrIndex);
                    Keyboard.setDefaultBounds(icon);
                    mIcons[iconId] = icon;
                } catch (Resources.NotFoundException e) {
                    Log.w(TAG, "Drawable resource for icon #" + iconId + " not found");
                }
            }
        }
    }

    public Drawable getIcon(int iconId) {
        if (iconId == ICON_UNDEFINED)
            return null;
        if (iconId < 0 || iconId >= mIcons.length)
            throw new IllegalArgumentException("icon id is out of range: " + iconId);
        return mIcons[iconId];
    }
}
