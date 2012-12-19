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

import android.view.KeyEvent;

/**
 * This class implements the logic between receiving events and generating code points.
 *
 * Event sources are multiple. It may be a hardware keyboard, a D-PAD, a software keyboard,
 * or any exotic input source.
 * This class will orchestrate the decoding chain that starts with an event and ends up with
 * a stream of code points + decoding state.
 */
public class EventInterpreter {
    // TODO: Implement an object pool for events, as we'll create a lot of them
    // TODO: Create a combiner
    // TODO: Create an object type to represent input material + visual feedback + decoding state
    // TODO: Create an interface to call back to Latin IME through the above object

    // TODO: replace this with an associative container to bind device id -> decoder
    HardwareEventDecoder mHardwareEventDecoder;
    SoftwareEventDecoder mSoftwareEventDecoder;

    public EventInterpreter() {
        this(null);
    }

    public EventInterpreter(final EventDecoderSpec specification) {
        // TODO: create the decoding chain from a specification. The decoders should be
        // created lazily
        mHardwareEventDecoder = new HardwareKeyboardEventDecoder(0);
    }

    // Helper method to decode a hardware key event into a generic event, and execute any
    // necessary action.
    public boolean onHardwareKeyEvent(final KeyEvent hardwareKeyEvent) {
        final Event decodedEvent = getHardwareKeyEventDecoder(hardwareKeyEvent.getDeviceId())
                .decodeHardwareKey(hardwareKeyEvent);
        return onEvent(decodedEvent);
    }

    public boolean onSoftwareEvent() {
        final Event decodedEvent = getSoftwareEventDecoder().decodeSoftwareEvent();
        return onEvent(decodedEvent);
    }

    private HardwareEventDecoder getHardwareKeyEventDecoder(final int deviceId) {
        // TODO: look up the decoder by device id. It should be created lazily
        return mHardwareEventDecoder;
    }

    private SoftwareEventDecoder getSoftwareEventDecoder() {
        return mSoftwareEventDecoder;
    }

    private boolean onEvent(final Event event) {
        // TODO: Classify the event - input or non-input (see design doc)
        // TODO: IF action event
        //          Send decoded action back to LatinIME
        //       ELSE
        //          Send input event to the combiner
        //          Get back new input material + visual feedback + combiner state
        //          Route the event to Latin IME
        //       ENDIF
        return false;
    }
}
