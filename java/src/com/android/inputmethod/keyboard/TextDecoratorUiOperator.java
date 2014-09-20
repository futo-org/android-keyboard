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

package com.android.inputmethod.keyboard;

import android.graphics.Matrix;
import android.graphics.RectF;

/**
 * This interface defines how UI operations required for {@link TextDecorator} are delegated to
 * the actual UI implementation class.
 */
public interface TextDecoratorUiOperator {
    /**
     * Called to notify that the UI is ready to be disposed.
     */
    void disposeUi();

    /**
     * Called when the UI should become invisible.
     */
    void hideUi();

    /**
     * Called to set the new click handler.
     * @param onClickListener the callback object whose {@link Runnable#run()} should be called when
     * the indicator is clicked.
     */
    void setOnClickListener(final Runnable onClickListener);

    /**
     * Called when the layout should be updated.
     * @param matrix The matrix that transforms the local coordinates into the screen coordinates.
     * @param composingTextBounds The bounding box of the composing text, in local coordinates.
     * @param useRtlLayout {@code true} if the indicator should be optimized for RTL layout.
     */
    void layoutUi(final Matrix matrix, final RectF composingTextBounds, final boolean useRtlLayout);
}
