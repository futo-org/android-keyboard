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

import com.android.inputmethod.latin.R;

import java.util.HashMap;
import java.util.Map;

public class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    // The value should be aligned with the enum value of Key.keyIcon.
    public static final int ICON_UNDEFINED = 0;
    private static final int NUM_ICONS = 14;

    private final Drawable[] mIcons = new Drawable[NUM_ICONS + 1];

    private static final Map<Integer, Integer> ATTR_ID_TO_ICON_ID = new HashMap<Integer, Integer>();
    private static final Map<String, Integer> NAME_TO_ICON_ID = new HashMap<String, Integer>();
    private static final String[] ICON_NAMES = new String[NUM_ICONS + 1];

    private static final int ATTR_UNDEFINED = 0;
    static {
        // The key value should be aligned with the enum value of Key.keyIcon.
        addIconIdMap(0, "undefined", ATTR_UNDEFINED);
        addIconIdMap(1, "shiftKey", R.styleable.Keyboard_iconShiftKey);
        addIconIdMap(2, "deleteKey", R.styleable.Keyboard_iconDeleteKey);
        addIconIdMap(3, "settingsKey", R.styleable.Keyboard_iconSettingsKey);
        addIconIdMap(4, "spaceKey", R.styleable.Keyboard_iconSpaceKey);
        addIconIdMap(5, "returnKey", R.styleable.Keyboard_iconReturnKey);
        addIconIdMap(6, "searchKey", R.styleable.Keyboard_iconSearchKey);
        addIconIdMap(7, "tabKey", R.styleable.Keyboard_iconTabKey);
        addIconIdMap(8, "shortcutKey", R.styleable.Keyboard_iconShortcutKey);
        addIconIdMap(9, "shortcutForLabel", R.styleable.Keyboard_iconShortcutForLabel);
        addIconIdMap(10, "spaceKeyForNumberLayout",
                R.styleable.Keyboard_iconSpaceKeyForNumberLayout);
        addIconIdMap(11, "shiftKeyShifted", R.styleable.Keyboard_iconShiftKeyShifted);
        addIconIdMap(12, "disabledShortcurKey", R.styleable.Keyboard_iconDisabledShortcutKey);
        addIconIdMap(13, "previewTabKey", R.styleable.Keyboard_iconPreviewTabKey);
        addIconIdMap(14, "languageSwitchKey", R.styleable.Keyboard_iconLanguageSwitchKey);
    }

    private static void addIconIdMap(int iconId, String name, int attrId) {
        if (attrId != ATTR_UNDEFINED) {
            ATTR_ID_TO_ICON_ID.put(attrId,  iconId);
        }
        NAME_TO_ICON_ID.put(name, iconId);
        ICON_NAMES[iconId] = name;
    }

    public void loadIcons(final TypedArray keyboardAttrs) {
        for (final Integer attrId : ATTR_ID_TO_ICON_ID.keySet()) {
            try {
                final Drawable icon = keyboardAttrs.getDrawable(attrId);
                setDefaultBounds(icon);
                final Integer iconId = ATTR_ID_TO_ICON_ID.get(attrId);
                mIcons[iconId] = icon;
            } catch (Resources.NotFoundException e) {
                Log.w(TAG, "Drawable resource for icon #"
                        + keyboardAttrs.getResources().getResourceEntryName(attrId)
                        + " not found");
            }
        }
    }

    private static boolean isValidIconId(final int iconId) {
        return iconId >= 0 && iconId < ICON_NAMES.length;
    }

    public static String getIconName(final int iconId) {
        return isValidIconId(iconId) ? ICON_NAMES[iconId] : "unknown<" + iconId + ">";
    }

    public static int getIconId(final String name) {
        final Integer iconId = NAME_TO_ICON_ID.get(name);
        if (iconId != null) {
            return iconId;
        }
        throw new RuntimeException("unknown icon name: " + name);
    }

    public Drawable getIconDrawable(final int iconId) {
        if (isValidIconId(iconId)) {
            return mIcons[iconId];
        }
        throw new RuntimeException("unknown icon id: " + getIconName(iconId));
    }

    private static void setDefaultBounds(final Drawable icon)  {
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        }
    }
}
