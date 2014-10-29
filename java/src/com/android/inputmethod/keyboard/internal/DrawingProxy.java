/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.PointerTracker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface DrawingProxy {
    // TODO: Remove this method.
    public void invalidateKey(@Nullable Key key);

    // TODO: Rename this method to onKeyPressed.
    public void showKeyPreview(@Nonnull Key key);

    // TODO: Rename this method to onKeyReleased.
    public void dismissKeyPreview(@Nonnull Key key);

    /**
     * Dismiss a key preview visual without delay.
     * @param key the key whose preview visual should be dismissed.
     */
    public void dismissKeyPreviewWithoutDelay(@Nonnull Key key);

    // TODO: Rename this method to onKeyLongPressed.
    public void onLongPress(@Nonnull PointerTracker tracker);

    /**
     * Start a while-typing-animation.
     * @param fadeInOrOut {@link #FADE_IN} starts while-typing-fade-in animation.
     * {@link #FADE_OUT} starts while-typing-fade-out animation.
     */
    public void startWhileTypingAnimation(int fadeInOrOut);
    public static final int FADE_IN = 0;
    public static final int FADE_OUT = 1;

    /**
     * Show sliding-key input preview.
     * @param tracker the {@link PointerTracker} that is currently doing the sliding-key input.
     * null to dismiss the sliding-key input preview.
     */
    public void showSlidingKeyInputPreview(@Nullable PointerTracker tracker);

    /**
     * Show gesture trails.
     * @param tracker the {@link PointerTracker} whose gesture trail will be shown.
     * @param showsFloatingPreviewText when true, a gesture floating preview text will be shown
     * with this <code>tracker</code>'s trail.
     */
    public void showGestureTrail(@Nonnull PointerTracker tracker, boolean showsFloatingPreviewText);

    /**
     * Dismiss a gesture floating preview text without delay.
     */
    public void dismissGestureFloatingPreviewTextWithoutDelay();
}
