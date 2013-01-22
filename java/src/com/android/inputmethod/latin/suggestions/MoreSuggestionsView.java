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

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.util.AttributeSet;

import com.android.inputmethod.keyboard.MoreKeysKeyboardView;
import com.android.inputmethod.latin.R;

/**
 * A view that renders a virtual {@link MoreSuggestions}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public final class MoreSuggestionsView extends MoreKeysKeyboardView {
    public MoreSuggestionsView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.moreSuggestionsViewStyle);
    }

    public MoreSuggestionsView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected int getDefaultCoordX() {
        final MoreSuggestions pane = (MoreSuggestions)getKeyboard();
        return pane.mOccupiedWidth / 2;
    }

    public void updateKeyboardGeometry(final int keyHeight) {
        mKeyDrawParams.updateParams(keyHeight, mKeyVisualAttributes);
    }

    @Override
    public void onCodeInput(final int code, final int x, final int y) {
        final int index = code - MoreSuggestions.SUGGESTION_CODE_BASE;
        if (index >= 0 && index < SuggestionStripView.MAX_SUGGESTIONS) {
            mListener.onCustomRequest(index);
        }
    }
}
