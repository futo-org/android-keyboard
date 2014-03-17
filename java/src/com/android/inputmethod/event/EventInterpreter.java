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

    private final EventDecoderSpec mDecoderSpec;
    private final ArrayList<Combiner> mCombiners;

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
     */
    public EventInterpreter(final EventDecoderSpec specification) {
        mDecoderSpec = null != specification ? specification : new EventDecoderSpec();
        mCombiners = CollectionUtils.newArrayList();
        mCombiners.add(new DeadKeyCombiner());
    }
}
