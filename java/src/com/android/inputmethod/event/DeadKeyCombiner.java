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
import android.util.SparseIntArray;

import com.android.inputmethod.latin.common.Constants;

import java.text.Normalizer;
import java.util.ArrayList;

import javax.annotation.Nonnull;

/**
 * A combiner that handles dead keys.
 */
public class DeadKeyCombiner implements Combiner {

    private static class Data {
        // This class data taken from KeyCharacterMap.java.

        /* Characters used to display placeholders for dead keys. */
        private static final int ACCENT_ACUTE = '\u00B4';
        private static final int ACCENT_BREVE = '\u02D8';
        private static final int ACCENT_CARON = '\u02C7';
        private static final int ACCENT_CEDILLA = '\u00B8';
        private static final int ACCENT_CIRCUMFLEX = '\u02C6';
        private static final int ACCENT_COMMA_ABOVE = '\u1FBD';
        private static final int ACCENT_COMMA_ABOVE_RIGHT = '\u02BC';
        private static final int ACCENT_DOT_ABOVE = '\u02D9';
        private static final int ACCENT_DOT_BELOW = Constants.CODE_PERIOD; // approximate
        private static final int ACCENT_DOUBLE_ACUTE = '\u02DD';
        private static final int ACCENT_GRAVE = '\u02CB';
        private static final int ACCENT_HOOK_ABOVE = '\u02C0';
        private static final int ACCENT_HORN = Constants.CODE_SINGLE_QUOTE; // approximate
        private static final int ACCENT_MACRON = '\u00AF';
        private static final int ACCENT_MACRON_BELOW = '\u02CD';
        private static final int ACCENT_OGONEK = '\u02DB';
        private static final int ACCENT_REVERSED_COMMA_ABOVE = '\u02BD';
        private static final int ACCENT_RING_ABOVE = '\u02DA';
        private static final int ACCENT_STROKE = Constants.CODE_DASH; // approximate
        private static final int ACCENT_TILDE = '\u02DC';
        private static final int ACCENT_TURNED_COMMA_ABOVE = '\u02BB';
        private static final int ACCENT_UMLAUT = '\u00A8';
        private static final int ACCENT_VERTICAL_LINE_ABOVE = '\u02C8';
        private static final int ACCENT_VERTICAL_LINE_BELOW = '\u02CC';

        /* Legacy dead key display characters used in previous versions of the API (before L)
         * We still support these characters by mapping them to their non-legacy version. */
        private static final int ACCENT_GRAVE_LEGACY = Constants.CODE_GRAVE_ACCENT;
        private static final int ACCENT_CIRCUMFLEX_LEGACY = Constants.CODE_CIRCUMFLEX_ACCENT;
        private static final int ACCENT_TILDE_LEGACY = Constants.CODE_TILDE;

