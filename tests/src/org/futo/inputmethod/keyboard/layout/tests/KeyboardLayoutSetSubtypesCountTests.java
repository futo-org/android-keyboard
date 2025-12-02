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

package org.futo.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import org.futo.inputmethod.keyboard.KeyboardLayoutSetTestsBase;
import org.futo.inputmethod.keyboard.KeyboardTheme;
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;

@SmallTest
public class KeyboardLayoutSetSubtypesCountTests extends KeyboardLayoutSetTestsBase {
    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_LXX_LIGHT;
    }

    private static String toString(final ArrayList<InputMethodSubtype> subtypeList) {
        final StringBuilder sb = new StringBuilder();
        for (int index = 0; index < subtypeList.size(); index++) {
            final InputMethodSubtype subtype = subtypeList.get(index);
            sb.append(index + ": ");
            sb.append(SubtypeLocaleUtils.getSubtypeNameForLogging(subtype));
            sb.append("\n");
        }
        return sb.toString();
    }

    public final void testSubtypeCountExceeds20251201() {
        final ArrayList<InputMethodSubtype> allSubtypesList = getAllSubtypesList();
        assertTrue(toString(allSubtypesList), allSubtypesList.size() >= 707);
    }
}
