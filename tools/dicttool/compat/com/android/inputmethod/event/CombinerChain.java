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

import java.util.ArrayList;

/**
 * Compatibility class that stands in for the combiner chain in LatinIME.
 *
 * This is not used by dicttool, it's just needed by the dependency chain.
 */
// TODO: there should not be a dependency to this in dicttool, so there
// should be a sensible way to separate them cleanly.
public class CombinerChain {
    private StringBuilder mComposingWord;
    public CombinerChain(final String initialText, final Combiner... combinerList) {
        mComposingWord = new StringBuilder(initialText);
    }

    public Event processEvent(final ArrayList<Event> previousEvents, final Event newEvent) {
        return newEvent;
    }

    public void applyProcessedEvent(final Event event) {
        mComposingWord.append(event.getTextToCommit());
    }

    public CharSequence getComposingWordWithCombiningFeedback() {
        return mComposingWord;
    }

    public void reset() {
        mComposingWord.setLength(0);
    }

    public static Combiner[] createCombiners(final String spec) {
        // Dicttool never uses a combiner at all, so we just return a zero-sized array.
        return new Combiner[0];
    }
}
