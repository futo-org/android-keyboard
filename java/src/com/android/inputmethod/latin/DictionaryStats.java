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

import java.io.File;
import java.util.Locale;

public class DictionaryStats {
    public static final int NOT_AN_ENTRY_COUNT = -1;

    public final Locale mLocale;
    public final String mDictName;
    public final String mDictFilePath;
    public final long mDictFileSize;

    public final int mUnigramCount;
    public final int mNgramCount;
    // TODO: Add more members.

    public DictionaryStats(final Locale locale, final String dictName, final File dictFile,
            final int unigramCount, final int ngramCount) {
        mLocale = locale;
        mDictName = dictName;
        mDictFilePath = dictFile.getAbsolutePath();
        mDictFileSize = dictFile.length();
        mUnigramCount = unigramCount;
        mNgramCount = ngramCount;
    }
}
