/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethodcommon;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.List;

/* package private */ class InputMethodSettingsImpl implements InputMethodSettingsInterface {
    private Preference mSubtypeEnablerPreference;
    private int mInputMethodSettingsCategoryTitleRes;
    private CharSequence mInputMethodSettingsCategoryTitle;
    private int mSubtypeEnablerTitleRes;
    private CharSequence mSubtypeEnablerTitle;
    private int mSubtypeEnablerIconRes;
    private Drawable mSubtypeEnablerIcon;
    private InputMethodManager mImm;
    private InputMethodInfo mImi;

    /**
     * Initialize internal states of this object.
     * @param context the context for this application.
     * @param prefScreen a PreferenceScreen of PreferenceActivity or PreferenceFragment.
     * @return true if this application is an IME and has two or more subtypes, false otherwise.
     */
    public boolean init(final Context context, final PreferenceScreen prefScreen) {
        mImm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        mImi = getMyImi(context, mImm);
        if (mImi == null || mImi.getSubtypeCount() <= 1) {
            return false;
        }
        final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, mImi.getId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mSubtypeEnablerPreference = new Preference(context);
        mSubtypeEnablerPreference.setIntent(intent);
        prefScreen.addPreference(mSubtypeEnablerPreference);
        updateSubtypeEnabler();
        return true;
    }

    private static InputMethodInfo getMyImi(Context context, InputMethodManager imm) {
        final List<InputMethodInfo> imis = imm.getInputMethodList();
        for (int i = 0; i < imis.size(); ++i) {
            final InputMethodInfo imi = imis.get(i);
            if (imis.get(i).getPackageName().equals(context.getPackageName())) {
                return imi;
            }
        }
        return null;
    }

    private static String getEnabledSubtypesLabel(
            Context context, InputMethodManager imm, InputMethodInfo imi) {
        if (context == null || imm == null || imi == null) return null;
        final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi, true);
        final StringBuilder sb = new StringBuilder();
        final int N = subtypes.size();
        for (int i = 0; i < N; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(subtype.getDisplayName(context, imi.getPackageName(),
                    imi.getServiceInfo().applicationInfo));
        }
        return sb.toString();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(int resId) {
        mInputMethodSettingsCategoryTitleRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(CharSequence title) {
        mInputMethodSettingsCategoryTitleRes = 0;
        mInputMethodSettingsCategoryTitle = title;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(int resId) {
        mSubtypeEnablerTitleRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(CharSequence title) {
        mSubtypeEnablerTitleRes = 0;
        mSubtypeEnablerTitle = title;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(int resId) {
        mSubtypeEnablerIconRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(Drawable drawable) {
        mSubtypeEnablerIconRes = 0;
        mSubtypeEnablerIcon = drawable;
        updateSubtypeEnabler();
    }

    public void updateSubtypeEnabler() {
        final Preference pref = mSubtypeEnablerPreference;
        if (pref == null) {
            return;
        }
        final Context context = pref.getContext();
        final CharSequence title;
        if (mSubtypeEnablerTitleRes != 0) {
            title = context.getString(mSubtypeEnablerTitleRes);
        } else {
            title = mSubtypeEnablerTitle;
        }
        pref.setTitle(title);
        final Intent intent = pref.getIntent();
        if (intent != null) {
            intent.putExtra(Intent.EXTRA_TITLE, title);
        }
        final String summary = getEnabledSubtypesLabel(context, mImm, mImi);
        if (!TextUtils.isEmpty(summary)) {
            pref.setSummary(summary);
        }
        if (mSubtypeEnablerIconRes != 0) {
            pref.setIcon(mSubtypeEnablerIconRes);
        } else {
            pref.setIcon(mSubtypeEnablerIcon);
        }
    }
}
