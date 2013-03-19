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

package com.android.inputmethod.dictionarypack;

import android.content.ContentValues;

/**
 * Struct class to encapsulate a client ID with content values about a download.
 */
public class DownloadRecord {
    public final String mClientId;
    // Only word lists have attributes, and the ContentValues should contain the same
    // keys as they do for all MetadataDbHelper functions. Since only word lists have
    // attributes, a null pointer here means this record represents metadata.
    public final ContentValues mAttributes;
    public DownloadRecord(final String clientId, final ContentValues attributes) {
        mClientId = clientId;
        mAttributes = attributes;
    }
    public boolean isMetadata() {
        return null == mAttributes;
    }
}