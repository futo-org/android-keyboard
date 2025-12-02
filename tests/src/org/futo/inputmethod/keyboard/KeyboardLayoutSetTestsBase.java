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

package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.test.AndroidTestCase;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import org.futo.inputmethod.compat.InputMethodSubtypeCompatUtils;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.RichInputMethodManager;
import org.futo.inputmethod.latin.RichInputMethodSubtype;
import org.futo.inputmethod.latin.Subtypes;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.settings.LongPressKeySettings;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.uix.actions.ActionRegistry;
import org.futo.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.futo.inputmethod.latin.utils.ResourceUtils;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params;
import org.futo.inputmethod.v2keyboard.LayoutManager;
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class KeyboardLayoutSetTestsBase extends AndroidTestCase {
    // All input method subtypes of LatinIME.
    private final ArrayList<InputMethodSubtype> mAllSubtypesList = new ArrayList<>();
    private final ArrayList<InputMethodSubtype> mReducedSubtypesList = new ArrayList<>();

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
        //mRichImm.setAdditionalInputMethodSubtypes(predefinedAdditionalSubtypes);

        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(context);
        setContext(new ContextThemeWrapper(getContext(), keyboardTheme.mStyleId));
        KeyboardLayoutSetV2.onKeyboardThemeChanged(getContext());

        mScreenMetrics = Settings.readScreenMetrics(res);

        LayoutManager.INSTANCE.init(context);
        Map<Locale, List<String>> mapping = LayoutManager.INSTANCE.getLayoutMapping(context);
        for(Map.Entry<Locale, List<String>> entry : mapping.entrySet()) {
            String locale = entry.getKey().toString();
            for(String layout : entry.getValue()) {
                InputMethodSubtype subtype = Subtypes.INSTANCE.makeSubtype(locale, layout);
                mAllSubtypesList.add(subtype);
            }
        }

        // Reduced: add each unique layout only once, unless it's the top choice for language
        HashSet<String> reducedLayoutsAdded = new HashSet<>();
        for(Map.Entry<Locale, List<String>> entry : mapping.entrySet()) {
            String locale = entry.getKey().toString();
            int i = 0;
            for(String layout : entry.getValue()) {
                InputMethodSubtype subtype = Subtypes.INSTANCE.makeSubtype(locale, layout);

                if(!reducedLayoutsAdded.contains(layout)) {
                    reducedLayoutsAdded.add(layout);
                    mReducedSubtypesList.add(subtype);
                } else if(i == 0) {
                    mReducedSubtypesList.add(subtype);
                }
                i++;
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Restore additional subtypes preference.
        //mRichImm.setAdditionalInputMethodSubtypes(mSavedAdditionalSubtypes);
        super.tearDown();
    }

    protected final ArrayList<InputMethodSubtype> getAllSubtypesList() {
        return mAllSubtypesList;
    }

    protected final ArrayList<InputMethodSubtype> getReducedSubtypesList() {
        return mReducedSubtypesList;
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
        return Subtypes.INSTANCE.makeSubtype(locale.toString(), keyboardLayout);
    }

    protected KeyboardLayoutSetV2 createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo) {
        return createKeyboardLayoutSet(subtype, editorInfo, false /* voiceInputKeyEnabled */,
                false /* languageSwitchKeyEnabled */, false /* splitLayoutEnabled */);
    }

    protected KeyboardLayoutSetV2 createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final boolean voiceInputKeyEnabled,
            final boolean languageSwitchKeyEnabled, final boolean splitLayoutEnabled) {
        final Context context = getContext();
        LayoutManager.INSTANCE.init(context);
        final Resources res = context.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(null, res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);

        final RichInputMethodSubtype richInputMethodSubtype = RichInputMethodSubtype.getRichInputMethodSubtype(subtype);

        return new KeyboardLayoutSetV2(
                context,
                new KeyboardLayoutSetV2Params(
                        new RegularKeyboardSize(keyboardHeight, keyboardWidth, new Rect(), keyboardHeight / 4),
                        richInputMethodSubtype.getKeyboardLayoutSetName(),
                        richInputMethodSubtype.getLocale(), null,
                        editorInfo, false, 0, true, false, false,
                        4.0f,
                        languageSwitchKeyEnabled ? ActionRegistry.INSTANCE.actionStringIdToIdx("switch_language") : null,
                        LongPressKeySettings.forTest()
                )
        );
    }
}