        /**
         * Maps Unicode combining diacritical to display-form dead key.
         */
        static final SparseIntArray sCombiningToAccent = new SparseIntArray();
        static final SparseIntArray sAccentToCombining = new SparseIntArray();
        static {
            // U+0300: COMBINING GRAVE ACCENT
            addCombining('\u0300', ACCENT_GRAVE);
            // U+0301: COMBINING ACUTE ACCENT
            addCombining('\u0301', ACCENT_ACUTE);
            // U+0302: COMBINING CIRCUMFLEX ACCENT
            addCombining('\u0302', ACCENT_CIRCUMFLEX);
            // U+0303: COMBINING TILDE
            addCombining('\u0303', ACCENT_TILDE);
            // U+0304: COMBINING MACRON
            addCombining('\u0304', ACCENT_MACRON);
            // U+0306: COMBINING BREVE
            addCombining('\u0306', ACCENT_BREVE);
            // U+0307: COMBINING DOT ABOVE
            addCombining('\u0307', ACCENT_DOT_ABOVE);
            // U+0308: COMBINING DIAERESIS
            addCombining('\u0308', ACCENT_UMLAUT);
            // U+0309: COMBINING HOOK ABOVE
            addCombining('\u0309', ACCENT_HOOK_ABOVE);
            // U+030A: COMBINING RING ABOVE
            addCombining('\u030A', ACCENT_RING_ABOVE);
            // U+030B: COMBINING DOUBLE ACUTE ACCENT
            addCombining('\u030B', ACCENT_DOUBLE_ACUTE);
            // U+030C: COMBINING CARON
            addCombining('\u030C', ACCENT_CARON);
            // U+030D: COMBINING VERTICAL LINE ABOVE
            addCombining('\u030D', ACCENT_VERTICAL_LINE_ABOVE);
            // U+030E: COMBINING DOUBLE VERTICAL LINE ABOVE
            //addCombining('\u030E', ACCENT_DOUBLE_VERTICAL_LINE_ABOVE);
            // U+030F: COMBINING DOUBLE GRAVE ACCENT
            //addCombining('\u030F', ACCENT_DOUBLE_GRAVE);
            // U+0310: COMBINING CANDRABINDU
            //addCombining('\u0310', ACCENT_CANDRABINDU);
            // U+0311: COMBINING INVERTED BREVE
            //addCombining('\u0311', ACCENT_INVERTED_BREVE);
            // U+0312: COMBINING TURNED COMMA ABOVE
            addCombining('\u0312', ACCENT_TURNED_COMMA_ABOVE);
            // U+0313: COMBINING COMMA ABOVE
            addCombining('\u0313', ACCENT_COMMA_ABOVE);
            // U+0314: COMBINING REVERSED COMMA ABOVE
            addCombining('\u0314', ACCENT_REVERSED_COMMA_ABOVE);
            // U+0315: COMBINING COMMA ABOVE RIGHT
            addCombining('\u0315', ACCENT_COMMA_ABOVE_RIGHT);
            // U+031B: COMBINING HORN
            addCombining('\u031B', ACCENT_HORN);
            // U+0323: COMBINING DOT BELOW
            addCombining('\u0323', ACCENT_DOT_BELOW);
            // U+0326: COMBINING COMMA BELOW
            //addCombining('\u0326', ACCENT_COMMA_BELOW);
            // U+0327: COMBINING CEDILLA
            addCombining('\u0327', ACCENT_CEDILLA);
            // U+0328: COMBINING OGONEK
            addCombining('\u0328', ACCENT_OGONEK);
            // U+0329: COMBINING VERTICAL LINE BELOW
            addCombining('\u0329', ACCENT_VERTICAL_LINE_BELOW);
            // U+0331: COMBINING MACRON BELOW
            addCombining('\u0331', ACCENT_MACRON_BELOW);
            // U+0335: COMBINING SHORT STROKE OVERLAY
            addCombining('\u0335', ACCENT_STROKE);
            // U+0342: COMBINING GREEK PERISPOMENI
            //addCombining('\u0342', ACCENT_PERISPOMENI);
            // U+0344: COMBINING GREEK DIALYTIKA TONOS
            //addCombining('\u0344', ACCENT_DIALYTIKA_TONOS);
            // U+0345: COMBINING GREEK YPOGEGRAMMENI
            //addCombining('\u0345', ACCENT_YPOGEGRAMMENI);

            // One-way mappings to equivalent preferred accents.
            // U+0340: COMBINING GRAVE TONE MARK
            sCombiningToAccent.append('\u0340', ACCENT_GRAVE);
            // U+0341: COMBINING ACUTE TONE MARK
            sCombiningToAccent.append('\u0341', ACCENT_ACUTE);
            // U+0343: COMBINING GREEK KORONIS
            sCombiningToAccent.append('\u0343', ACCENT_COMMA_ABOVE);

            // One-way legacy mappings to preserve compatibility with older applications.
            // U+0300: COMBINING GRAVE ACCENT
            sAccentToCombining.append(ACCENT_GRAVE_LEGACY, '\u0300');
            // U+0302: COMBINING CIRCUMFLEX ACCENT
            sAccentToCombining.append(ACCENT_CIRCUMFLEX_LEGACY, '\u0302');
            // U+0303: COMBINING TILDE
            sAccentToCombining.append(ACCENT_TILDE_LEGACY, '\u0303');
        }

        private static void addCombining(int combining, int accent) {
            sCombiningToAccent.append(combining, accent);
            sAccentToCombining.append(accent, combining);
        }

