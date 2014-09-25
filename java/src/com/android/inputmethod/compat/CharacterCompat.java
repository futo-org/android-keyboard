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

import java.lang.reflect.Method;

public final class CharacterCompat {
    // Note that Character.isAlphabetic(int), has been introduced in API level 19
    // (Build.VERSION_CODE.KITKAT).
    private static final Method METHOD_isAlphabetic = CompatUtils.getMethod(
            Character.class, "isAlphabetic", int.class);

    private CharacterCompat() {
        // This utility class is not publicly instantiable.
    }

    public static boolean isAlphabetic(final int code) {
        if (METHOD_isAlphabetic != null) {
            return (Boolean)CompatUtils.invoke(null, false, METHOD_isAlphabetic, code);
        }
        switch (Character.getType(code)) {
        case Character.UPPERCASE_LETTER:
        case Character.LOWERCASE_LETTER:
        case Character.TITLECASE_LETTER:
        case Character.MODIFIER_LETTER:
        case Character.OTHER_LETTER:
        case Character.LETTER_NUMBER:
            return true;
        default:
            return false;
        }
    }
}
