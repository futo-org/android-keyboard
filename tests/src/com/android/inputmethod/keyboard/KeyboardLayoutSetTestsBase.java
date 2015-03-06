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
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils;
import com.android.inputmethod.keyboard.KeyboardLayoutSet.Builder;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.Locale;

public abstract class KeyboardLayoutSetTestsBase extends AndroidTestCase {
    // All input method subtypes of LatinIME.
    private final ArrayList<InputMethodSubtype> mAllSubtypesList = new ArrayList<>();

    public interface SubtypeFilter {
        public boolean accept(final InputMethodSubtype subtype);
    }

    public static final SubtypeFilter FILTER_IS_ASCII_CAPABLE = new SubtypeFilter() {
        @Override
        public boolean accept(InputMethodSubtype subtype) {
            return InputMethodSubtypeCompatUtils.isAsciiCapable(subtype);
        }
    };

    public static final SubtypeFilter FILTER_IS_ADDITIONAL_SUBTYPE = new SubtypeFilter() {
        @Override
        public boolean accept(InputMethodSubtype subtype) {
            return AdditionalSubtypeUtils.isAdditionalSubtype(subtype);
        }
    };

    private RichInputMethodManager mRichImm;
    private InputMethodSubtype[] mSavedAdditionalSubtypes;
    private int mScreenMetrics;

    protected abstract int getKeyboardThemeForTests();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        final Resources res = context.getResources();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();

        // Save and reset additional subtypes preference.
        mSavedAdditionalSubtypes = mRichImm.getAdditionalSubtypes();
        final InputMethodSubtype[] predefinedAdditionalSubtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(
                        AdditionalSubtypeUtils.createPrefSubtypes(
                                res.getStringArray(R.array.predefined_subtypes)));
        mRichImm.setAdditionalInputMethodSubtypes(predefinedAdditionalSubtypes);

        final KeyboardTheme keyboardTheme = KeyboardTheme.searchKeyboardThemeById(
                getKeyboardThemeForTests(), KeyboardTheme.KEYBOARD_THEMES);
        setContext(new ContextThemeWrapper(getContext(), keyboardTheme.mStyleId));
        KeyboardLayoutSet.onKeyboardThemeChanged();

        mScreenMetrics = Settings.readScreenMetrics(res);

        final InputMethodInfo imi = mRichImm.getInputMethodInfoOfThisIme();
        final int subtypeCount = imi.getSubtypeCount();
        for (int index = 0; index < subtypeCount; index++) {
            mAllSubtypesList.add(imi.getSubtypeAt(index));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Restore additional subtypes preference.
        mRichImm.setAdditionalInputMethodSubtypes(mSavedAdditionalSubtypes);
        super.tearDown();
    }

    protected final ArrayList<InputMethodSubtype> getAllSubtypesList() {
        return mAllSubtypesList;
    }

    protected final ArrayList<InputMethodSubtype> getSubtypesFilteredBy(
            final SubtypeFilter filter) {
        final ArrayList<InputMethodSubtype> list = new ArrayList<>();
        for (final InputMethodSubtype subtype : mAllSubtypesList) {
            if (filter.accept(subtype)) {
                list.add(subtype);
            }
        }
        return list;
    }

    protected final boolean isPhone() {
        return Constants.isPhone(mScreenMetrics);
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
        for (final InputMethodSubtype subtype : getSubtypesFilteredBy(FILTER_IS_ASCII_CAPABLE)) {
            final Locale subtypeLocale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            if (locale.equals(subtypeLocale)) {
                // Create additional subtype.
                return AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                        locale.toString(), keyboardLayout);
            }
        }
        throw new RuntimeException(
                "Unknown subtype: locale=" + locale + " keyboardLayout=" + keyboardLayout);
    }

    protected KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo) {
        return createKeyboardLayoutSet(subtype, editorInfo, false /* voiceInputKeyEnabled */,
                false /* languageSwitchKeyEnabled */, false /* splitLayoutEnabled */);
    }

    protected KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final boolean voiceInputKeyEnabled,
            final boolean languageSwitchKeyEnabled, final boolean splitLayoutEnabled) {
        final Context context = getContext();
        final Resources res = context.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        final Builder builder = new Builder(context, editorInfo);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight)
                .setSubtype(RichInputMethodSubtype.getRichInputMethodSubtype(subtype))
                .setVoiceInputKeyEnabled(voiceInputKeyEnabled)
                .setLanguageSwitchKeyEnabled(languageSwitchKeyEnabled)
                .setSplitLayoutEnabledByUser(splitLayoutEnabled);
        return builder.build();
    }
}
