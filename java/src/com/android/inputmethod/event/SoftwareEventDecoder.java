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

package com.android.inputmethod.event;

/**
 * An event decoder for events out of a software keyboard.
 *
 * This defines the interface for an event decoder that supports events out of a software keyboard.
 * This differs significantly from hardware keyboard event decoders in several respects. First,
 * a software keyboard does not have a scancode/layout system; the keypresses that insert
 * characters output unicode characters directly.
 */
public interface SoftwareEventDecoder extends EventDecoder {
    public Event decodeSoftwareEvent();
}
