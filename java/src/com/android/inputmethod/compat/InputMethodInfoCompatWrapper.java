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

import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;

import java.lang.reflect.Method;

public class InputMethodInfoCompatWrapper {
    private final InputMethodInfo mImi;
    private static final Method METHOD_getSubtypeAt = CompatUtils.getMethod(
            InputMethodInfo.class, "getSubtypeAt", int.class);
    private static final Method METHOD_getSubtypeCount = CompatUtils.getMethod(
            InputMethodInfo.class, "getSubtypeCount");

    public InputMethodInfoCompatWrapper(InputMethodInfo imi) {
        mImi = imi;
    }

    public InputMethodInfo getInputMethodInfo() {
        return mImi;
    }

    public String getId() {
        return mImi.getId();
    }

    public String getPackageName() {
        return mImi.getPackageName();
    }

    public ServiceInfo getServiceInfo() {
        return mImi.getServiceInfo();
    }

    public int getSubtypeCount() {
        return (Integer) CompatUtils.invoke(mImi, 0, METHOD_getSubtypeCount);
    }

    public InputMethodSubtypeCompatWrapper getSubtypeAt(int index) {
        return new InputMethodSubtypeCompatWrapper(CompatUtils.invoke(mImi, null,
                METHOD_getSubtypeAt, index));
    }

    public CharSequence loadLabel(PackageManager pm) {
        return mImi.loadLabel(pm);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InputMethodInfoCompatWrapper) {
            return mImi.equals(((InputMethodInfoCompatWrapper)o).mImi);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mImi.hashCode();
    }
}
