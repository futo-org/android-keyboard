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

    private final Map<Integer, Drawable> mIcons = new HashMap<Integer, Drawable>();

    // The key value should be aligned with the enum value of Keyboard.icon*.
    private static final Map<Integer, Integer> ICONS_TO_ATTRS_MAP = new HashMap<Integer, Integer>();
    private static final Collection<Integer> VALID_ATTRS;

    static {
        addIconIdMap(1, R.styleable.Keyboard_iconShiftKey);
        addIconIdMap(2, R.styleable.Keyboard_iconDeleteKey);
        // This is also represented as "@icon/3" in keyboard layout XML.
        addIconIdMap(3, R.styleable.Keyboard_iconSettingsKey);
        addIconIdMap(4, R.styleable.Keyboard_iconSpaceKey);
        addIconIdMap(5, R.styleable.Keyboard_iconReturnKey);
        addIconIdMap(6, R.styleable.Keyboard_iconSearchKey);
        // This is also represented as "@icon/7" in keyboard layout XML.
        addIconIdMap(7, R.styleable.Keyboard_iconTabKey);
        addIconIdMap(8, R.styleable.Keyboard_iconShortcutKey);
        addIconIdMap(9, R.styleable.Keyboard_iconShortcutForLabel);
        addIconIdMap(10, R.styleable.Keyboard_iconSpaceKeyForNumberLayout);
        addIconIdMap(11, R.styleable.Keyboard_iconShiftKeyShifted);
        addIconIdMap(12, R.styleable.Keyboard_iconDisabledShortcutKey);
        addIconIdMap(13, R.styleable.Keyboard_iconPreviewTabKey);
        VALID_ATTRS = ICONS_TO_ATTRS_MAP.values();
    }

    private static void addIconIdMap(int iconId, int attrId) {
        ICONS_TO_ATTRS_MAP.put(iconId, attrId);
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

    public Drawable getIconByIconId(final Integer iconId) {
        if (iconId == ICON_UNDEFINED) {
            return null;
        }
        final Integer attrId = ICONS_TO_ATTRS_MAP.get(iconId);
        if (attrId == null) {
            throw new IllegalArgumentException("icon id is out of range: " + iconId);
        }
        return getIconByAttrId(attrId);
    }

    public Drawable getIconByAttrId(final Integer attrId) {
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
