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

import android.content.res.Resources;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardTextsSet;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

@MediumTest
public class KlpActionLabelTests extends KlpActionTestsBase {
    void doTestActionKeys(final InputMethodSubtype subtype, final String tag,
            final ExpectedActionKey unspecifiedKey, final ExpectedActionKey noneKey,
            final ExpectedActionKey goKey, final ExpectedActionKey searchKey,
            final ExpectedActionKey sendKey, final ExpectedActionKey nextKey,
            final ExpectedActionKey doneKey, final ExpectedActionKey previousKey) {
        doTestActionKey(
                tag + " unspecified", subtype, EditorInfo.IME_ACTION_UNSPECIFIED, unspecifiedKey);
        doTestActionKey(tag + " none", subtype, EditorInfo.IME_ACTION_NONE, noneKey);
        doTestActionKey(tag + " go", subtype, EditorInfo.IME_ACTION_GO, goKey);
        doTestActionKey(tag + " search", subtype, EditorInfo.IME_ACTION_SEARCH, searchKey);
        doTestActionKey(tag + " send", subtype, EditorInfo.IME_ACTION_SEND, sendKey);
        doTestActionKey(tag + " next", subtype, EditorInfo.IME_ACTION_NEXT, nextKey);
        doTestActionKey(tag + " done", subtype, EditorInfo.IME_ACTION_DONE, doneKey);
        doTestActionKey(tag + " previous", subtype, EditorInfo.IME_ACTION_PREVIOUS, previousKey);
    }

    // Working variable to simulate system locale changing.
    private Locale mSystemLocale = Locale.getDefault();

    private void doTestActionKeysInLocaleWithStringResources(final InputMethodSubtype subtype,
            final Locale labelLocale, final Locale systemLocale) {
        // Simulate system locale changing, see {@link SystemBroadcastReceiver}.
        if (!systemLocale.equals(mSystemLocale)) {
            KeyboardLayoutSet.onSystemLocaleChanged();
            mSystemLocale = systemLocale;
        }
        final ExpectedActionKey enterKey = ExpectedActionKey.newIconKey(
                KeyboardIconsSet.NAME_ENTER_KEY);
        final ExpectedActionKey goKey = ExpectedActionKey.newLabelKey(
                R.string.label_go_key, labelLocale, getContext());
        final ExpectedActionKey searchKey = ExpectedActionKey.newIconKey(
                KeyboardIconsSet.NAME_SEARCH_KEY);
        final ExpectedActionKey sendKey = ExpectedActionKey.newLabelKey(
                R.string.label_send_key, labelLocale, getContext());
        final ExpectedActionKey nextKey = ExpectedActionKey.newLabelKey(
                R.string.label_next_key, labelLocale, getContext());
        final ExpectedActionKey doneKey = ExpectedActionKey.newLabelKey(
                R.string.label_done_key, labelLocale, getContext());
        final ExpectedActionKey previousKey = ExpectedActionKey.newLabelKey(
                R.string.label_previous_key, labelLocale, getContext());
        final String tag = "label=" + labelLocale + " system=" + systemLocale
                + " " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
        final RunInLocale<Void> job = new RunInLocale<Void>() {
            @Override
            public Void job(final Resources res) {
                doTestActionKeys(subtype, tag, enterKey, enterKey, goKey, searchKey, sendKey,
                        nextKey, doneKey, previousKey);
                return null;
            }
        };
        job.runInLocale(getContext().getResources(), systemLocale);
    }

