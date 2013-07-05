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

package com.android.inputmethod.latin.utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public final class ViewLayoutUtils {
    private ViewLayoutUtils() {
        // This utility class is not publicly instantiable.
    }

    public static MarginLayoutParams newLayoutParam(final ViewGroup placer, final int width,
            final int height) {
        if (placer instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(width, height);
        } else if (placer instanceof RelativeLayout) {
            return new RelativeLayout.LayoutParams(width, height);
        } else if (placer == null) {
            throw new NullPointerException("placer is null");
        } else {
            throw new IllegalArgumentException("placer is neither FrameLayout nor RelativeLayout: "
                    + placer.getClass().getName());
        }
    }

    public static void placeViewAt(final View view, final int x, final int y, final int w,
            final int h) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof MarginLayoutParams) {
            final MarginLayoutParams marginLayoutParams = (MarginLayoutParams)lp;
            marginLayoutParams.width = w;
            marginLayoutParams.height = h;
            marginLayoutParams.setMargins(x, y, 0, 0);
        }
    }
}
