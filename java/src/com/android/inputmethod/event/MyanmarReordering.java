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

import com.android.inputmethod.latin.Constants;

import java.util.ArrayList;
import java.util.Arrays;

import javax.annotation.Nonnull;

/**
 * A combiner that reorders input for Myanmar.
 */
public class MyanmarReordering implements Combiner {
    // U+1031 MYANMAR VOWEL SIGN E
    private final static int VOWEL_E = 0x1031; // Code point for vowel E that we need to reorder
    // U+200C ZERO WIDTH NON-JOINER
    // U+200B ZERO WIDTH SPACE
    private final static int ZERO_WIDTH_NON_JOINER = 0x200B; // should be 0x200C

    private final ArrayList<Event> mCurrentEvents = new ArrayList<>();

    // List of consonants :
    // U+1000 MYANMAR LETTER KA
    // U+1001 MYANMAR LETTER KHA
    // U+1002 MYANMAR LETTER GA
    // U+1003 MYANMAR LETTER GHA
    // U+1004 MYANMAR LETTER NGA
    // U+1005 MYANMAR LETTER CA
    // U+1006 MYANMAR LETTER CHA
    // U+1007 MYANMAR LETTER JA
    // U+1008 MYANMAR LETTER JHA
    // U+1009 MYANMAR LETTER NYA
    // U+100A MYANMAR LETTER NNYA
    // U+100B MYANMAR LETTER TTA
    // U+100C MYANMAR LETTER TTHA
    // U+100D MYANMAR LETTER DDA
    // U+100E MYANMAR LETTER DDHA
    // U+100F MYANMAR LETTER NNA
    // U+1010 MYANMAR LETTER TA
    // U+1011 MYANMAR LETTER THA
    // U+1012 MYANMAR LETTER DA
    // U+1013 MYANMAR LETTER DHA
    // U+1014 MYANMAR LETTER NA
    // U+1015 MYANMAR LETTER PA
    // U+1016 MYANMAR LETTER PHA
    // U+1017 MYANMAR LETTER BA
    // U+1018 MYANMAR LETTER BHA
    // U+1019 MYANMAR LETTER MA
    // U+101A MYANMAR LETTER YA
    // U+101B MYANMAR LETTER RA
    // U+101C MYANMAR LETTER LA
    // U+101D MYANMAR LETTER WA
    // U+101E MYANMAR LETTER SA
    // U+101F MYANMAR LETTER HA
    // U+1020 MYANMAR LETTER LLA
    // U+103F MYANMAR LETTER GREAT SA
    private static boolean isConsonant(final int codePoint) {
        return (codePoint >= 0x1000 && codePoint <= 0x1020) || 0x103F == codePoint;
    }

    // List of medials :
    // U+103B MYANMAR CONSONANT SIGN MEDIAL YA
    // U+103C MYANMAR CONSONANT SIGN MEDIAL RA
    // U+103D MYANMAR CONSONANT SIGN MEDIAL WA
    // U+103E MYANMAR CONSONANT SIGN MEDIAL HA
    // U+105E MYANMAR CONSONANT SIGN MON MEDIAL NA
    // U+105F MYANMAR CONSONANT SIGN MON MEDIAL MA
    // U+1060 MYANMAR CONSONANT SIGN MON MEDIAL LA
    // U+1082 MYANMAR CONSONANT SIGN SHAN MEDIAL WA
    private static int[] MEDIAL_LIST = { 0x103B, 0x103C, 0x103D, 0x103E,
            0x105E, 0x105F, 0x1060, 0x1082};
    private static boolean isMedial(final int codePoint) {
        return Arrays.binarySearch(MEDIAL_LIST, codePoint) >= 0;
    }

    private static boolean isConsonantOrMedial(final int codePoint) {
        return isConsonant(codePoint) || isMedial(codePoint);
    }

    private Event getLastEvent() {
        final int size = mCurrentEvents.size();
        if (size <= 0) {
            return null;
        }
        return mCurrentEvents.get(size - 1);
    }

    private CharSequence getCharSequence() {
        final StringBuilder s = new StringBuilder();
        for (final Event e : mCurrentEvents) {
            s.appendCodePoint(e.mCodePoint);
        }
        return s;
    }

    /**
     * Clears the currently combining stream of events and returns the resulting software text
     * event corresponding to the stream. Optionally adds a new event to the cleared stream.
     * @param newEvent the new event to add to the stream. null if none.
     * @return the resulting software text event. Never null.
     */
    private Event clearAndGetResultingEvent(final Event newEvent) {
        final CharSequence combinedText;
        if (mCurrentEvents.size() > 0) {
            combinedText = getCharSequence();
            mCurrentEvents.clear();
        } else {
            combinedText = null;
        }
        if (null != newEvent) {
            mCurrentEvents.add(newEvent);
        }
        return null == combinedText ? Event.createConsumedEvent(newEvent)
                : Event.createSoftwareTextEvent(combinedText, Event.NOT_A_KEY_CODE);
    }

