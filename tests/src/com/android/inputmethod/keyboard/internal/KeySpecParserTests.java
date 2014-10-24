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

package com.android.inputmethod.keyboard.internal;

import static com.android.inputmethod.keyboard.internal.KeyboardIconsSet.ICON_UNDEFINED;
import static com.android.inputmethod.latin.common.Constants.CODE_UNSPECIFIED;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.common.Constants;

@SmallTest
public final class KeySpecParserTests extends KeySpecParserTestsBase {
    @Override
    protected void assertParser(final String message, final String keySpec,
            final String expectedLabel, final String expectedOutputText, final int expectedIcon,
            final int expectedCode) {
        final String keySpecResolved = mTextsSet.resolveTextReference(keySpec);
        final String actualLabel = KeySpecParser.getLabel(keySpecResolved);
        final String actualOutputText = KeySpecParser.getOutputText(keySpecResolved);
        final int actualIcon = KeySpecParser.getIconId(keySpecResolved);
        final int actualCode = KeySpecParser.getCode(keySpecResolved);
        assertEquals(message + " [label]", expectedLabel, actualLabel);
        assertEquals(message + " [ouptputText]", expectedOutputText, actualOutputText);
        assertEquals(message + " [icon]",
                KeyboardIconsSet.getIconName(expectedIcon),
                KeyboardIconsSet.getIconName(actualIcon));
        assertEquals(message + " [code]",
                Constants.printableCode(expectedCode),
                Constants.printableCode(actualCode));
    }

    // TODO: Remove this method.
    // These should throw {@link KeySpecParserError} when Key.keyLabel attribute become mandatory.
    public void testEmptySpec() {
        assertParser("Null spec", null,
                null, null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParser("Empty spec", "",
                null, null, ICON_UNDEFINED, CODE_UNSPECIFIED);
    }
}
