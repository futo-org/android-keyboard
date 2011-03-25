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

package com.android.inputmethod.deprecated.compat;

import com.android.common.userhappiness.UserHappinessSignals;
import com.android.inputmethod.compat.CompatUtils;

import java.lang.reflect.Method;

public class VoiceInputLoggerCompatUtils {
    public static final String EXTRA_TEXT_REPLACED_LENGTH = "length";
    public static final String EXTRA_BEFORE_N_BEST_CHOOSE = "before";
    public static final String EXTRA_AFTER_N_BEST_CHOOSE = "after";
    private static final Method METHOD_UserHappinessSignals_setHasVoiceLoggingInfo =
            CompatUtils.getMethod(UserHappinessSignals.class, "setHasVoiceLoggingInfo",
                    boolean.class);

    public static void setHasVoiceLoggingInfoCompat(boolean hasLoggingInfo) {
        CompatUtils.invoke(null, null, METHOD_UserHappinessSignals_setHasVoiceLoggingInfo,
                hasLoggingInfo);
    }
}
