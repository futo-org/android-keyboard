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

package com.android.inputmethod.latin.utils;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JsonUtils {
    private static final String TAG = JsonUtils.class.getSimpleName();

    private static final String INTEGER_CLASS_NAME = Integer.class.getSimpleName();
    private static final String STRING_CLASS_NAME = String.class.getSimpleName();

    private static final String EMPTY_STRING = "";

    public static List<Object> jsonStrToList(final String s) {
        final ArrayList<Object> list = new ArrayList<>();
        final JsonReader reader = new JsonReader(new StringReader(s));
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                while (reader.hasNext()) {
                    final String name = reader.nextName();
                    if (name.equals(INTEGER_CLASS_NAME)) {
                        list.add(reader.nextInt());
                    } else if (name.equals(STRING_CLASS_NAME)) {
                        list.add(reader.nextString());
                    } else {
                        Log.w(TAG, "Invalid name: " + name);
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
            reader.endArray();
            return list;
        } catch (final IOException e) {
        } finally {
            close(reader);
        }
        return Collections.<Object>emptyList();
    }

    public static String listToJsonStr(final List<Object> list) {
        if (list == null || list.isEmpty()) {
            return EMPTY_STRING;
        }
        final StringWriter sw = new StringWriter();
        final JsonWriter writer = new JsonWriter(sw);
        try {
            writer.beginArray();
            for (final Object o : list) {
                writer.beginObject();
                if (o instanceof Integer) {
                    writer.name(INTEGER_CLASS_NAME).value((Integer)o);
                } else if (o instanceof String) {
                    writer.name(STRING_CLASS_NAME).value((String)o);
                }
                writer.endObject();
            }
            writer.endArray();
            return sw.toString();
        } catch (final IOException e) {
        } finally {
            close(writer);
        }
        return EMPTY_STRING;
    }

    private static void close(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException e) {
            // Ignore
        }
    }
}
