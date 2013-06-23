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

import android.util.SparseArray;
import android.view.KeyEvent;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;

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

    final EventDecoderSpec mDecoderSpec;
    final SparseArray<HardwareEventDecoder> mHardwareEventDecoders;
    final SoftwareEventDecoder mSoftwareEventDecoder;
    final LatinIME mLatinIme;
    final ArrayList<Combiner> mCombiners;

    /**
     * Create a default interpreter.
     *
     * This creates a default interpreter that does nothing. A default interpreter should normally
     * only be used for fallback purposes, when we really don't know what we want to do with input.
     *
     * @param latinIme a reference to the ime.
     */
    public EventInterpreter(final LatinIME latinIme) {
        this(null, latinIme);
    }

    /**
     * Create an event interpreter according to a specification.
     *
     * The specification contains information about what to do with events. Typically, it will
     * contain information about the type of keyboards - for example, if hardware keyboard(s) is/are
     * attached, their type will be included here so that the decoder knows what to do with each
     * keypress (a 10-key keyboard is not handled like a qwerty-ish keyboard).
     * It also contains information for combining characters. For example, if the input language
     * is Japanese, the specification will typically request kana conversion.
     * Also note that the specification can be null. This means that we need to create a default
     * interpreter that does no specific combining, and assumes the most common cases.
     *
     * @param specification the specification for event interpretation. null for default.
     * @param latinIme a reference to the ime.
     */
    public EventInterpreter(final EventDecoderSpec specification, final LatinIME latinIme) {
        mDecoderSpec = null != specification ? specification : new EventDecoderSpec();
        // For both, we expect to have only one decoder in almost all cases, hence the default
        // capacity of 1.
        mHardwareEventDecoders = new SparseArray<HardwareEventDecoder>(1);
        mSoftwareEventDecoder = new SoftwareKeyboardEventDecoder();
        mCombiners = CollectionUtils.newArrayList();
        mCombiners.add(new DeadKeyCombiner());
        mLatinIme = latinIme;
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
        final HardwareEventDecoder decoder = mHardwareEventDecoders.get(deviceId);
        if (null != decoder) return decoder;
        // TODO: create the decoder according to the specification
        final HardwareEventDecoder newDecoder = new HardwareKeyboardEventDecoder(deviceId);
        mHardwareEventDecoders.put(deviceId, newDecoder);
        return newDecoder;
    }

    private SoftwareEventDecoder getSoftwareEventDecoder() {
        // Within the context of Latin IME, since we never present several software interfaces
        // at the time, we should never need multiple software event decoders at a time.
        return mSoftwareEventDecoder;
    }

    private boolean onEvent(final Event event) {
        Event currentlyProcessingEvent = event;
        boolean processed = false;
        for (int i = 0; i < mCombiners.size(); ++i) {
            currentlyProcessingEvent = mCombiners.get(i).combine(event);
        }
        while (null != currentlyProcessingEvent) {
            if (currentlyProcessingEvent.isCommittable()) {
                mLatinIme.onCodeInput(currentlyProcessingEvent.mCodePoint,
                        Constants.EXTERNAL_KEYBOARD_COORDINATE,
                        Constants.EXTERNAL_KEYBOARD_COORDINATE);
                processed = true;
            } else if (event.isDead()) {
                processed = true;
            }
            currentlyProcessingEvent = currentlyProcessingEvent.mNextEvent;
        }
        return processed;
    }
}