    @Override
    @Nonnull
    public Event processEvent(ArrayList<Event> previousEvents, Event newEvent) {
        final int codePoint = newEvent.mCodePoint;
        if (VOWEL_E == codePoint) {
            final Event lastEvent = getLastEvent();
            if (null == lastEvent) {
                mCurrentEvents.add(newEvent);
                return Event.createConsumedEvent(newEvent);
            } else if (isConsonantOrMedial(lastEvent.mCodePoint)) {
                final Event resultingEvent = clearAndGetResultingEvent(null);
                mCurrentEvents.add(Event.createSoftwareKeypressEvent(ZERO_WIDTH_NON_JOINER,
                        Event.NOT_A_KEY_CODE,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                        false /* isKeyRepeat */));
                mCurrentEvents.add(newEvent);
                return resultingEvent;
            } else { // VOWEL_E == lastCodePoint. But if that was anything else this is correct too.
                return clearAndGetResultingEvent(newEvent);
            }
        } if (isConsonant(codePoint)) {
            final Event lastEvent = getLastEvent();
            if (null == lastEvent) {
                mCurrentEvents.add(newEvent);
                return Event.createConsumedEvent(newEvent);
            } else if (VOWEL_E == lastEvent.mCodePoint) {
                final int eventSize = mCurrentEvents.size();
                if (eventSize >= 2
                        && mCurrentEvents.get(eventSize - 2).mCodePoint == ZERO_WIDTH_NON_JOINER) {
                    // We have a ZWJN before a vowel E. We need to remove the ZWNJ and then
                    // reorder the vowel with respect to the consonant.
                    mCurrentEvents.remove(eventSize - 1);
                    mCurrentEvents.remove(eventSize - 2);
                    mCurrentEvents.add(newEvent);
                    mCurrentEvents.add(lastEvent);
                    return Event.createConsumedEvent(newEvent);
                }
                // If there is already a consonant, then we are starting a new syllable.
                for (int i = eventSize - 2; i >= 0; --i) {
                    if (isConsonant(mCurrentEvents.get(i).mCodePoint)) {
                        return clearAndGetResultingEvent(newEvent);
                    }
                }
                // If we come here, we didn't have a consonant so we reorder
                mCurrentEvents.remove(eventSize - 1);
                mCurrentEvents.add(newEvent);
                mCurrentEvents.add(lastEvent);
                return Event.createConsumedEvent(newEvent);
            } else { // lastCodePoint is a consonant/medial. But if it's something else it's fine
                return clearAndGetResultingEvent(newEvent);
            }
        } else if (isMedial(codePoint)) {
            final Event lastEvent = getLastEvent();
            if (null == lastEvent) {
                mCurrentEvents.add(newEvent);
                return Event.createConsumedEvent(newEvent);
            } else if (VOWEL_E == lastEvent.mCodePoint) {
                final int eventSize = mCurrentEvents.size();
                // If there is already a consonant, then we are in the middle of a syllable, and we
                // need to reorder.
                boolean hasConsonant = false;
                for (int i = eventSize - 2; i >= 0; --i) {
                    if (isConsonant(mCurrentEvents.get(i).mCodePoint)) {
                        hasConsonant = true;
                        break;
                    }
                }
                if (hasConsonant) {
                    mCurrentEvents.remove(eventSize - 1);
                    mCurrentEvents.add(newEvent);
                    mCurrentEvents.add(lastEvent);
                    return Event.createConsumedEvent(newEvent);
                }
                // Otherwise, we just commit everything.
                return clearAndGetResultingEvent(null);
            } else { // lastCodePoint is a consonant/medial. But if it's something else it's fine
                return clearAndGetResultingEvent(newEvent);
            }
        } else if (Constants.CODE_DELETE == newEvent.mKeyCode) {
            final Event lastEvent = getLastEvent();
            final int eventSize = mCurrentEvents.size();
            if (null != lastEvent) {
                if (VOWEL_E == lastEvent.mCodePoint) {
                    // We have a VOWEL_E at the end. There are four cases.
                    // - The vowel is the only code point in the buffer. Remove it.
                    // - The vowel is preceded by a ZWNJ. Remove both vowel E and ZWNJ.
                    // - The vowel is preceded by a consonant/medial, remove the consonant/medial.
                    // - In all other cases, it's strange, so just remove the last code point.
                    if (eventSize <= 1) {
                        mCurrentEvents.clear();
                    } else { // eventSize >= 2
                        final int previousCodePoint = mCurrentEvents.get(eventSize - 2).mCodePoint;
                        if (previousCodePoint == ZERO_WIDTH_NON_JOINER) {
                            mCurrentEvents.remove(eventSize - 1);
                            mCurrentEvents.remove(eventSize - 2);
                        } else if (isConsonantOrMedial(previousCodePoint)) {
                            mCurrentEvents.remove(eventSize - 2);
                        } else {
                            mCurrentEvents.remove(eventSize - 1);
                        }
                    }
                    return Event.createConsumedEvent(newEvent);
                } else if (eventSize > 0) {
                    mCurrentEvents.remove(eventSize - 1);
                    return Event.createConsumedEvent(newEvent);
                }
            }
        }
        // This character is not part of the combining scheme, so we should reset everything.
        if (mCurrentEvents.size() > 0) {
            // If we have events in flight, then add the new event and return the resulting event.
            mCurrentEvents.add(newEvent);
            return clearAndGetResultingEvent(null);
        } else {
            // If we don't have any events in flight, then just pass this one through.
            return newEvent;
        }
    }

    @Override
    public CharSequence getCombiningStateFeedback() {
        return getCharSequence();
    }

    @Override
    public void reset() {
        mCurrentEvents.clear();
    }
}
