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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;

/**
 * Class representing dictionary header.
 */
public final class DictionaryHeader {
    public final int mBodyOffset;
    public final DictionaryOptions mDictionaryOptions;
    public final FormatOptions mFormatOptions;

    // Note that these are corresponding definitions in native code in latinime::HeaderPolicy
    // and latinime::HeaderReadWriteUtils.
    // TODO: Standardize the key names and bump up the format version, taking care not to
    // break format version 2 dictionaries.
    public static final String DICTIONARY_VERSION_KEY = "version";
    public static final String DICTIONARY_LOCALE_KEY = "locale";
    public static final String DICTIONARY_ID_KEY = "dictionary";
    public static final String DICTIONARY_DESCRIPTION_KEY = "description";
    public static final String DICTIONARY_DATE_KEY = "date";
    public static final String HAS_HISTORICAL_INFO_KEY = "HAS_HISTORICAL_INFO";
    public static final String USES_FORGETTING_CURVE_KEY = "USES_FORGETTING_CURVE";
    public static final String FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP_KEY =
            "FORGETTING_CURVE_OCCURRENCES_TO_LEVEL_UP";
    public static final String FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID_KEY =
            "FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID";
    public static final String FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS_KEY =
            "FORGETTING_CURVE_DURATION_TO_LEVEL_DOWN_IN_SECONDS";
    public static final String MAX_UNIGRAM_COUNT_KEY = "MAX_UNIGRAM_COUNT";
    public static final String MAX_BIGRAM_COUNT_KEY = "MAX_BIGRAM_COUNT";
    public static final String ATTRIBUTE_VALUE_TRUE = "1";

    public DictionaryHeader(final int headerSize, final DictionaryOptions dictionaryOptions,
            final FormatOptions formatOptions) throws UnsupportedFormatException {
        mDictionaryOptions = dictionaryOptions;
        mFormatOptions = formatOptions;
        mBodyOffset = formatOptions.mVersion < FormatSpec.VERSION4 ? headerSize : 0;
        if (null == getLocaleString()) {
            throw new UnsupportedFormatException("Cannot create a FileHeader without a locale");
        }
        if (null == getVersion()) {
            throw new UnsupportedFormatException(
                    "Cannot create a FileHeader without a version");
        }
        if (null == getId()) {
            throw new UnsupportedFormatException("Cannot create a FileHeader without an ID");
        }
    }

    // Helper method to get the locale as a String
    public String getLocaleString() {
        return mDictionaryOptions.mAttributes.get(DICTIONARY_LOCALE_KEY);
    }

    // Helper method to get the version String
    public String getVersion() {
        return mDictionaryOptions.mAttributes.get(DICTIONARY_VERSION_KEY);
    }

    // Helper method to get the dictionary ID as a String
    public String getId() {
        return mDictionaryOptions.mAttributes.get(DICTIONARY_ID_KEY);
    }

    // Helper method to get the description
    public String getDescription() {
        // TODO: Right now each dictionary file comes with a description in its own language.
        // It will display as is no matter the device's locale. It should be internationalized.
        return mDictionaryOptions.mAttributes.get(DICTIONARY_DESCRIPTION_KEY);
    }
}