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

// TODO: Use {@link android.support.v4.view.ViewCompat} instead of this utility class.
// Currently {@link #getPaddingEnd(View)} and {@link #setPaddingRelative(View,int,int,int,int)}
// are missing from android-support-v4 static library in KitKat SDK.
public final class ViewCompatUtils {
    // Note that View.getPaddingEnd(), View.setPaddingRelative(int,int,int,int) have been
    // introduced in API level 17 (Build.VERSION_CODE.JELLY_BEAN_MR1).
    private static final Method METHOD_getPaddingEnd = CompatUtils.getMethod(
            View.class, "getPaddingEnd");
    private static final Method METHOD_setPaddingRelative = CompatUtils.getMethod(
            View.class, "setPaddingRelative",
            int.class, int.class, int.class, int.class);
    // Note that View.setTextAlignment(int) has been introduced in API level 17.
    private static final Method METHOD_setTextAlignment = CompatUtils.getMethod(
            View.class, "setTextAlignment", int.class);

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

    // These TEXT_ALIGNMENT_* constants have been introduced in API 17.
    public static final int TEXT_ALIGNMENT_INHERIT = 0;
    public static final int TEXT_ALIGNMENT_GRAVITY = 1;
    public static final int TEXT_ALIGNMENT_TEXT_START = 2;
    public static final int TEXT_ALIGNMENT_TEXT_END = 3;
    public static final int TEXT_ALIGNMENT_CENTER = 4;
    public static final int TEXT_ALIGNMENT_VIEW_START = 5;
    public static final int TEXT_ALIGNMENT_VIEW_END = 6;

    public static void setTextAlignment(final View view, final int textAlignment) {
        CompatUtils.invoke(view, null, METHOD_setTextAlignment, textAlignment);
    }
}