        // Caution! This may only contain chars, not supplementary code points. It's unlikely
        // it will ever need to, but if it does we'll have to change this
        private static final SparseIntArray sNonstandardDeadCombinations = new SparseIntArray();
        static {
            // Non-standard decompositions.
            // Stroke modifier for Finnish multilingual keyboard and others.
            // U+0110: LATIN CAPITAL LETTER D WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'D', '\u0110');
            // U+01E4: LATIN CAPITAL LETTER G WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'G', '\u01e4');
            // U+0126: LATIN CAPITAL LETTER H WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'H', '\u0126');
            // U+0197: LATIN CAPITAL LETTER I WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'I', '\u0197');
            // U+0141: LATIN CAPITAL LETTER L WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'L', '\u0141');
            // U+00D8: LATIN CAPITAL LETTER O WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'O', '\u00d8');
            // U+0166: LATIN CAPITAL LETTER T WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'T', '\u0166');
            // U+0111: LATIN SMALL LETTER D WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'd', '\u0111');
            // U+01E5: LATIN SMALL LETTER G WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'g', '\u01e5');
            // U+0127: LATIN SMALL LETTER H WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'h', '\u0127');
            // U+0268: LATIN SMALL LETTER I WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'i', '\u0268');
            // U+0142: LATIN SMALL LETTER L WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'l', '\u0142');
            // U+00F8: LATIN SMALL LETTER O WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'o', '\u00f8');
            // U+0167: LATIN SMALL LETTER T WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 't', '\u0167');
        }

        private static void addNonStandardDeadCombination(final int deadCodePoint,
                final int spacingCodePoint, final int result) {
            final int combination = (deadCodePoint << 16) | spacingCodePoint;
            sNonstandardDeadCombinations.put(combination, result);
        }

        public static final int NOT_A_CHAR = 0;
        public static final int BITS_TO_SHIFT_DEAD_CODE_POINT_FOR_NON_STANDARD_COMBINATION = 16;
        // Get a non-standard combination
        public static char getNonstandardCombination(final int deadCodePoint,
                final int spacingCodePoint) {
            final int combination = spacingCodePoint |
                    (deadCodePoint << BITS_TO_SHIFT_DEAD_CODE_POINT_FOR_NON_STANDARD_COMBINATION);
            return (char)sNonstandardDeadCombinations.get(combination, NOT_A_CHAR);
        }
    }

    // TODO: make this a list of events instead
    final StringBuilder mDeadSequence = new StringBuilder();

    @Nonnull
    private static Event createEventChainFromSequence(final @Nonnull CharSequence text,
            @Nonnull final Event originalEvent) {
        int index = text.length();
        if (index <= 0) {
            return originalEvent;
        }
        Event lastEvent = null;
        do {
            final int codePoint = Character.codePointBefore(text, index);
            lastEvent = Event.createHardwareKeypressEvent(codePoint,
                    originalEvent.mKeyCode, lastEvent, false /* isKeyRepeat */);
            index -= Character.charCount(codePoint);
        } while (index > 0);
        return lastEvent;
    }

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
        }
        if (Character.isWhitespace(event.mCodePoint)
                || event.mCodePoint == mDeadSequence.codePointBefore(mDeadSequence.length())) {
            // When whitespace or twice the same dead key, we should output the dead sequence as is.
            final Event resultEvent = createEventChainFromSequence(mDeadSequence.toString(),
                    event);
            mDeadSequence.setLength(0);
            return resultEvent;
        }
        if (event.isFunctionalKeyEvent()) {
            if (Constants.CODE_DELETE == event.mKeyCode) {
                // Remove the last code point
                final int trimIndex = mDeadSequence.length() - Character.charCount(
                        mDeadSequence.codePointBefore(mDeadSequence.length()));
                mDeadSequence.setLength(trimIndex);
                return Event.createConsumedEvent(event);
            }
            return event;
        }
        if (event.isDead()) {
            mDeadSequence.appendCodePoint(event.mCodePoint);
            return Event.createConsumedEvent(event);
        }
        // Combine normally.
        final StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(event.mCodePoint);
        int codePointIndex = 0;
        while (codePointIndex < mDeadSequence.length()) {
            final int deadCodePoint = mDeadSequence.codePointAt(codePointIndex);
            final char replacementSpacingChar =
                    Data.getNonstandardCombination(deadCodePoint, event.mCodePoint);
            if (Data.NOT_A_CHAR != replacementSpacingChar) {
                sb.setCharAt(0, replacementSpacingChar);
            } else {
                final int combining = Data.sAccentToCombining.get(deadCodePoint);
                sb.appendCodePoint(0 == combining ? deadCodePoint : combining);
            }
            codePointIndex += Character.isSupplementaryCodePoint(deadCodePoint) ? 2 : 1;
        }
        final String normalizedString = Normalizer.normalize(sb, Normalizer.Form.NFC);
        final Event resultEvent = createEventChainFromSequence(normalizedString, event);
        mDeadSequence.setLength(0);
        return resultEvent;
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
