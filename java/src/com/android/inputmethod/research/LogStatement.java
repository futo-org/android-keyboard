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

package com.android.inputmethod.research;

/**
 * A template for typed information stored in the logs.
 *
 * A LogStatement contains a name, keys, and flags about whether the {@code Object[] values}
 * associated with the {@code String[] keys} are likely to reveal information about the user.  The
 * actual values are stored separately.
 */
class LogStatement {
    // Constants for particular statements
    public static final String TYPE_POINTER_TRACKER_CALL_LISTENER_ON_CODE_INPUT =
            "PointerTrackerCallListenerOnCodeInput";
    public static final String KEY_CODE = "code";
    public static final String VALUE_RESEARCH = "research";
    public static final String TYPE_LATIN_KEYBOARD_VIEW_ON_LONG_PRESS =
            "LatinKeyboardViewOnLongPress";
    public static final String ACTION = "action";
    public static final String VALUE_DOWN = "DOWN";
    public static final String TYPE_LATIN_KEYBOARD_VIEW_PROCESS_MOTION_EVENTS =
            "LatinKeyboardViewProcessMotionEvents";
    public static final String KEY_LOGGING_RELATED = "loggingRelated";

    // Name specifying the LogStatement type.
    private final String mType;

    // mIsPotentiallyPrivate indicates that event contains potentially private information.  If
    // the word that this event is a part of is determined to be privacy-sensitive, then this
    // event should not be included in the output log.  The system waits to output until the
    // containing word is known.
    private final boolean mIsPotentiallyPrivate;

    // mIsPotentiallyRevealing indicates that this statement may disclose details about other
    // words typed in other LogUnits.  This can happen if the user is not inserting spaces, and
    // data from Suggestions and/or Composing text reveals the entire "megaword".  For example,
    // say the user is typing "for the win", and the system wants to record the bigram "the
    // win".  If the user types "forthe", omitting the space, the system will give "for the" as
    // a suggestion.  If the user accepts the autocorrection, the suggestion for "for the" is
    // included in the log for the word "the", disclosing that the previous word had been "for".
    // For now, we simply do not include this data when logging part of a "megaword".
    private final boolean mIsPotentiallyRevealing;

    // mKeys stores the names that are the attributes in the output json objects
    private final String[] mKeys;
    private static final String[] NULL_KEYS = new String[0];

    LogStatement(final String name, final boolean isPotentiallyPrivate,
            final boolean isPotentiallyRevealing, final String... keys) {
        mType = name;
        mIsPotentiallyPrivate = isPotentiallyPrivate;
        mIsPotentiallyRevealing = isPotentiallyRevealing;
        mKeys = (keys == null) ? NULL_KEYS : keys;
    }

    public String getType() {
        return mType;
    }

    public boolean isPotentiallyPrivate() {
        return mIsPotentiallyPrivate;
    }

    public boolean isPotentiallyRevealing() {
        return mIsPotentiallyRevealing;
    }

    public String[] getKeys() {
        return mKeys;
    }

    /**
     * Utility function to test whether a key-value pair exists in a LogStatement.
     *
     * A LogStatement is really just a template -- it does not contain the values, only the
     * keys.  So the values must be passed in as an argument.
     *
     * @param queryKey the String that is tested by {@code String.equals()} to the keys in the
     * LogStatement
     * @param queryValue an Object that must be {@code Object.equals()} to the key's corresponding
     * value in the {@code values} array
     * @param values the values corresponding to mKeys
     *
     * @returns {@true} if {@code queryKey} exists in the keys for this LogStatement, and {@code
     * queryValue} matches the corresponding value in {@code values}
     *
     * @throws IllegalArgumentException if {@code values.length} is not equal to keys().length()
     */
    public boolean containsKeyValuePair(final String queryKey, final Object queryValue,
            final Object[] values) {
        if (mKeys.length != values.length) {
            throw new IllegalArgumentException("Mismatched number of keys and values.");
        }
        final int length = mKeys.length;
        for (int i = 0; i < length; i++) {
            if (mKeys[i].equals(queryKey) && values[i].equals(queryValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Utility function to set a value in a LogStatement.
     *
     * A LogStatement is really just a template -- it does not contain the values, only the
     * keys.  So the values must be passed in as an argument.
     *
     * @param queryKey the String that is tested by {@code String.equals()} to the keys in the
     * LogStatement
     * @param values the array of values corresponding to mKeys
     * @param newValue the replacement value to go into the {@code values} array
     *
     * @returns {@true} if the key exists and the value was successfully set, {@false} otherwise
     *
     * @throws IllegalArgumentException if {@code values.length} is not equal to keys().length()
     */
    public boolean setValue(final String queryKey, final Object[] values, final Object newValue) {
        if (mKeys.length != values.length) {
            throw new IllegalArgumentException("Mismatched number of keys and values.");
        }
        final int length = mKeys.length;
        for (int i = 0; i < length; i++) {
            if (mKeys[i].equals(queryKey)) {
                values[i] = newValue;
                return true;
            }
        }
        return false;
    }
}
