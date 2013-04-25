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

import android.content.Context;
import android.provider.UserDictionary.Words;

import java.lang.reflect.Method;
import java.util.Locale;

public final class UserDictionaryCompatUtils {
    // UserDictionary.Words#addWord(Context, String, int, String, Locale) was introduced
    // in API level 16 (Build.VERSION_CODES.JELLY_BEAN).
    private static final Method METHOD_addWord = CompatUtils.getMethod(Words.class, "addWord",
            Context.class, String.class, Integer.TYPE, String.class, Locale.class);

    @SuppressWarnings("deprecation")
    public static void addWord(final Context context, final String word, final int freq,
            final String shortcut, final Locale locale) {
        if (hasNewerAddWord()) {
            CompatUtils.invoke(Words.class, null, METHOD_addWord, context, word, freq, shortcut,
                    locale);
        } else {
            // Fall back to the pre-JellyBean method.
            final int localeType;
            if (null == locale) {
                localeType = Words.LOCALE_TYPE_ALL;
            } else {
                final Locale currentLocale = context.getResources().getConfiguration().locale;
                if (locale.equals(currentLocale)) {
                    localeType = Words.LOCALE_TYPE_CURRENT;
                } else {
                    localeType = Words.LOCALE_TYPE_ALL;
                }
            }
            Words.addWord(context, word, freq, localeType);
        }
    }

    public static final boolean hasNewerAddWord() {
        return null != METHOD_addWord;
    }
}
