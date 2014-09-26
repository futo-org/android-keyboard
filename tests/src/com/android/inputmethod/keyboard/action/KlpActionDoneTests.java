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

package com.android.inputmethod.keyboard.action;

import android.test.suitebuilder.annotation.LargeTest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

@LargeTest
public class KlpActionDoneTests extends KlpActionTestsBase {
    public void testActionDone() {
        for (final InputMethodSubtype subtype : mSubtypesWhoseNameIsDisplayedInItsLocale) {
            final String tag = "done " + SubtypeLocaleUtils.getSubtypeNameForLogging(subtype);
            final ExpectedActionKey expectedKey = ExpectedActionKey.newLabelKey(
                    R.string.label_done_key, getLabelLocale(subtype), getContext());
            doTestActionKey(tag, subtype, EditorInfo.IME_ACTION_DONE, expectedKey);
        }
    }
}
