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

import android.view.View;

import java.lang.reflect.Method;

public final class ViewCompatUtils {
    // Note that View.LAYOUT_DIRECTION_LTR and View.LAYOUT_DIRECTION_RTL have been introduced in
    // API level 17 (Build.VERSION_CODE.JELLY_BEAN_MR1).
    public static final int LAYOUT_DIRECTION_LTR = (Integer)CompatUtils.getFieldValue(null, 0x0,
            CompatUtils.getField(View.class, "LAYOUT_DIRECTION_LTR"));
    public static final int LAYOUT_DIRECTION_RTL = (Integer)CompatUtils.getFieldValue(null, 0x1,
            CompatUtils.getField(View.class, "LAYOUT_DIRECTION_RTL"));

    // Note that View.getPaddingEnd(), View.setPaddingRelative(int,int,int,int), and
    // View.getLayoutDirection() have been introduced in API level 17
    // (Build.VERSION_CODE.JELLY_BEAN_MR1).
    private static final Method METHOD_getPaddingEnd = CompatUtils.getMethod(
            View.class, "getPaddingEnd");
    private static final Method METHOD_setPaddingRelative = CompatUtils.getMethod(
            View.class, "setPaddingRelative",
            Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
    private static final Method METHOD_getLayoutDirection = CompatUtils.getMethod(
            View.class, "getLayoutDirection");

    private ViewCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getPaddingEnd(final View view) {
        if (METHOD_getPaddingEnd == null) {
            return view.getPaddingRight();
        }
        return (Integer)CompatUtils.invoke(view, 0, METHOD_getPaddingEnd);
    }

    public static void setPaddingRelative(final View view, final int start, final int top,
            final int end, final int bottom) {
        if (METHOD_setPaddingRelative == null) {
            view.setPadding(start, top, end, bottom);
            return;
        }
        CompatUtils.invoke(view, null, METHOD_setPaddingRelative, start, top, end, bottom);
    }

    public static int getLayoutDirection(final View view) {
        if (METHOD_getLayoutDirection == null) {
            return LAYOUT_DIRECTION_LTR;
        }
        return (Integer)CompatUtils.invoke(view, 0, METHOD_getLayoutDirection);
    }
}
