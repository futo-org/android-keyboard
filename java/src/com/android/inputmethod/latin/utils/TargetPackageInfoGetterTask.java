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

package com.android.inputmethod.latin.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.LruCache;

import com.android.inputmethod.compat.AppWorkaroundsUtils;

public final class TargetPackageInfoGetterTask extends
        AsyncTask<String, Void, PackageInfo> {
    private static final int MAX_CACHE_ENTRIES = 64; // arbitrary
    private static final LruCache<String, PackageInfo> sCache = new LruCache<>(MAX_CACHE_ENTRIES);

    public static PackageInfo getCachedPackageInfo(final String packageName) {
        if (null == packageName) return null;
        return sCache.get(packageName);
    }

    public static void removeCachedPackageInfo(final String packageName) {
        sCache.remove(packageName);
    }

    private Context mContext;
    private final AsyncResultHolder<AppWorkaroundsUtils> mResult;

    public TargetPackageInfoGetterTask(final Context context,
            final AsyncResultHolder<AppWorkaroundsUtils> result) {
        mContext = context;
        mResult = result;
    }

    @Override
    protected PackageInfo doInBackground(final String... packageName) {
        final PackageManager pm = mContext.getPackageManager();
        mContext = null; // Bazooka-powered anti-leak device
        try {
            final PackageInfo packageInfo = pm.getPackageInfo(packageName[0], 0 /* flags */);
            sCache.put(packageName[0], packageInfo);
            return packageInfo;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(final PackageInfo info) {
        mResult.set(new AppWorkaroundsUtils(info));
    }
}
