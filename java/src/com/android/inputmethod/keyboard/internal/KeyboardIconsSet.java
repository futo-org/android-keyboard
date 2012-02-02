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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    public static final int ICON_UNDEFINED = 0;
    public static final int ATTR_UNDEFINED = 0;

    private final Map<Integer, Drawable> mIcons = new HashMap<Integer, Drawable>();

    // The key value should be aligned with the enum value of Keyboard.icon*.
    private static final Map<Integer, Integer> ID_TO_ATTR_MAP = new HashMap<Integer, Integer>();
    private static final Map<String, Integer> NAME_TO_ATTR_MAP = new HashMap<String, Integer>();
    private static final Map<Integer, String> ATTR_TO_NAME_MAP = new HashMap<Integer, String>();
    private static final Collection<Integer> VALID_ATTRS;

    static {
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
        VALID_ATTRS = ID_TO_ATTR_MAP.values();
    }

    private static void addIconIdMap(int iconId, String name, Integer attrId) {
        ID_TO_ATTR_MAP.put(iconId, attrId);
        NAME_TO_ATTR_MAP.put(name, attrId);
        ATTR_TO_NAME_MAP.put(attrId, name);
    }

    public void loadIcons(final TypedArray keyboardAttrs) {
        for (final Integer attrId : VALID_ATTRS) {
            try {
                final Drawable icon = keyboardAttrs.getDrawable(attrId);
                if (icon == null) continue;
                setDefaultBounds(icon);
                mIcons.put(attrId, icon);
            } catch (Resources.NotFoundException e) {
                Log.w(TAG, "Drawable resource for icon #"
                        + keyboardAttrs.getResources().getResourceEntryName(attrId)
                        + " not found");
            }
        }
    }

    public static int getIconAttrId(final Integer iconId) {
        if (iconId == ICON_UNDEFINED) {
            return ATTR_UNDEFINED;
        }
        final Integer attrId = ID_TO_ATTR_MAP.get(iconId);
        if (attrId == null) {
            throw new IllegalArgumentException("icon id is out of range: " + iconId);
        }
        return attrId;
    }

    public static int getIconAttrId(final String iconName) {
        final Integer attrId = NAME_TO_ATTR_MAP.get(iconName);
        if (attrId == null) {
            throw new IllegalArgumentException("unknown icon name: " + iconName);
        }
        return attrId;
    }

    public static String getIconName(final int attrId) {
        if (attrId == ATTR_UNDEFINED) {
            return "null";
        }
        if (ATTR_TO_NAME_MAP.containsKey(attrId)) {
            return ATTR_TO_NAME_MAP.get(attrId);
        }
        return String.format("unknown<0x%08x>", attrId);
    }

    public Drawable getIconByAttrId(final Integer attrId) {
        if (attrId == ATTR_UNDEFINED) {
            return null;
        }
        if (!VALID_ATTRS.contains(attrId)) {
            throw new IllegalArgumentException("unknown icon attribute id: " + attrId);
        }
        return mIcons.get(attrId);
    }

    private static Drawable setDefaultBounds(final Drawable icon)  {
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        }
        return icon;
    }
}
