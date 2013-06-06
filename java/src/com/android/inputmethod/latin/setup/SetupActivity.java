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

    /*
     * We may not be able to get our own {@link InputMethodInfo} just after this IME is installed
     * because {@link InputMethodManagerService} may not be aware of this IME yet.
     * Note: {@link RichInputMethodManager} has similar methods. Here in setup wizard, we can't
     * use it for the reason above.
     */

    /**
     * Check if the IME specified by the context is enabled.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is enabled.
     */
    /* package */ static boolean isThisImeEnabled(final Context context,
            final InputMethodManager imm) {
        final String packageName = context.getPackageName();
        for (final InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (packageName.equals(imi.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the IME specified by the context is the current IME.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param context package context of the IME to be checked.
     * @param imm the {@link InputMethodManager}.
     * @return true if this IME is the current IME.
     */
    /* package */ static boolean isThisImeCurrent(final Context context,
            final InputMethodManager imm) {
        final InputMethodInfo imi = getInputMethodInfoOf(context.getPackageName(), imm);
        final String currentImeId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return imi != null && imi.getId().equals(currentImeId);
    }

    /**
     * Get {@link InputMethodInfo} of the IME specified by the package name.
     * CAVEAT: This may cause a round trip IPC.
     *
     * @param packageName package name of the IME.
     * @param imm the {@link InputMethodManager}.
     * @return the {@link InputMethodInfo} of the IME specified by the <code>packageName</code>,
     * or null if not found.
     */
    /* package */ static InputMethodInfo getInputMethodInfoOf(final String packageName,
            final InputMethodManager imm) {
        for (final InputMethodInfo imi : imm.getInputMethodList()) {
            if (packageName.equals(imi.getPackageName())) {
                return imi;
            }
        }
        return null;
    }
}
