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

package com.android.inputmethod.latin;

import android.test.suitebuilder.annotation.LargeTest;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.WordComposer;

@LargeTest
public class ShiftModeTests extends InputTestsBase {

    @Override
    protected EditorInfo enrichEditorInfo(final EditorInfo ei) {
        ei.inputType |= TextUtils.CAP_MODE_SENTENCES;
        ei.initialCapsMode = TextUtils.CAP_MODE_SENTENCES;
        return ei;
    }

    private boolean isCapsModeAutoShifted() {
        return mLatinIME.mKeyboardSwitcher.getKeyboardShiftMode()
                == WordComposer.CAPS_MODE_AUTO_SHIFTED;
    }

    public void testTypicalSentence() {
        assertTrue("Initial auto caps state", isCapsModeAutoShifted());
        type("Test");
        assertFalse("Caps after letter", isCapsModeAutoShifted());
        type(" ");
        assertFalse("Caps after space", isCapsModeAutoShifted());
        type("some,");
        assertFalse("Caps after comma", isCapsModeAutoShifted());
        type(" ");
        assertFalse("Caps after comma space", isCapsModeAutoShifted());
        type("words.");
        assertFalse("Caps directly after period", isCapsModeAutoShifted());
        type(" ");
        assertTrue("Caps after period space", isCapsModeAutoShifted());
    }
}
