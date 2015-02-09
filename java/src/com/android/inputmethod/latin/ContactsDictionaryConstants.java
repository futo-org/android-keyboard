/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;

/**
 * Constants related to Contacts Content Provider.
 */
public class ContactsDictionaryConstants {
    /**
     * Projections for {@link Contacts.CONTENT_URI}
     */
    public static final String[] PROJECTION = { BaseColumns._ID, Contacts.DISPLAY_NAME };
    public static final String[] PROJECTION_ID_ONLY = { BaseColumns._ID };

    /**
     * Frequency for contacts information into the dictionary
     */
    public static final int FREQUENCY_FOR_CONTACTS = 40;
    public static final int FREQUENCY_FOR_CONTACTS_BIGRAM = 90;

    /**
     *  The maximum number of contacts that this dictionary supports.
     */
    public static final int MAX_CONTACT_COUNT = 10000;

    /**
     * Index of the column for 'name' in content providers:
     * Contacts & ContactsContract.Profile.
     */
    public static final int NAME_INDEX = 1;
}
