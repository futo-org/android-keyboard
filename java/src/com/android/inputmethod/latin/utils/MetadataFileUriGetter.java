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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.R;

import android.content.Context;

/**
 * Helper class to get the metadata URI and the additional ID.
 */
@SuppressWarnings("unused")
public class MetadataFileUriGetter {
    private MetadataFileUriGetter() {
        // This helper class is not instantiable.
    }

    public static String getMetadataUri(final Context context) {
        return context.getString(R.string.dictionary_pack_metadata_uri);
    }

    public static String getMetadataAdditionalId(final Context context) {
        return "";
    }
}
