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

package com.android.inputmethod.latin.makedict;

/**
 * A not-yet-resolved attribute.
 *
 * An attribute is either a bigram or a shortcut.
 * All instances of this class are always immutable.
 */
public final class PendingAttribute {
    public final int mFrequency;
    public final int mAddress;
    public PendingAttribute(final int frequency, final int address) {
        mFrequency = frequency;
        mAddress = address;
    }
}
