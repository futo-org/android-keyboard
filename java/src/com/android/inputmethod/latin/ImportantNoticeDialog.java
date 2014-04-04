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

package com.android.inputmethod.latin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;

import com.android.inputmethod.latin.utils.DialogUtils;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;

/**
 * The dialog box that shows the important notice contents.
 */
public final class ImportantNoticeDialog extends AlertDialog implements OnShowListener,
        OnClickListener, OnDismissListener {
    public interface ImportantNoticeDialogListener {
        public void onClickSettingsOfImportantNoticeDialog(final int nextVersion);
        public void onDismissImportantNoticeDialog(final int nextVersion);
    }

    private final ImportantNoticeDialogListener mListener;
    private final int mNextImportantNoticeVersion;

    public ImportantNoticeDialog(
            final Context context, final ImportantNoticeDialogListener listener) {
        super(DialogUtils.getPlatformDialogThemeContext(context));
        mListener = listener;
        mNextImportantNoticeVersion = ImportantNoticeUtils.getNextImportantNoticeVersion(context);
        setMessage(ImportantNoticeUtils.getNextImportantNoticeContents(context));
        // Create buttons and set listeners.
        setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        if (shouldHaveSettingsButton()) {
            setButton(BUTTON_NEGATIVE, context.getString(R.string.go_to_settings), this);
        }
        // Set listeners.
        setOnShowListener(this);
        setOnDismissListener(this);
    }

    private boolean shouldHaveSettingsButton() {
        return mNextImportantNoticeVersion
                == ImportantNoticeUtils.VERSION_TO_ENABLE_PERSONALIZED_SUGGESTIONS;
    }

    @Override
    public void onShow(final DialogInterface dialog) {
        ImportantNoticeUtils.updateLastImportantNoticeVersion(getContext());
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        if (shouldHaveSettingsButton() && which == BUTTON_NEGATIVE) {
            mListener.onClickSettingsOfImportantNoticeDialog(mNextImportantNoticeVersion);
        }
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        mListener.onDismissImportantNoticeDialog(mNextImportantNoticeVersion);
    }
}
