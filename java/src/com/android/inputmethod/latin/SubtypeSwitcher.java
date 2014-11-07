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

import com.android.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.List;

import javax.annotation.Nonnull;

public final class SubtypeSwitcher {
    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();

    private /* final */ RichInputMethodManager mRichImm;
    private /* final */ Resources mResources;

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

        onSubtypeChanged(mRichImm.getCurrentSubtype());
        updateParametersOnStartInputView();
    }

    /**
     * Update parameters which are changed outside LatinIME. This parameters affect UI so that they
     * should be updated every time onStartInputView is called.
     */
    public void updateParametersOnStartInputView() {
        final List<InputMethodSubtype> enabledSubtypesOfThisIme =
                mRichImm.getMyEnabledInputMethodSubtypeList(true);
        LanguageOnSpacebarUtils.setEnabledSubtypes(enabledSubtypesOfThisIme);
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void onSubtypeChanged(@Nonnull final RichInputMethodSubtype richSubtype) {
        final boolean implicitlyEnabledSubtype = mRichImm
                .checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(richSubtype.getRawSubtype());
        LanguageOnSpacebarUtils.onSubtypeChanged(
                richSubtype, implicitlyEnabledSubtype, mResources.getConfiguration().locale);
    }
}
