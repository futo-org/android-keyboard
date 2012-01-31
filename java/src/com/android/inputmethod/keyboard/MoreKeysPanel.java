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

package com.android.inputmethod.keyboard;

import android.view.View;
import android.widget.PopupWindow;

public interface MoreKeysPanel extends PointerTracker.KeyEventHandler {
    public interface Controller {
        public boolean dismissMoreKeysPanel();
    }

    /**
     * Show more keys panel.
     *
     * @param parentView the parent view of this more keys panel
     * @param controller the controller that can dismiss this more keys panel
     * @param pointX x coordinate of this more keys panel
     * @param pointY y coordinate of this more keys panel
     * @param window PopupWindow to be used to show this more keys panel
     * @param listener the listener that will receive keyboard action from this more keys panel.
     */
    public void showMoreKeysPanel(View parentView, Controller controller, int pointX, int pointY,
            PopupWindow window, KeyboardActionListener listener);

    /**
     * Translate X-coordinate of touch event to the local X-coordinate of this
     * {@link MoreKeysPanel}.
     *
     * @param x the global X-coordinate
     * @return the local X-coordinate to this {@link MoreKeysPanel}
     */
    public int translateX(int x);

    /**
     * Translate Y-coordinate of touch event to the local Y-coordinate of this
     * {@link MoreKeysPanel}.
     *
     * @param y the global Y-coordinate
     * @return the local Y-coordinate to this {@link MoreKeysPanel}
     */
    public int translateY(int y);
}
