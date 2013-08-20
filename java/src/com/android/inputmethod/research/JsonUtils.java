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

package com.android.inputmethod.research;

import android.content.SharedPreferences;
import android.util.JsonWriter;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.io.IOException;
import java.util.Map;

/**
 * Routines for mapping classes and variables to JSON representations for logging.
 */
/* package */ class JsonUtils {
    private JsonUtils() {
        // This utility class is not publicly instantiable.
    }

    /* package */ static void writeJson(final CompletionInfo[] ci, final JsonWriter jsonWriter)
            throws IOException {
        jsonWriter.beginArray();
        for (int j = 0; j < ci.length; j++) {
            jsonWriter.value(ci[j].toString());
        }
        jsonWriter.endArray();
    }

    /* package */ static void writeJson(final SharedPreferences prefs, final JsonWriter jsonWriter)
            throws IOException {
        jsonWriter.beginObject();
        for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
            jsonWriter.name(entry.getKey());
            final Object innerValue = entry.getValue();
            if (innerValue == null) {
                jsonWriter.nullValue();
            } else if (innerValue instanceof Boolean) {
                jsonWriter.value((Boolean) innerValue);
            } else if (innerValue instanceof Number) {
                jsonWriter.value((Number) innerValue);
            } else {
                jsonWriter.value(innerValue.toString());
            }
        }
        jsonWriter.endObject();
    }

    /* package */ static void writeJson(final Key[] keys, final JsonWriter jsonWriter)
            throws IOException {
        jsonWriter.beginArray();
        for (Key key : keys) {
            writeJson(key, jsonWriter);
        }
        jsonWriter.endArray();
    }

    private static void writeJson(final Key key, final JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("code").value(key.getCode());
        jsonWriter.name("altCode").value(key.getAltCode());
        jsonWriter.name("x").value(key.getX());
        jsonWriter.name("y").value(key.getY());
        jsonWriter.name("w").value(key.getWidth());
        jsonWriter.name("h").value(key.getHeight());
        jsonWriter.endObject();
    }

    /* package */ static void writeJson(final SuggestedWords words, final JsonWriter jsonWriter)
            throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("typedWordValid").value(words.mTypedWordValid);
        jsonWriter.name("willAutoCorrect")
                .value(words.mWillAutoCorrect);
        jsonWriter.name("isPunctuationSuggestions")
                .value(words.mIsPunctuationSuggestions);
        jsonWriter.name("isObsoleteSuggestions").value(words.mIsObsoleteSuggestions);
        jsonWriter.name("isPrediction").value(words.mIsPrediction);
        jsonWriter.name("suggestedWords");
        jsonWriter.beginArray();
        final int size = words.size();
        for (int j = 0; j < size; j++) {
            final SuggestedWordInfo wordInfo = words.getInfo(j);
            jsonWriter.beginObject();
            jsonWriter.name("word").value(wordInfo.toString());
            jsonWriter.name("score").value(wordInfo.mScore);
            jsonWriter.name("kind").value(wordInfo.mKind);
            jsonWriter.name("sourceDict").value(wordInfo.mSourceDict.mDictType);
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    /* package */ static void writeJson(final MotionEvent me, final JsonWriter jsonWriter)
            throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("pointerIds");
        jsonWriter.beginArray();
        final int pointerCount = me.getPointerCount();
        for (int index = 0; index < pointerCount; index++) {
            jsonWriter.value(me.getPointerId(index));
        }
        jsonWriter.endArray();

        jsonWriter.name("xyt");
        jsonWriter.beginArray();
        final int historicalSize = me.getHistorySize();
        for (int index = 0; index < historicalSize; index++) {
            jsonWriter.beginObject();
            jsonWriter.name("t");
            jsonWriter.value(me.getHistoricalEventTime(index));
            jsonWriter.name("d");
            jsonWriter.beginArray();
            for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
                jsonWriter.beginObject();
                jsonWriter.name("x");
                jsonWriter.value(me.getHistoricalX(pointerIndex, index));
                jsonWriter.name("y");
                jsonWriter.value(me.getHistoricalY(pointerIndex, index));
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.beginObject();
        jsonWriter.name("t");
        jsonWriter.value(me.getEventTime());
        jsonWriter.name("d");
        jsonWriter.beginArray();
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
            jsonWriter.beginObject();
            jsonWriter.name("x");
            jsonWriter.value(me.getX(pointerIndex));
            jsonWriter.name("y");
            jsonWriter.value(me.getY(pointerIndex));
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        jsonWriter.endObject();
        jsonWriter.endArray();
        jsonWriter.endObject();
    }
}
