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

import android.content.SharedPreferences;
import android.util.JsonWriter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.IOException;

/**
 * A template for typed information stored in the logs.
 *
 * A LogStatement contains a name, keys, and flags about whether the {@code Object[] values}
 * associated with the {@code String[] keys} are likely to reveal information about the user.  The
 * actual values are stored separately.
 */
public class LogStatement {
    private static final String TAG = LogStatement.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;

    // Constants for particular statements
    public static final String TYPE_POINTER_TRACKER_CALL_LISTENER_ON_CODE_INPUT =
            "PointerTrackerCallListenerOnCodeInput";
    public static final String KEY_CODE = "code";
    public static final String VALUE_RESEARCH = "research";
    public static final String TYPE_MAIN_KEYBOARD_VIEW_ON_LONG_PRESS =
            "MainKeyboardViewOnLongPress";
    public static final String ACTION = "action";
    public static final String VALUE_DOWN = "DOWN";
    public static final String TYPE_MOTION_EVENT = "MotionEvent";
    public static final String KEY_IS_LOGGING_RELATED = "isLoggingRelated";

    // Keys for internal key/value pairs
    private static final String CURRENT_TIME_KEY = "_ct";
    private static final String UPTIME_KEY = "_ut";
    private static final String EVENT_TYPE_KEY = "_ty";

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

    /**
     * Write the contents out through jsonWriter.
     *
     * The JsonWriter class must have already had {@code JsonWriter.beginArray} called on it.
     *
     * Note that this method is not thread safe for the same jsonWriter.  Callers must ensure
     * thread safety.
     */
    public boolean outputToLocked(final JsonWriter jsonWriter, final Long time,
            final Object... values) {
        if (DEBUG) {
            if (mKeys.length != values.length) {
                Log.d(TAG, "Key and Value list sizes do not match. " + mType);
            }
        }
        try {
            jsonWriter.beginObject();
            jsonWriter.name(CURRENT_TIME_KEY).value(System.currentTimeMillis());
            jsonWriter.name(UPTIME_KEY).value(time);
            jsonWriter.name(EVENT_TYPE_KEY).value(mType);
            final int length = values.length;
            for (int i = 0; i < length; i++) {
                jsonWriter.name(mKeys[i]);
                final Object value = values[i];
                if (value instanceof CharSequence) {
                    jsonWriter.value(value.toString());
                } else if (value instanceof Number) {
                    jsonWriter.value((Number) value);
                } else if (value instanceof Boolean) {
                    jsonWriter.value((Boolean) value);
                } else if (value instanceof CompletionInfo[]) {
                    JsonUtils.writeJson((CompletionInfo[]) value, jsonWriter);
                } else if (value instanceof SharedPreferences) {
                    JsonUtils.writeJson((SharedPreferences) value, jsonWriter);
                } else if (value instanceof Key[]) {
                    JsonUtils.writeJson((Key[]) value, jsonWriter);
                } else if (value instanceof SuggestedWords) {
                    JsonUtils.writeJson((SuggestedWords) value, jsonWriter);
                } else if (value instanceof MotionEvent) {
                    JsonUtils.writeJson((MotionEvent) value, jsonWriter);
                } else if (value == null) {
                    jsonWriter.nullValue();
                } else {
                    if (DEBUG) {
                        Log.w(TAG, "Unrecognized type to be logged: "
                                + (value == null ? "<null>" : value.getClass().getName()));
                    }
                    jsonWriter.nullValue();
                }
            }
            jsonWriter.endObject();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Error in JsonWriter; skipping LogStatement");
            return false;
        }
        return true;
    }
}
