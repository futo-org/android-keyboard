/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.android.inputmethod.latin.R;

/**
 * A view that handles buttons inside it according to a status.
 */
public class ButtonSwitcher extends FrameLayout {
    // Animation directions
    public static final int ANIMATION_IN = 1;
    public static final int ANIMATION_OUT = 2;

    public ButtonSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonSwitcher(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setText(final CharSequence text) {
        ((Button)findViewById(R.id.dict_install_button)).setText(text);
    }

    public void setInternalButtonVisiblility(final int visibility) {
        findViewById(R.id.dict_install_button).setVisibility(visibility);
    }

    public void setInternalOnClickListener(final OnClickListener listener) {
        findViewById(R.id.dict_install_button).setOnClickListener(listener);
    }

    public void animateButton(final int direction) {
        final View button = findViewById(R.id.dict_install_button);
        final float outerX = getWidth();
        final float innerX = button.getX() - button.getTranslationX();
        if (View.INVISIBLE == button.getVisibility()) {
            button.setTranslationX(outerX - innerX);
            button.setVisibility(View.VISIBLE);
        }
        if (ANIMATION_IN == direction) {
            button.animate().translationX(0);
        } else {
            button.animate().translationX(outerX - innerX);
        }
    }
}
