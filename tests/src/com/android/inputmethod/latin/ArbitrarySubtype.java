/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.compat.InputMethodSubtypeCompatWrapper;

public class ArbitrarySubtype extends InputMethodSubtypeCompatWrapper {
    final String mLocale;
    final String mExtraValue;

    public ArbitrarySubtype(final String locale, final String extraValue) {
        super(locale);
        mLocale = locale;
        mExtraValue = extraValue;
    }

    public String getLocale() {
        return mLocale;
    }

    public String getExtraValue() {
        return mExtraValue;
    }

    public String getMode() {
        return "keyboard";
    }

    public String getExtraValueOf(final String key) {
        if (LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE.equals(key)) {
            return "";
        } else {
            return null;
        }
    }

    public boolean containsExtraValueKey(final String key) {
        return LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE.equals(key);
    }
}
