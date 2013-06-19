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

package com.android.inputmethod.research.ui;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import com.android.inputmethod.latin.R.string;

/**
 * Show a dialog when the user first opens the keyboard.
 *
 * The splash screen is a modal dialog box presented when the user opens this keyboard for the first
 * time.  It is useful for giving specific warnings that must be shown to the user before use.
 *
 * While the splash screen does share with the setup wizard the common goal of presenting
 * information to the user before use, they are presented at different times and with different
 * capabilities.  The setup wizard is launched by tapping on the icon, and walks the user through
 * the setup process.  It can, however, be bypassed by enabling the keyboard from Settings directly.
 * The splash screen cannot be bypassed, and is therefore more appropriate for obtaining user
 * consent.
 */
public class SplashScreen {
    public interface UserConsentListener {
        public void onSplashScreenUserClickedOk();
    }

    final UserConsentListener mListener;
    final Dialog mSplashDialog;

    public SplashScreen(final InputMethodService inputMethodService,
            final UserConsentListener listener) {
        mListener = listener;
        final Builder builder = new Builder(inputMethodService)
                .setTitle(string.research_splash_title)
                .setMessage(string.research_splash_content)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mListener.onSplashScreenUserClickedOk();
                                mSplashDialog.dismiss();
                            }
                })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final String packageName = inputMethodService.getPackageName();
                                final Uri packageUri = Uri.parse("package:" + packageName);
                                final Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                                        packageUri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                inputMethodService.startActivity(intent);
                            }
                })
                .setCancelable(true)
                .setOnCancelListener(
                        new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                inputMethodService.requestHideSelf(0);
                            }
                });
        mSplashDialog = builder.create();
    }

    /**
     * Show the splash screen.
     *
     * The user must consent to the terms presented in the SplashScreen before they can use the
     * keyboard.  If they cancel instead, they are given the option to uninstall the keybard.
     *
     * @param windowToken {@link IBinder} to attach dialog to
     */
    public void showSplashScreen(final IBinder windowToken) {
        final Window window = mSplashDialog.getWindow();
        final LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mSplashDialog.show();
    }

    public boolean isShowing() {
        return mSplashDialog.isShowing();
    }
}
