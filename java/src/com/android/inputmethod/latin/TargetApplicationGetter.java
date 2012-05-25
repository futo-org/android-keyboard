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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

public class TargetApplicationGetter extends AsyncTask<String, Void, ApplicationInfo> {

    public interface OnTargetApplicationKnownListener {
        public void onTargetApplicationKnown(final ApplicationInfo info);
    }

    private Context mContext;
    private final OnTargetApplicationKnownListener mListener;

    public TargetApplicationGetter(final Context context,
            final OnTargetApplicationKnownListener listener) {
        mContext = context;
        mListener = listener;
    }

    @Override
    protected ApplicationInfo doInBackground(final String... packageName) {
        // TODO: cache app info. Wipe the cache when new packages are installed.
        final PackageManager pm = mContext.getPackageManager();
        mContext = null; // Bazooka-powered anti-leak device
        try {
            final ApplicationInfo targetAppInfo =
                    pm.getApplicationInfo(packageName[0], 0 /* flags */);
            return targetAppInfo;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(final ApplicationInfo info) {
        mListener.onTargetApplicationKnown(info);
    }
}
