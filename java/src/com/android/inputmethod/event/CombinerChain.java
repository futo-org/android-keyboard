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
import android.text.TextUtils;

import com.android.inputmethod.latin.common.Constants;

import java.util.ArrayList;

import javax.annotation.Nonnull;

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
     * kana conversion. This takes a string for initial text, taken to be present before the
     * cursor: we'll start after this.
     *
     * @param initialText The text that has already been combined so far.
     */
    public CombinerChain(final String initialText) {
        mCombiners = new ArrayList<>();
        // The dead key combiner is always active, and always first
        mCombiners.add(new DeadKeyCombiner());
        mCombinedText = new StringBuilder(initialText);
        mStateFeedback = new SpannableStringBuilder();
    }

    public void reset() {
        mCombinedText.setLength(0);
        mStateFeedback.clear();
        for (final Combiner c : mCombiners) {
            c.reset();
        }
    }

    private void updateStateFeedback() {
        mStateFeedback.clear();
        for (int i = mCombiners.size() - 1; i >= 0; --i) {
            mStateFeedback.append(mCombiners.get(i).getCombiningStateFeedback());
        }
    }

    /**
     * Process an event through the combining chain, and return a processed event to apply.
     * @param previousEvents the list of previous events in this composition
     * @param newEvent the new event to process
     * @return the processed event. It may be the same event, or a consumed event, or a completely
     *   new event. However it may never be null.
     */
    @Nonnull
    public Event processEvent(final ArrayList<Event> previousEvents,
            @Nonnull final Event newEvent) {
        final ArrayList<Event> modifiablePreviousEvents = new ArrayList<>(previousEvents);
        Event event = newEvent;
        for (final Combiner combiner : mCombiners) {
            // A combiner can never return more than one event; it can return several
            // code points, but they should be encapsulated within one event.
            event = combiner.processEvent(modifiablePreviousEvents, event);
            if (event.isConsumed()) {
                // If the event is consumed, then we don't pass it to subsequent combiners:
                // they should not see it at all.
                break;
            }
        }
        updateStateFeedback();
        return event;
    }

    /**
     * Apply a processed event.
     * @param event the event to be applied
     */
    public void applyProcessedEvent(final Event event) {
        if (null != event) {
            // TODO: figure out the generic way of doing this
            if (Constants.CODE_DELETE == event.mKeyCode) {
                final int length = mCombinedText.length();
                if (length > 0) {
                    final int lastCodePoint = mCombinedText.codePointBefore(length);
                    mCombinedText.delete(length - Character.charCount(lastCodePoint), length);
                }
            } else {
                final CharSequence textToCommit = event.getTextToCommit();
                if (!TextUtils.isEmpty(textToCommit)) {
                    mCombinedText.append(textToCommit);
                }
            }
        }
        updateStateFeedback();
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
