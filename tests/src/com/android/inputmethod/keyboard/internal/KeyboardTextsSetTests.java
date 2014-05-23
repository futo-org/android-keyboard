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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@SmallTest
public final class KeyboardTextsSetTests extends AndroidTestCase {
    // All input method subtypes of LatinIME.
    private List<InputMethodSubtype> mAllSubtypesList;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RichInputMethodManager.init(getContext());
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();

        final ArrayList<InputMethodSubtype> allSubtypesList = new ArrayList<>();
        final InputMethodInfo imi = richImm.getInputMethodInfoOfThisIme();
        final int subtypeCount = imi.getSubtypeCount();
        for (int index = 0; index < subtypeCount; index++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(index);
            allSubtypesList.add(subtype);
        }
        mAllSubtypesList = Collections.unmodifiableList(allSubtypesList);
    }

    // Test that the text {@link KeyboardTextsSet#SWITCH_TO_ALPHA_KEY_LABEL} exists for all
    // subtypes. The text is needed to implement Emoji Keyboard, see
    // {@link KeyboardSwitcher#setEmojiKeyboard()}.
    public void testSwitchToAlphaKeyLabel() {
        final Context context = getContext();
        final KeyboardTextsSet textsSet = new KeyboardTextsSet();
        for (final InputMethodSubtype subtype : mAllSubtypesList) {
            final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            textsSet.setLocale(locale, context);
            final String switchToAlphaKeyLabel = textsSet.getText(
                    KeyboardTextsSet.SWITCH_TO_ALPHA_KEY_LABEL);
            assertNotNull("Switch to alpha key label of " + locale, switchToAlphaKeyLabel);
            assertFalse("Switch to alpha key label of " + locale, switchToAlphaKeyLabel.isEmpty());
        }
    }

    private static final String[] TEXT_NAMES_FROM_RESOURCE = {
        // Labels for action.
        "label_go_key",
        "label_send_key",
        "label_next_key",
        "label_done_key",
        "label_previous_key",
        // Other labels.
        "label_pause_key",
        "label_wait_key",
    };

    // Test that the text from resources are correctly loaded for all subtypes.
    public void testTextFromResources() {
        final Context context = getContext();
        final KeyboardTextsSet textsSet = new KeyboardTextsSet();
        for (final InputMethodSubtype subtype : mAllSubtypesList) {
            final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            textsSet.setLocale(locale, context);
            for (final String name : TEXT_NAMES_FROM_RESOURCE) {
                final String text = textsSet.getText(name);
                assertNotNull(name + " of " + locale, text);
                assertFalse(name + " of " + locale, text.isEmpty());
            }
        }
    }
}
