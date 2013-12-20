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

package com.android.inputmethod.compat;

import android.content.pm.PackageInfo;
import android.os.Build.VERSION_CODES;

/**
 * A class to encapsulate work-arounds specific to particular apps.
 */
public class AppWorkaroundsUtils {
    private final PackageInfo mPackageInfo; // May be null
    private final boolean mIsBrokenByRecorrection;

    public AppWorkaroundsUtils(final PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
        mIsBrokenByRecorrection = AppWorkaroundsHelper.evaluateIsBrokenByRecorrection(
                packageInfo);
    }

    public boolean isBrokenByRecorrection() {
        return mIsBrokenByRecorrection;
    }

    public boolean isBeforeJellyBean() {
        if (null == mPackageInfo || null == mPackageInfo.applicationInfo) {
            return false;
        }
        return mPackageInfo.applicationInfo.targetSdkVersion < VERSION_CODES.JELLY_BEAN;
    }

    @Override
    public String toString() {
        if (null == mPackageInfo || null == mPackageInfo.applicationInfo) {
            return "";
        }
        final StringBuilder s = new StringBuilder();
        s.append("Target application : ")
                .append(mPackageInfo.applicationInfo.name)
                .append("\nPackage : ")
                .append(mPackageInfo.applicationInfo.packageName)
                .append("\nTarget app sdk version : ")
                .append(mPackageInfo.applicationInfo.targetSdkVersion);
        return s.toString();
    }
}
