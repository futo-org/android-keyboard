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
     * Authority for the ContentProvider protocol.
     */
    // TODO: find some way to factorize this string with the one in the resources
    public static final String AUTHORITY = "com.android.inputmethod.dictionarypack.aosp";

    /**
     * The action of the intent for publishing that new dictionary data is available.
     */
    // TODO: make this different across different packages. A suggested course of action is
    // to use the package name inside this string.
    public static final String NEW_DICTIONARY_INTENT_ACTION =
            "com.android.inputmethod.dictionarypack.newdict";
}
