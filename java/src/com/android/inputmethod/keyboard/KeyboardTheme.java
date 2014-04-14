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

package com.android.inputmethod.keyboard;

import com.android.inputmethod.latin.R;

public final class KeyboardTheme {
    public static final int THEME_INDEX_ICS = 0;
    public static final int THEME_INDEX_GB = 1;
    public static final int THEME_INDEX_KLP = 2;
    public static final int DEFAULT_THEME_INDEX = THEME_INDEX_KLP;

    public static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_INDEX_ICS, R.style.KeyboardTheme_ICS),
        new KeyboardTheme(THEME_INDEX_GB, R.style.KeyboardTheme_GB),
        new KeyboardTheme(THEME_INDEX_KLP, R.style.KeyboardTheme_KLP),
    };

    public final int mThemeId;
    public final int mStyleId;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/style.xml.
    public KeyboardTheme(final int themeId, final int styleId) {
        mThemeId = themeId;
        mStyleId = styleId;
    }
}
