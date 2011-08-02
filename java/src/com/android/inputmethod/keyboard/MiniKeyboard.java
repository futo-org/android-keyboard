/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import com.android.inputmethod.keyboard.internal.MiniKeyboardBuilder.MiniKeyboardParams;

public class MiniKeyboard extends Keyboard {
    private final int mDefaultKeyCoordX;

    public MiniKeyboard(MiniKeyboardParams params) {
        super(params);
        mDefaultKeyCoordX = params.getDefaultKeyCoordX() + params.mDefaultKeyWidth / 2;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }
}
