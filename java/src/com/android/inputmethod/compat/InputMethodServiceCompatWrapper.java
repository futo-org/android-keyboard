/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.inputmethod.latin.SubtypeSwitcher;

import android.inputmethodservice.InputMethodService;
import android.view.View;
// import android.view.inputmethod.InputMethodSubtype;
import android.widget.HorizontalScrollView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InputMethodServiceCompatWrapper extends InputMethodService {
    private static final Method METHOD_HorizontalScrollView_setOverScrollMode =
            CompatUtils.getMethod(HorizontalScrollView.class, "setOverScrollMode", int.class);
    private static final Field FIELD_View_OVER_SCROLL_NEVER =
            CompatUtils.getField(View.class, "OVER_SCROLL_NEVER");
    private static final Integer View_OVER_SCROLL_NEVER =
            (Integer)CompatUtils.getFieldValue(null, null, FIELD_View_OVER_SCROLL_NEVER);
    // CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED needs to be false if the API level is 10
    // or previous. Note that InputMethodSubtype was added in the API level 11.
    // For the API level 11 or later, LatinIME should override onCurrentInputMethodSubtypeChanged().
    // For the API level 10 or previous, we handle the "subtype changed" events by ourselves
    // without having support from framework -- onCurrentInputMethodSubtypeChanged().
    private static final boolean CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED = false;

    private InputMethodManagerCompatWrapper mImm;

    @Override
    public void onCreate() {
        super.onCreate();
        mImm = InputMethodManagerCompatWrapper.getInstance(this);
    }

    // When the API level is 10 or previous, notifyOnCurrentInputMethodSubtypeChanged should
    // handle the event the current subtype was changed. LatinIME calls
    // notifyOnCurrentInputMethodSubtypeChanged every time LatinIME
    // changes the current subtype.
    // This call is required to let LatinIME itself know a subtype changed
    // event when the API level is 10 or previous.
    @SuppressWarnings("unused")
    public void notifyOnCurrentInputMethodSubtypeChanged(InputMethodSubtypeCompatWrapper subtype) {
        // Do nothing when the API level is 11 or later
        if (CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) return;
        if (subtype == null) {
            subtype = mImm.getCurrentInputMethodSubtype();
        }
        if (subtype != null) {
            SubtypeSwitcher.getInstance().updateSubtype(subtype);
        }
    }

    protected static void setOverScrollModeNever(HorizontalScrollView scrollView) {
        if (View_OVER_SCROLL_NEVER != null) {
            CompatUtils.invoke(scrollView, null, METHOD_HorizontalScrollView_setOverScrollMode,
                    View_OVER_SCROLL_NEVER);
        }
    }

    //////////////////////////////////////
    // Functions using API v11 or later //
    //////////////////////////////////////
    /*@Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        // Do nothing when the API level is 10 or previous
        if (!CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) return;
        SubtypeSwitcher.getInstance().updateSubtype(
                new InputMethodSubtypeCompatWrapper(subtype));
    }*/

    protected static void setTouchableRegionCompat(InputMethodService.Insets outInsets,
            int x, int y, int width, int height) {
        //outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        //outInsets.touchableRegion.set(x, y, width, height);
    }
}
