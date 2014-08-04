/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.text.TextUtils;
import android.view.KeyCharacterMap;

import com.android.inputmethod.latin.Constants;

import java.util.ArrayList;

import javax.annotation.Nonnull;

/**
 * A combiner that handles dead keys.
 */
public class DeadKeyCombiner implements Combiner {
    // TODO: make this a list of events instead
    final StringBuilder mDeadSequence = new StringBuilder();

    @Override
    @Nonnull
    public Event processEvent(final ArrayList<Event> previousEvents, final Event event) {
        if (TextUtils.isEmpty(mDeadSequence)) {
            // No dead char is currently being tracked: this is the most common case.
            if (event.isDead()) {
                // The event was a dead key. Start tracking it.
                mDeadSequence.appendCodePoint(event.mCodePoint);
                return Event.createConsumedEvent(event);
            }
            // Regular keystroke when not keeping track of a dead key. Simply said, there are
            // no dead keys at all in the current input, so this combiner has nothing to do and
            // simply returns the event as is. The majority of events will go through this path.
            return event;
        } else {
            // TODO: Allow combining for several dead chars rather than only the first one.
            // The framework doesn't know how to do this now.
            final int deadCodePoint = mDeadSequence.codePointAt(0);
            mDeadSequence.setLength(0);
            final int resultingCodePoint =
                    KeyCharacterMap.getDeadChar(deadCodePoint, event.mCodePoint);
            if (0 == resultingCodePoint) {
                // We can't combine both characters. We need to commit the dead key as a separate
                // character, and the next char too unless it's a space (because as a special case,
                // dead key + space should result in only the dead key being committed - that's
                // how dead keys work).
                // If the event is a space, we should commit the dead char alone, but if it's
                // not, we need to commit both.
                // TODO: this is not necessarily triggered by hardware key events, so it's not
                // a good idea to masquerade as one. This should be typed as a software
                // composite event or something.
                return Event.createHardwareKeypressEvent(deadCodePoint, event.mKeyCode,
                        Constants.CODE_SPACE == event.mCodePoint ? null : event /* next */,
                        false /* isKeyRepeat */);
            } else {
                // We could combine the characters.
                return Event.createHardwareKeypressEvent(resultingCodePoint, event.mKeyCode,
                        null /* next */, false /* isKeyRepeat */);
            }
        }
    }

    @Override
    public void reset() {
        mDeadSequence.setLength(0);
    }

    @Override
    public CharSequence getCombiningStateFeedback() {
        return mDeadSequence;
    }
}
