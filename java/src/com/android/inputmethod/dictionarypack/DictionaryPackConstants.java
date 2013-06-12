/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.dictionarypack;

/**
 * A class to group constants for dictionary pack usage.
 *
 * This class only defines constants. It should not make any references to outside code as far as
 * possible, as it's used to separate cleanly the keyboard code from the dictionary pack code; this
 * is needed in particular to cleanly compile regression tests.
 */
public class DictionaryPackConstants {
    /**
     * The root domain for the dictionary pack, upon which authorities and actions will append
     * their own distinctive strings.
     */
    private static final String DICTIONARY_DOMAIN = "com.android.inputmethod.dictionarypack.aosp";

    /**
     * Authority for the ContentProvider protocol.
     */
    // TODO: find some way to factorize this string with the one in the resources
    public static final String AUTHORITY = DICTIONARY_DOMAIN;

    /**
     * The action of the intent for publishing that new dictionary data is available.
     */
    // TODO: make this different across different packages. A suggested course of action is
    // to use the package name inside this string.
    // NOTE: The appended string should be uppercase like all other actions, but it's not for
    // historical reasons.
    public static final String NEW_DICTIONARY_INTENT_ACTION = DICTIONARY_DOMAIN + ".newdict";

    /**
     * The action of the intent sent by the dictionary pack to ask for a client to make
     * itself known. This is used when the settings activity is brought up for a client the
     * dictionary pack does not know about.
     */
    public static final String UNKNOWN_DICTIONARY_PROVIDER_CLIENT = DICTIONARY_DOMAIN
            + ".UNKNOWN_CLIENT";

    // In the above intents, the name of the string extra that contains the name of the client
    // we want information about.
    public static final String DICTIONARY_PROVIDER_CLIENT_EXTRA = "client";

    /**
     * The action of the intent to tell the dictionary provider to update now.
     */
    public static final String UPDATE_NOW_INTENT_ACTION = DICTIONARY_DOMAIN
            + ".UPDATE_NOW";
}
