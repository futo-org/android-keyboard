/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context;

import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;

// TODO: We should remove this class
public class LatinKeyboard extends Keyboard {
    private LatinKeyboard(KeyboardParams params) {
        super(params);
    }

    public static class Builder extends KeyboardBuilder<KeyboardParams> {
        public Builder(Context context) {
            super(context, new KeyboardParams());
        }

        @Override
        public Builder load(int xmlId, KeyboardId id) {
            super.load(xmlId, id);
            return this;
        }

        @Override
        public LatinKeyboard build() {
            return new LatinKeyboard(mParams);
        }
    }

    @Override
    public Key[] getNearestKeys(int x, int y) {
        // Avoid dead pixels at edges of the keyboard
        return super.getNearestKeys(Math.max(0, Math.min(x, mOccupiedWidth - 1)),
                Math.max(0, Math.min(y, mOccupiedHeight - 1)));
    }
}
