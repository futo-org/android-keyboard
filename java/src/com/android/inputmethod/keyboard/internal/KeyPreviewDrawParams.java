/*
 * Copyright (C) 2012 The Android Open Source Project
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

public final class KeyPreviewDrawParams {
    // The graphical geometry of the key preview.
    // <-width->
    // +-------+   ^
    // |       |   |
    // |preview| height (visible)
    // |       |   |
    // +       + ^ v
    //  \     /  |offset
    // +-\   /-+ v
    // |  +-+  |
    // |parent |
    // |    key|
    // +-------+
    // The background of a {@link TextView} being used for a key preview may have invisible
    // paddings. To align the more keys keyboard panel's visible part with the visible part of
    // the background, we need to record the width and height of key preview that don't include
    // invisible paddings.
    public int mPreviewVisibleWidth;
    public int mPreviewVisibleHeight;
    // The key preview may have an arbitrary offset and its background that may have a bottom
    // padding. To align the more keys keyboard and the key preview we also need to record the
    // offset between the top edge of parent key and the bottom of the visible part of key
    // preview background.
    public int mPreviewVisibleOffset;
}
