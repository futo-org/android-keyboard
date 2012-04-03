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

package com.android.inputmethod.compat;

import android.app.AlertDialog;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;

import com.android.inputmethod.keyboard.KeyboardSwitcher;

public class InputMethodServiceCompatWrapper extends InputMethodService {
    // For compatibility of {@link InputMethodManager#showInputMethodPicker}.
    // TODO: Move this variable back to LatinIME when this compatibility wrapper is removed.
    protected AlertDialog mOptionsDialog;

    public void showOptionDialogInternal(AlertDialog dialog) {
        final IBinder windowToken = KeyboardSwitcher.getInstance().getKeyboardView()
                .getWindowToken();
        if (windowToken == null) return;

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        final Window window = dialog.getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        mOptionsDialog = dialog;
        dialog.show();
    }

    protected static void setTouchableRegionCompat(InputMethodService.Insets outInsets,
            int x, int y, int width, int height) {
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(x, y, width, height);
    }
}
