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

package com.android.inputmethod.latin.personalization;

import android.content.Context;

import com.android.inputmethod.annotations.ExternallyReferenced;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;

import java.io.File;
import java.util.Locale;

import javax.annotation.Nullable;

public class ContextualDictionary extends ExpandableBinaryDictionary {
    /* package */ static final String NAME = ContextualDictionary.class.getSimpleName();

    private ContextualDictionary(final Context context, final Locale locale,
            final File dictFile) {
        super(context, getDictName(NAME, locale, dictFile), locale, Dictionary.TYPE_CONTEXTUAL,
                dictFile);
        // Always reset the contents.
        clear();
    }

    // Note: This method is called by {@link DictionaryFacilitator} using Java reflection.
    @SuppressWarnings("unused")
    @ExternallyReferenced
    public static ContextualDictionary getDictionary(final Context context, final Locale locale,
            final File dictFile, final String dictNamePrefix, @Nullable final String account) {
        return new ContextualDictionary(context, locale, dictFile);
    }

    @Override
    public boolean isValidWord(final String word) {
        // Strings out of this dictionary should not be considered existing words.
        return false;
    }

    @Override
    protected void loadInitialContentsLocked() {
    }
}
