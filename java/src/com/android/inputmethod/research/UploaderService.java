/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.android.inputmethod.latin.define.ProductionFlag;

/**
 * Service to invoke the uploader.
 *
 * Can be regularly invoked, invoked on boot, etc.
 */
public final class UploaderService extends IntentService {
    private static final String TAG = UploaderService.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    public static final long RUN_INTERVAL = AlarmManager.INTERVAL_HOUR;
    public static final String EXTRA_UPLOAD_UNCONDITIONALLY = UploaderService.class.getName()
            + ".extra.UPLOAD_UNCONDITIONALLY";
    protected static final int TIMEOUT_IN_MS = 1000 * 4;

    public UploaderService() {
        super("Research Uploader Service");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final Uploader uploader = new Uploader(this);
        if (!uploader.isPossibleToUpload()) return;
        if (isUploadingUnconditionally(intent.getExtras()) || uploader.isConvenientToUpload()) {
            uploader.doUpload();
        }
    }

    private boolean isUploadingUnconditionally(final Bundle bundle) {
        if (bundle == null) return false;
        if (bundle.containsKey(EXTRA_UPLOAD_UNCONDITIONALLY)) {
            return bundle.getBoolean(EXTRA_UPLOAD_UNCONDITIONALLY);
        }
        return false;
    }
}
