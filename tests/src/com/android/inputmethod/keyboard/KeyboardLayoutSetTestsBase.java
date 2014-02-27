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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils;
import com.android.inputmethod.keyboard.KeyboardLayoutSet.Builder;
import com.android.inputmethod.keyboard.KeyboardSwitcher.KeyboardTheme;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@SmallTest
public class KeyboardLayoutSetTestsBase extends AndroidTestCase {
    private static final KeyboardTheme DEFAULT_KEYBOARD_THEME =
            KeyboardSwitcher.KEYBOARD_THEMES[KeyboardSwitcher.THEME_INDEX_DEFAULT];

    // All input method subtypes of LatinIME.
    private List<InputMethodSubtype> mAllSubtypesList;
    private List<InputMethodSubtype> mAsciiCabapleSubtypesList;

    private Context mThemeContext;
    private int mScreenMetrics;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mScreenMetrics = mContext.getResources().getInteger(R.integer.config_screen_metrics);

        mThemeContext = new ContextThemeWrapper(mContext, DEFAULT_KEYBOARD_THEME.mStyleId);
        RichInputMethodManager.init(mThemeContext);
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();

        final ArrayList<InputMethodSubtype> allSubtypesList = CollectionUtils.newArrayList();
        final ArrayList<InputMethodSubtype> asciiCapableSubtypesList =
                CollectionUtils.newArrayList();
        final InputMethodInfo imi = richImm.getInputMethodInfoOfThisIme();
        final int subtypeCount = imi.getSubtypeCount();
        for (int index = 0; index < subtypeCount; index++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(index);
            allSubtypesList.add(subtype);
            if (InputMethodSubtypeCompatUtils.isAsciiCapable(subtype)) {
                asciiCapableSubtypesList.add(subtype);
            }
        }
        mAllSubtypesList = Collections.unmodifiableList(allSubtypesList);
        mAsciiCabapleSubtypesList = Collections.unmodifiableList(asciiCapableSubtypesList);
    }

    protected final boolean isPhone() {
        return mScreenMetrics == Constants.SCREEN_METRICS_SMALL_PHONE
                || mScreenMetrics == Constants.SCREEN_METRICS_LARGE_PHONE;
    }

    public final void testAllSubtypesCount() {
        assertEquals(63, mAllSubtypesList.size());
    }

    public final void testAsciiCapableSubtypesCount() {
        assertEquals(40, mAsciiCabapleSubtypesList.size());
    }

    protected final InputMethodSubtype getSubtype(final Locale locale,
            final String keyboardLayout) {
        for (final InputMethodSubtype subtype : mAllSubtypesList) {
            final Locale subtypeLocale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            final String subtypeLayout = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
            if (locale.equals(subtypeLocale) && keyboardLayout.equals(subtypeLayout)) {
                // Found subtype that matches locale and keyboard layout.
                return subtype;
            }
        }
        for (final InputMethodSubtype subtype : mAsciiCabapleSubtypesList) {
            final Locale subtypeLocale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            if (locale.equals(subtypeLocale)) {
                // Create additional subtype.
                return AdditionalSubtypeUtils.createAdditionalSubtype(
                        locale.toString(), keyboardLayout, null /* extraValue */);
            }
        }
        throw new RuntimeException(
                "Unknown subtype: locale=" + locale + " keyboardLayout=" + keyboardLayout);
    }

    protected final KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo) {
        return createKeyboardLayoutSet(subtype, editorInfo, false /* isShortcutImeEnabled */,
                false /* showsVoiceInputKey */, false /* isLanguageSwitchKeyEnabled */);
    }

    protected final KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final boolean isShortcutImeEnabled,
            final boolean showsVoiceInputKey, final boolean isLanguageSwitchKeyEnabled) {
        final Context context = mThemeContext;
        final Resources res = context.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        final Builder builder = new Builder(context, editorInfo);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight)
                .setSubtype(subtype)
                .setOptions(isShortcutImeEnabled, showsVoiceInputKey, isLanguageSwitchKeyEnabled);
        return builder.build();
    }
}
