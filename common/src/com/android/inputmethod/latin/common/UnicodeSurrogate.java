/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.inputmethod.latin.common;

/**
 * Emojis are supplementary characters expressed as a low+high pair. For instance,
 * the emoji U+1F625 is encoded as "\uD83D\uDE25" in UTF-16, where '\uD83D' is in
 * the range of [0xd800, 0xdbff] and '\uDE25' is in the range of [0xdc00, 0xdfff].
 * {@see http://docs.oracle.com/javase/6/docs/api/java/lang/Character.html#unicode}
 */
public final class UnicodeSurrogate {
    private static final char LOW_SURROGATE_MIN = '\uD800';
    private static final char LOW_SURROGATE_MAX = '\uDBFF';
    private static final char HIGH_SURROGATE_MIN = '\uDC00';
    private static final char HIGH_SURROGATE_MAX = '\uDFFF';

    public static boolean isLowSurrogate(final char c) {
        return c >= LOW_SURROGATE_MIN && c <= LOW_SURROGATE_MAX;
    }

    public static boolean isHighSurrogate(final char c) {
        return c >= HIGH_SURROGATE_MIN && c <= HIGH_SURROGATE_MAX;
    }
}
