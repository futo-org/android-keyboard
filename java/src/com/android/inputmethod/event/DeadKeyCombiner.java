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

/**
 * A combiner that handles dead keys.
 */
public class DeadKeyCombiner implements Combiner {
    final StringBuilder mDeadSequence = new StringBuilder();

    @Override
    public Event combine(final Event event) {
        if (null == event) return null; // Just in case some combiner is broken
        if (TextUtils.isEmpty(mDeadSequence)) {
            if (event.isDead()) {
                mDeadSequence.appendCodePoint(event.mCodePoint);
            }
            return event;
        } else {
            // TODO: Allow combining for several dead chars rather than only the first one.
            // The framework doesn't know how to do this now.
            final int deadCodePoint = mDeadSequence.codePointAt(0);
            mDeadSequence.setLength(0);
            final int resultingCodePoint =
                    KeyCharacterMap.getDeadChar(deadCodePoint, event.mCodePoint);
            if (0 == resultingCodePoint) {
                // We can't combine both characters. We need to commit the dead key as a committable
                // character, and the next char too unless it's a space (because as a special case,
                // dead key + space should result in only the dead key being committed - that's
                // how dead keys work).
                // If the event is a space, we should commit the dead char alone, but if it's
                // not, we need to commit both.
                return Event.createCommittableEvent(deadCodePoint,
                        Constants.CODE_SPACE == event.mCodePoint ? null : event /* next */);
            } else {
                // We could combine the characters.
                return Event.createCommittableEvent(resultingCodePoint, null /* next */);
            }
        }
    }

}
