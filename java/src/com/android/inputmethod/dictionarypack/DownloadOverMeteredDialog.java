/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.dictionarypack;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.inputmethod.annotations.ExternallyReferenced;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.common.LocaleUtils;

import javax.annotation.Nullable;

/**
 * This implements the dialog for asking the user whether it's okay to download dictionaries over
 * a metered connection or not (e.g. their mobile data plan).
 */
public final class DownloadOverMeteredDialog extends Activity {
    final public static String CLIENT_ID_KEY = "client_id";
    final public static String WORDLIST_TO_DOWNLOAD_KEY = "wordlist_to_download";
    final public static String SIZE_KEY = "size";
    final public static String LOCALE_KEY = "locale";
    private String mClientId;
    private String mWordListToDownload;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        mClientId = intent.getStringExtra(CLIENT_ID_KEY);
        mWordListToDownload = intent.getStringExtra(WORDLIST_TO_DOWNLOAD_KEY);
        final String localeString = intent.getStringExtra(LOCALE_KEY);
        final long size = intent.getIntExtra(SIZE_KEY, 0);
        setContentView(R.layout.download_over_metered);
        setTexts(localeString, size);
    }

    private void setTexts(@Nullable final String localeString, final long size) {
        final String promptFormat = getString(R.string.should_download_over_metered_prompt);
        final String allowButtonFormat = getString(R.string.download_over_metered);
        final String language = (null == localeString) ? ""
                : LocaleUtils.constructLocaleFromString(localeString).getDisplayLanguage();
        final TextView prompt = (TextView)findViewById(R.id.download_over_metered_prompt);
        prompt.setText(Html.fromHtml(String.format(promptFormat, language)));
        final Button allowButton = (Button)findViewById(R.id.allow_button);
        allowButton.setText(String.format(allowButtonFormat, ((float)size)/(1024*1024)));
    }

    // This method is externally referenced from layout/download_over_metered.xml using onClick
    // attribute of Button.
    @ExternallyReferenced
    @SuppressWarnings("unused")
    public void onClickDeny(final View v) {
        UpdateHandler.setDownloadOverMeteredSetting(this, false);
        finish();
    }

    // This method is externally referenced from layout/download_over_metered.xml using onClick
    // attribute of Button.
    @ExternallyReferenced
    @SuppressWarnings("unused")
    public void onClickAllow(final View v) {
        UpdateHandler.setDownloadOverMeteredSetting(this, true);
        UpdateHandler.installIfNeverRequested(this, mClientId, mWordListToDownload);
        finish();
    }
}
