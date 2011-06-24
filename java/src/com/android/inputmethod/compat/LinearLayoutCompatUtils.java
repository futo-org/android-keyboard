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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;

import java.lang.reflect.Field;

public class LinearLayoutCompatUtils {
    private static final String TAG = LinearLayoutCompatUtils.class.getSimpleName();

    private static final Class<?> CLASS_R_STYLEABLE = CompatUtils.getClass(
            "com.android.internal.R$styleable");
    private static final Field STYLEABLE_VIEW = CompatUtils.getField(
            CLASS_R_STYLEABLE, "View");
    private static final Field STYLEABLE_VIEW_BACKGROUND = CompatUtils.getField(
            CLASS_R_STYLEABLE, "View_background");
    private static final Object VALUE_STYLEABLE_VIEW = CompatUtils.getFieldValue(
            null, null, STYLEABLE_VIEW);
    private static final Integer VALUE_STYLEABLE_VIEW_BACKGROUND =
            (Integer)CompatUtils.getFieldValue(null, null, STYLEABLE_VIEW_BACKGROUND);

    public static Drawable getBackgroundDrawable(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        if (!(VALUE_STYLEABLE_VIEW instanceof int[]) || VALUE_STYLEABLE_VIEW_BACKGROUND == null) {
            Log.w(TAG, "Can't get View background attribute using reflection");
            return null;
        }

        final int[] styleableView = (int[])VALUE_STYLEABLE_VIEW;
        final TypedArray a = context.obtainStyledAttributes(
                attrs, styleableView, defStyleAttr, defStyleRes);
        final Drawable background = a.getDrawable(VALUE_STYLEABLE_VIEW_BACKGROUND);
        a.recycle();
        return background;
    }
}
