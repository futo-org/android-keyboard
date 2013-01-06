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

import android.view.textservice.SuggestionsInfo;

import java.lang.reflect.Field;

public final class SuggestionsInfoCompatUtils {
    // Note that SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS has been introduced
    // in API level 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1).
    private static final Field FIELD_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS =
            CompatUtils.getField(SuggestionsInfo.class, "RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS");
    private static final Integer OBJ_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS =
            (Integer) CompatUtils.getFieldValue(null /* receiver */, null /* defaultValue */,
                    FIELD_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS);
    private static final int RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS =
            OBJ_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != null
                    ? OBJ_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS : 0;

    private SuggestionsInfoCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Returns the flag value of the attributes of the suggestions that can be obtained by
     * {@link SuggestionsInfo#getSuggestionsAttributes()}: this tells that the text service thinks
     * the result suggestions include highly recommended ones.
     */
    public static int getValueOf_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS() {
        return RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS;
    }
}
