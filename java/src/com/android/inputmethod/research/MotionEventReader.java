/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.research;

import android.util.JsonReader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MotionEventReader {
    private static final String TAG = MotionEventReader.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    // Assumes that MotionEvent.ACTION_MASK does not have all bits set.`
    private static final int UNINITIALIZED_ACTION = ~MotionEvent.ACTION_MASK;
    // No legitimate int is negative
    private static final int UNINITIALIZED_INT = -1;
    // No legitimate long is negative
    private static final long UNINITIALIZED_LONG = -1L;
    // No legitimate float is negative
    private static final float UNINITIALIZED_FLOAT = -1.0f;

    public ReplayData readMotionEventData(final File file) {
        final ReplayData replayData = new ReplayData();
        try {
            // Read file
            final JsonReader jsonReader = new JsonReader(new BufferedReader(new InputStreamReader(
                    new FileInputStream(file))));
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                readLogStatement(jsonReader, replayData);
            }
            jsonReader.endArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return replayData;
    }

    @UsedForTesting
    static class ReplayData {
        final ArrayList<Integer> mActions = new ArrayList<Integer>();
        final ArrayList<PointerProperties[]> mPointerPropertiesArrays
                = new ArrayList<PointerProperties[]>();
        final ArrayList<PointerCoords[]> mPointerCoordsArrays = new ArrayList<PointerCoords[]>();
        final ArrayList<Long> mTimes = new ArrayList<Long>();
    }

    /**
     * Read motion data from a logStatement and store it in {@code replayData}.
     *
     * Two kinds of logStatements can be read.  In the first variant, the MotionEvent data is
     * represented as attributes at the top level like so:
     *
     * <pre>
     * {
     *   "_ct": 1359590400000,
     *   "_ut": 4381933,
     *   "_ty": "MotionEvent",
     *   "action": "UP",
     *   "isLoggingRelated": false,
     *   "x": 100,
     *   "y": 200
     * }
     * </pre>
     *
     * In the second variant, there is a separate attribute for the MotionEvent that includes
     * historical data if present:
     *
     * <pre>
     * {
     *   "_ct": 135959040000,
     *   "_ut": 4382702,
     *   "_ty": "MotionEvent",
     *   "action": "MOVE",
     *   "isLoggingRelated": false,
     *   "motionEvent": {
     *     "pointerIds": [
     *       0
     *     ],
     *     "xyt": [
     *       {
     *         "t": 4382551,
     *         "d": [
     *           {
     *             "x": 141.25,
     *             "y": 151.8485107421875,
     *             "toma": 101.82337188720703,
     *             "tomi": 101.82337188720703,
     *             "o": 0.0
     *           }
     *         ]
     *       },
     *       {
     *         "t": 4382559,
     *         "d": [
     *           {
     *             "x": 140.7266082763672,
     *             "y": 151.8485107421875,
     *             "toma": 101.82337188720703,
     *             "tomi": 101.82337188720703,
     *             "o": 0.0
     *           }
     *         ]
     *       }
     *     ]
     *   }
     * },
     * </pre>
     */
    @UsedForTesting
    /* package for test */ void readLogStatement(final JsonReader jsonReader,
            final ReplayData replayData) throws IOException {
        String logStatementType = null;
        int actionType = UNINITIALIZED_ACTION;
        int x = UNINITIALIZED_INT;
        int y = UNINITIALIZED_INT;
        long time = UNINITIALIZED_LONG;
        boolean isLoggingRelated = false;

        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            final String key = jsonReader.nextName();
            if (key.equals("_ty")) {
                logStatementType = jsonReader.nextString();
            } else if (key.equals("_ut")) {
                time = jsonReader.nextLong();
            } else if (key.equals("x")) {
                x = jsonReader.nextInt();
            } else if (key.equals("y")) {
                y = jsonReader.nextInt();
            } else if (key.equals("action")) {
                final String s = jsonReader.nextString();
                if (s.equals("UP")) {
                    actionType = MotionEvent.ACTION_UP;
                } else if (s.equals("DOWN")) {
                    actionType = MotionEvent.ACTION_DOWN;
                } else if (s.equals("MOVE")) {
                    actionType = MotionEvent.ACTION_MOVE;
                }
            } else if (key.equals("loggingRelated")) {
                isLoggingRelated = jsonReader.nextBoolean();
            } else if (logStatementType != null && logStatementType.equals("MotionEvent")
                    && key.equals("motionEvent")) {
                if (actionType == UNINITIALIZED_ACTION) {
                    Log.e(TAG, "no actionType assigned in MotionEvent json");
                }
                // Second variant of LogStatement.
                if (isLoggingRelated) {
                    jsonReader.skipValue();
                } else {
                    readEmbeddedMotionEvent(jsonReader, replayData, actionType);
                }
            } else {
                if (DEBUG) {
                    Log.w(TAG, "Unknown JSON key in LogStatement: " + key);
                }
                jsonReader.skipValue();
            }
        }
        jsonReader.endObject();

        if (logStatementType != null && time != UNINITIALIZED_LONG && x != UNINITIALIZED_INT
                && y != UNINITIALIZED_INT && actionType != UNINITIALIZED_ACTION
                && logStatementType.equals("MotionEvent") && !isLoggingRelated) {
            // First variant of LogStatement.
            final PointerProperties pointerProperties = new PointerProperties();
            pointerProperties.id = 0;
            pointerProperties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
            final PointerProperties[] pointerPropertiesArray = {
                pointerProperties
            };
            final PointerCoords pointerCoords = new PointerCoords();
            pointerCoords.x = x;
            pointerCoords.y = y;
            pointerCoords.pressure = 1.0f;
            pointerCoords.size = 1.0f;
            final PointerCoords[] pointerCoordsArray = {
                pointerCoords
            };
            addMotionEventData(replayData, actionType, time, pointerPropertiesArray,
                    pointerCoordsArray);
        }
    }

    private void readEmbeddedMotionEvent(final JsonReader jsonReader, final ReplayData replayData,
            final int actionType) throws IOException {
        jsonReader.beginObject();
        PointerProperties[] pointerPropertiesArray = null;
        while (jsonReader.hasNext()) {  // pointerIds/xyt
            final String name = jsonReader.nextName();
            if (name.equals("pointerIds")) {
                pointerPropertiesArray = readPointerProperties(jsonReader);
            } else if (name.equals("xyt")) {
                readPointerData(jsonReader, replayData, actionType, pointerPropertiesArray);
            }
        }
        jsonReader.endObject();
    }

    private PointerProperties[] readPointerProperties(final JsonReader jsonReader)
            throws IOException {
        final ArrayList<PointerProperties> pointerPropertiesArrayList =
                new ArrayList<PointerProperties>();
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
            final PointerProperties pointerProperties = new PointerProperties();
            pointerProperties.id = jsonReader.nextInt();
            pointerProperties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
            pointerPropertiesArrayList.add(pointerProperties);
        }
        jsonReader.endArray();
        return pointerPropertiesArrayList.toArray(
                new PointerProperties[pointerPropertiesArrayList.size()]);
    }

    private void readPointerData(final JsonReader jsonReader, final ReplayData replayData,
            final int actionType, final PointerProperties[] pointerPropertiesArray)
            throws IOException {
        if (pointerPropertiesArray == null) {
            Log.e(TAG, "PointerIDs must be given before xyt data in json for MotionEvent");
            jsonReader.skipValue();
            return;
        }
        long time = UNINITIALIZED_LONG;
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {  // Array of historical data
            jsonReader.beginObject();
            final ArrayList<PointerCoords> pointerCoordsArrayList = new ArrayList<PointerCoords>();
            while (jsonReader.hasNext()) {  // Time/data object
                final String name = jsonReader.nextName();
                if (name.equals("t")) {
                    time = jsonReader.nextLong();
                } else if (name.equals("d")) {
                    jsonReader.beginArray();
                    while (jsonReader.hasNext()) {  // array of data per pointer
                        final PointerCoords pointerCoords = readPointerCoords(jsonReader);
                        if (pointerCoords != null) {
                            pointerCoordsArrayList.add(pointerCoords);
                        }
                    }
                    jsonReader.endArray();
                } else {
                    jsonReader.skipValue();
                }
            }
            jsonReader.endObject();
            // Data was recorded as historical events, but must be split apart into
            // separate MotionEvents for replaying
            if (time != UNINITIALIZED_LONG) {
                addMotionEventData(replayData, actionType, time, pointerPropertiesArray,
                        pointerCoordsArrayList.toArray(
                                new PointerCoords[pointerCoordsArrayList.size()]));
            } else {
                Log.e(TAG, "Time not assigned in json for MotionEvent");
            }
        }
        jsonReader.endArray();
    }

    private PointerCoords readPointerCoords(final JsonReader jsonReader) throws IOException {
        jsonReader.beginObject();
        float x = UNINITIALIZED_FLOAT;
        float y = UNINITIALIZED_FLOAT;
        while (jsonReader.hasNext()) {  // x,y
            final String name = jsonReader.nextName();
            if (name.equals("x")) {
                x = (float) jsonReader.nextDouble();
            } else if (name.equals("y")) {
                y = (float) jsonReader.nextDouble();
            } else {
                jsonReader.skipValue();
            }
        }
        jsonReader.endObject();

        if (Float.compare(x, UNINITIALIZED_FLOAT) == 0
                || Float.compare(y, UNINITIALIZED_FLOAT) == 0) {
            Log.w(TAG, "missing x or y value in MotionEvent json");
            return null;
        }
        final PointerCoords pointerCoords = new PointerCoords();
        pointerCoords.x = x;
        pointerCoords.y = y;
        pointerCoords.pressure = 1.0f;
        pointerCoords.size = 1.0f;
        return pointerCoords;
    }

    private void addMotionEventData(final ReplayData replayData, final int actionType,
            final long time, final PointerProperties[] pointerProperties,
            final PointerCoords[] pointerCoords) {
        replayData.mActions.add(actionType);
        replayData.mTimes.add(time);
        replayData.mPointerPropertiesArrays.add(pointerProperties);
        replayData.mPointerCoordsArrays.add(pointerCoords);
    }
}
