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

package org.futo.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseIntArray;

import org.futo.inputmethod.latin.uix.DynamicThemeProvider;
import org.futo.inputmethod.latin.R;

import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    public static final String PREFIX_ICON = "!icon/";
    public static final String ICON_UNDEFINED = "";

    private static final String NAME_UNDEFINED = "undefined";
    public static final String NAME_SHIFT_KEY = "shift_key";
    public static final String NAME_SHIFT_KEY_SHIFTED = "shift_key_shifted";
    public static final String NAME_DELETE_KEY = "delete_key";
    public static final String NAME_SETTINGS_KEY = "settings_key";
    public static final String NAME_SPACE_KEY = "space_key";
    public static final String NAME_SPACE_KEY_FOR_NUMBER_LAYOUT = "space_key_for_number_layout";
    public static final String NAME_ENTER_KEY = "enter_key";
    public static final String NAME_GO_KEY = "go_key";
    public static final String NAME_SEARCH_KEY = "search_key";
    public static final String NAME_SEND_KEY = "send_key";
    public static final String NAME_NEXT_KEY = "next_key";
    public static final String NAME_DONE_KEY = "done_key";
    public static final String NAME_PREVIOUS_KEY = "previous_key";
    public static final String NAME_TAB_KEY = "tab_key";
    public static final String NAME_SHORTCUT_KEY = "shortcut_key";
    public static final String NAME_SHORTCUT_KEY_DISABLED = "shortcut_key_disabled";
    public static final String NAME_LANGUAGE_SWITCH_KEY = "language_switch_key";
    public static final String NAME_ZWNJ_KEY = "zwnj_key";
    public static final String NAME_ZWJ_KEY = "zwj_key";
    public static final String NAME_EMOJI_ACTION_KEY = "emoji_action_key";
    public static final String NAME_EMOJI_NORMAL_KEY = "emoji_normal_key";

    private DynamicThemeProvider provider;
    public void loadIcons(final TypedArray keyboardAttrs, @Nullable DynamicThemeProvider provider) {
        this.provider = provider;
        /*
        final int size = ATTR_ID_TO_ICON_ID.size();
        for (int index = 0; index < size; index++) {
            final int attrId = ATTR_ID_TO_ICON_ID.keyAt(index);
            try {
                final Drawable icon = DynamicThemeProvider.Companion.getDrawableOrDefault(attrId, keyboardAttrs, provider);
                setDefaultBounds(icon);
                final Integer iconId = ATTR_ID_TO_ICON_ID.get(attrId);
                mIcons[iconId] = icon;
            } catch (Resources.NotFoundException e) {
                Log.w(TAG, "Drawable resource for icon #"
                        + keyboardAttrs.getResources().getResourceEntryName(attrId)
                        + " not found");
            }
        }
        */
    }

    @Nullable
    public Drawable getIconDrawable(final String iconId) {
        return provider.getIcon(iconId);
    }

    private static void setDefaultBounds(final Drawable icon)  {
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        }
    }
}
