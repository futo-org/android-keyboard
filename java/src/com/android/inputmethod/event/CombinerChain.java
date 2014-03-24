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

package com.android.inputmethod.event;

import android.text.SpannableStringBuilder;

import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;

/**
 * This class implements the logic chain between receiving events and generating code points.
 *
 * Event sources are multiple. It may be a hardware keyboard, a D-PAD, a software keyboard,
 * or any exotic input source.
 * This class will orchestrate the composing chain that starts with an event as its input. Each
 * composer will be given turns one after the other.
 * The output is composed of two sequences of code points: the first, representing the already
 * finished combining part, will be shown normally as the composing string, while the second is
 * feedback on the composing state and will typically be shown with different styling such as
 * a colored background.
 */
public class CombinerChain {
    // The already combined text, as described above
    private StringBuilder mCombinedText;
    // The feedback on the composing state, as described above
    private SpannableStringBuilder mStateFeedback;
    private final ArrayList<Combiner> mCombiners;

    /**
     * Create an combiner chain.
     *
     * The combiner chain takes events as inputs and outputs code points and combining state.
     * For example, if the input language is Japanese, the combining chain will typically perform
     * kana conversion.
     *
     * @param combinerList A list of combiners to be applied in order.
     */
    public CombinerChain(final Combiner... combinerList) {
        mCombiners = CollectionUtils.newArrayList();
        // The dead key combiner is always active, and always first
        mCombiners.add(new DeadKeyCombiner());
        mCombinedText = new StringBuilder();
        mStateFeedback = new SpannableStringBuilder();
    }

    /**
     * Pass a new event through the whole chain.
     * @param previousEvents the list of previous events in this composition
     * @param newEvent the new event to process
     */
    public void processEvent(final ArrayList<Event> previousEvents, final Event newEvent) {
        final ArrayList<Event> modifiablePreviousEvents = new ArrayList<Event>(previousEvents);
        Event event = newEvent;
        for (final Combiner combiner : mCombiners) {
            // A combiner can never return more than one event; it can return several
            // code points, but they should be encapsulated within one event.
            event = combiner.processEvent(modifiablePreviousEvents, event);
            if (null == event) {
                // Combiners return null if they eat the event.
                break;
            }
        }
        if (null != event) {
            mCombinedText.append(event.getTextToCommit());
        }
        mStateFeedback.clear();
        for (int i = mCombiners.size() - 1; i >= 0; --i) {
            mStateFeedback.append(mCombiners.get(i).getCombiningStateFeedback());
        }
    }

    /**
     * Get the char sequence that should be displayed as the composing word. It may include
     * styling spans.
     */
    public CharSequence getComposingWordWithCombiningFeedback() {
        final SpannableStringBuilder s = new SpannableStringBuilder(mCombinedText);
        return s.append(mStateFeedback);
    }
}
