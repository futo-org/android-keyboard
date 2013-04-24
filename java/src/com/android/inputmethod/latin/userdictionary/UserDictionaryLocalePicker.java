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

package com.android.inputmethod.latin.userdictionary;

import android.app.Fragment;

import java.util.Locale;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryLocalePicker.java
// in order to deal with some devices that have issues with the user dictionary handling

public class UserDictionaryLocalePicker extends Fragment {
    public UserDictionaryLocalePicker() {
        super();
        // TODO: implement
    }

    public interface LocationChangedListener {
        public void onLocaleSelected(Locale locale);
    }
}
