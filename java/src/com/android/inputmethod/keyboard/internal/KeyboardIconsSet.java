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

package com.android.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.HashMap;

public final class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    public static final int ICON_UNDEFINED = 0;
    private static final int ATTR_UNDEFINED = 0;

    private static final SparseIntArray ATTR_ID_TO_ICON_ID = new SparseIntArray();

    // Icon name to icon id map.
    private static final HashMap<String, Integer> sNameToIdsMap = CollectionUtils.newHashMap();

    private static final Object[] NAMES_AND_ATTR_IDS = {
        "undefined",                    ATTR_UNDEFINED,
        "shift_key",                    R.styleable.Keyboard_iconShiftKey,
        "delete_key",                   R.styleable.Keyboard_iconDeleteKey,
        "settings_key",                 R.styleable.Keyboard_iconSettingsKey,
        "space_key",                    R.styleable.Keyboard_iconSpaceKey,
        "enter_key",                    R.styleable.Keyboard_iconEnterKey,
        "search_key",                   R.styleable.Keyboard_iconSearchKey,
        "tab_key",                      R.styleable.Keyboard_iconTabKey,
        "shortcut_key",                 R.styleable.Keyboard_iconShortcutKey,
        "shortcut_for_label",           R.styleable.Keyboard_iconShortcutForLabel,
        "space_key_for_number_layout",  R.styleable.Keyboard_iconSpaceKeyForNumberLayout,
        "shift_key_shifted",            R.styleable.Keyboard_iconShiftKeyShifted,
        "shortcut_key_disabled",        R.styleable.Keyboard_iconShortcutKeyDisabled,
        "tab_key_preview",              R.styleable.Keyboard_iconTabKeyPreview,
        "language_switch_key",          R.styleable.Keyboard_iconLanguageSwitchKey,
        "zwnj_key",                     R.styleable.Keyboard_iconZwnjKey,
        "zwj_key",                      R.styleable.Keyboard_iconZwjKey,
        "emoji_key",                    R.styleable.Keyboard_iconEmojiKey,
    };

    private static int NUM_ICONS = NAMES_AND_ATTR_IDS.length / 2;
    private static final String[] ICON_NAMES = new String[NUM_ICONS];
    private final Drawable[] mIcons = new Drawable[NUM_ICONS];

    static {
        int iconId = ICON_UNDEFINED;
        for (int i = 0; i < NAMES_AND_ATTR_IDS.length; i += 2) {
            final String name = (String)NAMES_AND_ATTR_IDS[i];
            final Integer attrId = (Integer)NAMES_AND_ATTR_IDS[i + 1];
            if (attrId != ATTR_UNDEFINED) {
                ATTR_ID_TO_ICON_ID.put(attrId,  iconId);
            }
            sNameToIdsMap.put(name, iconId);
            ICON_NAMES[iconId] = name;
            iconId++;
        }
    }

    public void loadIcons(final TypedArray keyboardAttrs) {
        final int size = ATTR_ID_TO_ICON_ID.size();
        for (int index = 0; index < size; index++) {
            final int attrId = ATTR_ID_TO_ICON_ID.keyAt(index);
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

    static int getIconId(final String name) {
        Integer iconId = sNameToIdsMap.get(name);
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
