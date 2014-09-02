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

import android.content.res.Resources;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

abstract class KeyboardLayoutSetActionLabelBase extends KeyboardLayoutSetTestsBase {
    public void testActionUnspecified() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "unspecifiled "
                    + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_UNSPECIFIED,
                    KeyboardIconsSet.NAME_ENTER_KEY);
        }
    }

    public void testActionNone() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "none " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_NONE,
                    KeyboardIconsSet.NAME_ENTER_KEY);
        }
    }

    public void testActionSearch() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "search " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_SEARCH,
                    KeyboardIconsSet.NAME_SEARCH_KEY);
        }
    }

    public abstract void testActionGo();
    public abstract void testActionSend();
    public abstract void testActionNext();
    public abstract void testActionDone();
    public abstract void testActionPrevious();

    public void testActionCustom() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "custom " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            final CharSequence customLabel = "customLabel";
            final EditorInfo editorInfo = new EditorInfo();
            editorInfo.imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED;
            editorInfo.actionLabel = customLabel;
            doTestActionKeyLabel(tag, subtype, editorInfo, customLabel);
        }
    }

    private static void doTestActionKey(final String tag, final KeyboardLayoutSet layoutSet,
            final int elementId, final CharSequence label, final int iconId) {
        final Keyboard keyboard = layoutSet.getKeyboard(elementId);
        final Key enterKey = keyboard.getKey(Constants.CODE_ENTER);
        assertNotNull(tag + " enter key on " + keyboard.mId, enterKey);
        assertEquals(tag + " enter label " + enterKey, label, enterKey.getLabel());
        assertEquals(tag + " enter icon " + enterKey, iconId, enterKey.getIconId());
    }

    protected void doTestActionKeyLabelResId(final String tag, final InputMethodSubtype subtype,
            final int actionId, final int labelResId) {
        final Locale labelLocale = subtype.getLocale().equals(SubtypeLocaleUtils.NO_LANGUAGE)
                ? null : SubtypeLocaleUtils.getSubtypeLocale(subtype);
        doTestActionKeyLabelResIdInLocale(tag, subtype, actionId, labelLocale, labelResId);
    }

    protected void doTestActionKeyLabelResIdInLocale(final String tag,
            final InputMethodSubtype subtype, final int actionId, final Locale labelLocale,
            final int labelResId) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.imeOptions = actionId;
        final RunInLocale<String> job = new RunInLocale<String>() {
            @Override
            protected String job(final Resources res) {
                return res.getString(labelResId);
            }
        };
        final String label = job.runInLocale(getContext().getResources(), labelLocale);
        doTestActionKeyLabel(tag, subtype, editorInfo, label);
    }

    protected void doTestActionKeyLabel(final String tag, final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final CharSequence label) {
        // Test text layouts.
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        final KeyboardLayoutSet layoutSet = createKeyboardLayoutSet(subtype, editorInfo);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_ALPHABET,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS_SHIFTED,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        // Test phone number layouts.
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_PHONE,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_PHONE_SYMBOLS,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        // Test normal number layout.
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_NUMBER,
                label, KeyboardIconsSet.ICON_UNDEFINED);
        // Test number password layouts.
        editorInfo.inputType =
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        final KeyboardLayoutSet passwordSet = createKeyboardLayoutSet(subtype, editorInfo);
        doTestActionKey(tag, passwordSet, KeyboardId.ELEMENT_NUMBER,
                label, KeyboardIconsSet.ICON_UNDEFINED);
    }

    protected void doTestActionKeyIcon(final String tag, final InputMethodSubtype subtype,
            final int actionId, final String iconName) {
        final int iconId = KeyboardIconsSet.getIconId(iconName);
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.imeOptions = actionId;
        // Test text layouts.
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        final KeyboardLayoutSet layoutSet = createKeyboardLayoutSet(subtype, editorInfo);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_ALPHABET, null /* label */, iconId);
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS, null /* label */, iconId);
        doTestActionKey(
                tag, layoutSet, KeyboardId.ELEMENT_SYMBOLS_SHIFTED, null /* label */, iconId);
        // Test phone number layouts.
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_PHONE, null /* label */, iconId);
        doTestActionKey(
                tag, layoutSet, KeyboardId.ELEMENT_PHONE_SYMBOLS, null /* label */, iconId);
        // Test normal number layout.
        doTestActionKey(tag, layoutSet, KeyboardId.ELEMENT_NUMBER, null /* label */, iconId);
        // Test number password layout.
        editorInfo.inputType =
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        final KeyboardLayoutSet passwordSet = createKeyboardLayoutSet(subtype, editorInfo);
        doTestActionKey(tag, passwordSet, KeyboardId.ELEMENT_NUMBER, null /* label */, iconId);
    }
}
