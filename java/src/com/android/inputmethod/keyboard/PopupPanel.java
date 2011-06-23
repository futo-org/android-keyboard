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

import android.view.MotionEvent;
import android.widget.PopupWindow;

public interface PopupPanel {
    /**
     * Show popup panel.
     * @param parentKeyboardView the parent KeyboardView that has the parent key.
     * @param parentKey the parent key that is the source of this popup panel
     * @param tracker the pointer tracker that pressesd the parent key
     * @param keyPreviewY the Y-coordinate of key preview
     * @param window PopupWindow to be used to show this popup panel
     */
    // TODO: Remove keyPreviewY from argument.
    public void showPanel(KeyboardView parentKeyboardView, Key parentKey,
            PointerTracker tracker, int keyPreviewY, PopupWindow window);

    /**
     * Check if the pointer is in siding key input mode.
     * @return true if the pointer is sliding key input mode.
     */
    public boolean isInSlidingKeyInput();

    /**
     * The motion event handler.
     * @param me the MotionEvent to be processed.
     * @return true if the motion event is processed and should be consumed.
     */
    public boolean onTouchEvent(MotionEvent me);
}
