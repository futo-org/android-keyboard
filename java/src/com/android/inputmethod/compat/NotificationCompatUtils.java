/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Notification;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NotificationCompatUtils {
    // Note that TextInfo.getCharSequence() is supposed to be available in API level 21 and later.
    private static final Method METHOD_setColor =
            CompatUtils.getMethod(Notification.Builder.class, "setColor", int.class);
    private static final Method METHOD_setVisibility =
            CompatUtils.getMethod(Notification.Builder.class, "setVisibility", int.class);
    private static final Method METHOD_setCategory =
            CompatUtils.getMethod(Notification.Builder.class, "setCategory", String.class);
    private static final Method METHOD_setPriority =
            CompatUtils.getMethod(Notification.Builder.class, "setPriority", int.class);
    private static final Method METHOD_build =
            CompatUtils.getMethod(Notification.Builder.class, "build");
    private static final Field FIELD_VISIBILITY_SECRET =
            CompatUtils.getField(Notification.class, "VISIBILITY_SECRET");
    private static final int VISIBILITY_SECRET = null == FIELD_VISIBILITY_SECRET ? 0
            : (Integer) CompatUtils.getFieldValue(null /* receiver */, null /* defaultValue */,
                    FIELD_VISIBILITY_SECRET);
    private static final Field FIELD_CATEGORY_RECOMMENDATION =
            CompatUtils.getField(Notification.class, "CATEGORY_RECOMMENDATION");
    private static final String CATEGORY_RECOMMENDATION = null == FIELD_CATEGORY_RECOMMENDATION ? ""
            : (String) CompatUtils.getFieldValue(null /* receiver */, null /* defaultValue */,
                    FIELD_CATEGORY_RECOMMENDATION);
    private static final Field FIELD_PRIORITY_LOW =
            CompatUtils.getField(Notification.class, "PRIORITY_LOW");
    private static final int PRIORITY_LOW = null == FIELD_PRIORITY_LOW ? 0
            : (Integer) CompatUtils.getFieldValue(null /* receiver */, null /* defaultValue */,
                    FIELD_PRIORITY_LOW);

    private NotificationCompatUtils() {
        // This class is non-instantiable.
    }

    // Sets the accent color
    public static void setColor(final Notification.Builder builder, final int color) {
        CompatUtils.invoke(builder, null, METHOD_setColor, color);
    }

    public static void setVisibilityToSecret(final Notification.Builder builder) {
        CompatUtils.invoke(builder, null, METHOD_setVisibility, VISIBILITY_SECRET);
    }

    public static void setCategoryToRecommendation(final Notification.Builder builder) {
        CompatUtils.invoke(builder, null, METHOD_setCategory, CATEGORY_RECOMMENDATION);
    }

    public static void setPriorityToLow(final Notification.Builder builder) {
        CompatUtils.invoke(builder, null, METHOD_setPriority, PRIORITY_LOW);
    }

    @SuppressWarnings("deprecation")
    public static Notification build(final Notification.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // #build was added in API level 16, JELLY_BEAN
            return (Notification) CompatUtils.invoke(builder, null, METHOD_build);
        }
        // #getNotification was deprecated in API level 16, JELLY_BEAN
        return builder.getNotification();
    }
}
