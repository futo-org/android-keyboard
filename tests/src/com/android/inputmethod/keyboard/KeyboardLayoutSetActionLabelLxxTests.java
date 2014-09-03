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

import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

@MediumTest
public class KeyboardLayoutSetActionLabelLxxTests extends KeyboardLayoutSetActionLabelBase {
    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_LXX_LIGHT;
    }

    @Override
    public void testActionGo() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "go " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_GO,
                    KeyboardIconsSet.NAME_GO_KEY);
        }
    }

    @Override
    public void testActionSend() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "send " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_SEND,
                    KeyboardIconsSet.NAME_SEND_KEY);
        }
    }

    @Override
    public void testActionNext() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "next " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_NEXT,
                    KeyboardIconsSet.NAME_NEXT_KEY);
        }
    }

    @Override
    public void testActionDone() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "done " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_DONE,
                    KeyboardIconsSet.NAME_DONE_KEY);
        }
    }

    @Override
    public void testActionPrevious() {
        for (final InputMethodSubtype subtype : getAllSubtypesList()) {
            final String tag = "previous " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            doTestActionKeyIcon(tag, subtype, EditorInfo.IME_ACTION_PREVIOUS,
                    KeyboardIconsSet.NAME_PREVIOUS_KEY);
        }
    }
}
