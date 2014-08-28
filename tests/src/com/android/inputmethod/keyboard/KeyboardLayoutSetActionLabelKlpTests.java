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
import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

@MediumTest
public class KeyboardLayoutSetActionLabelKlpTests extends KeyboardLayoutSetActionLabelLxxTests {
    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_KLP;
    }

    protected void doTestActionKeyLabel(final String tag, final InputMethodSubtype subtype,
            final int actionId, final int labelResId) {
        final Locale labelLocale = subtype.getLocale().equals(SubtypeLocaleUtils.NO_LANGUAGE)
                ? null : SubtypeLocaleUtils.getSubtypeLocale(subtype);
        doTestActionKeyLabel(tag, subtype, actionId, labelLocale, labelResId);
    }

    protected void doTestActionKeyLabel(final String tag, final InputMethodSubtype subtype,
            final int actionId, final Locale labelLocale, final int labelResId) {
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

    @Override
    public void testActionUnspecified() {
        super.testActionUnspecified();
    }

    @Override
    public void testActionNone() {
        super.testActionNone();
    }

    @Override
    public void testActionGo() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "go " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyLabel(tag, subtype, EditorInfo.IME_ACTION_GO, R.string.label_go_key);
        }
    }

    @Override
    public void testActionSearch() {
        super.testActionSearch();
    }

    @Override
    public void testActionSend() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "send " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyLabel(tag, subtype, EditorInfo.IME_ACTION_SEND, R.string.label_send_key);
        }
    }

    @Override
    public void testActionNext() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "next " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyLabel(tag, subtype, EditorInfo.IME_ACTION_NEXT, R.string.label_next_key);
        }
    }

    @Override
    public void testActionDone() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "done " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyLabel(tag, subtype, EditorInfo.IME_ACTION_DONE, R.string.label_done_key);
        }
    }

    @Override
    public void testActionPrevious() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "previous " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyLabel(
                    tag, subtype, EditorInfo.IME_ACTION_PREVIOUS, R.string.label_previous_key);
        }
    }

    @Override
    public void testActionCustom() {
        super.testActionCustom();
    }

    private void doTestActionLabelInLocale(final InputMethodSubtype subtype,
            final Locale labelLocale, final Locale systemLocale) {
        final String tag = "label=" + labelLocale + " system=" + systemLocale
                + " " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
        final RunInLocale<Void> job = new RunInLocale<Void>() {
            @Override
            public Void job(final Resources res) {
                doTestActionKeyIcon(tag + " unspecified", subtype,
                        EditorInfo.IME_ACTION_UNSPECIFIED, KeyboardIconsSet.NAME_ENTER_KEY);
                doTestActionKeyIcon(tag + " none", subtype,
                        EditorInfo.IME_ACTION_NONE, KeyboardIconsSet.NAME_ENTER_KEY);
                doTestActionKeyLabel(tag + " go", subtype,
                        EditorInfo.IME_ACTION_GO, labelLocale, R.string.label_go_key);
                doTestActionKeyIcon(tag + " search", subtype,
                        EditorInfo.IME_ACTION_SEARCH, KeyboardIconsSet.NAME_SEARCH_KEY);
                doTestActionKeyLabel(tag + " send", subtype,
                        EditorInfo.IME_ACTION_SEND, labelLocale, R.string.label_send_key);
                doTestActionKeyLabel(tag + " next", subtype,
                        EditorInfo.IME_ACTION_NEXT, labelLocale, R.string.label_next_key);
                doTestActionKeyLabel(tag + " done", subtype,
                        EditorInfo.IME_ACTION_DONE, labelLocale, R.string.label_done_key);
                doTestActionKeyLabel(tag + " previous", subtype,
                        EditorInfo.IME_ACTION_PREVIOUS, labelLocale, R.string.label_previous_key);
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
        doTestActionLabelInLocale(italian, Locale.ITALIAN, Locale.US);
        doTestActionLabelInLocale(italian, Locale.ITALIAN, Locale.FRENCH);
        doTestActionLabelInLocale(italian, Locale.ITALIAN, Locale.ITALIAN);
        doTestActionLabelInLocale(italian, Locale.ITALIAN, Locale.JAPANESE);
    }

    public void testNoLanguageSubtypeActionLabel() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final InputMethodSubtype noLanguage = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.QWERTY);
        // An action label of no language keyboard should be displayed in the system locale.
        doTestActionLabelInLocale(noLanguage, Locale.US, Locale.US);
        // TODO: Uncomment the following test once a bug is fixed.
        // doTestActionLabelInLocale(noLanguage, Locale.FRENCH, Locale.FRENCH);
        // doTestActionLabelInLocale(noLanguage, Locale.ITALIAN, Locale.ITALIAN);
        // doTestActionLabelInLocale(noLanguage, Locale.JAPANESE, Locale.JAPANESE);
    }
}
