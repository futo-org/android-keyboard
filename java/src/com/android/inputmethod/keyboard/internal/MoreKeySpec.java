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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.StringUtils;

import java.util.Locale;

public class MoreKeySpec {
    public final int mCode;
    public final String mLabel;
    public final String mOutputText;
    public final int mIconId;

    public MoreKeySpec(final String moreKeySpec, boolean needsToUpperCase, final Locale locale,
            final KeyboardCodesSet codesSet) {
        mLabel = KeySpecParser.toUpperCaseOfStringForLocale(
                KeySpecParser.getLabel(moreKeySpec), needsToUpperCase, locale);
        final int code = KeySpecParser.toUpperCaseOfCodeForLocale(
                KeySpecParser.getCode(moreKeySpec, codesSet), needsToUpperCase, locale);
        if (code == Keyboard.CODE_UNSPECIFIED) {
            // Some letter, for example German Eszett (U+00DF: "ß"), has multiple characters
            // upper case representation ("SS").
            mCode = Keyboard.CODE_OUTPUT_TEXT;
            mOutputText = mLabel;
        } else {
            mCode = code;
            mOutputText = KeySpecParser.toUpperCaseOfStringForLocale(
                    KeySpecParser.getOutputText(moreKeySpec), needsToUpperCase, locale);
        }
        mIconId = KeySpecParser.getIconId(moreKeySpec);
    }

    @Override
    public String toString() {
        final String label = (mIconId == KeyboardIconsSet.ICON_UNDEFINED ? mLabel
                : KeySpecParser.PREFIX_ICON + KeyboardIconsSet.getIconName(mIconId));
        final String output = (mCode == Keyboard.CODE_OUTPUT_TEXT ? mOutputText
                : Keyboard.printableCode(mCode));
        if (StringUtils.codePointCount(label) == 1 && label.codePointAt(0) == mCode) {
            return output;
        } else {
            return label + "|" + output;
        }
    }
}
