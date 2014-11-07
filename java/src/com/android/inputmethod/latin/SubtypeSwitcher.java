/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

public final class SubtypeSwitcher {
    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();

    private /* final */ RichInputMethodManager mRichImm;
    private /* final */ Resources mResources;

    private final LanguageOnSpacebarHelper mLanguageOnSpacebarHelper =
            new LanguageOnSpacebarHelper();

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        SubtypeLocaleUtils.init(context);
        RichInputMethodManager.init(context);
        sInstance.initialize(context);
    }

    private SubtypeSwitcher() {
        // Intentional empty constructor for singleton.
    }

    private void initialize(final Context context) {
        if (mResources != null) {
            return;
        }
        mResources = context.getResources();
        mRichImm = RichInputMethodManager.getInstance();

        onSubtypeChanged(mRichImm.getCurrentRawSubtype());
        updateParametersOnStartInputView();
    }

    /**
     * Update parameters which are changed outside LatinIME. This parameters affect UI so that they
     * should be updated every time onStartInputView is called.
     */
    public void updateParametersOnStartInputView() {
        final List<InputMethodSubtype> enabledSubtypesOfThisIme =
                mRichImm.getMyEnabledInputMethodSubtypeList(true);
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(enabledSubtypesOfThisIme);
        mRichImm.updateShortcutIME();
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void onSubtypeChanged(@Nonnull final InputMethodSubtype newSubtype) {
        final RichInputMethodSubtype richSubtype = mRichImm.onSubtypeChanged(newSubtype);
        final Locale[] newLocales = richSubtype.getLocales();
        if (newLocales.length > 1) {
            // In multi-locales mode, the system language is never the same as the input language
            // because there is no single input language.
            mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(false);
        } else {
            final Locale newLocale = newLocales[0];
            final Locale systemLocale = mResources.getConfiguration().locale;
            final boolean sameLocale = systemLocale.equals(newLocale);
            final boolean sameLanguage = systemLocale.getLanguage().equals(newLocale.getLanguage());
            final boolean implicitlyEnabled = mRichImm
                    .checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(newSubtype);
            mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(
                    sameLocale || (sameLanguage && implicitlyEnabled));
        }
        mRichImm.updateShortcutIME();
    }

    public int getLanguageOnSpacebarFormatType(final RichInputMethodSubtype subtype) {
        return mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(subtype);
    }
}
