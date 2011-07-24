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

import android.content.Context;

public class MiniKeyboard extends Keyboard {
    private int mDefaultKeyCoordX;

    public MiniKeyboard(Context context, int xmlLayoutResId, Keyboard parentKeyboard) {
        super(context, xmlLayoutResId, parentKeyboard.mId.cloneAsMiniKeyboard(),
                parentKeyboard.getMinWidth());
        // HACK: Current mini keyboard design totally relies on the 9-patch padding about horizontal
        // and vertical key spacing. To keep the visual of mini keyboard as is, these hacks are
        // needed to keep having the same horizontal and vertical key spacing.
        setHorizontalGap(0);
        setVerticalGap(parentKeyboard.getVerticalGap() / 2);

        // TODO: When we have correctly padded key background 9-patch drawables for mini keyboard,
        // revert the above hacks and uncomment the following lines.
        //setHorizontalGap(parentKeyboard.getHorizontalGap());
        //setVerticalGap(parentKeyboard.getVerticalGap());

        setRtlKeyboard(parentKeyboard.isRtlKeyboard());
    }

    public void setDefaultCoordX(int pos) {
        mDefaultKeyCoordX = pos;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }
}
