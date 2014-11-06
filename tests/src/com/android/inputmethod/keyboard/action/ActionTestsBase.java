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

package com.android.inputmethod.keyboard.action;

import android.content.Context;
import android.content.res.Resources;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.KeyboardLayoutSetTestsBase;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyVisual;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

abstract class ActionTestsBase extends KeyboardLayoutSetTestsBase {
    static class ExpectedActionKey {
        static ExpectedActionKey newIconKey(final String iconName) {
            final int iconId = KeyboardIconsSet.getIconId(iconName);
            return new ExpectedActionKey(ExpectedKeyVisual.newInstance(iconId));
        }

        static ExpectedActionKey newLabelKey(final String label) {
            return new ExpectedActionKey(ExpectedKeyVisual.newInstance(label));
        }

        static ExpectedActionKey newLabelKey(final int labelResId,
                final Locale labelLocale, final Context context) {
            final RunInLocale<String> getString = new RunInLocale<String>() {
                @Override
                protected String job(final Resources res) {
                    return res.getString(labelResId);
                }
            };
            return newLabelKey(getString.runInLocale(context.getResources(), labelLocale));
        }

        private final ExpectedKeyVisual mVisual;

        private ExpectedActionKey(final ExpectedKeyVisual visual) {
            mVisual = visual;
        }

        public int getIconId() { return mVisual.getIconId(); }

        public String getLabel() { return mVisual.getLabel(); }
    }

    protected static Locale getLabelLocale(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        if (localeString.equals(SubtypeLocaleUtils.NO_LANGUAGE)) {
            return null;
        }
        return LocaleUtils.constructLocaleFromString(localeString);
    }

    private static void assertActionKey(final String tag, final KeyboardLayoutSet layoutSet,
            final int elementId, final ExpectedActionKey expectedKey) {
        final Keyboard keyboard = layoutSet.getKeyboard(elementId);
        final Key actualKey = keyboard.getKey(Constants.CODE_ENTER);
        assertNotNull(tag + " enter key on " + keyboard.mId, actualKey);
        assertEquals(tag + " label " + expectedKey, expectedKey.getLabel(), actualKey.getLabel());
        assertEquals(tag + " icon " + expectedKey, expectedKey.getIconId(), actualKey.getIconId());
    }

    protected void doTestActionKey(final String tag, final InputMethodSubtype subtype,
            final int actionId, final ExpectedActionKey expectedKey) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.imeOptions = actionId;
        doTestActionKey(tag, subtype, editorInfo, expectedKey);
    }

    protected void doTestActionKey(final String tag, final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final ExpectedActionKey expectedKey) {
        // Test text layouts.
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        final KeyboardLayoutSet layoutSet = createKeyboardLayoutSet(subtype, editorInfo);
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_ALPHABET, expectedKey);
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS, expectedKey);
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS_SHIFTED, expectedKey);
        // Test phone number layouts.
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_PHONE, expectedKey);
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_PHONE_SYMBOLS, expectedKey);
        // Test normal number layout.
        assertActionKey(tag, layoutSet, KeyboardId.ELEMENT_NUMBER, expectedKey);
        // Test number password layout.
        editorInfo.inputType =
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        final KeyboardLayoutSet passwordSet = createKeyboardLayoutSet(subtype, editorInfo);
        assertActionKey(tag, passwordSet, KeyboardId.ELEMENT_NUMBER, expectedKey);
    }
}
