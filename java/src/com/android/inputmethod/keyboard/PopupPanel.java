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

import android.widget.PopupWindow;

public interface PopupPanel extends PointerTracker.KeyEventHandler {
    /**
     * Show popup panel.
     * @param parentKeyboardView the parent KeyboardView that has the parent key.
     * @param parentKey the parent key that is the source of this popup panel
     * @param tracker the pointer tracker that pressesd the parent key
     * @param window PopupWindow to be used to show this popup panel
     */
    public void showPopupPanel(LatinKeyboardBaseView parentKeyboardView, Key parentKey,
            PointerTracker tracker, PopupWindow window);

    /**
     * Translate X-coordinate of touch event to the local X-coordinate of this PopupPanel.
     * @param x the global X-coordinate
     * @return the local X-coordinate to this PopupPanel
     */
    public int translateX(int x);

    /**
     * Translate Y-coordinate of touch event to the local Y-coordinate of this PopupPanel.
     * @param y the global Y-coordinate
     * @return the local Y-coordinate to this PopupPanel
     */
    public int translateY(int y);
}
