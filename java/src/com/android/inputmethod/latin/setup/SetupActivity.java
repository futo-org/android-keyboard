/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethod.latin.RichInputMethodManager;

public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = new Intent();
        intent.setClass(this, SetupWizardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (!isFinishing()) {
            finish();
        }
    }

    /**
     * Check if the IME specified by the context is enabled.
     * Note that {@link RichInputMethodManager} must have been initialized before calling this
     * method.
     *
     * @param context package context of the IME to be checked.
     * @return true if this IME is enabled.
     */
    public static boolean isThisImeEnabled(final Context context) {
        final String packageName = context.getPackageName();
        final InputMethodManager imm = RichInputMethodManager.getInstance().getInputMethodManager();
        for (final InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (packageName.equals(imi.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the IME specified by the context is the current IME.
     * Note that {@link RichInputMethodManager} must have been initialized before calling this
     * method.
     *
     * @param context package context of the IME to be checked.
     * @return true if this IME is the current IME.
     */
    public static boolean isThisImeCurrent(final Context context) {
        final InputMethodInfo myImi =
                RichInputMethodManager.getInstance().getInputMethodInfoOfThisIme();
        final String currentImeId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return myImi.getId().equals(currentImeId);
    }
}
