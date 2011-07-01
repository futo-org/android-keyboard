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

package com.android.inputmethod.accessibility;

public interface AccessibleKeyboardActionListener {
    /**
     * Called when the user hovers inside a key. This is sent only when
     * Accessibility is turned on. For keys that repeat, this is only called
     * once.
     *
     * @param primaryCode the code of the key that was hovered over
     */
    public void onHoverEnter(int primaryCode);

    /**
     * Called when the user hovers outside a key. This is sent only when
     * Accessibility is turned on. For keys that repeat, this is only called
     * once.
     *
     * @param primaryCode the code of the key that was hovered over
     */
    public void onHoverExit(int primaryCode);

    /**
     * @param direction the direction of the flick gesture, one of
     *            <ul>
     *              <li>{@link FlickGestureDetector#FLICK_UP}
     *              <li>{@link FlickGestureDetector#FLICK_DOWN}
     *              <li>{@link FlickGestureDetector#FLICK_LEFT}
     *              <li>{@link FlickGestureDetector#FLICK_RIGHT}
     *            </ul>
     */
    public void onFlickGesture(int direction);
}
