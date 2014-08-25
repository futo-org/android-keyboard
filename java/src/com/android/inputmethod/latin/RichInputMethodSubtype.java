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

package com.android.inputmethod.latin;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Enrichment class for InputMethodSubtype to enable concurrent multi-lingual input.
 *
 * Right now, this returns the extra value of its primary subtype.
 */
public final class RichInputMethodSubtype {
    private final InputMethodSubtype mSubtype;
    private final Locale[] mLocales;

    public RichInputMethodSubtype(final InputMethodSubtype subtype, final Locale... locales) {
        mSubtype = subtype;
        mLocales = new Locale[locales.length+1];
        mLocales[0] = LocaleUtils.constructLocaleFromString(mSubtype.getLocale());
        System.arraycopy(locales, 0, mLocales, 1, locales.length);
    }

    // Extra values are determined by the primary subtype. This is probably right, but
    // we may have to revisit this later.
    public String getExtraValueOf(final String key) {
        return mSubtype.getExtraValueOf(key);
    }

    // The mode is also determined by the primary subtype.
    public String getMode() {
        return mSubtype.getMode();
    }

    public boolean isNoLanguage() {
        if (mLocales.length > 1) {
            return false;
        }
        return SubtypeLocaleUtils.NO_LANGUAGE.equals(mSubtype.getLocale());
    }

    public String getNameForLogging() {
        return toString();
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
    // Get the RichInputMethodSubtype's full display name in its locale.
    public String getFullDisplayName() {
        if (isNoLanguage()) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(mSubtype);
        }
        return SubtypeLocaleUtils.getSubtypeLocaleDisplayName(mSubtype.getLocale());
    }

    // Get the RichInputMethodSubtype's middle display name in its locale.
    public String getMiddleDisplayName() {
        if (isNoLanguage()) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(mSubtype);
        }
        return SubtypeLocaleUtils.getSubtypeLanguageDisplayName(mSubtype.getLocale());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RichInputMethodSubtype)) {
            return false;
        }
        final RichInputMethodSubtype other = (RichInputMethodSubtype)o;
        return mSubtype.equals(other.mSubtype) && Arrays.equals(mLocales, other.mLocales);
    }

    @Override
    public int hashCode() {
        return mSubtype.hashCode() + Arrays.hashCode(mLocales);
    }

    @Override
    public String toString() {
        return "Multi-lingual subtype: " + mSubtype.toString() + ", " + Arrays.toString(mLocales);
    }

    // TODO: remove this method! We can always have several locales. Multi-lingual input will only
    // be done when this method is gone.
    public String getLocale() {
        return mSubtype.getLocale();
    }

    // TODO: remove this method
    public InputMethodSubtype getRawSubtype() { return mSubtype; }
}
