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

package com.android.inputmethod.latin.utils;

import android.view.inputmethod.InputMethodSubtype;

public final class SpacebarLanguageUtils {
    private SpacebarLanguageUtils() {
        // Intentional empty constructor for utility class.
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  Middle      Full
    // ------ ------- - --------- ----------------------
    //  en_US qwerty  F  English   English (US)           exception
    //  en_GB qwerty  F  English   English (UK)           exception
    //  es_US spanish F  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Français  Français
    //  fr_CA qwerty  F  Français  Français (Canada)
    //  fr_CH swiss   F  Français  Français (Suisse)
    //  de    qwertz  F  Deutsch   Deutsch
    //  de_CH swiss   T  Deutsch   Deutsch (Schweiz)
    //  zz    qwerty  F  QWERTY    QWERTY
    //  fr    qwertz  T  Français  Français
    //  de    qwerty  T  Deutsch   Deutsch
    //  en_US azerty  T  English   English (US)
    //  zz    azerty  T  AZERTY    AZERTY
    // Get InputMethodSubtype's full display name in its locale.
    public static String getFullDisplayName(final InputMethodSubtype subtype) {
        if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype);
        }
        return SubtypeLocaleUtils.getSubtypeLocaleDisplayName(subtype.getLocale());
    }

    // Get InputMethodSubtype's middle display name in its locale.
    public static String getMiddleDisplayName(final InputMethodSubtype subtype) {
        if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype);
        }
        return SubtypeLocaleUtils.getSubtypeLanguageDisplayName(subtype.getLocale());
    }
}