    public void testActionLabelInOtherLocale() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final InputMethodSubtype italian = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.ITALIAN.toString(), SubtypeLocaleUtils.QWERTY);
        // An action label should be displayed in subtype's locale regardless of the system locale.
        doTestActionKeysInLocaleWithStringResources(italian, Locale.ITALIAN, Locale.US);
        doTestActionKeysInLocaleWithStringResources(italian, Locale.ITALIAN, Locale.FRENCH);
        doTestActionKeysInLocaleWithStringResources(italian, Locale.ITALIAN, Locale.ITALIAN);
        doTestActionKeysInLocaleWithStringResources(italian, Locale.ITALIAN, Locale.JAPANESE);
    }

    public void testNoLanguageSubtypeActionLabel() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final InputMethodSubtype noLanguage = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.QWERTY);
        // An action label of no language keyboard should be displayed in the system locale.
        doTestActionKeysInLocaleWithStringResources(noLanguage, Locale.US, Locale.US);
        doTestActionKeysInLocaleWithStringResources(noLanguage, Locale.FRENCH, Locale.FRENCH);
        doTestActionKeysInLocaleWithStringResources(noLanguage, Locale.ITALIAN, Locale.ITALIAN);
        doTestActionKeysInLocaleWithStringResources(noLanguage, Locale.JAPANESE, Locale.JAPANESE);
    }

    private void doTestActionKeysInLocaleWithKeyboardTextsSet(final InputMethodSubtype subtype,
            final Locale labelLocale, final Locale systemLocale) {
        // Simulate system locale changing, see {@link SystemBroadcastReceiver}.
        if (!systemLocale.equals(mSystemLocale)) {
            KeyboardLayoutSet.onSystemLocaleChanged();
            mSystemLocale = systemLocale;
        }
        final KeyboardTextsSet textsSet = new KeyboardTextsSet();
        textsSet.setLocale(labelLocale, getContext());
        final ExpectedActionKey enterKey = ExpectedActionKey.newIconKey(
                KeyboardIconsSet.NAME_ENTER_KEY);
        final ExpectedActionKey goKey = ExpectedActionKey.newLabelKey(
                textsSet.getText("label_go_key"));
        final ExpectedActionKey searchKey = ExpectedActionKey.newIconKey(
                KeyboardIconsSet.NAME_SEARCH_KEY);
        final ExpectedActionKey sendKey = ExpectedActionKey.newLabelKey(
                textsSet.getText("label_send_key"));
        final ExpectedActionKey nextKey = ExpectedActionKey.newLabelKey(
                textsSet.getText("label_next_key"));
        final ExpectedActionKey doneKey = ExpectedActionKey.newLabelKey(
                textsSet.getText("label_done_key"));
        final ExpectedActionKey previousKey = ExpectedActionKey.newLabelKey(
                textsSet.getText("label_previous_key"));
        final String tag = "label=" + subtype.getLocale() + " system=" + systemLocale
                + " " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
        final RunInLocale<Void> job = new RunInLocale<Void>() {
            @Override
            public Void job(final Resources res) {
                doTestActionKeys(subtype, tag, enterKey, enterKey, goKey, searchKey, sendKey,
                        nextKey, doneKey, previousKey);
                return null;
            }
        };
        job.runInLocale(getContext().getResources(), systemLocale);
    }

    public void testHinglishActionLabel() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final Locale hi_ZZ = new Locale("hi", "ZZ");
        final InputMethodSubtype hiLatn = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                hi_ZZ.toString(), SubtypeLocaleUtils.QWERTY);
        // This is a preliminary subtype and may not exist.
        if (hiLatn == null) {
            return;
        }
        // An action label should be displayed in subtype's locale regardless of the system locale.
        doTestActionKeysInLocaleWithKeyboardTextsSet(hiLatn, hi_ZZ, new Locale("hi"));
        doTestActionKeysInLocaleWithKeyboardTextsSet(hiLatn, hi_ZZ, Locale.US);
        doTestActionKeysInLocaleWithKeyboardTextsSet(hiLatn, hi_ZZ, Locale.FRENCH);
        doTestActionKeysInLocaleWithKeyboardTextsSet(hiLatn, hi_ZZ, Locale.ITALIAN);
        doTestActionKeysInLocaleWithKeyboardTextsSet(hiLatn, hi_ZZ, Locale.JAPANESE);
    }

    public void testSerbianLatinActionLabel() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final Locale sr_ZZ = new Locale("sr", "ZZ");
        final InputMethodSubtype srLatn = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                sr_ZZ.toString(), "serbian_qwertz");
        // This is a preliminary subtype and may not exist.
        if (srLatn == null) {
            return;
        }
        // An action label should be displayed in subtype's locale regardless of the system locale.
        doTestActionKeysInLocaleWithKeyboardTextsSet(srLatn, sr_ZZ, new Locale("sr"));
        doTestActionKeysInLocaleWithKeyboardTextsSet(srLatn, sr_ZZ, Locale.US);
        doTestActionKeysInLocaleWithKeyboardTextsSet(srLatn, sr_ZZ, Locale.FRENCH);
        doTestActionKeysInLocaleWithKeyboardTextsSet(srLatn, sr_ZZ, Locale.ITALIAN);
        doTestActionKeysInLocaleWithKeyboardTextsSet(srLatn, sr_ZZ, Locale.JAPANESE);
    }
}
