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

import android.app.DownloadManager;

import java.lang.reflect.Method;

public final class DownloadManagerCompatUtils {
    // DownloadManager.Request#setAllowedOverMetered() has been introduced
    // in API level 16 (Build.VERSION_CODES.JELLY_BEAN).
    private static final Method METHOD_setAllowedOverMetered = CompatUtils.getMethod(
            DownloadManager.Request.class, "setAllowedOverMetered", Boolean.TYPE);

    public static DownloadManager.Request setAllowedOverMetered(
            final DownloadManager.Request request, final boolean allowOverMetered) {
        return (DownloadManager.Request)CompatUtils.invoke(request,
                request /* default return value */, METHOD_setAllowedOverMetered, allowOverMetered);
    }

    public static final boolean hasSetAllowedOverMetered() {
        return null != METHOD_setAllowedOverMetered;
    }
}
