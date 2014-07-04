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

package com.android.inputmethod.compat;

import android.os.Build;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Locale;

@SmallTest
public class LocaleSpanCompatUtilsTests extends AndroidTestCase {
    public void testInstantiatable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // LocaleSpan isn't yet available.
            return;
        }
        assertTrue(LocaleSpanCompatUtils.isLocaleSpanAvailable());
        final Object japaneseLocaleSpan = LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE);
        assertNotNull(japaneseLocaleSpan);
        assertEquals(Locale.JAPANESE,
                LocaleSpanCompatUtils.getLocaleFromLocaleSpan(japaneseLocaleSpan));
    }
}
